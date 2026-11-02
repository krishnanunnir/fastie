package app.fastie

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
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Spotlight colour roles: a true-black ground, white as the focus colour, and amber, the only accent,
 * kept to progress, the wordmark and the current page. Role names follow Material so screens read the same.
 */
object C {
    const val ground = 0xFF08080A.toInt()
    const val container = 0xFF17171A.toInt()
    const val containerHigh = 0xFF222226.toInt()
    const val containerHighest = 0xFF2E2E33.toInt()
    const val onSurface = 0xFFFFFFFF.toInt()
    const val variant = 0xFFA3A3AB.toInt()
    const val outlineVariant = 0xFF3A3A42.toInt()
    // White means focus: focused and primary controls invert to white with ground ink.
    const val primary = onSurface
    const val onPrimary = ground
    const val primaryContainer = onSurface
    const val onPrimaryContainer = ground
    const val secondaryContainer = 0x2EFFFFFF          // white at 18%: resting buttons, selected chips
    const val onSecondaryContainer = onSurface
    const val inverse = 0xFFF2F2F4.toInt()
    const val inverseOn = ground
    const val amber = 0xFFFFB627.toInt()

    fun alpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a * 255).toInt() shl 24)
}

/** Spotlight corners, in dp: buttons, rows, inputs and the snackbar; cards and thumbnails; chips and TV tabs. */
object Shape {
    const val S = 6
    const val M = 8
    const val PILL = 20
}

private val FOCUSED = intArrayOf(android.R.attr.state_focused)
private val PRESSED = intArrayOf(android.R.attr.state_pressed)
private val SELECTED = intArrayOf(android.R.attr.state_selected)

