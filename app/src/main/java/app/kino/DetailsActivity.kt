package app.kino

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

/** One title: backdrop, synopsis, Play and My List; for series, seasons and episodes. */
class DetailsActivity : Activity() {
    private lateinit var item: Item
    private val header by lazy { LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } }
    private val logo by lazy { ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_START } }
    private val title by lazy { text(T.DISPLAY, lines = 2) }
    private val meta by lazy { text(T.META, C.alpha(C.onSurface, 0.72f), lines = 2) }
    private val desc by lazy { text(T.BODY, C.alpha(C.onSurface, 0.82f), lines = if (isTv) 3 else 8) }
    private val seasons by lazy { LinearLayout(this) }
    // Every stream for Play's target, best for this device first, found as soon as the page opens.
    private val streamSlot by lazy { LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } }
    private var streams: List<Stream> = emptyList()
    private var streamsFor: String? = null
    private var showAll = false
    private val links = HashMap<String?, String>()
    private lateinit var play: MaterialButton
    private lateinit var list: MaterialButton
    private val episodes = mutableListOf<Video>()
    private var season = -1
    private lateinit var adapter: EpisodeAdapter

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        edgeToEdge()
        item = Item.from(JSONObject(intent.getStringExtra("item")!!))
        val root = FrameLayout(this).apply { setBackgroundColor(C.ground) }
        // As on Home: on TV the art fills the screen behind the title.
        val layers = backdrop(root, if (isTv) 1f else 0.55f, bottom = if (isTv) 0.75f else 0.55f)
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val side = dp(if (isTv) 48 else 16)

        // The logo keeps its own aspect up to this box, so short logos don't leave a gap above the meta.
        logo.adjustViewBounds = true
        logo.maxWidth = if (isTv) (w * 0.36f).toInt() else (w * 0.66f).toInt()
        logo.maxHeight = dp(if (isTv) 104 else 80)
        header.addView(logo, lp(-2, -2).apply { bottomMargin = dp(16) })
        if (isTv) title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 44f)
        header.addView(title, lp().apply { bottomMargin = dp(10) })
        header.addView(meta, lp().apply { bottomMargin = dp(12) })
        header.addView(desc, lp(if (isTv) (w * 0.45f).toInt() else -1, -2))
        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        play = button("Play", R.drawable.ic_play, primary = true) { play() }
        list = button("My List", R.drawable.ic_add) { toggleList() }
        // Just the icon on phones, where Play and My List already share the width.
        val save = button(if (isTv) "Download" else "", R.drawable.ic_download) { target()?.let(::download) }.apply {
            contentDescription = "Download"
            if (!isTv) { setPadding(0, dp(10), 0, dp(10)); iconPadding = 0; iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START }
        }
        buttons.addView(play, if (isTv) lp(-2, -2).apply { marginEnd = dp(12) } else lp(0, -2).apply { weight = 1f; marginEnd = dp(10) })
        buttons.addView(list, if (isTv) lp(-2, -2).apply { marginEnd = dp(12) } else lp(0, -2).apply { weight = 1f; marginEnd = dp(10) })
        buttons.addView(save, lp(if (isTv) -2 else dp(56), -2))
        header.addView(buttons, lp().apply { topMargin = dp(22) })
        header.addView(streamSlot, lp(if (isTv) dp(640) else -1, -2))
        header.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(seasons)
        }, lp().apply { topMargin = dp(26) })
        header.setPadding(side, if (isTv) dp(64) else (h * 0.32f).toInt(), side, dp(8))
        header.layoutParams = RecyclerView.LayoutParams(-1, -2)

        adapter = EpisodeAdapter()
        // The art fades to the ground as episodes scroll over it, so their text stays readable.
        val dim = View(this).apply { setBackgroundColor(C.ground); alpha = 0f }
        root.addView(dim, FrameLayout.LayoutParams(-1, -1))
        root.addView(RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@DetailsActivity.adapter
            itemAnimator = null
            clipToPadding = false
            setPadding(0, 0, 0, dp(48))
            setOnApplyWindowInsetsListener { v, insets ->
                @Suppress("DEPRECATION") v.setPadding(0, 0, 0, insets.systemWindowInsetBottom + dp(48)); insets
            }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    dim.alpha = (rv.computeVerticalScrollOffset() / (h * 0.35f)).coerceIn(0f, 0.92f)
                }
            })
        }, FrameLayout.LayoutParams(-1, -1))
        // The status bar gets the same ground once episodes scroll under it.
        val statusScrim = View(this).apply { setBackgroundColor(C.ground); alpha = 0f }
        root.addView(statusScrim, FrameLayout.LayoutParams(-1, 0))
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION") statusScrim.layoutParams.height = insets.systemWindowInsetTop
            statusScrim.requestLayout(); insets
        }
        dim.viewTreeObserver.addOnDrawListener { statusScrim.alpha = dim.alpha / 0.92f }
        setContentView(root)

        bind(item)
        crossfade(layers, item.backgroundOrHub, w)
        play.requestFocus()
        load({ Addons.meta(this, item.type, item.id) }) { r ->
            r.onSuccess { full ->
                item = full
                bind(full)
                if (full.background != null) crossfade(layers, full.background, w)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) { updatePlay(); adapter.notifyDataSetChanged() }
    }

    private fun bind(i: Item) {
        title.text = i.name
        meta.text = metaText(i, meta.textSize)
        desc.text = i.description
        val logoUrl = i.logo ?: if (i.id.startsWith("tt")) "https://images.metahub.space/logo/medium/${i.id}/img" else null
        Images.load(logo, logoUrl, logo.maxWidth, alpha = true) { ok ->
            logo.visibility = if (ok) View.VISIBLE else View.GONE
            title.visibility = if (ok) View.GONE else View.VISIBLE
        }
        setListLabel(list, Store.inLibrary(this, i.id))
        // Specials (season 0) go last, as every streaming app lists them.
        val all = i.videos.filter { it.id.isNotBlank() }
        val numbers = all.map { it.season }.distinct().sortedWith(compareBy({ it == 0 }, { it }))
        seasons.removeAllViews()
        if (numbers.isNotEmpty()) {
            if (season !in numbers) season = resumeTarget()?.season ?: numbers.first()
            numbers.forEach { n -> seasons.addView(chip(if (n == 0) "Specials" else "Season $n") { showSeason(n) }.apply { tag = n }, lp(-2, -2).apply { marginEnd = dp(8) }) }
            showSeason(season)
        }
        updatePlay()
    }

    private fun showSeason(n: Int) {
        season = n
        for (i in 0 until seasons.childCount) seasons.getChildAt(i).let { it.isSelected = it.tag == n }
        episodes.clear()
        episodes += item.videos.filter { it.season == n && it.id.isNotBlank() }
        adapter.notifyDataSetChanged()
    }

    /** The episode to resume, or the one after the last finished one. Null before anything was watched. */
    private fun resumeTarget(): Video? {
        val last = Store.progress(this).filter { it.contentId == item.id }.maxByOrNull { it.lastWatched } ?: return null
        val vids = item.videos.filter { it.season > 0 }
        val i = vids.indexOfFirst { it.id == last.videoId || (it.season == last.season && it.episode == last.episode) }
        if (i < 0) return null
        return if (last.finished) vids.getOrNull(i + 1) else vids[i]
    }

    private fun updatePlay() {
        findStreams()
        if (item.isSeries && item.videos.isNotEmpty()) {
            val target = resumeTarget()
            val v = target ?: item.videos.firstOrNull { it.season > 0 } ?: item.videos.first()
            val started = target != null && (Store.progressFor(this, v.id)?.position ?: 0) > 0
            play.text = "${if (started) "Resume" else "Play"} S${v.season}:E${v.episode}"
        } else {
            val p = Store.progressFor(this, item.id)
            play.text = if (p != null && p.position > 0 && !p.finished) "Resume" else "Play"
        }
    }

    /** What Play plays: the episode to resume or start for a series, the movie itself otherwise. Null until episodes load. */
    private fun target(): Video? = when {
        item.isSeries && item.videos.isNotEmpty() -> resumeTarget() ?: item.videos.firstOrNull { it.season > 0 } ?: item.videos.first()
        item.isSeries -> null
        else -> Video(item.id, "", -1, -1, null, null)
    }

    private fun type() = if (item.isSeries) "series" else item.type

    /**
     * Plays straight away: the link Play's target last played from (Resume), else the best stream listed below, else
     * the player opens and finds the best stream itself while the list is still loading.
     */
    private fun play() {
        val v = target() ?: return
        val s = streams.firstOrNull()?.takeIf { streamsFor == v.id }
        if (Store.lastLink(this, v.id) == null && s != null) playStream(item, s, links[s.hash], type(), v.id, v.season, v.episode)
        else playBest(item, type(), v.id, v.season, v.episode)
    }

    /** Lists every playable stream for Play's target under the buttons, best first; tapping one plays it. */
    private fun findStreams() {
        val v = target() ?: return
        if (streamsFor == v.id) return
        streamsFor = v.id; streams = emptyList(); showAll = false
        renderStreams("Finding streams…")
        load({ findStreams(this, type(), v.id, v.season >= 0) }) { r ->
            if (streamsFor != v.id) return@load
            val found = r.getOrDefault(Found(emptyList(), 0, null))
            streams = found.streams
            renderStreams(found.emptyMessage())
            // Get the best one's link ready too, so Play starts at once.
            streams.firstOrNull()?.takeIf { it.hash != null }?.let { s ->
                load({ Debrid.connected(this)!!.resolve(s.hash!!, s.filename, s.fileIdx) }) { l -> l.getOrNull()?.let { links[s.hash] = it } }
            }
        }
    }

    private fun renderStreams(empty: String) {
        streamSlot.removeAllViews()
        val v = target() ?: return
        streamSlot.addView(sectionTitle(if (v.season >= 0) "Streams for S${v.season}:E${v.episode}" else "Streams"))
        if (streams.isEmpty()) return streamSlot.addView(text(T.BODY, C.variant, s = empty))
        val shown = if (showAll) streams else streams.take(5)
        shown.forEachIndexed { i, s ->
            streamSlot.addView(streamCard(s, best = i == 0) { playStream(item, s, links[s.hash], type(), v.id, v.season, v.episode) },
                lp().apply { bottomMargin = dp(10) })
        }
        if (!showAll && streams.size > shown.size) streamSlot.addView(listRow("Show all ${streams.size} streams", null) {
            showAll = true; renderStreams(empty); streamSlot.getChildAt(6)?.requestFocus()
        })
    }

    /** An episode plays its best stream straight away; the player finds it behind the poster. */
    private fun playEpisode(v: Video) = playBest(item, "series", v.id, v.season, v.episode)

    /** Picks a stream for [v], best first, and saves it to the device. */
    private fun download(v: Video) = chooseStream(item, type(), v.id, v.season, v.episode, "Download a stream") { s ->
        download(item, s, type(), v.id, v.season, v.episode)
    }

    private fun toggleList() {
        val on = !Store.inLibrary(this, item.id)
        Store.setInLibrary(this, item, on)
        Nuvio.later(this) { Nuvio.pushLibrary(it, item, on) }
        setListLabel(list, on)
    }

    private inner class EpisodeAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 1 + episodes.size
        override fun getItemViewType(position: Int) = if (position == 0) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
            if (type == 1) return object : RecyclerView.ViewHolder(header) {}
            val ctx = parent.context
            val side = dp(if (isTv) 48 else 8)
            val r = dp(Shape.M).toFloat()
            val row = LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                setPadding(dp(10), dp(10), dp(16), dp(10))
                // Imagery plus text: a raised fill and a 2dp white ring under focus; the type stays white.
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), rounded(C.containerHigh, r, dp(2), C.onSurface))
                    addState(intArrayOf(android.R.attr.state_pressed), rounded(C.containerHigh, r))
                    addState(intArrayOf(), rounded(Color.TRANSPARENT, r))
                }
                layoutParams = RecyclerView.LayoutParams(if (isTv) (resources.displayMetrics.widthPixels * 0.62f).toInt() else -1, -2)
                    .apply { setMargins(side, 0, side, dp(4)) }
            }
            val tw = dp(if (isTv) 176 else 132)
            val thumb = FrameLayout(ctx).apply {
                background = rounded(C.container, dp(Shape.M).toFloat())
                outlineProvider = MorphOutline(dp(Shape.M).toFloat())
                clipToOutline = true
                addView(ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1))
                addView(ctx.progressLine(), FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM).apply { setMargins(dp(10), 0, dp(10), dp(8)) })
            }
            row.addView(thumb, lp(tw, tw * 9 / 16))
            val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, 0, 0) }
            texts.addView(text(T.LABEL, lines = 1))
            texts.addView(text(T.META, C.variant, lines = if (isTv) 2 else 3).apply { setPadding(0, dp(6), 0, 0) })
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            return object : RecyclerView.ViewHolder(row) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
            if (pos == 0) return
            val v = episodes[pos - 1]
            val row = h.itemView as LinearLayout
            val thumb = row.getChildAt(0) as FrameLayout
            val texts = row.getChildAt(1) as LinearLayout
            Images.load(thumb.getChildAt(0) as ImageView, v.thumbnail, dp(176))
            (texts.getChildAt(0) as TextView).text = "${v.episode}.  ${v.title.ifBlank { "Episode ${v.episode}" }}"
            (texts.getChildAt(1) as TextView).text = v.overview
            val p = Store.progressFor(this@DetailsActivity, v.id)
            val bar = thumb.getChildAt(1) as LinearProgressIndicator
            bar.visibility = if (p != null && p.position > 0) View.VISIBLE else View.GONE
            if (p != null) bar.fraction = p.fraction.coerceAtLeast(0.04f)
            row.contentDescription = "Episode ${v.episode}, ${v.title}"
            row.setOnClickListener { playEpisode(v) }
            row.setOnLongClickListener { download(v); true } // a long press downloads the episode
        }
    }
}
