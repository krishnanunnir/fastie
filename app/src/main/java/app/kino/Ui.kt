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
 * Spotlight colour roles: a true-black ground, white as the focus colour, and ember red, the only accent,
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
    const val accent = 0xFFFF4D3D.toInt()
    const val danger = 0xFFFF6B6B.toInt() // destructive actions only, such as Remove

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

/** A 4dp progress line: white at 30% under a red fill, rounded ends, no gap or stop dot. */
fun Context.progressLine() = LinearProgressIndicator(this).apply {
    max = 1000
    trackThickness = dp(4)
    trackCornerRadius = dp(2)
    indicatorTrackGapSize = 0
    trackStopIndicatorSize = 0
    setIndicatorColor(C.accent)
    trackColor = C.alpha(C.onSurface, 0.3f)
}

var LinearProgressIndicator.fraction: Float
    get() = progress / 1000f
    set(v) { progress = (v.coerceIn(0f, 1f) * 1000).toInt() }

/** Left-to-right and bottom-up scrims, in the surface color, that keep type readable over any backdrop. */
fun Context.backdrop(parent: FrameLayout, heightFraction: Float, bottom: Float = 0.55f): Pair<ImageView, ImageView> {
    val h = (resources.displayMetrics.heightPixels * heightFraction).toInt()
    val a = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    val b = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f; visibility = View.INVISIBLE }
    val w = resources.displayMetrics.widthPixels
    listOf(a, b).forEach { parent.addView(it, FrameLayout.LayoutParams(w, h, Gravity.END or Gravity.TOP)) }
    // TV: the scrims are painted into the backdrop itself (see [bakeScrims]), so the GPU fills one full-screen layer
    // per frame instead of four; on a cheap box that difference is the stutter.
    if (isTv) { a.setTag(R.id.scrim, bottom); b.setTag(R.id.scrim, bottom); return a to b }
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

/**
 * The backdrop's scrims (left to right over 70% of the width, bottom up over [bottom] of the height, a little from the
 * top for the tabs) painted into a copy of [src], in full colour so the dark gradients don't band. Off the main thread.
 */
fun bakeScrims(src: android.graphics.Bitmap, bottom: Float): android.graphics.Bitmap {
    val out = src.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
    val w = out.width.toFloat()
    val h = out.height.toFloat()
    val g = C.ground
    val canvas = android.graphics.Canvas(out)
    val paint = android.graphics.Paint(android.graphics.Paint.DITHER_FLAG)
    fun fill(x0: Float, y0: Float, x1: Float, y1: Float, colors: IntArray, l: Float, t: Float, r: Float, b: Float) {
        paint.shader = android.graphics.LinearGradient(x0, y0, x1, y1, colors, null, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(l, t, r, b, paint)
    }
    fill(0f, 0f, w * 0.7f, 0f, intArrayOf(g, C.alpha(g, 0.94f), C.alpha(g, 0.7f), C.alpha(g, 0.25f), C.alpha(g, 0f)), 0f, 0f, w * 0.7f, h)
    fill(0f, h, 0f, h * (1 - bottom), intArrayOf(g, C.alpha(g, 0.8f), C.alpha(g, 0f)), 0f, h * (1 - bottom), w, h)
    fill(0f, 0f, 0f, h * 0.22f, intArrayOf(C.alpha(g, 0.7f), C.alpha(g, 0f)), 0f, 0f, w, h * 0.22f)
    if (out !== src) src.recycle()
    return out
}

/**
 * Crossfades the two backdrop layers to [url]. On TV the art is decoded at half the screen's width: it sits dimmed under
 * scrims, and a quarter of the pixels is what keeps a cheap box's GPU from stuttering as focus moves. The layer that has
 * faded out is hidden rather than drawn at zero.
 */
fun crossfade(layers: Pair<ImageView, ImageView>, url: String?, width: Int) {
    val (a, b) = layers
    val (front, back) = if (a.alpha >= b.alpha) a to b else b to a
    if (front.tag == url) return
    val bottom = back.getTag(R.id.scrim) as? Float
    Images.load(back, url, if (back.context.isTv) width / 2 else width,
        transform = bottom?.let { f -> { bmp: android.graphics.Bitmap -> bakeScrims(bmp, f) } }, variant = bottom?.let { "#scrim$it" } ?: "") { ok ->
        if (!ok && url != null) return@load
        back.visibility = View.VISIBLE
        back.animate().alpha(1f).setDuration(400).start()
        front.animate().alpha(0f).setDuration(400).withEndAction { if (front.alpha == 0f) front.visibility = View.INVISIBLE }.start()
    }
}

fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT) = LinearLayout.LayoutParams(w, h)


