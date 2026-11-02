package app.fastie

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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

/** One title: backdrop, synopsis, Play and My List; for series, seasons and episodes. */
class DetailsActivity : Activity() {
    private lateinit var item: Item
    private val header by lazy { LinearLayout(this).apply { orientation = LinearLayout.VERTICAL } }
    private val logo by lazy { ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_START } }
    private val title by lazy { text(T.DISPLAY, lines = 2) }
    private val meta by lazy { text(T.META, C.variant, lines = 2) }
    private val desc by lazy { text(T.BODY, C.variant, lines = if (isTv) 4 else 8) }
    private val seasons by lazy { LinearLayout(this) }
    private lateinit var play: TextView
    private lateinit var list: TextView
    private val episodes = mutableListOf<Video>()
    private var season = -1
    private lateinit var adapter: EpisodeAdapter

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        edgeToEdge()
        item = Item.from(JSONObject(intent.getStringExtra("item")!!))
        val root = FrameLayout(this).apply { setBackgroundColor(C.ground) }
        val layers = backdrop(root, if (isTv) 0.8f else 0.5f)
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val side = dp(if (isTv) 56 else 16)

        header.addView(logo, lp(if (isTv) (w * 0.3f).toInt() else (w * 0.6f).toInt(), dp(if (isTv) 104 else 80)).apply { bottomMargin = dp(14) })
        header.addView(title, lp().apply { bottomMargin = dp(10) })
        header.addView(meta, lp().apply { bottomMargin = dp(12) })
        header.addView(desc, lp(if (isTv) (w * 0.45f).toInt() else -1, -2))
        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        play = button("Play", R.drawable.ic_play, primary = true) { play() }
        list = button("My List", R.drawable.ic_add) { toggleList() }
        buttons.addView(play, if (isTv) lp(-2, -2).apply { marginEnd = dp(12) } else lp(0, -2).apply { weight = 1f; marginEnd = dp(10) })
        buttons.addView(list, if (isTv) lp(-2, -2) else lp(0, -2).apply { weight = 1f })
        header.addView(buttons, lp().apply { topMargin = dp(22) })
        header.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(seasons)
        }, lp().apply { topMargin = dp(26) })
        header.setPadding(side, if (isTv) (h * 0.1f).toInt() else (h * 0.32f).toInt(), side, dp(8))
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
        meta.text = listOfNotNull(i.metaLine.ifBlank { null }, i.rating?.let { "IMDb $it" }).joinToString("  ·  ")
        desc.text = i.description
        val logoUrl = i.logo ?: if (i.id.startsWith("tt")) "https://images.metahub.space/logo/medium/${i.id}/img" else null
        Images.load(logo, logoUrl, (resources.displayMetrics.widthPixels * 0.4f).toInt(), alpha = true) { ok ->
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
            numbers.forEach { n -> seasons.addView(chip(if (n == 0) "Specials" else "Season $n", n), lp(-2, -2).apply { marginEnd = dp(8) }) }
            showSeason(season)
        }
        updatePlay()
    }

    private fun chip(label: String, n: Int) = text(T.LABEL, s = label).apply {
        isFocusable = true
        isClickable = true
        gravity = Gravity.CENTER
        minHeight = dp(if (isTv) 40 else 44)
        setPadding(dp(16), dp(8), dp(16), dp(8))
        // Material filter chip: 8dp corners, a tonal fill when selected, primary when focused.
        val r = dp(Shape.S).toFloat()
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(C.primary, r))
            addState(intArrayOf(android.R.attr.state_selected), rounded(C.secondaryContainer, r))
            addState(intArrayOf(), rounded(Color.TRANSPARENT, r, dp(1), C.outlineVariant))
        }
        setTextColor(android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_selected), intArrayOf()),
            intArrayOf(C.onPrimary, C.onSecondaryContainer, C.variant)))
        setOnFocusChangeListener { v, has ->
            v.animate().scaleX(if (has) 1.06f else 1f).scaleY(if (has) 1.06f else 1f).setInterpolator(SPRING).setDuration(SPRING.duration).start()
        }
        tag = n
        setOnClickListener { showSeason(n) }
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

    private fun play() {
        if (item.isSeries && item.videos.isNotEmpty()) {
            openEpisode(resumeTarget() ?: item.videos.firstOrNull { it.season > 0 } ?: item.videos.first())
        } else if (!item.isSeries) {
            startActivity(Intent(this, StreamsActivity::class.java).putExtra("type", item.type).putExtra("id", item.id)
                .putExtra("item", item.toJson().toString()))
        }
    }

    private fun openEpisode(v: Video) = startActivity(Intent(this, StreamsActivity::class.java)
        .putExtra("type", "series").putExtra("id", v.id).putExtra("item", item.toJson().toString())
        .putExtra("season", v.season).putExtra("episode", v.episode).putExtra("episodeTitle", v.title))

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
            val r = dp(Shape.XL).toFloat()
            val row = LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isClickable = true
                setPadding(dp(10), dp(10), dp(16), dp(10))
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), rounded(C.primaryContainer, r))
                    addState(intArrayOf(android.R.attr.state_pressed), rounded(C.containerHigh, r))
                    addState(intArrayOf(), rounded(Color.TRANSPARENT, r))
                }
                setOnFocusChangeListener { v, has ->
                    v.animate().scaleX(if (has) 1.03f else 1f).scaleY(if (has) 1.03f else 1f).setInterpolator(SPRING).setDuration(SPRING.duration).start()
                }
                layoutParams = RecyclerView.LayoutParams(if (isTv) (resources.displayMetrics.widthPixels * 0.62f).toInt() else -1, -2)
                    .apply { setMargins(side, 0, side, dp(4)) }
            }
            val tw = dp(if (isTv) 176 else 132)
            val thumb = FrameLayout(ctx).apply {
                background = rounded(C.container, dp(Shape.L).toFloat())
                outlineProvider = MorphOutline(dp(Shape.L).toFloat())
                clipToOutline = true
                addView(ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }, FrameLayout.LayoutParams(-1, -1))
                addView(GapProgress(ctx), FrameLayout.LayoutParams(-1, dp(4), Gravity.BOTTOM).apply { setMargins(dp(10), 0, dp(10), dp(8)) })
            }
            row.addView(thumb, lp(tw, tw * 9 / 16))
            val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, 0, 0) }
            val titleInk = android.content.res.ColorStateList(arrayOf(FOCUSED_STATE, intArrayOf()), intArrayOf(C.onPrimaryContainer, C.onSurface))
            val subInk = android.content.res.ColorStateList(arrayOf(FOCUSED_STATE, intArrayOf()), intArrayOf(C.onPrimaryContainer, C.variant))
            texts.addView(text(T.LABEL, lines = 1).apply { setTextColor(titleInk); isDuplicateParentStateEnabled = true })
            texts.addView(text(T.META, lines = if (isTv) 2 else 3).apply { setTextColor(subInk); isDuplicateParentStateEnabled = true; setPadding(0, dp(6), 0, 0) })
            texts.isDuplicateParentStateEnabled = true
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
            val bar = thumb.getChildAt(1) as GapProgress
            bar.visibility = if (p != null && p.position > 0) View.VISIBLE else View.GONE
            if (p != null) bar.fraction = p.fraction.coerceAtLeast(0.04f)
            row.contentDescription = "Episode ${v.episode}, ${v.title}"
            row.setOnClickListener { openEpisode(v) }
        }
    }
}
