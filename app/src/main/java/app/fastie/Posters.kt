package app.fastie

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView

/** Poster width: about 6.5 across a TV next to the rail, 3.3 across a phone. */
fun Context.posterWidth() = dp(if (isTv) 118 else 108)

/** Cards hand these back: what was focused (TV) or tapped. */
interface PosterHost {
    fun focused(item: Item, row: Int) {}
    fun open(item: Item, progress: Progress?)
}

class PosterHolder(val card: FrameLayout, val image: ImageView, val title: TextView, val bar: GapProgress, val scrim: View) :
    RecyclerView.ViewHolder(card) {
    val ring: View.OnFocusChangeListener = card.onFocusChangeListener
}

class PosterAdapter(
    private val items: List<Item>,
    private val host: PosterHost,
    private val row: Int = 0,
    private val progress: Map<String, Progress> = emptyMap(),
    private val fill: Boolean = false,
) : RecyclerView.Adapter<PosterHolder>() {

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): PosterHolder {
        val ctx = parent.context
        val w = ctx.posterWidth()
        val r = ctx.dp(Shape.L).toFloat()
        val shape = MorphOutline(r)
        val card = FrameLayout(ctx).apply {
            background = rounded(C.container, r)
            outlineProvider = shape
            clipToOutline = true
            focusMorph(shape, r, ctx.dp(Shape.XL).toFloat())
            isClickable = true
        }
        val title = ctx.text(T.LABEL, C.variant, lines = 3).apply { gravity = Gravity.CENTER; setPadding(ctx.dp(8), 0, ctx.dp(8), 0) }
        card.addView(title, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        val image = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        card.addView(image, FrameLayout.LayoutParams(-1, -1))
        // Progress sits on a short surface scrim so its track reads over any poster.
        val scrim = View(ctx).apply {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(C.alpha(C.ground, 0.85f), C.alpha(C.ground, 0f)))
        }
        card.addView(scrim, FrameLayout.LayoutParams(-1, ctx.dp(40), Gravity.BOTTOM))
        val inset = ctx.dp(12)
        val bar = GapProgress(ctx)
        card.addView(bar, FrameLayout.LayoutParams(-1, ctx.dp(4), Gravity.BOTTOM).apply { setMargins(inset, 0, inset, inset) })
        val m = ctx.dp(if (ctx.isTv) 7 else 4)
        card.layoutParams = if (fill) ViewGroup.MarginLayoutParams(-1, (w * 1.5f).toInt()).apply { setMargins(m, m, m, m) }
        else RecyclerView.LayoutParams(w, (w * 1.5f).toInt()).apply { setMargins(m, m * 2, m, m * 2) }
        return PosterHolder(card, image, title, bar, scrim)
    }

    override fun onBindViewHolder(h: PosterHolder, pos: Int) {
        val item = items[pos]
        val p = progress[item.id]
        h.title.text = item.name
        h.card.contentDescription = item.name
        Images.load(h.image, item.posterOrHub, h.card.context.posterWidth())
        h.bar.visibility = if (p != null) View.VISIBLE else View.GONE
        h.scrim.visibility = h.bar.visibility
        if (p != null) h.bar.fraction = p.fraction.coerceAtLeast(0.04f)
        h.card.setOnClickListener { host.open(item, p) }
        h.card.setOnFocusChangeListener { v, has ->
            h.ring.onFocusChange(v, has)
            if (has) host.focused(item, row)
        }
    }
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
        val side = ctx.dp(if (ctx.isTv) 8 else 12)
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = RecyclerView.LayoutParams(-1, -2)
        }
        box.addView(ctx.text(T.ROW).apply { setPadding(side + ctx.dp(4), ctx.dp(14), side, 0) })
        box.addView(RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
            setRecycledViewPool(pool)
            clipToPadding = false
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