/** The brand: the K alone, on the TV tab bar and over the phone's Home. */
fun Context.wordmark() = LinearLayout(this).apply {
    gravity = Gravity.CENTER_VERTICAL
    contentDescription = "Kino"
    addView(ImageView(context).apply { setImageResource(R.drawable.kino_k) }, lp(dp(29), dp(32)))
}

/** A small rounded label: [solid] is white with ground ink (quality), otherwise a hairline outline (tags). */
fun Context.pill(label: String, solid: Boolean = false) = text(T.META, if (solid) C.onPrimary else C.variant, s = label).apply {
    typeface = Typeface.create("sans-serif-medium", if (solid) Typeface.BOLD else Typeface.NORMAL)
    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isTv) 14f else if (solid) 13f else 12f)
    setPadding(dp(8), dp(3), dp(8), dp(3))
    background = if (solid) rounded(C.onSurface, dp(4).toFloat()) else rounded(Color.TRANSPARENT, dp(4).toFloat(), dp(1), C.outlineVariant)
}

/**
 * One stream, everywhere streams are listed: the quality badge, source and size up top; codec, HDR and audio as pills;
 * then where it comes from and the file's name. The best pick carries a hairline and says so. Focus draws a white ring.
 */
fun Context.streamCard(s: Stream, best: Boolean, onClick: () -> Unit): View = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    isFocusable = true
    isClickable = true
    setPadding(dp(16), dp(14), dp(16), dp(14))
    val r = dp(Shape.M).toFloat()
    background = StateListDrawable().apply {
        addState(FOCUSED, rounded(C.containerHigh, r, dp(2), C.onSurface))
        addState(PRESSED, rounded(C.containerHigh, r))
        addState(intArrayOf(), rounded(C.container, r, if (best) dp(1) else 0, C.alpha(C.onSurface, 0.3f)))
    }
    val top = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    s.quality?.let { top.addView(pill(it, solid = true), lp(-2, -2).apply { marginEnd = dp(10) }) }
    top.addView(text(T.LABEL, lines = 1, s = when (s.source) {
        null -> "Stream"
        "CAM" -> "CAM  ·  filmed in a cinema"
        else -> s.source
    }), LinearLayout.LayoutParams(0, -2, 1f))
    s.size?.let { top.addView(text(T.META, C.onSurface, s = it).apply { typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }, lp(-2, -2).apply { marginStart = dp(12) }) }
    addView(top)
    if (s.tags.isNotEmpty()) addView(com.google.android.material.chip.ChipGroup(context).apply {
        chipSpacingHorizontal = dp(6); chipSpacingVertical = dp(6)
        s.tags.forEach { addView(pill(it)) }
    }, lp().apply { topMargin = dp(10) })
    addView(text(T.META, C.variant, lines = 1).apply {
        text = android.text.SpannableStringBuilder().apply {
            if (best) {
                append("Best for this ${if (isTv) "TV" else "phone"}", android.text.style.ForegroundColorSpan(C.onSurface), 0)
                append("  ·  ")
            }
            append(listOfNotNull(Debrid.connected(context)?.label?.takeIf { s.hash != null }, s.addon).joinToString("  ·  "))
        }
    }, lp().apply { topMargin = dp(10) })
    s.fileName?.let { addView(text(T.META, C.alpha(C.variant, 0.7f), lines = 1, s = it).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isTv) 13f else 11.5f); ellipsize = TextUtils.TruncateAt.MIDDLE
    }, lp().apply { topMargin = dp(4) }) }
    setOnClickListener { onClick() }
}

/** A small grey heading above a [group], as on a phone's settings screen. */
fun Context.groupTitle(s: String) = text(T.META, C.variant, s = s.uppercase()).apply {
    letterSpacing = 0.08f
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    setPadding(dp(4), dp(28), 0, dp(10))
}

