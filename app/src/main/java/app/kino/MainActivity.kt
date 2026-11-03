package app.kino

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import androidx.recyclerview.widget.RecyclerView

/** Home, Search, My List and Settings, under tabs (TV) or over a bottom bar (phone). */
class MainActivity : Activity(), PosterHost {
    private lateinit var pages: FrameLayout
    lateinit var navBar: View
        private set
    private val nav = mutableListOf<View>()
    private val views = arrayOfNulls<View>(4)
    private var page = -1
    var topInset = 0
        private set
    private lateinit var home: HomePage
    private var search: SearchPage? = null
    private var library: LibraryPage? = null
    private var settings: SettingsPage? = null

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        edgeToEdge()
        val root = FrameLayout(this).apply { setBackgroundColor(C.ground) }
        pages = FrameLayout(this)
        root.addView(pages, FrameLayout.LayoutParams(-1, -1))
        val bar = if (isTv) tabs() else bottomBar()
        navBar = bar
        root.addView(bar)
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            topInset = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            val bottom = insets.systemWindowInsetBottom
            if (!isTv) {
                bar.setPadding(0, dp(6), 0, bottom + dp(10))
                pages.setPadding(0, 0, 0, bottom + dp(80))
            }
            settings?.view?.setPadding(0, topInset, 0, 0)
            if (::home.isInitialized) home.inset(topInset)
            insets
        }
        setContentView(root)
        // Entering the tabs lands on the current page, not on whichever tab is nearest:
        // outside the tab bar only that tab can take focus.
        if (isTv) root.viewTreeObserver.addOnGlobalFocusChangeListener { _, now ->
            val inTabs = now != null && now.parent === bar
            nav.forEach { it.isFocusable = inTabs || it.tag == page }
        }
        home = HomePage(this)
        show(0)
        // Each launch picks up what changed on other devices.
        if (Nuvio.signedIn(this)) load({ Nuvio.pull(this) }) { if (it.isSuccess) { home.reload(); settings?.refresh() } }
    }

    override fun onResume() {
        super.onResume()
        if (Nuvio.changed) { Nuvio.changed = false; home.reload() }
        home.refreshContinue()
        library?.refresh()
        settings?.refresh()
    }

    private val icons = intArrayOf(R.drawable.ic_home, R.drawable.ic_search, R.drawable.ic_list, R.drawable.ic_settings)
    private val labels = arrayOf("Home", "Search", "My List", "Settings")

    /** TV: the wordmark and text tabs along the top, so rows get the full width. */
    private fun tabs() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = FrameLayout.LayoutParams(-1, dp(TABS))
        setPadding(dp(48), dp(8), dp(48), 0)
        addView(wordmark(), lp(-2, -2).apply { marginEnd = dp(28) })
        // Search first, as on every big-screen streaming app.
        for (i in intArrayOf(1, 0, 2, 3)) addView(navItem(i), lp(-2, -2).apply { marginEnd = dp(4) })
    }

    private fun bottomBar() = LinearLayout(this).apply {
        layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        setBackgroundColor(C.alpha(C.ground, 0.96f))
        for (i in 0..3) addView(navItem(i), LinearLayout.LayoutParams(0, -2, 1f))
    }

    /** Phone: icon over label. TV: a text tab (Search is its icon) with an amber underline on the current page. */
    private fun navItem(i: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isClickable = true
        isFocusable = isTv
        contentDescription = labels[i]
        val iconOnly = isTv && i == 1
        if (!isTv || iconOnly) addView(ImageView(context).apply { setImageResource(icons[i]) }, lp(dp(if (isTv) 22 else 24), dp(if (isTv) 22 else 24)))
        if (!iconOnly) addView(text(T.META, s = labels[i]).apply {
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (isTv) 16f else 12f)
            if (!isTv) setPadding(0, dp(4), 0, 0)
        }, lp(-2, -2))
        if (isTv) {
            setPadding(dp(16), dp(9), dp(16), dp(5))
            background = rounded(Color.TRANSPARENT, dp(20).toFloat())
            addView(View(context).apply { background = rounded(C.amber, dp(1).toFloat()) }, lp(-1, dp(2)).apply { topMargin = dp(4) })
        } else setPadding(0, dp(6), 0, dp(6))
        setOnFocusChangeListener { v, _ -> tint(v, i) }
        setOnClickListener { show(i) }
        nav += this
        tag = i
    }

    private fun tint(v: View, i: Int) {
        val item = v as LinearLayout
        val active = i == page
        val ink = when {
            v.isFocused -> C.onPrimary
            active -> if (isTv) C.onSurface else C.amber
            else -> if (isTv) C.alpha(C.onSurface, 0.62f) else C.variant
        }
        (item.background as? android.graphics.drawable.GradientDrawable)?.setColor(if (v.isFocused) C.primary else Color.TRANSPARENT)
        for (k in 0 until item.childCount) when (val c = item.getChildAt(k)) {
            is ImageView -> c.setColorFilter(ink)
            is TextView -> c.setTextColor(ink)
            else -> c.visibility = if (active && !v.isFocused) View.VISIBLE else View.INVISIBLE
        }
    }

    fun show(i: Int) {
        if (i == page) return
        page = i
        val v = views[i] ?: when (i) {
            0 -> home.view
            1 -> SearchPage(this).also { search = it }.view
            2 -> LibraryPage(this).also { library = it }.view
            else -> SettingsPage(this).also { settings = it }.view.apply { setPadding(0, topInset, 0, 0) }
        }.also { views[i] = it; pages.addView(it, FrameLayout.LayoutParams(-1, -1).apply { topMargin = if (isTv && i != 0) dp(TABS) else 0 }) }
        views.forEach { it?.visibility = if (it === v) View.VISIBLE else View.GONE }
        val inTabs = nav.any { it.isFocused }
        nav.forEach { tint(it, it.tag as Int); if (isTv && !inTabs) it.isFocusable = it.tag == i }
        when (i) {
            1 -> search?.focus()
            2 -> library?.refresh()
            3 -> settings?.refresh()
        }
        if (isTv) v.post { v.requestFocus() }
    }

    @Deprecated("Back returns to Home before leaving the app.")
    override fun onBackPressed() {
        if (page != 0) show(0) else @Suppress("DEPRECATION") super.onBackPressed()
    }

    @Deprecated("Voice search result.")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(request, result, data)
        val heard = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (request == SearchPage.VOICE && heard != null) search?.voice(heard)
    }

    override fun focused(item: Item, row: Int) = home.focused(item, row)

    override fun open(item: Item, progress: Progress?) = openItem(this, item, progress)

    /** Called by Settings after the addon list or the account changes. */
    fun reloadHome() = home.reload()

    companion object {
        const val TABS = 64
    }
}

