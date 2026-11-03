package app.kino

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView

/** Poster width: about 8 across a TV, 3.3 across a phone. */
fun Context.posterWidth() = dp(if (isTv) 100 else 108)

/** Space around TV cards for the focus ring's gap; phones have no focus. */
private fun Context.ringGap() = if (isTv) dp(4) else 0

private val EASE = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)

/** Cards hand these back: what was focused (TV) or tapped. */
interface PosterHost {
    fun focused(item: Item, row: Int) {}
    fun open(item: Item, progress: Progress?)
}

/** [wide] is the TV poster's 16:9 face (backdrop and name), shown while it has focus. */
class PosterHolder(
    val card: FrameLayout, val image: ImageView, val title: TextView?, val name: TextView, val sub: TextView?,
    val bar: GapProgress?, val wide: FrameLayout?, val still: ImageView?,
) : RecyclerView.ViewHolder(card)

/**
 * One row's cards. Posters are 2:3, and on TV the focused one widens to 16:9 to show its backdrop.
 * A row with [progress] is Continue Watching: landscape stills with the episode and time left.
 * [fill] sizes posters to a grid cell (My List).
 */
class PosterAdapter(
    private val items: List<Item>,
    private val host: PosterHost,
    private val row: Int = 0,
    private val progress: Map<String, Progress> = emptyMap(),
    private val fill: Boolean = false,
) : RecyclerView.Adapter<PosterHolder>() {
    private val landscape = progress.isNotEmpty() && !fill

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): PosterHolder {
        val ctx = parent.context
        val tv = ctx.isTv
        val gap = ctx.ringGap()
        val r = ctx.dp(8).toFloat()
        val (w, h) = size(ctx)
        val art = FrameLayout(ctx).apply {
            background = rounded(C.container, r)
            outlineProvider = MorphOutline(r)
            clipToOutline = true
        }
        val card = FrameLayout(ctx).apply {
            setPadding(gap, gap, gap, gap)
            isClickable = true
            if (tv) focusRing(r, gap)
            addView(art, FrameLayout.LayoutParams(-1, -1))
        }
        val title = if (landscape) null else ctx.text(T.LABEL, C.variant, lines = 3).apply {
            gravity = Gravity.CENTER; setPadding(ctx.dp(8), 0, ctx.dp(8), 0)
            art.addView(this, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        }
        val image = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        art.addView(image, FrameLayout.LayoutParams(-1, -1))
        // Landscape cards and the widened poster both carry a caption over a black scrim.
        val face = if (landscape) art else if (tv && !fill) FrameLayout(ctx).apply { alpha = 0f; art.addView(this, FrameLayout.LayoutParams(-1, -1)) } else null
        val still = if (face != null && !landscape) ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            face.addView(this, FrameLayout.LayoutParams(-1, -1))
        } else null
        val name = ctx.text(T.LABEL, lines = if (landscape) 1 else 2)
        var sub: TextView? = null
        var bar: GapProgress? = null
        face?.apply {
            addView(View(ctx).apply {
                background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xD9000000.toInt(), 0x66000000, Color.TRANSPARENT))
            }, FrameLayout.LayoutParams(-1, (h * 0.7f).toInt(), Gravity.BOTTOM))
            val inset = ctx.dp(10)
            val caption = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; addView(name) }
            if (landscape) {
                sub = ctx.text(T.META, C.alpha(C.onSurface, 0.75f), lines = 1).apply { setPadding(0, ctx.dp(3), 0, 0) }
                caption.addView(sub)
                bar = GapProgress(ctx)
                addView(bar, FrameLayout.LayoutParams(-1, ctx.dp(4), Gravity.BOTTOM).apply { setMargins(inset, 0, inset, ctx.dp(8)) })
            } else {
                name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
            }
            addView(caption, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                setMargins(ctx.dp(12), 0, ctx.dp(12), if (landscape) ctx.dp(20) else ctx.dp(12))
            })
        }
        val m = ctx.dp(if (tv) 2 else 4)
        card.layoutParams = if (fill) ViewGroup.MarginLayoutParams(-1, h + 2 * gap).apply { setMargins(m, m, m, m) }
        else RecyclerView.LayoutParams(w + 2 * gap, h + 2 * gap).apply { setMargins(m, ctx.dp(if (tv) 4 else 6), m, ctx.dp(if (tv) 4 else 6)) }
        return PosterHolder(card, image, title, name, sub, bar, face?.takeIf { !landscape }, still)
    }

    /** Art size, without the ring's gap: 2:3 posters, or 16:9 stills for Continue Watching. */
    private fun size(ctx: Context): Pair<Int, Int> {
        if (landscape) return if (ctx.isTv) ctx.dp(200) to ctx.dp(112) else ctx.dp(220) to ctx.dp(124)
        val w = ctx.posterWidth()
        return w to (w * 1.5f).toInt()
    }

    override fun onBindViewHolder(h: PosterHolder, pos: Int) {
        val item = items[pos]
        val p = progress[item.id]
        val ctx = h.card.context
        h.card.contentDescription = item.name
        h.title?.text = item.name
        h.name.text = item.name
        if (landscape) {
            Images.load(h.image, item.backgroundOrHub ?: item.posterOrHub, size(ctx).first)
            h.sub?.text = p?.let(::resumeLabel)
            h.bar?.fraction = (p?.fraction ?: 0f).coerceAtLeast(0.04f)
        } else {
            Images.load(h.image, item.posterOrHub, ctx.posterWidth())
        }
        // A recycled card may still be mid-widen; settle it to its rest (or focused) shape.
        (h.card.getTag(R.id.spotlight) as? ValueAnimator)?.cancel()
        if (h.wide != null) {
            val on = h.card.isFocused
            h.card.layoutParams.width = cardWidth(ctx, on)
            h.wide.alpha = if (on) 1f else 0f
        }
        h.card.setOnClickListener { host.open(item, p) }
        h.card.setOnFocusChangeListener { v, has ->
            if (h.wide != null) spotlight(h, item, has)
            else {
                if (fill) v.animate().scaleX(if (has) 1.06f else 1f).scaleY(if (has) 1.06f else 1f).translationZ(if (has) 8f else 0f).setDuration(150).start()
                else if (has) spotlight(h, item, true)
            }
            if (has) host.focused(item, row)
        }
    }

    private fun cardWidth(ctx: Context, wide: Boolean): Int {
        val (w, h) = size(ctx)
        return (if (wide) h * 16 / 9 else w) + 2 * ctx.ringGap()
    }

    /**
     * TV: the focused card slides to the row's start while a poster widens to 16:9 and fades in its backdrop;
     * losing focus narrows it back. One animator drives width, fade and scroll so each frame lays out once.
     */
    private fun spotlight(h: PosterHolder, item: Item, on: Boolean) {
        val card = h.card
        val ctx = card.context
        val rv = card.parent as? RecyclerView ?: return
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        if (on && h.still != null) Images.load(h.still, item.backgroundOrHub, cardWidth(ctx, true))
        (card.getTag(R.id.spotlight) as? ValueAnimator)?.cancel()
        val from = card.layoutParams.width
        val to = if (h.wide != null) cardWidth(ctx, on) else from
        val fade = h.wide?.alpha ?: 0f
        val pos = h.bindingAdapterPosition
        val lpm = card.layoutParams as ViewGroup.MarginLayoutParams
        val start = card.left - lpm.leftMargin - rv.paddingLeft
        card.setTag(R.id.spotlight, ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260
            interpolator = EASE
            addUpdateListener {
                val f = it.animatedValue as Float
                if (from != to) { card.layoutParams.width = (from + (to - from) * f).toInt(); card.requestLayout() }
                h.wide?.alpha = fade + ((if (on) 1f else 0f) - fade) * f
                if (on && pos != RecyclerView.NO_POSITION) lm.scrollToPositionWithOffset(pos, (start * (1 - f)).toInt())
            }
            start()
        })
    }
}

