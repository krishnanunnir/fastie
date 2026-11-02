package app.fastie

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.Interpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Material 3 dark color roles. On Android 12+ they come from the wallpaper (Material You);
 * elsewhere, and on most TV boxes, from Fastie's amber seed. Call [init] before building UI.
 */
object C {
    var ground = 0xFF16130E.toInt()          // surface
    var container = 0xFF221F1A.toInt()       // surfaceContainer
    var containerHigh = 0xFF2D2924.toInt()   // surfaceContainerHigh
    var containerHighest = 0xFF38342E.toInt()
    var onSurface = 0xFFEBE1D6.toInt()
    var variant = 0xFFD4C4B2.toInt()         // onSurfaceVariant
    var outlineVariant = 0xFF504538.toInt()
    var primary = 0xFFFFB94F.toInt()
    var onPrimary = 0xFF452B00.toInt()
    var primaryContainer = 0xFF633F00.toInt()
    var onPrimaryContainer = 0xFFFFDDB4.toInt()
    var secondaryContainer = 0xFF574427.toInt()
    var onSecondaryContainer = 0xFFFBDEBC.toInt()
    var inverse = 0xFFEBE1D6.toInt()
    var inverseOn = 0xFF353027.toInt()

    fun init(ctx: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        fun c(id: Int) = ctx.getColor(id)
        val n1 = c(android.R.color.system_neutral1_900)
        ground = blend(n1, Color.BLACK, 0.4f)
        container = n1
        containerHigh = blend(n1, c(android.R.color.system_neutral1_800), 0.6f)
        containerHighest = c(android.R.color.system_neutral1_800)
        onSurface = c(android.R.color.system_neutral1_100)
        variant = c(android.R.color.system_neutral2_200)
        outlineVariant = c(android.R.color.system_neutral2_700)
        primary = c(android.R.color.system_accent1_200)
        onPrimary = c(android.R.color.system_accent1_800)
        primaryContainer = c(android.R.color.system_accent1_700)
        onPrimaryContainer = c(android.R.color.system_accent1_100)
        secondaryContainer = c(android.R.color.system_accent2_700)
        onSecondaryContainer = c(android.R.color.system_accent2_100)
        inverse = onSurface
        inverseOn = c(android.R.color.system_neutral1_800)
    }

    fun alpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a * 255).toInt() shl 24)

    private fun blend(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt())
}

/** Expressive corner scale, in dp. */
object Shape {
    const val S = 8
    const val M = 12
    const val L = 16
    const val XL = 28
    const val FULL = 999
}

/**
 * Material 3 Expressive's spatial spring (damping 0.8, stiffness 380) as an interpolator: a small, quick overshoot.
 * Platform-only, so no dynamicanimation library.
 */
class Spring(private val damping: Float = 0.8f, stiffness: Float = 380f) : Interpolator {
    private val w = sqrt(stiffness)
    private val wd = w * sqrt(1 - damping * damping)
    // The spring settles in about this many seconds; animations using it should last as long.
    val duration = (4.6f / (damping * w) * 1000).toLong()
    override fun getInterpolation(t: Float): Float {
        val s = t * duration / 1000f
        return 1 - exp(-damping * w * s) * (cos(wd * s) + damping * w / wd * kotlin.math.sin(wd * s))
    }
}

val SPRING = Spring()

private val FOCUSED = intArrayOf(android.R.attr.state_focused)
private val PRESSED = intArrayOf(android.R.attr.state_pressed)
private val SELECTED = intArrayOf(android.R.attr.state_selected)

/** TV, or anything without a touchscreen (TV boxes, desktops with a remote): drive the UI by focus. */
val Context.isTv: Boolean
    get() = (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

fun Context.dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()

/** Type scale, Expressive-emphasised headlines. TV sizes are larger: the screen is read from 3 m away. */
enum class T(val phone: Float, val tv: Float, val style: Int, val family: String, val tracking: Float = 0f) {
    DISPLAY(34f, 40f, Typeface.BOLD, "sans-serif", -0.02f),
    TITLE(22f, 24f, Typeface.BOLD, "sans-serif", -0.01f),
    ROW(18f, 21f, Typeface.BOLD, "sans-serif", -0.01f),
    BODY(14f, 18f, Typeface.NORMAL, "sans-serif"),
    META(13f, 16f, Typeface.NORMAL, "sans-serif-medium"),
    LABEL(14f, 16f, Typeface.BOLD, "sans-serif-medium", 0.01f),
}

fun Context.text(style: T, color: Int = C.onSurface, lines: Int = 0, s: CharSequence? = null) = TextView(this).apply {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isTv) style.tv else style.phone)
    typeface = Typeface.create(style.family, style.style)
    letterSpacing = style.tracking
    setTextColor(color)
    includeFontPadding = false
    setLineSpacing(0f, if (style == T.BODY) 1.25f else 1.1f)
    if (lines > 0) { maxLines = lines; ellipsize = TextUtils.TruncateAt.END }
    text = s
}

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
    if (stroke > 0) setStroke(stroke, strokeColor)
}