/** A rounded surface whose rows ([groupRow]) are separated by hairlines. */
fun Context.group() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = rounded(C.container, dp(14).toFloat())
    outlineProvider = MorphOutline(dp(14).toFloat())
    clipToOutline = true
}

/** A round badge with [letter], for things without a logo (or until theirs loads). */
fun Context.initial(letter: String, size: Int = 40) = FrameLayout(this).apply {
    background = rounded(C.containerHighest, dp(size / 2).toFloat())
    addView(text(T.LABEL, s = letter.take(1).uppercase()).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
}

/**
 * Adds a row to a [group]: an optional [leading] badge, a title with a line under it (and [below], such as pills),
 * and an optional [trailing] view; a hairline separates it from the row above. Focus draws a white ring (TV).
 */
fun LinearLayout.groupRow(
    title: CharSequence, sub: CharSequence? = null, leading: View? = null, trailing: View? = null,
    below: View? = null, subLines: Int = 2, onClick: (() -> Unit)? = null,
): LinearLayout {
    val ctx = context
    if (childCount > 0) addView(View(ctx).apply { setBackgroundColor(C.alpha(C.outlineVariant, 0.6f)) },
        LinearLayout.LayoutParams(-1, 1).apply { marginStart = ctx.dp(if (leading != null) 68 else 16) })
    val row = LinearLayout(ctx).apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ctx.dp(60)
        setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(14), ctx.dp(12))
        if (onClick != null) {
            isFocusable = true
            isClickable = true
            setOnClickListener { onClick() }
            background = StateListDrawable().apply {
                addState(FOCUSED, rounded(C.containerHighest, ctx.dp(14).toFloat(), ctx.dp(2), C.onSurface))
                addState(PRESSED, rounded(C.containerHigh, 0f))
            }
        }
        leading?.let { addView(it, LinearLayout.LayoutParams(ctx.dp(40), ctx.dp(40)).apply { marginEnd = ctx.dp(12) }) }
        val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(ctx.text(T.LABEL, lines = 1, s = title))
        sub?.let { texts.addView(ctx.text(T.META, C.variant, lines = subLines, s = it).apply { setPadding(0, ctx.dp(3), 0, 0) }) }
        below?.let { texts.addView(it, lp().apply { topMargin = ctx.dp(8) }) }
        addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        trailing?.let { addView(it, lp(-2, -2).apply { marginStart = ctx.dp(12) }) }
    }
    addView(row, lp())
    return row
}

/** A grey chevron for rows that open something. */
fun Context.chevron() = ImageView(this).apply { setImageResource(R.drawable.ic_chevron); setColorFilter(C.variant) }

/** A [sheet]: the dialog, its subtitle line (to update once things load), and the body to fill. */
class Sheet(val dialog: com.google.android.material.bottomsheet.BottomSheetDialog, val sub: TextView, val body: LinearLayout)

/**
 * Kino's bottom sheet: ground with rounded top corners, a grab handle, a title and a grey line under it, then [Sheet.body].
 * It opens fully expanded; call `dialog.show()` once the body is in.
 */
fun Context.sheet(title: String, sub: String?): Sheet {
    val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
    val pad = dp(if (isTv) 32 else 20)
    val subView = text(T.META, C.variant, s = sub).apply { visibility = if (sub == null) View.GONE else View.VISIBLE }
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    dialog.setContentView(LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, dp(16))
        addView(View(context).apply { background = rounded(C.outlineVariant, dp(2).toFloat()) },
            lp(dp(36), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(10) })
        addView(text(T.TITLE, s = title), lp().apply { setMargins(pad, dp(18), pad, 0) })
        addView(subView, lp().apply { setMargins(pad, dp(4), pad, 0) })
        addView(body, lp().apply { topMargin = dp(16) })
    })
    // The ground and rounded top go on Material's own sheet frame, which also pads itself above the gesture bar,
    // so that strip is covered too instead of showing the page through it.
    dialog.setOnShowListener {
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.background = GradientDrawable().apply {
            setColor(C.ground)
            val r = dp(20).toFloat()
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
    }
    dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
    dialog.behavior.skipCollapsed = true
    return Sheet(dialog, subView, body)
}
