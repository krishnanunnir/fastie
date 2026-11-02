package app.fastie

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** The palette from DESIGN.md. */
object C {
    const val GROUND = 0xFF0F0F11.toInt()
    const val SURFACE = 0xFF1C1C21.toInt()
    const val RAISED = 0xFF2B2B31.toInt()
    const val TEXT = Color.WHITE
    const val MUTED = 0xFFA3A3A8.toInt()
    const val AMBER = 0xFFFFB627.toInt()
    const val ON_LIGHT = 0xFF0F0F11.toInt()
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
enum class T(val phone: Float, val tv: Float, val style: Int, val family: String) {
    DISPLAY(30f, 34f, Typeface.BOLD, "sans-serif"),
    TITLE(20f, 22f, Typeface.BOLD, "sans-serif"),
    ROW(16f, 19f, Typeface.BOLD, "sans-serif"),
    BODY(14f, 18f, Typeface.NORMAL, "sans-serif"),
    META(13f, 16f, Typeface.NORMAL, "sans-serif-medium"),
    LABEL(14f, 16f, Typeface.BOLD, "sans-serif-medium"),
}

fun Context.text(style: T, color: Int = C.TEXT, lines: Int = 0, s: CharSequence? = null) = TextView(this).apply {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isTv) style.tv else style.phone)
    typeface = Typeface.create(style.family, style.style)
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

/** The focus look shared by every card: a white outline over the card and a small scale-up. */
fun View.focusRing(radius: Float, scale: Float = 1.08f) {
    isFocusable = true
    foreground = StateListDrawable().apply {
        addState(FOCUSED, rounded(Color.TRANSPARENT, radius, context.dp(3), Color.WHITE))
        addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
    }
    onFocusChangeListener = View.OnFocusChangeListener { v, has ->
        v.animate().scaleX(if (has) scale else 1f).scaleY(if (has) scale else 1f).translationZ(if (has) 8f else 0f)
            .setDuration(150).start()
    }
}

fun Context.icon(res: Int): Drawable = getDrawable(res)!!.mutate()

/**
 * Netflix's button: white when focused (or always, for the phone's primary action), quiet grey otherwise.
 */
fun Context.button(label: String, iconRes: Int? = null, primary: Boolean = false, onClick: () -> Unit): TextView {
    val loud = primary && !isTv
    val r = dp(6).toFloat()
    return text(T.LABEL, s = label).apply {
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        minHeight = dp(if (isTv) 44 else 48)
        setPadding(dp(18), dp(10), dp(22), dp(10))
        background = StateListDrawable().apply {
            addState(FOCUSED, rounded(Color.WHITE, r))
            addState(PRESSED, rounded(0xFFDDDDDD.toInt(), r))
            addState(intArrayOf(), rounded(if (loud) Color.WHITE else C.RAISED, r))
        }
        val colors = ColorStateList(arrayOf(FOCUSED, PRESSED, intArrayOf()),
            intArrayOf(C.ON_LIGHT, C.ON_LIGHT, if (loud) C.ON_LIGHT else C.TEXT))
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

/** A full-width focusable row for settings and stream lists. */
fun Context.listRow(title: String, sub: String? = null, onClick: (() -> Unit)?): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(18), dp(14), dp(18), dp(14))
    val r = dp(6).toFloat()
    background = StateListDrawable().apply {
        addState(FOCUSED, rounded(Color.WHITE, r))
        addState(PRESSED, rounded(C.RAISED, r))
        addState(intArrayOf(), rounded(C.SURFACE, r))
    }
    val subColors = ColorStateList(arrayOf(FOCUSED, intArrayOf()), intArrayOf(0xFF4A4A52.toInt(), C.MUTED))
    val titleColors = ColorStateList(arrayOf(FOCUSED, intArrayOf()), intArrayOf(C.ON_LIGHT, C.TEXT))
    addView(text(T.LABEL, s = title).apply { setTextColor(titleColors); isDuplicateParentStateEnabled = true })
    if (!sub.isNullOrBlank()) addView(text(T.META, lines = 3, s = sub).apply {
        setTextColor(subColors); isDuplicateParentStateEnabled = true; setPadding(0, dp(4), 0, 0)
    })
    if (onClick != null) { isFocusable = true; isClickable = true; setOnClickListener { onClick() } }
}

fun Context.sectionTitle(s: String) = text(T.ROW, s = s).apply { setPadding(0, dp(28), 0, dp(12)) }

/** Left-to-right and bottom-up scrims that keep type readable over any backdrop. */
fun Context.backdrop(parent: FrameLayout, heightFraction: Float): Pair<ImageView, ImageView> {
    val h = (resources.displayMetrics.heightPixels * heightFraction).toInt()
    val a = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    val b = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
    val w = resources.displayMetrics.widthPixels
    listOf(a, b).forEach { parent.addView(it, FrameLayout.LayoutParams(w, h, Gravity.END or Gravity.TOP)) }
    parent.addView(View(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(C.GROUND, 0xF00F0F11.toInt(), 0xB30F0F11.toInt(), 0x400F0F11, 0x000F0F11))
    }, FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * if (isTv) 0.7f else 0f).toInt().coerceAtLeast(1), h))
    parent.addView(View(this).apply {
        background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
            intArrayOf(C.GROUND, 0xCC0F0F11.toInt(), 0x000F0F11))
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