/** Clips a view to a rounded rect whose radius can be animated. */
class MorphOutline(var radius: Float) : ViewOutlineProvider() {
    override fun getOutline(v: View, o: Outline) = o.setRoundRect(0, 0, v.width, v.height, radius.coerceAtMost(v.height / 2f))
}

/** Springs [v]'s corners (outline and any GradientDrawable layers it owns) to [to]. */
fun morph(v: View, outline: MorphOutline?, from: Float, to: Float, vararg shapes: GradientDrawable) {
    (v.getTag(R.id.morph) as? ValueAnimator)?.cancel()
    v.setTag(R.id.morph, ValueAnimator.ofFloat(from, to).apply {
        duration = SPRING.duration
        interpolator = SPRING
        addUpdateListener {
            val r = (it.animatedValue as Float).coerceAtLeast(0f)
            outline?.let { o -> o.radius = r; v.invalidateOutline() }
            shapes.forEach { s -> s.cornerRadius = r }
        }
        start()
    })
}

/**
 * The imagery focus: the card springs up in scale and its corners round further (Expressive shape morph),
 * with an outline ring in the on-surface color.
 */
fun View.focusMorph(outline: MorphOutline, rest: Float, focused: Float, scale: Float = 1.08f) {
    isFocusable = true
    val ring = rounded(Color.TRANSPARENT, rest, context.dp(3), C.onSurface)
    foreground = StateListDrawable().apply {
        addState(FOCUSED, ring)
        addState(intArrayOf(), rounded(Color.TRANSPARENT, rest))
    }
    onFocusChangeListener = View.OnFocusChangeListener { v, has ->
        v.animate().scaleX(if (has) scale else 1f).scaleY(if (has) scale else 1f).translationZ(if (has) 8f else 0f)
            .setInterpolator(SPRING).setDuration(SPRING.duration).start()
        morph(v, outline, outline.radius, if (has) focused else rest, ring)
    }
}

fun Context.icon(res: Int): Drawable = getDrawable(res)!!.mutate()

/**
 * Expressive pill button. Phone: filled (primary) or tonal (secondary container); TV: tonal at rest, filled when focused.
 * Pressing squishes the pill toward a rounded square and springs back.
 */
@SuppressLint("ClickableViewAccessibility")
fun Context.button(label: String, iconRes: Int? = null, primary: Boolean = false, onClick: () -> Unit): TextView {
    val loud = primary && !isTv
    val pill = dp(Shape.FULL).toFloat()
    val rest = rounded(if (loud) C.primary else C.secondaryContainer, pill)
    val focus = rounded(C.primary, pill)
    val press = rounded(if (loud) C.primary else C.containerHighest, pill)
    return text(T.LABEL, s = label).apply {
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        minHeight = dp(if (isTv) 48 else 56)
        setPadding(dp(24), dp(12), dp(28), dp(12))
        background = StateListDrawable().apply {
            addState(FOCUSED, focus)
            addState(PRESSED, press)
            addState(intArrayOf(), rest)
        }
        val colors = ColorStateList(arrayOf(FOCUSED, intArrayOf()),
            intArrayOf(C.onPrimary, if (loud) C.onPrimary else C.onSecondaryContainer))
        setTextColor(colors)
        iconRes?.let {
            val d = icon(it).apply { setBounds(0, 0, dp(22), dp(22)); setTintList(colors) }
            setCompoundDrawablesRelative(d, null, null, null)
            compoundDrawablePadding = dp(8)
            // Stretched buttons (phone) would pin the icon to the edge, apart from the centred label.
            addOnLayoutChangeListener { v, l, _, r, _, _, _, _, _ ->
                val tv = v as TextView
                val content = tv.paint.measureText(tv.text.toString()) + dp(22) + tv.compoundDrawablePadding
                val side = ((r - l - content) / 2).toInt().coerceAtLeast(dp(20))
                if (side != tv.paddingStart) tv.post { tv.setPaddingRelative(side, tv.paddingTop, side, tv.paddingBottom) }
            }
        }
        val square = dp(Shape.M).toFloat()
        setOnTouchListener { v, e ->
            val h = v.height / 2f
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> morph(v, null, h, square, rest, focus, press)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> morph(v, null, square, h, rest, focus, press)
            }
            false
        }
        onFocusChangeListener = View.OnFocusChangeListener { v, has ->
            // The grown pill would be cut off by its row.
            listOf(v.parent, v.parent?.parent).forEach { (it as? ViewGroup)?.apply { clipChildren = false; clipToPadding = false } }
            v.animate().scaleX(if (has) 1.06f else 1f).scaleY(if (has) 1.06f else 1f)
                .setInterpolator(SPRING).setDuration(SPRING.duration).start()
        }
        setOnClickListener { onClick() }
    }
}