/** TV, or anything without a touchscreen (TV boxes, desktops with a remote): drive the UI by focus. */
val Context.isTv: Boolean
    get() = (getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

fun Context.dp(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()

/** Type scale. TV sizes are larger: the screen is read from 3 m away. */
enum class T(val phone: Float, val tv: Float, val style: Int, val family: String, val tracking: Float = 0f) {
    DISPLAY(34f, 40f, Typeface.BOLD, "sans-serif", -0.02f),
    TITLE(22f, 24f, Typeface.BOLD, "sans-serif", -0.01f),
    ROW(17f, 19f, Typeface.NORMAL, "sans-serif-medium"),
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

fun Context.icon(res: Int): Drawable = getDrawable(res)!!.mutate()

/**
 * Spotlight's imagery focus: a 2dp white ring drawn [gap] outside the art, so the view needs that much padding.
 * The art itself is clipped to [radius] by its own outline.
 */
fun View.focusRing(radius: Float, gap: Int) {
    isFocusable = true
    foreground = StateListDrawable().apply {
        addState(FOCUSED, rounded(Color.TRANSPARENT, radius + gap, context.dp(2), C.onSurface))
    }
}

/**
 * A 6dp button. Phone: the one primary action rests white, the rest on white at 18%. Focus inverts to white with ground ink.
 */
fun Context.button(label: String, iconRes: Int? = null, primary: Boolean = false, onClick: () -> Unit): TextView {
    val loud = primary && !isTv
    val r = dp(6).toFloat()
    val colors = ColorStateList(arrayOf(FOCUSED, intArrayOf()),
        intArrayOf(C.onPrimary, if (loud) C.onPrimary else C.onSecondaryContainer))
    return text(T.LABEL, s = label).apply {
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        minHeight = dp(48)
        setPadding(dp(18), dp(10), dp(22), dp(10))
        background = StateListDrawable().apply {
            addState(FOCUSED, rounded(C.primary, r))
            addState(PRESSED, rounded(if (loud) 0xFFDDDDDD.toInt() else C.alpha(C.onSurface, 0.28f), r))
            addState(intArrayOf(), rounded(if (loud) C.primary else C.secondaryContainer, r))
        }
        setTextColor(colors)
        iconRes?.let {
            val d = icon(it).apply { setBounds(0, 0, dp(22), dp(22)); setTintList(colors) }
            setCompoundDrawablesRelative(d, null, null, null)
            compoundDrawablePadding = dp(8)
            // Stretched buttons (phone) would pin the icon to the edge, apart from the centred label.
            addOnLayoutChangeListener { v, l, _, r, _, _, _, _, _ ->
                val tv = v as TextView
                val content = tv.paint.measureText(tv.text.toString()) + dp(22) + tv.compoundDrawablePadding
                val side = ((r - l - content) / 2).toInt().coerceAtLeast(dp(18))
                if (side != tv.paddingStart) tv.post { tv.setPaddingRelative(side, tv.paddingTop, side, tv.paddingBottom) }
            }
        }
        setOnClickListener { onClick() }
    }
}

/** A row for settings and stream lists: a 6dp surface that inverts to white with ground ink under focus. */
fun Context.listRow(title: String, sub: String? = null, onClick: (() -> Unit)?): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(18), dp(14), dp(18), dp(14))
    val r = dp(Shape.S).toFloat()
    background = StateListDrawable().apply {
        addState(FOCUSED, rounded(C.primary, r))
        addState(PRESSED, rounded(C.containerHigh, r))
        addState(intArrayOf(), rounded(C.container, r))
    }
    val titleColors = ColorStateList(arrayOf(FOCUSED, intArrayOf()), intArrayOf(C.onPrimary, C.onSurface))
    val subColors = ColorStateList(arrayOf(FOCUSED, intArrayOf()), intArrayOf(0xFF4A4A52.toInt(), C.variant))
    addView(text(T.LABEL, s = title).apply { setTextColor(titleColors); isDuplicateParentStateEnabled = true })
    if (!sub.isNullOrBlank()) addView(text(T.META, lines = 3, s = sub).apply {
        setTextColor(subColors); isDuplicateParentStateEnabled = true; setPadding(0, dp(4), 0, 0)
    })
    if (onClick != null) { isFocusable = true; isClickable = true; setOnClickListener { onClick() } }
}

/** A text field: a 6dp surface with a 2dp white stroke under focus. */
fun Context.fieldBackground() = StateListDrawable().apply {
    addState(FOCUSED, rounded(C.containerHigh, dp(Shape.S).toFloat(), dp(2), C.onSurface))
    addState(intArrayOf(), rounded(C.containerHigh, dp(Shape.S).toFloat()))
}

/** A pill chip: hairline at rest, white 18% when selected, white with ground ink under focus. */
fun Context.chip(label: String, onClick: () -> Unit) = text(T.LABEL, s = label).apply {
    isFocusable = true
    isClickable = true
    gravity = Gravity.CENTER
    minHeight = dp(if (isTv) 40 else 44)
    setPadding(dp(16), dp(8), dp(16), dp(8))
    val r = dp(Shape.PILL).toFloat()
    background = StateListDrawable().apply {
        addState(FOCUSED, rounded(C.primary, r))
        addState(SELECTED, rounded(C.secondaryContainer, r))
        addState(intArrayOf(), rounded(Color.TRANSPARENT, r, dp(1), C.outlineVariant))
    }
    setTextColor(ColorStateList(arrayOf(FOCUSED, SELECTED, intArrayOf()), intArrayOf(C.onPrimary, C.onSurface, C.variant)))
    setOnClickListener { onClick() }
}

/** "★ 7.8  ·  2024  ·  Drama, Crime  ·  1h 49m": the rating first, in full white, for text of [size] px. */
fun Context.metaText(item: Item, size: Float): CharSequence = android.text.SpannableStringBuilder().apply {
    item.rating?.let {
        val star = (size * 0.95f).toInt()
        append(" ", android.text.style.ImageSpan(icon(R.drawable.ic_star).apply { setBounds(0, 0, star, star) }, android.text.style.ImageSpan.ALIGN_BASELINE), 0)
        val start = length
        append(" $it")
        setSpan(android.text.style.ForegroundColorSpan(C.onSurface), start, length, 0)
        setSpan(android.text.style.StyleSpan(Typeface.BOLD), start, length, 0)
        if (item.metaLine.isNotBlank()) append("  ·  ")
    }
    append(item.metaLine)
}

fun Context.sectionTitle(s: String) = text(T.ROW, s = s).apply { setPadding(dp(4), dp(28), 0, dp(12)) }

/** A 4dp progress line: white at 30% under an amber fill, rounded ends. */
class GapProgress(ctx: Context) : View(ctx) {
    var fraction = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val active = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.amber }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = C.alpha(C.onSurface, 0.3f) }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, track)
        canvas.drawRoundRect(0f, 0f, width * fraction, height.toFloat(), r, r, active)
    }
}

/** Left-to-right and bottom-up scrims, in the surface color, that keep type readable over any backdrop. */
fun Context.backdrop(parent: FrameLayout, heightFraction: Float, bottom: Float = 0.55f): Pair<ImageView, ImageView> {
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
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (h * bottom).toInt(), Gravity.TOP).apply { topMargin = h - (h * bottom).toInt() })
    // Keeps the TV tabs and the phone status bar readable over bright art.
    parent.addView(View(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(C.alpha(g, 0.7f), C.alpha(g, 0f)))
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (h * 0.22f).toInt()))
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


/** The amber bolt and "fastie": the brand on the TV tab bar and the phone's home. */
fun Context.wordmark() = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL
    addView(ImageView(context).apply { setImageResource(R.drawable.ic_bolt); setColorFilter(C.amber) }, lp(dp(22), dp(22)))
    addView(text(T.TITLE, C.amber, s = "fastie").apply { setPadding(dp(4), 0, 0, 0) })
}