/** A title opens its details; a continue-watching card goes straight to its streams. */
fun openItem(a: Activity, item: Item, progress: Progress?) {
    if (progress != null) {
        a.startActivity(Intent(a, StreamsActivity::class.java)
            .putExtra("type", progress.type).putExtra("id", progress.videoId).putExtra("item", item.toJson().toString())
            .putExtra("season", progress.season ?: -1).putExtra("episode", progress.episode ?: -1))
    } else {
        a.startActivity(Intent(a, DetailsActivity::class.java).putExtra("item", item.toJson().toString()))
    }
}

@Suppress("DEPRECATION")
fun Activity.edgeToEdge() {
    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(C.ground))
    window.statusBarColor = Color.TRANSPARENT
    // TV has no system bars; on phones the 3-button bar needs a ground behind it to stay readable over content.
    window.navigationBarColor = if (isTv) Color.TRANSPARENT else C.container
    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
}

/**
 * Spotlight home. TV: the focused title's backdrop fills the screen behind its logo, meta and synopsis, with rows below.
 * Phone: the first title as a floating poster on a glow tinted from its art; the hero scrolls away with the rows.
 */
class HomePage(private val a: MainActivity) {
    val view = FrameLayout(a)
    private val tv = a.isTv
    private val adapter: RowsAdapter
    private val rows = RecyclerView(a)
    private val layout = SnapRowsLayout(a)
    private val hero = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    private val logo = ImageView(a).apply { scaleType = if (tv) ImageView.ScaleType.FIT_START else ImageView.ScaleType.FIT_CENTER }
    private val title = a.text(T.DISPLAY, lines = 2)
    private val meta = a.text(T.META, C.alpha(C.onSurface, 0.72f), lines = 1)
    private val desc = a.text(T.BODY, C.alpha(C.onSurface, 0.82f), lines = 2)
    private val status = a.text(T.BODY, C.variant)
    private var layers: Pair<ImageView, ImageView>? = null
    private var appBar: View? = null
    private var poster: ImageView? = null
    private val glow = android.graphics.drawable.GradientDrawable().apply {
        gradientType = android.graphics.drawable.GradientDrawable.RADIAL_GRADIENT
        setGradientCenter(0.5f, 0.12f)
        colors = intArrayOf(C.containerHigh, C.container, C.ground)
    }
    private var catalogRows: List<Row> = emptyList()
    private var heroItem: Item? = null
    private var listButton: MaterialButton? = null
    private val pending = Runnable { pendingItem?.let(::showHero) }
    private var pendingItem: Item? = null
    private val screenW = a.resources.displayMetrics.widthPixels
    private val screenH = a.resources.displayMetrics.heightPixels
    private val logoWidth: Int

