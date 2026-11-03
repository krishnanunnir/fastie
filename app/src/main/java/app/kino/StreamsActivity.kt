package app.kino

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.chip.Chip
import org.json.JSONArray
import org.json.JSONObject

/**
 * Every stream your addons offer for one movie or episode, grouped by addon in the user's order. Each addon's
 * section appears as soon as it answers, so one slow addon doesn't hold up the rest. Rows lead with a quality badge,
 * then the release name and what it carries (HDR, codec, sound, size), pulled out of each addon's own free text.
 */
class StreamsActivity : Activity() {
    private lateinit var item: Item
    private lateinit var type: String
    private lateinit var id: String
    private var season = -1
    private var episode = -1

    private lateinit var col: LinearLayout
    private lateinit var status: TextView
    private val progress by lazy { progressLine() }
    private val chipRow by lazy { LinearLayout(this) }
    private val chipScroll by lazy {
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            visibility = View.GONE
            addView(chipRow)
        }
    }
    private val chips = linkedMapOf<String?, Chip>()

    /** Rows by the quality they're filed under, so the chips can filter them. */
    private val rows = mutableListOf<Pair<View, String?>>()
    private val sections = mutableListOf<Section>()
    private var filter: String? = null

    private var total = 0
    private var answered = 0
    private var hidden = 0
    private val failed = mutableListOf<String>()

    private class Section(val addon: Addon, val view: LinearLayout, val rows: MutableList<View> = mutableListOf(), var done: Boolean = false)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        edgeToEdge()
        item = Item.from(JSONObject(intent.getStringExtra("item")!!))
        type = intent.getStringExtra("type")!!
        id = intent.getStringExtra("id")!!
        season = intent.getIntExtra("season", -1)
        episode = intent.getIntExtra("episode", -1)

        val root = FrameLayout(this).apply { setBackgroundColor(C.ground) }
        val layers = backdrop(root, if (isTv) 1f else 0.4f)
        crossfade(layers, item.backgroundOrHub, resources.displayMetrics.widthPixels)
        // Dim the art further: this screen is for reading.
        root.addView(View(this).apply { setBackgroundColor(C.alpha(C.ground, 0.7f)) }, FrameLayout.LayoutParams(-1, -1))
        val side = dp(if (isTv) 48 else 16)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(side, dp(if (isTv) 40 else 56), side, dp(48)) }
        col.addView(text(T.DISPLAY, lines = 2, s = item.name.ifBlank { "Streams" }))
        if (season >= 0) {
            val ep = intent.getStringExtra("episodeTitle")?.takeIf { it.isNotBlank() }
            col.addView(text(T.META, C.variant, s = listOfNotNull("Season $season, Episode $episode", ep).joinToString("  ·  "))
                .apply { setPadding(0, dp(8), 0, 0) })
        }
        status = text(T.META, C.variant, s = "Asking your addons for streams…").apply { setPadding(0, dp(24), 0, 0) }
        col.addView(status)
        col.addView(progress, lp(dp(if (isTv) 200 else 160)).apply { topMargin = dp(12) })
        col.addView(chipScroll, lp().apply { topMargin = dp(20) })

        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(col, FrameLayout.LayoutParams(if (isTv) dp(760) else -1, -2))
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        load({ Addons.streamSources(this, type, id) }) { r -> ask(r.getOrDefault(emptyList())) }
    }

    private fun ask(sources: List<Addon>) {
        total = sources.size
        if (sources.isEmpty()) {
            progress.visibility = View.GONE
            status.styled(T.BODY)
            status.text = "None of your addons offer streams for this. Add a stream addon in Settings, or sign in to Nuvio to bring yours."
            return
        }
        // Sections are laid out in addon order up front and stay hidden until their addon answers.
        sources.forEach { a ->
            val section = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
            col.addView(section)
            sections += Section(a, section)
        }
        showProgress()
        sections.forEach { s ->
            Net.fan.execute {
                val r = runCatching { Addons.streams(s.addon, type, id) }
                Net.main.post { if (!isDestroyed) answer(s, r) }
            }
        }
    }

    private fun answer(s: Section, r: Result<Pair<List<Stream>, Int>>) {
        answered++
        s.done = true
        r.onFailure { failed += s.addon.name }
        val (streams, skipped) = r.getOrDefault(emptyList<Stream>() to 0)
        hidden += skipped
        if (streams.isNotEmpty()) {
            s.view.addView(sectionTitle(sectionLabel(s.addon.name, streams.size)))
            streams.forEach { stream ->
                val q = stream.info.quality
                val row = streamRow(stream)
                s.view.addView(row, lp().apply { bottomMargin = dp(8) })
                s.rows += row
                rows += row to q
                if (q != null) addChip(q)
            }
            applyFilter()
            val focused = currentFocus
            if (focused == null) s.rows.first().requestFocus()
            // A faster addon's section may have just opened above the row in focus: keep that row on screen.
            else focused.post { focused.requestRectangleOnScreen(Rect(0, 0, focused.width, focused.height)) }
        }
        showProgress()
    }

    private fun showProgress() {
        val pending = total - answered
        if (pending > 0) {
            progress.fraction = 0.08f + 0.92f * answered / total
            status.text = if (answered == 0) "Asking ${plural(total, "addon")} for streams…"
                else "${plural(rows.size, "stream")} so far  ·  waiting on ${pendingNames()}"
            return
        }
        progress.visibility = View.GONE
        if (rows.isEmpty()) {
            status.styled(T.BODY)
            status.setTextColor(C.onSurface)
            status.text = when {
                hidden > 0 -> "Your addons found ${plural(hidden, "result")}, but only torrents, which Kino can't play. " +
                    "An addon set up with a debrid service gives direct links."
                failed.size == total -> "Couldn't reach ${failed.joinToString(", ")}. Check your connection and try again."
                else -> "No playable streams for this one. Add a stream addon in Settings, or sign in to Nuvio to bring yours."
            }
            return
        }
        status.text = listOfNotNull(
            "${plural(rows.size, "stream")} from ${plural(sections.count { it.rows.isNotEmpty() }, "addon")}",
            hidden.takeIf { it > 0 }?.let { "$it torrent-only hidden" },
            failed.takeIf { it.isNotEmpty() }?.let { "couldn't reach ${it.joinToString(", ")}" },
        ).joinToString("  ·  ")
    }

    private fun pendingNames(): String {
        val names = sections.filter { !it.done }.map { it.addon.name }
        return if (names.size <= 2) names.joinToString(" and ") else "${names.take(2).joinToString(", ")} and ${names.size - 2} more"
    }

    private fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"

    /** "Torrentio  12": the addon in Row type, its count quieter. */
    private fun sectionLabel(name: String, count: Int) = SpannableStringBuilder(name).apply {
        val start = length
        append("  $count")
        setSpan(ForegroundColorSpan(C.variant), start, length, 0)
    }

    // ---- Quality filter -------------------------------------------------------------------------------------------

    /** Adds a chip for [quality] in best-first order; the row only shows once there are two qualities to pick between. */
    private fun addChip(quality: String) {
        if (chips.isEmpty()) chips[null] = chip("All") { pick(null) }.also { chipRow.addView(it, chipLp()) }
        if (quality in chips) return
        val c = chip(quality) { pick(quality) }
        val rank = StreamInfo.QUALITIES.indexOf(quality)
        val at = 1 + chips.keys.count { it != null && StreamInfo.QUALITIES.indexOf(it) < rank }
        chipRow.addView(c, at, chipLp())
        chips[quality] = c
        chipScroll.visibility = if (chips.size > 2) View.VISIBLE else View.GONE
        markChips()
    }

    private fun chipLp() = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }

    private fun pick(quality: String?) {
        filter = quality
        markChips()
        applyFilter()
    }

    private fun markChips() = chips.forEach { (q, c) -> c.isSelected = q == filter }

    private fun applyFilter() {
        rows.forEach { (row, q) -> row.visibility = if (filter == null || q == filter) View.VISIBLE else View.GONE }
        sections.forEach { s -> s.view.visibility = if (s.rows.any { it.visibility == View.VISIBLE }) View.VISIBLE else View.GONE }
    }

    // ---- Rows -----------------------------------------------------------------------------------------------------

    /**
     * A stream: the quality badge, then the release name and a line of what it carries. Like [listRow], the surface
     * inverts to white with ground ink under focus; the badge turns ground so it still reads as a badge.
     */
    private fun streamRow(s: Stream): LinearLayout = LinearLayout(this).apply {
        val info = s.info
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(14), dp(18), dp(14))
        val r = dp(Shape.S).toFloat()
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(C.primary, r))
            addState(intArrayOf(android.R.attr.state_pressed), rounded(C.containerHigh, r))
            addState(intArrayOf(), rounded(C.container, r))
        }
        val focusedOr = { focused: Int, rest: Int -> ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()), intArrayOf(focused, rest)) }

        addView(text(T.LABEL, s = info.quality ?: "").apply {
            gravity = Gravity.CENTER
            minHeight = dp(if (isTv) 34 else 30)
            setPadding(dp(6), dp(4), dp(6), dp(4))
            setTextColor(focusedOr(C.onSurface, C.onSurface))
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(C.ground, dp(4).toFloat()))
                addState(intArrayOf(), rounded(C.containerHighest, dp(4).toFloat()))
            }
            isDuplicateParentStateEnabled = true
            // No quality given: keep the column so the names still line up.
            if (info.quality == null) visibility = View.INVISIBLE
        }, LinearLayout.LayoutParams(dp(if (isTv) 68 else 58), -2).apply { marginEnd = dp(16) })

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isDuplicateParentStateEnabled = true
            addView(text(T.LABEL, lines = 2, s = info.label).apply {
                setTextColor(focusedOr(C.onPrimary, C.onSurface)); isDuplicateParentStateEnabled = true
            })
            val details = listOfNotNull(info.details.ifBlank { null }, if (s.subtitles.isNotEmpty()) "Subtitles" else null)
                .joinToString("  ·  ")
            if (details.isNotBlank()) addView(text(T.META, lines = 2, s = details).apply {
                setTextColor(focusedOr(0xFF4A4A52.toInt(), C.variant)); isDuplicateParentStateEnabled = true
                setPadding(0, dp(6), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))

        isFocusable = true
        isClickable = true
        setOnClickListener { play(s) }
    }

    private fun play(s: Stream) {
        if (Store.externalPlayer(this)) {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(s.url), "video/*").putExtra("title", item.name)
            runCatching { startActivity(Intent.createChooser(view, "Play with")) }
            return
        }
        val subs = JSONArray().apply { s.subtitles.forEach { (lang, url) -> put(JSONObject().put("lang", lang).put("url", url)) } }
        startActivity(Intent(this, PlayerActivity::class.java)
            .putExtra("url", s.url).putExtra("subs", subs.toString())
            .putExtra("type", type).putExtra("id", id).putExtra("item", item.toJson().toString())
            .putExtra("season", season).putExtra("episode", episode))
    }
}