/** "S1 · E4 · 31 min left", or just the time for a film. */
fun resumeLabel(p: Progress): String {
    val left = ((p.duration - p.position) / 60_000).coerceAtLeast(1)
    val time = if (left >= 60) "${left / 60}h ${left % 60}m left" else "$left min left"
    return if (p.season != null && p.episode != null) "S${p.season} · E${p.episode} · $time" else time
}

class Row(val title: String, val items: List<Item>, val progress: Map<String, Progress> = emptyMap())

/** The vertical list of titled poster rows. [header] (the phone's hero) scrolls with them as item 0. */
class RowsAdapter(private val host: PosterHost, private val header: View? = null) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    val rows = mutableListOf<Row>()
    private val pool = RecyclerView.RecycledViewPool().apply { setMaxRecycledViews(0, 40) }
    private val offset get() = if (header != null) 1 else 0

    fun set(newRows: List<Row>) {
        rows.clear()
        rows += newRows.filter { it.items.isNotEmpty() }
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size + offset
    override fun getItemViewType(position: Int) = if (position < offset) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): RecyclerView.ViewHolder {
        if (type == 1) return object : RecyclerView.ViewHolder(header!!) {}
        val ctx = parent.context
        val tv = ctx.isTv
        // Art starts at the page edge (48dp TV, 16dp phone); the list padding leaves room for each card's margin and ring gap.
        val edge = ctx.dp(if (tv) 48 else 16)
        val side = edge - ctx.dp(if (tv) 2 else 4) - ctx.ringGap()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
        box.addView(ctx.text(T.ROW, C.alpha(C.onSurface, 0.9f)).apply { setPadding(edge, ctx.dp(if (tv) 10 else 18), edge, ctx.dp(if (tv) 4 else 4)) })
        box.addView(RecyclerView(ctx).apply {
            layoutManager = RowLayout(ctx)
            setRecycledViewPool(pool)
            // TV: cards that slid past the row's start are hidden, so the focused one always leads.
            clipToPadding = tv
            clipChildren = false
            setPadding(side, 0, side, 0)
            itemAnimator = null
        })
        return object : RecyclerView.ViewHolder(box) {}
    }

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
        if (pos < offset) return
        val row = rows[pos - offset]
        val box = h.itemView as LinearLayout
        (box.getChildAt(0) as TextView).text = row.title
        (box.getChildAt(1) as RecyclerView).adapter = PosterAdapter(row.items, host, pos, row.progress)
    }
}

/** A horizontal row. On TV the cards scroll themselves (see [PosterAdapter]), so focus doesn't nudge the row. */
class RowLayout(private val ctx: Context) : LinearLayoutManager(ctx, HORIZONTAL, false) {
    override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: android.graphics.Rect, immediate: Boolean, focusedChildVisible: Boolean) =
        if (ctx.isTv) false else super.requestChildRectangleOnScreen(parent, child, rect, immediate, focusedChildVisible)
}

/** On TV the focused row snaps to the top of the list, like Netflix, instead of scrolling the minimum. */
class SnapRowsLayout(private val ctx: Context) : LinearLayoutManager(ctx) {
    override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: android.graphics.Rect, immediate: Boolean, focusedChildVisible: Boolean) =
        if (ctx.isTv) false else super.requestChildRectangleOnScreen(parent, child, rect, immediate, focusedChildVisible)

    fun snapTo(position: Int) {
        startSmoothScroll(object : LinearSmoothScroller(ctx) {
            override fun getVerticalSnapPreference() = SNAP_TO_START
            override fun calculateSpeedPerPixel(dm: android.util.DisplayMetrics) = 60f / dm.densityDpi
        }.apply { targetPosition = position })
    }
}