    init {
        rows.layoutManager = layout
        rows.itemAnimator = null
        rows.clipToPadding = false
        if (tv) {
            // The backdrop follows focus across the whole screen; rows sit in its darkened lower half.
            layers = a.backdrop(view, 1f, bottom = 0.8f)
            logoWidth = (screenW * 0.36f).toInt()
            // The logo keeps its own aspect up to this box, so short logos don't leave a gap above the meta.
            logo.adjustViewBounds = true
            logo.maxWidth = logoWidth
            logo.maxHeight = a.dp(104)
            hero.addView(logo, lp(-2, -2).apply { bottomMargin = a.dp(18) })
            hero.addView(title.apply { setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 44f) }, lp().apply { bottomMargin = a.dp(12) })
            hero.addView(meta, lp().apply { bottomMargin = a.dp(12) })
            hero.addView(desc, lp())
            view.addView(hero, FrameLayout.LayoutParams((screenW * 0.46f).toInt(), -2).apply { topMargin = a.dp(92); marginStart = a.dp(48) })
            adapter = RowsAdapter(a)
            view.addView(rows, FrameLayout.LayoutParams(-1, -1).apply { topMargin = (screenH * 0.55f).toInt() })
            rows.setPadding(0, 0, 0, a.dp(240))
        } else {
            val pw = (screenW * 0.78f).toInt().coerceAtMost(a.dp(340))
            val ph = (pw * 1.42f).toInt()
            logoWidth = (pw * 0.72f).toInt()
            val header = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = RecyclerView.LayoutParams(-1, -2)
                background = glow
                glow.gradientRadius = screenW * 0.95f
            }
            val bar = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(a.dp(18), a.dp(30), a.dp(6), 0) }
            bar.addView(a.wordmark(), LinearLayout.LayoutParams(0, -2, 1f))
            bar.addView(ImageView(a).apply {
                setImageResource(R.drawable.ic_search); setColorFilter(C.onSurface); contentDescription = "Search"
                setPadding(a.dp(12), a.dp(12), a.dp(12), a.dp(12)); isClickable = true; setOnClickListener { a.show(1) }
            }, lp(a.dp(48), a.dp(48)))
            header.addView(bar)
            appBar = bar
            val card = FrameLayout(a).apply {
                background = rounded(C.container, a.dp(14).toFloat())
                outlineProvider = MorphOutline(a.dp(14).toFloat())
                clipToOutline = true
                elevation = a.dp(16).toFloat()
                setOnClickListener { heroItem?.let { openItem(a, it, null) } }
            }
            poster = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            card.addView(poster, FrameLayout.LayoutParams(-1, -1))
            card.addView(View(a).apply {
                background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                    intArrayOf(0xEB000000.toInt(), 0x66000000, Color.TRANSPARENT))
            }, FrameLayout.LayoutParams(-1, (ph * 0.6f).toInt(), Gravity.BOTTOM))
            hero.gravity = Gravity.CENTER_HORIZONTAL
            hero.addView(logo, lp(logoWidth, a.dp(68)).apply { bottomMargin = a.dp(10) })
            hero.addView(title.apply { gravity = Gravity.CENTER }, lp().apply { bottomMargin = a.dp(8) })
            hero.addView(meta.apply { gravity = Gravity.CENTER; maxLines = 2 }, lp().apply { bottomMargin = a.dp(16) })
            val buttons = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
            buttons.addView(a.button("Play", R.drawable.ic_play, primary = true) { heroItem?.let { openItem(a, it, null) } }, lp(0, -2).apply { weight = 1f; marginEnd = a.dp(10) })
            listButton = a.button("My List", R.drawable.ic_add) { toggleList() }
            buttons.addView(listButton, lp(0, -2).apply { weight = 1f })
            hero.addView(buttons, lp())
            card.addView(hero, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(a.dp(16), 0, a.dp(16), a.dp(16)) })
            header.addView(card, lp(pw, ph).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = a.dp(12); bottomMargin = a.dp(6) })
            adapter = RowsAdapter(a, header)
            view.addView(rows, FrameLayout.LayoutParams(-1, -1))
            rows.setPadding(0, 0, 0, a.dp(16))
        }
        rows.adapter = adapter
        view.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        reload()
    }

    // Phone: keeps the status bar readable once rows scroll under it.
    private val statusScrim = if (tv) null else View(a).apply {
        background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(C.alpha(C.ground, 0.9f), C.alpha(C.ground, 0f)))
        view.addView(this, FrameLayout.LayoutParams(-1, 0))
    }

    /** Phone: keeps the wordmark clear of the status bar, and sizes the scrim behind it. */
    fun inset(top: Int) {
        appBar?.let { it.setPadding(it.paddingLeft, top + a.dp(6), it.paddingRight, 0) }
        statusScrim?.let { it.layoutParams.height = top + a.dp(16); it.requestLayout() }
    }

    /** Loads every addon catalog in parallel; each row lands as soon as its own addon answers. */
    fun reload() {
        status.text = "Loading your catalogs…"
        status.visibility = View.VISIBLE
        a.load({
            Addons.ensureDefaults(a)
            Addons.list(a).flatMap { it.catalogs }
        }) { r ->
            val catalogs = r.getOrElse {
                status.text = "Couldn't reach your addons. Check the connection and open Home again."
                return@load
            }
            val slots = arrayOfNulls<Row>(catalogs.size)
            catalogs.forEachIndexed { i, c ->
                a.load({ Addons.catalog(c) }) { res ->
                    slots[i] = Row(rowTitle(c, catalogs), res.getOrDefault(emptyList()))
                    catalogRows = slots.filterNotNull()
                    publish()
                }
            }
            if (catalogs.isEmpty()) status.text = "No catalogs yet. Add an addon in Settings."
        }
    }

    private fun rowTitle(c: Catalog, all: List<Catalog>): String {
        val type = when (c.type) { "movie" -> "Movies"; "series" -> "Series"; else -> c.type.replaceFirstChar(Char::uppercase) }
        // Cinemeta names both its lists "Popular"; say which.
        val name = if (all.count { it.name == c.name && it.addon == c.addon } > 1) "${c.name} $type" else c.name
        return if (all.map { it.addon }.distinct().size > 1 && c.addon.url != Addons.CINEMETA) "$name · ${c.addon.name}" else name
    }

    private fun continueRow(): Row? {
        val cw = Store.continueWatching(a)
        if (cw.isEmpty()) return null
        val known = catalogRows.flatMap { it.items }.associateBy { it.id } + Store.library(a).associateBy { it.id }
        val items = cw.map { p -> known[p.contentId] ?: Item(p.contentId, p.type, p.name ?: "") }
        return Row("Continue Watching", items, cw.associateBy { it.contentId })
    }

    fun refreshContinue() { if (catalogRows.isNotEmpty()) publish() }

    private fun publish() {
        val first = adapter.rows.isEmpty()
        adapter.set(listOfNotNull(continueRow()) + catalogRows)
        if (adapter.rows.isEmpty()) return
        status.visibility = View.GONE
        if (heroItem == null) showHero(adapter.rows.first { it.title != "Continue Watching" || catalogRows.isEmpty() }.items.first())
        if (first && tv) rows.post { rows.findViewHolderForAdapterPosition(0)?.itemView?.let { (it as ViewGroup).getChildAt(1).requestFocus() } }
    }

    fun focused(item: Item, row: Int) {
        layout.snapTo(row)
        pendingItem = item
        view.removeCallbacks(pending)
        view.postDelayed(pending, 220) // only settle on titles the user pauses on
    }

    private fun showHero(item: Item) {
        heroItem = item
        layers?.let { crossfade(it, item.backgroundOrHub, screenW) }
        poster?.let { p ->
            // The backdrop, cropped to portrait: posters print their own title, which would fight the logo and buttons.
            Images.load(p, item.backgroundOrHub ?: item.posterOrHub, a.dp(560)) { ok ->
                if (ok) (p.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.let(::tint)
            }
        }
        title.text = item.name
        meta.text = a.metaText(item, meta.textSize)
        desc.text = item.description
        desc.visibility = if (item.description.isNullOrBlank() || !tv) View.GONE else View.VISIBLE
        val logoUrl = item.logo ?: if (item.id.startsWith("tt")) "https://images.metahub.space/logo/medium/${item.id}/img" else null
        title.visibility = View.VISIBLE
        logo.visibility = View.GONE
        Images.load(logo, logoUrl, logoWidth, alpha = true) { ok ->
            logo.visibility = if (ok) View.VISIBLE else View.GONE
            title.visibility = if (ok) View.GONE else View.VISIBLE
        }
        listButton?.let { setListLabel(it, Store.inLibrary(a, item.id)) }
    }

    /** Phone: tints the glow behind the hero from the poster's average colour, kept dark so white type holds. */
    private fun tint(bmp: android.graphics.Bitmap) {
        val hsv = FloatArray(3)
        Color.colorToHSV(android.graphics.Bitmap.createScaledBitmap(bmp, 1, 1, true).getPixel(0, 0), hsv)
        hsv[1] = hsv[1].coerceAtMost(0.55f)
        hsv[2] = 0.32f
        val top = Color.HSVToColor(hsv)
        hsv[2] = 0.12f
        glow.colors = intArrayOf(top, Color.HSVToColor(hsv), C.ground)
    }

    private fun toggleList() {
        val item = heroItem ?: return
        val on = !Store.inLibrary(a, item.id)
        Store.setInLibrary(a, item, on)
        Nuvio.later(a) { Nuvio.pushLibrary(it, item, on) }
        listButton?.let { setListLabel(it, on) }
    }
}

fun setListLabel(b: MaterialButton, on: Boolean) {
    b.text = if (on) "In My List" else "My List"
    b.setIconResource(if (on) R.drawable.ic_check else R.drawable.ic_add)
}

