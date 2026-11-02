package app.fastie

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
import androidx.recyclerview.widget.RecyclerView

/** Home, Search, My List and Settings, under a rail (TV) or a bottom bar (phone). */
class MainActivity : Activity(), PosterHost {
    private lateinit var pages: FrameLayout
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
        val bar = if (isTv) rail() else bottomBar()
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
            insets
        }
        setContentView(root)
        // Entering the rail lands on the current page, not on whichever icon is nearest:
        // outside the rail only that icon can take focus.
        if (isTv) root.viewTreeObserver.addOnGlobalFocusChangeListener { _, now ->
            val inRail = now != null && now.parent === bar
            nav.forEach { it.isFocusable = inRail || it.tag == page }
        }
        home = HomePage(this)
        show(0)
        // Each launch picks up what changed on other devices.
        if (Nuvio.signedIn(this)) load({ Nuvio.pull(this) }) { if (it.isSuccess) home.reload() }
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

    /** TV: a navigation rail. Phone: a navigation bar. Both use Expressive's pill indicator behind the icon. */
    private fun rail() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        layoutParams = FrameLayout.LayoutParams(dp(RAIL), -1)
        setPadding(0, dp(28), 0, 0)
        background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(C.alpha(C.ground, 0.95f), C.alpha(C.ground, 0f)))
        addView(ImageView(context).apply { setImageResource(R.drawable.ic_bolt); setColorFilter(C.primary) }, lp(dp(28), dp(28)).apply { bottomMargin = dp(36) })
        // Search first, as on every big-screen streaming app.
        for (i in intArrayOf(1, 0, 2, 3)) addView(navItem(i), lp(dp(RAIL), -2).apply { bottomMargin = dp(12) })
    }

    private fun bottomBar() = LinearLayout(this).apply {
        layoutParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM)
        setBackgroundColor(C.container)
        for (i in 0..3) addView(navItem(i), LinearLayout.LayoutParams(0, -2, 1f))
    }

    private fun navItem(i: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isClickable = true
        isFocusable = isTv
        contentDescription = labels[i]
        setPadding(0, dp(6), 0, dp(6))
        val pill = FrameLayout(context).apply { background = rounded(android.graphics.Color.TRANSPARENT, dp(16).toFloat()) }
        pill.addView(ImageView(context).apply { setImageResource(icons[i]) }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        addView(pill, lp(dp(56), dp(32)))
        addView(text(T.META, s = labels[i]).apply { setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (isTv) 13f else 12f); setPadding(0, dp(4), 0, 0) }, lp(-2, -2))
        setOnFocusChangeListener { v, _ -> tint(v, i) }
        setOnClickListener { show(i) }
        nav += this
        tag = i
    }

    private fun tint(v: View, i: Int) {
        val item = v as LinearLayout
        val pill = item.getChildAt(0) as FrameLayout
        val icon = pill.getChildAt(0) as ImageView
        val label = item.getChildAt(1) as TextView
        val active = i == page
        val (fill, ink) = when {
            v.isFocused -> C.primary to C.onPrimary
            active -> C.secondaryContainer to C.onSecondaryContainer
            else -> android.graphics.Color.TRANSPARENT to C.variant
        }
        val wasFilled = (pill.background as android.graphics.drawable.GradientDrawable).color?.defaultColor != android.graphics.Color.TRANSPARENT
        (pill.background as android.graphics.drawable.GradientDrawable).setColor(fill)
        icon.setColorFilter(ink)
        label.setTextColor(if (active || v.isFocused) C.onSurface else C.variant)
        label.typeface = android.graphics.Typeface.create("sans-serif-medium", if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        // The indicator grows out from the icon when it appears.
        if (!wasFilled && fill != android.graphics.Color.TRANSPARENT) {
            pill.scaleX = 0.55f
            pill.animate().scaleX(1f).setInterpolator(SPRING).setDuration(SPRING.duration).start()
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
        }.also { views[i] = it; pages.addView(it, FrameLayout.LayoutParams(-1, -1).apply { marginStart = if (isTv) dp(RAIL) else 0 }) }
        views.forEach { it?.visibility = if (it === v) View.VISIBLE else View.GONE }
        nav.forEach { tint(it, it.tag as Int); if (isTv && it.findFocus() == null) it.isFocusable = it.tag == i }
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
        const val RAIL = 88
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

class HomePage(private val a: MainActivity) {
    val view = FrameLayout(a)
    private val tv = a.isTv
    private val adapter: RowsAdapter
    private val rows = RecyclerView(a)
    private val layout = SnapRowsLayout(a)
    private val hero = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    private val logo = ImageView(a).apply { scaleType = ImageView.ScaleType.FIT_START }
    private val title = a.text(T.DISPLAY, lines = 2)
    private val meta = a.text(T.META, C.variant, lines = 1)
    private val desc = a.text(T.BODY, C.variant, lines = 3)
    private val status = a.text(T.BODY, C.variant)
    private val layers: Pair<ImageView, ImageView>
    private var catalogRows: List<Row> = emptyList()
    private var heroItem: Item? = null
    private var listButton: TextView? = null
    private val pending = Runnable { pendingItem?.let(::showHero) }
    private var pendingItem: Item? = null
    private val width = a.resources.displayMetrics.widthPixels

    init {
        hero.addView(logo, lp(if (tv) (width * 0.3f).toInt() else (width * 0.62f).toInt(), a.dp(if (tv) 96 else 84)).apply { bottomMargin = a.dp(14) })
        hero.addView(title, lp().apply { bottomMargin = a.dp(10) })
        hero.addView(meta, lp().apply { bottomMargin = a.dp(10) })
        hero.addView(desc, lp())
        rows.layoutManager = layout
        rows.itemAnimator = null
        rows.clipToPadding = false
        if (tv) {
            // Fixed hero on top, rows below; the backdrop follows focus.
            layers = a.backdrop(view, 0.74f)
            view.addView(hero, FrameLayout.LayoutParams((width * 0.42f).toInt(), -2).apply {
                topMargin = (a.resources.displayMetrics.heightPixels * 0.09f).toInt(); marginStart = a.dp(40)
            })
            adapter = RowsAdapter(a)
            view.addView(rows, FrameLayout.LayoutParams(-1, -1).apply { topMargin = (a.resources.displayMetrics.heightPixels * 0.5f).toInt() })
            rows.setPadding(a.dp(28), 0, 0, a.dp(240))
        } else {
            // The hero scrolls away with the rows, as on Netflix's phone app.
            val h = (a.resources.displayMetrics.heightPixels * 0.66f).toInt()
            val header = FrameLayout(a).apply { layoutParams = RecyclerView.LayoutParams(-1, h) }
            layers = a.backdrop(header, 0.66f)
            val buttons = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
            buttons.addView(a.button("Play", R.drawable.ic_play, primary = true) { heroItem?.let { openItem(a, it, null) } }, lp(0, -2).apply { weight = 1f; marginEnd = a.dp(10) })
            listButton = a.button("My List", R.drawable.ic_add) { toggleList() }
            buttons.addView(listButton, lp(0, -2).apply { weight = 1f })
            hero.addView(buttons, lp().apply { topMargin = a.dp(18) })
            hero.gravity = Gravity.BOTTOM
            header.addView(hero, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(a.dp(16), 0, a.dp(16), a.dp(8)) })
            header.setOnClickListener { heroItem?.let { openItem(a, it, null) } }
            adapter = RowsAdapter(a, header)
            view.addView(rows, FrameLayout.LayoutParams(-1, -1))
            rows.setPadding(0, 0, 0, a.dp(16))
        }
        rows.adapter = adapter
        view.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        // Wordmark, phone only: the TV rail carries the bolt.
        if (!tv) view.addView(a.text(T.TITLE, C.primary, s = "fastie").apply {
            letterSpacing = -0.02f; setShadowLayer(12f, 0f, 2f, 0x99000000.toInt())
            setOnApplyWindowInsetsListener { v, insets ->
                @Suppress("DEPRECATION") (v.layoutParams as FrameLayout.LayoutParams).topMargin = insets.systemWindowInsetTop + a.dp(10)
                v.requestLayout(); insets
            }
        }, FrameLayout.LayoutParams(-2, -2).apply { marginStart = a.dp(16) })
        reload()
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
        crossfade(layers, item.backgroundOrHub, width)
        title.text = item.name
        meta.text = listOfNotNull(item.metaLine.ifBlank { null }, item.rating?.let { "IMDb $it" }).joinToString("  ·  ")
        desc.text = item.description
        desc.visibility = if (item.description.isNullOrBlank()) View.GONE else View.VISIBLE
        val logoUrl = item.logo ?: if (item.id.startsWith("tt")) "https://images.metahub.space/logo/medium/${item.id}/img" else null
        title.visibility = View.VISIBLE
        logo.visibility = View.GONE
        Images.load(logo, logoUrl, (width * 0.4f).toInt(), alpha = true) { ok ->
            logo.visibility = if (ok) View.VISIBLE else View.GONE
            title.visibility = if (ok) View.GONE else View.VISIBLE
        }
        listButton?.let { setListLabel(it, Store.inLibrary(a, item.id)) }
    }

    private fun toggleList() {
        val item = heroItem ?: return
        val on = !Store.inLibrary(a, item.id)
        Store.setInLibrary(a, item, on)
        Nuvio.later(a) { Nuvio.pushLibrary(it, item, on) }
        listButton?.let { setListLabel(it, on) }
    }
}

fun setListLabel(b: TextView, on: Boolean) {
    b.text = if (on) "In My List" else "My List"
    val d = b.context.icon(if (on) R.drawable.ic_check else R.drawable.ic_add).apply {
        setBounds(0, 0, b.context.dp(22), b.context.dp(22)); setTintList(b.textColors)
    }
    b.setCompoundDrawablesRelative(d, null, null, null)
}

