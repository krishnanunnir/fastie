package app.kino

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

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
    styled(style)
    setTextColor(color)
    if (lines > 0) { maxLines = lines; ellipsize = TextUtils.TruncateAt.END }
    text = s
}

/** Applies [style] over whatever text appearance the widget (a Material one, say) brought. */
fun TextView.styled(style: T) {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (context.isTv) style.tv else style.phone)
    typeface = Typeface.create(style.family, style.style)
    letterSpacing = style.tracking
    isAllCaps = false
    includeFontPadding = false
    setLineSpacing(0f, if (style == T.BODY) 1.25f else 1.1f)
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
fun Context.button(label: String, iconRes: Int? = null, primary: Boolean = false, onClick: () -> Unit): MaterialButton {
    val loud = primary && !isTv
    val ink = ColorStateList(arrayOf(FOCUSED, intArrayOf()),
        intArrayOf(C.onPrimary, if (loud) C.onPrimary else C.onSecondaryContainer))
    return MaterialButton(this).apply {
        styled(T.LABEL)
        text = label
        minHeight = dp(48)
        insetTop = 0
        insetBottom = 0
        stateListAnimator = null
        setPadding(dp(18), dp(10), dp(22), dp(10))
        cornerRadius = dp(6)
        backgroundTintList = ColorStateList(arrayOf(FOCUSED, PRESSED, intArrayOf()), intArrayOf(
            C.primary,
            if (loud) 0xFFDDDDDD.toInt() else C.alpha(C.onSurface, 0.28f),
            if (loud) C.primary else C.secondaryContainer))
        rippleColor = ColorStateList.valueOf(C.alpha(C.onSurface, 0.12f))
        setTextColor(ink)
        iconTint = ink
        iconSize = dp(22)
        iconPadding = dp(8)
        // Keeps the icon beside the label when the button is stretched (phone).
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        iconRes?.let { icon = icon(it) }
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

/**
 * A text field: a 6dp surface with a 2dp white stroke under focus. The hint stays inside the box
 * rather than floating, since there is no room above it on TV. Read and listen through [TextInputLayout.getEditText].
 */
fun Context.textField(hint: String, iconRes: Int? = null) = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
    val r = dp(Shape.S).toFloat()
    isHintEnabled = false
    boxBackgroundColor = C.containerHigh
    setBoxCornerRadii(r, r, r, r)
    boxStrokeWidth = 0
    boxStrokeWidthFocused = dp(2)
    setBoxStrokeColorStateList(ColorStateList(arrayOf(FOCUSED, intArrayOf()), intArrayOf(C.onSurface, Color.TRANSPARENT)))
    iconRes?.let { setStartIconDrawable(it); setStartIconTintList(ColorStateList.valueOf(C.variant)) }
    addView(TextInputEditText(context).apply {
        this.hint = hint
        setHintTextColor(C.variant)
        setTextColor(C.onSurface)
        typeface = Typeface.DEFAULT // password fields otherwise fall back to monospace
        isSingleLine = true
        setPadding(dp(18), dp(14), dp(18), dp(14))
    })
}

/** A pill chip: hairline at rest, white 18% when selected, white with ground ink under focus. */
fun Context.chip(label: String, onClick: () -> Unit) = Chip(this).apply {
    styled(T.LABEL)
    text = label
    isCheckable = false
    chipMinHeight = dp(if (isTv) 40 else 44).toFloat()
    chipStartPadding = dp(8).toFloat()
    chipEndPadding = dp(8).toFloat()
    chipCornerRadius = dp(Shape.PILL).toFloat()
    chipStrokeWidth = dp(1).toFloat()
    // The chip's own hit-target inset would leave a gap above and below it.
    setEnsureMinTouchTargetSize(false)
    chipBackgroundColor = ColorStateList(arrayOf(FOCUSED, SELECTED, intArrayOf()), intArrayOf(C.primary, C.secondaryContainer, Color.TRANSPARENT))
    chipStrokeColor = ColorStateList(arrayOf(FOCUSED, SELECTED, intArrayOf()), intArrayOf(C.primary, Color.TRANSPARENT, C.outlineVariant))
    setTextColor(ColorStateList(arrayOf(FOCUSED, SELECTED, intArrayOf()), intArrayOf(C.onPrimary, C.onSurface, C.variant)))
    rippleColor = ColorStateList.valueOf(C.alpha(C.onSurface, 0.12f))
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

/** A 4dp progress line: white at 30% under an amber fill, rounded ends, no gap or stop dot. */
fun Context.progressLine() = LinearProgressIndicator(this).apply {
    max = 1000
    trackThickness = dp(4)
    trackCornerRadius = dp(2)
    indicatorTrackGapSize = 0
    trackStopIndicatorSize = 0
    setIndicatorColor(C.amber)
    trackColor = C.alpha(C.onSurface, 0.3f)
}

var LinearProgressIndicator.fraction: Float
    get() = progress / 1000f
    set(v) { progress = (v.coerceIn(0f, 1f) * 1000).toInt() }

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


/** The Kino lockup, the k and "kino" in amber: the brand on the TV tab bar and the phone's home. */
fun Context.wordmark() = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL
    contentDescription = "Kino"
    addView(ImageView(context).apply { setImageResource(R.drawable.kino_logo) }, lp(dp(94), dp(28)))
}