/**
 * A row for settings and stream lists. Consecutive rows form one Expressive group: big outer corners,
 * small inner ones, 2dp apart. Call [groupRows] on their parent once it's filled.
 */
fun Context.listRow(title: String, sub: String? = null, onClick: (() -> Unit)?): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(20), dp(16), dp(20), dp(16))
    tag = ROW_TAG
    val titleColors = ColorStateList(arrayOf(FOCUSED, intArrayOf()), intArrayOf(C.onPrimaryContainer, C.onSurface))
    val subColors = ColorStateList(arrayOf(FOCUSED, intArrayOf()), intArrayOf(C.onPrimaryContainer, C.variant))
    addView(text(T.LABEL, s = title).apply { setTextColor(titleColors); isDuplicateParentStateEnabled = true })
    if (!sub.isNullOrBlank()) addView(text(T.META, lines = 3, s = sub).apply {
        setTextColor(subColors); isDuplicateParentStateEnabled = true; setPadding(0, dp(4), 0, 0)
    })
    if (onClick != null) { isFocusable = true; isClickable = true; setOnClickListener { onClick() } }
}

private const val ROW_TAG = "fastie-row"

/** Gives each run of [listRow]s grouped corners: 28dp outside, 4dp between. */
fun groupRows(parent: ViewGroup) {
    val ctx = parent.context
    val big = ctx.dp(Shape.XL).toFloat()
    val small = ctx.dp(4).toFloat()
    val kids = (0 until parent.childCount).map(parent::getChildAt)
    kids.forEachIndexed { i, v ->
        if (v.tag != ROW_TAG) return@forEachIndexed
        val top = if (kids.getOrNull(i - 1)?.tag == ROW_TAG) small else big
        val bottom = if (kids.getOrNull(i + 1)?.tag == ROW_TAG) small else big
        val radii = floatArrayOf(top, top, top, top, bottom, bottom, bottom, bottom)
        fun shape(color: Int) = GradientDrawable().apply { setColor(color); cornerRadii = radii }
        v.background = StateListDrawable().apply {
            addState(FOCUSED, shape(C.primaryContainer))
            addState(PRESSED, shape(C.containerHighest))
            addState(intArrayOf(), shape(C.container))
        }
        (v.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = if (bottom == small) ctx.dp(2) else ctx.dp(8)
    }
}

fun Context.sectionTitle(s: String) = text(T.ROW, s = s).apply { setPadding(dp(4), dp(28), 0, dp(12)) }

/**
 * Material 3's progress indicator: a rounded active track, a gap, the remaining track, and a stop dot at the end.
 */
class GapProgress(ctx: Context) : View(ctx) {
    var fraction = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val active = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.primary; strokeCap = Paint.Cap.ROUND }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.secondaryContainer; strokeCap = Paint.Cap.ROUND }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val r = h / 2
        val gap = h
        active.strokeWidth = h
        track.strokeWidth = h
        val end = r + (width - h) * fraction
        if (fraction > 0f) canvas.drawLine(r, r, end, r, active)
        if (end + gap < width - r) canvas.drawLine(end + gap + r, r, width - r, r, track)
        canvas.drawCircle(width - r, r, r * 0.7f, active)
    }
}

/** Left-to-right and bottom-up scrims, in the surface color, that keep type readable over any backdrop. */
fun Context.backdrop(parent: FrameLayout, heightFraction: Float): Pair<ImageView, ImageView> {
    val h = (resources.displayMetrics.heightPixels * heightFraction).toInt()
    val a = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    val b = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
    val w = resources.displayMetrics.widthPixels
    listOf(a, b).forEach { parent.addView(it, FrameLayout.LayoutParams(w, h, Gravity.END or Gravity.TOP)) }
    val g = C.ground
    parent.addView(View(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(g, C.alpha(g, 0.94f), C.alpha(g, 0.7f), C.alpha(g, 0.25f), C.alpha(g, 0f)))
    }, FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * if (isTv) 0.7f else 0f).toInt().coerceAtLeast(1), h))
    parent.addView(View(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(g, C.alpha(g, 0.8f), C.alpha(g, 0f)))
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (h * 0.55f).toInt(), Gravity.TOP).apply { topMargin = h - (h * 0.55f).toInt() })
    return a to b
}

/** Crossfades the two backdrop layers to [url]. */
fun crossfade(layers: Pair<ImageView, ImageView>, url: String?, width: Int) {
    val (a, b) = layers
    val (front, back) = if (a.alpha >= b.alpha) a to b else b to a
    if (front.tag == url) return
    Images.load(back, url, width) { ok ->
        if (!ok && url != null) return@load
        back.animate().alpha(1f).setDuration(400).start()
        front.animate().alpha(0f).setDuration(400).start()
    }
}

fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT) = LinearLayout.LayoutParams(w, h)

val SELECTED_STATE = SELECTED
val FOCUSED_STATE = FOCUSED
