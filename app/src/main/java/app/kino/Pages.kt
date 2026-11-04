package app.kino

import com.google.android.material.snackbar.Snackbar
import android.app.DownloadManager
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

private fun MainActivity.side() = dp(if (isTv) 48 else 16)

/**
 * Search: a pill field, then before you type your recent searches and what's popular on Home; while searching a red
 * line; then one poster grid merged across every addon that searches, with All / Movies / Series to narrow it.
 */
class SearchPage(private val a: MainActivity) {
    val view = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    private val grid = RecyclerView(a)
    private val box = a.textField("Movies, series, anime", R.drawable.ic_search).apply {
        val r = a.dp(26).toFloat()
        setBoxCornerRadii(r, r, r, r)
        endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_CLEAR_TEXT
        setEndIconTintList(android.content.res.ColorStateList.valueOf(C.variant))
    }
    private val field = box.editText!!
    // While addons answer: the grid shows pulsing poster-shaped placeholders instead of a spinner or a line.
    private val pulse = android.animation.ObjectAnimator.ofFloat(grid, View.ALPHA, 0.35f, 0.8f).apply {
        duration = 700; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE
    }
    private val recentBlock = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    private val filters = LinearLayout(a)
    private val label = a.groupTitle("")
    private val empty = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; visibility = View.GONE
        setPadding(a.dp(32), 0, a.dp(32), a.dp(120))
    }
    private val host = object : PosterHost { override fun open(item: Item, progress: Progress?) = openItem(a, item, null) }
    private var query = ""
    private var results: List<Item> = emptyList()
    private var filter = "all"
    private val run = Runnable { search(field.text.toString().trim()) }

    init {
        val s = a.side()
        view.addView(a.text(T.DISPLAY, s = "Search"), lp().apply { setMargins(s, a.dp(if (a.isTv) 32 else 16), s, a.dp(14)) })
        val top = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        field.apply {
            textSize = if (a.isTv) 20f else 17f
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { removeCallbacks(run); postDelayed(run, 450) }
            })
            setOnEditorActionListener { _, id, e ->
                if (id == EditorInfo.IME_ACTION_SEARCH || e?.keyCode == KeyEvent.KEYCODE_ENTER) { removeCallbacks(run); run.run(); hideKeyboard(); true } else false
            }
        }
        top.addView(box, LinearLayout.LayoutParams(0, -2, 1f))
        // Remotes have a mic; typing with a D-pad is slow.
        val voice = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        if (a.isTv && voice.resolveActivity(a.packageManager) != null) {
            top.addView(a.button("Voice", R.drawable.ic_mic) { runCatching { a.startActivityForResult(voice, VOICE) } },
                lp(-2, -2).apply { marginStart = a.dp(12) })
        }
        view.addView(top, lp().apply { setMargins(s, 0, s, 0) })
        view.addView(recentBlock, lp().apply { setMargins(s, 0, s, 0) })
        view.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(filters) }, lp().apply { setMargins(s, a.dp(14), s, 0) })
        view.addView(label, lp().apply { setMargins(s, 0, s, 0) })
        val cell = a.posterWidth() + a.dp(if (a.isTv) 14 else 8)
        grid.layoutManager = GridLayoutManager(a, ((a.resources.displayMetrics.widthPixels - 2 * s) / cell).coerceAtLeast(3))
        grid.clipToPadding = false
        grid.setPadding(s - a.dp(6), 0, s - a.dp(6), a.dp(136))
        view.addView(grid, lp(-1, 0).apply { weight = 1f })
        view.addView(empty, lp(-1, 0).apply { weight = 1f })
        view.setPadding(0, a.topInset, 0, 0)
        view.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION") v.setPadding(0, insets.systemWindowInsetTop, 0, 0); insets
        }
        idle()
    }

    fun focus() {
        if (query.isEmpty()) idle() // Home may have loaded more since
        field.requestFocus()
        if (!a.isTv) field.post { (a.getSystemService(InputMethodManager::class.java)).showSoftInput(field, 0) }
    }

    private fun hideKeyboard() = a.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken, 0)

    fun voice(text: String) { field.setText(text); field.setSelection(text.length); field.removeCallbacks(run); run.run() }

    /** Before you type: recent searches as chips, and what's popular on Home as posters. */
    private fun idle() {
        stopPulse()
        filters.removeAllViews()
        empty.visibility = View.GONE
        recentBlock.removeAllViews()
        val recent = Store.recentSearches(a)
        if (recent.isNotEmpty()) {
            recentBlock.addView(LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(a.groupTitle("Recent"), LinearLayout.LayoutParams(0, -2, 1f))
                addView(a.text(T.META, C.variant, s = "Clear").apply {
                    setPadding(a.dp(12), a.dp(28), a.dp(4), a.dp(10)); isClickable = true; isFocusable = true
                    setOnClickListener { Store.clearRecentSearches(a); idle() }
                })
            })
            recentBlock.addView(com.google.android.material.chip.ChipGroup(a).apply {
                chipSpacingHorizontal = a.dp(8); chipSpacingVertical = a.dp(8)
                recent.forEach { q -> addView(a.chip(q) { field.setText(q); field.setSelection(q.length); field.removeCallbacks(run); run.run(); hideKeyboard() }) }
            })
        }
        val popular = a.popular()
        label.text = if (popular.isEmpty()) "" else "POPULAR NOW"
        show(popular)
    }

    private fun show(items: List<Item>) {
        stopPulse()
        grid.adapter = PosterAdapter(items, host, fill = true)
        grid.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        grid.isFocusable = items.isNotEmpty() // an empty grid would swallow D-pad focus invisibly
    }

    /** Poster-shaped placeholders, pulsing, until the first results land. */
    private fun skeleton() {
        filters.removeAllViews()
        empty.visibility = View.GONE
        label.text = "SEARCHING…"
        val spans = (grid.layoutManager as GridLayoutManager).spanCount
        grid.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = spans * 3
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val w = (parent.width - parent.paddingLeft - parent.paddingRight) / spans
                return object : RecyclerView.ViewHolder(View(parent.context).apply {
                    background = rounded(C.container, a.dp(Shape.M).toFloat())
                    layoutParams = RecyclerView.LayoutParams(-1, (w - a.dp(8)) * 3 / 2).apply { setMargins(a.dp(4), a.dp(4), a.dp(4), a.dp(4)) }
                }) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
        }
        grid.visibility = View.VISIBLE
        grid.isFocusable = false
        if (!pulse.isStarted) pulse.start()
    }

    private fun stopPulse() { pulse.cancel(); grid.alpha = 1f }

    /** A centred message in place of the grid: an icon, a line, and a hint. */
    private fun message(title: String, hint: String) {
        show(emptyList())
        label.text = ""
        empty.removeAllViews()
        empty.addView(FrameLayout(a).apply {
            background = rounded(C.container, a.dp(40).toFloat())
            addView(ImageView(a).apply { setImageResource(R.drawable.ic_search); setColorFilter(C.variant) },
                FrameLayout.LayoutParams(a.dp(34), a.dp(34), Gravity.CENTER))
        }, lp(a.dp(80), a.dp(80)))
        empty.addView(a.text(T.TITLE, s = title).apply { gravity = Gravity.CENTER }, lp(-2, -2).apply { topMargin = a.dp(20) })
        empty.addView(a.text(T.BODY, C.variant, s = hint).apply { gravity = Gravity.CENTER }, lp(-2, -2).apply { topMargin = a.dp(8) })
        empty.visibility = View.VISIBLE
    }

    private var pending = 0

    /** All, Movies, Series (only the kinds present), narrowing the grid in place. */
    private fun renderResults() {
        val kinds = results.map { it.type }.distinct()
        filters.removeAllViews()
        if (kinds.size > 1) (listOf("all") + kinds).forEach { k ->
            val name = when (k) { "all" -> "All"; "movie" -> "Movies"; "series" -> "Series"; else -> k.replaceFirstChar(Char::uppercase) }
            filters.addView(a.chip(name) { filter = k; renderResults() }.apply { isSelected = k == filter }, lp(-2, -2).apply { marginEnd = a.dp(8) })
        }
        val shown = if (filter == "all") results else results.filter { it.type == filter }
        label.text = "${shown.size} ${if (shown.size == 1) "RESULT" else "RESULTS"}" + if (pending > 0) "  ·  SEARCHING…" else ""
        empty.visibility = View.GONE
        show(shown)
    }

    private fun search(q: String) {
        if (q == query) return
        query = q
        recentBlock.removeAllViews()
        if (q.length < 2) { results = emptyList(); filter = "all"; return idle() }
        val catalogs = Addons.list(a).flatMap { it.searchCatalogs }
        if (catalogs.isEmpty()) return message("Nothing can search yet", "None of your addons can search. Add one in Settings.")
        results = emptyList()
        pending = catalogs.size
        skeleton()
        val slots = arrayOfNulls<List<Item>>(catalogs.size)
        var answered = 0
        catalogs.forEachIndexed { i, c ->
            a.load({ Addons.catalog(c, q) }) { r ->
                if (q != query) return@load
                slots[i] = r.getOrDefault(emptyList())
                answered++
                pending = catalogs.size - answered
                // Every addon's results in one grid, in addon order, each title once.
                results = slots.filterNotNull().flatten().distinctBy { it.id }
                if (filter != "all" && results.none { it.type == filter }) filter = "all"
                when {
                    results.isNotEmpty() -> { renderResults(); if (answered == catalogs.size) Store.addRecentSearch(a, q) }
                    answered == catalogs.size -> message("No matches for “$q”", "Check the spelling, or try the original title.")
                }
            }
        }
    }

    companion object { const val VOICE = 7 }
}

class LibraryPage(private val a: MainActivity) {
    val view = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    private val grid = RecyclerView(a)
    private val count = a.text(T.META, C.variant)
    private val host = object : PosterHost { override fun open(item: Item, progress: Progress?) = openItem(a, item, null) }
    // Downloads, above the saved titles: one row each with its progress, updated every second while any is going.
    private val downloads = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    private var shown = emptyList<Download>()
    private val rows = mutableMapOf<Long, Pair<TextView, com.google.android.material.progressindicator.LinearProgressIndicator>>()
    private val tick = Runnable { showDownloads() }

    /** Nothing saved yet: a bookmark in a circle, a line on how to save, and a way back to browsing. */
    private val empty = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(a.dp(32), 0, a.dp(32), a.dp(120))
        addView(FrameLayout(a).apply {
            background = rounded(C.container, a.dp(40).toFloat())
            addView(ImageView(a).apply { setImageResource(R.drawable.ic_list); setColorFilter(C.variant) },
                FrameLayout.LayoutParams(a.dp(34), a.dp(34), Gravity.CENTER))
        }, lp(a.dp(80), a.dp(80)))
        addView(a.text(T.TITLE, s = "Nothing saved yet").apply { gravity = Gravity.CENTER }, lp(-2, -2).apply { topMargin = a.dp(20) })
        addView(a.text(T.BODY, C.variant, s = "Tap My List on any title to keep it here. It syncs with your Nuvio account.").apply {
            gravity = Gravity.CENTER
        }, lp(-2, -2).apply { topMargin = a.dp(8) })
        addView(a.button("Browse Home", primary = true) { a.show(0) }, lp(-2, -2).apply { topMargin = a.dp(24) })
    }

    init {
        val s = a.side()
        view.addView(a.text(T.DISPLAY, s = "My List"), lp().apply { setMargins(s, a.dp(if (a.isTv) 32 else 16), s, 0) })
        view.addView(count, lp().apply { setMargins(s, a.dp(4), s, a.dp(12)) })
        view.addView(downloads, lp(if (a.isTv) a.dp(640) else -1, -2).apply { setMargins(s, 0, s, a.dp(12)) })
        view.addView(empty, lp(-1, 0).apply { weight = 1f })
        val cell = a.posterWidth() + a.dp(if (a.isTv) 14 else 8)
        val available = a.resources.displayMetrics.widthPixels - 2 * s
        grid.layoutManager = GridLayoutManager(a, (available / cell).coerceAtLeast(3))
        grid.clipToPadding = false
        grid.setPadding(s - a.dp(6), 0, s - a.dp(6), a.dp(136))
        view.addView(grid, lp(-1, 0).apply { weight = 1f })
        // Created after the window's insets arrived, so it starts from what Main measured.
        view.setPadding(0, a.topInset, 0, 0)
        view.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION") v.setPadding(0, insets.systemWindowInsetTop, 0, 0); insets
        }
        refresh()
    }

    fun refresh() {
        val items = Store.library(a)
        grid.adapter = PosterAdapter(items, host, fill = true)
        showDownloads()
        empty.visibility = if (items.isEmpty() && shown.isEmpty()) View.VISIBLE else View.GONE
        grid.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        count.text = if (items.isEmpty()) "" else "${items.size} ${if (items.size == 1) "title" else "titles"}"
        count.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        grid.isFocusable = items.isNotEmpty() // an empty grid would swallow D-pad focus invisibly
    }

    private fun showDownloads() {
        val list = Downloads.list(a)
        // Rebuilt only when a download comes, goes or finishes, so D-pad focus stays put while the numbers move.
        if (list.map { it.id to it.status } != shown.map { it.id to it.status }) {
            downloads.removeAllViews(); rows.clear()
            if (list.isNotEmpty()) {
                downloads.addView(a.groupTitle("Downloads").apply { setPadding(a.dp(4), a.dp(4), 0, a.dp(10)) })
                downloads.addView(a.group().apply {
                    list.forEach { d ->
                        val bar = a.progressLine().apply { visibility = if (d.running) View.VISIBLE else View.GONE }
                        val row = groupRow(d.label, "", below = bar) { downloadSheet(d) }
                        rows[d.id] = ((row.getChildAt(0) as LinearLayout).getChildAt(1) as TextView) to bar
                    }
                })
            }
        }
        shown = list
        list.forEach { d -> rows[d.id]?.let { (sub, bar) -> sub.text = status(d); bar.fraction = d.fraction } }
        downloads.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        view.removeCallbacks(tick)
        if (list.any { it.running }) view.postDelayed(tick, 1000)
    }

    private fun status(d: Download): String {
        val size = { b: Long -> android.text.format.Formatter.formatShortFileSize(a, b) }
        return when (d.status) {
            DownloadManager.STATUS_SUCCESSFUL -> "Downloaded  ·  ${size(d.total)}"
            DownloadManager.STATUS_FAILED -> "Download failed. Delete it and try another stream."
            DownloadManager.STATUS_PAUSED -> "Waiting for the network  ·  ${size(d.done)}"
            else -> if (d.total <= 0) "Starting…" else "Downloading ${(d.fraction * 100).toInt()}%  ·  ${size(d.done)} of ${size(d.total)}"
        }
    }

    /** Play (once it's finished) or delete one download. */
    private fun downloadSheet(d: Download) {
        val shell = a.sheet(d.label, status(d))
        val pad = a.dp(if (a.isTv) 32 else 20)
        if (d.finished) shell.body.addView(a.button("Play", R.drawable.ic_play, primary = true) { shell.dialog.dismiss(); a.playDownload(d) },
            lp().apply { setMargins(pad, 0, pad, a.dp(10)) })
        shell.body.addView(a.button(if (d.finished) "Delete download" else "Cancel download") {
            shell.dialog.dismiss(); Downloads.delete(a, d.id); refresh()
        }, lp().apply { setMargins(pad, 0, pad, 0) })
        shell.dialog.show()
    }
}

class SettingsPage(private val a: MainActivity) {
    val view = ScrollView(a).apply { isFillViewport = true }
    private val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    // Each debrid service's plan and expiry, asked for once per visit (declared before init, which draws the page).
    private val plans = mutableMapOf<Debrid, String>()
    private val asked = mutableSetOf<Debrid>()

    init {
        val s = a.side()
        col.setPadding(s, a.dp(if (a.isTv) 24 else 8), s, a.dp(136))
        view.addView(col, FrameLayout.LayoutParams(if (a.isTv) a.dp(640) else -1, -2))
        refresh()
    }

    fun refresh() {
        val keep = col.findFocus()?.let { f -> generateSequence(f) { it.parent as? View }.firstOrNull { it.parent === col } }?.let { col.indexOfChild(it) } ?: -1
        col.removeAllViews()
        col.addView(a.text(T.DISPLAY, s = "Settings"), lp().apply { bottomMargin = a.dp(4) })

        // Account: who you are, and what syncing does, with its two actions right there.
        col.addView(a.groupTitle("Nuvio account"))
        col.addView(a.group().apply {
            if (Nuvio.signedIn(a)) {
                val email = Nuvio.email(a) ?: "Your Nuvio account"
                val at = Nuvio.syncedAt(a)
                val synced = if (at == 0L) "My List, progress and addons sync with Nuvio"
                    else if (System.currentTimeMillis() - at < 60_000) "Synced just now"
                    else "Synced " + android.text.format.DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), 60_000).toString().lowercase()
                groupRow(email, synced, leading = a.initial(email))
                addView(LinearLayout(a).apply {
                    setPadding(a.dp(16), a.dp(4), a.dp(16), a.dp(16))
                    lateinit var syncButton: com.google.android.material.button.MaterialButton
                    syncButton = a.button("Sync now") { syncButton.text = "Syncing…"; syncButton.isEnabled = false; sync() }
                    addView(syncButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = a.dp(10) })
                    addView(a.button("Sign out") { Nuvio.signOut(a); a.say("Signed out of Nuvio"); refresh() }, LinearLayout.LayoutParams(0, -2, 1f))
                })
            } else groupRow("Sign in to Nuvio", "Bring your addons, My List and Continue Watching", leading = a.initial("N"), trailing = a.chevron()) {
                a.startActivity(Intent(a, SignInActivity::class.java))
            }
        })

        // Addons: logo, name, what each one provides, and just its site (the rest of the link is its private config).
        col.addView(a.groupTitle("Addons"))
        col.addView(a.group().apply {
            Addons.list(a).forEach { addon ->
                val pills = rolePills(addon)
                groupRow(addon.name, Uri.parse(addon.url).host, leading = badge(addon, 40), trailing = a.chevron(), below = pills, subLines = 1) { addonSheet(addon) }
            }
            groupRow("Add an addon", "Paste a Stremio addon link", leading = a.initial("+"), trailing = a.chevron()) { addAddon() }
        })

        // Playback: where streams open, and the debrid service that makes torrents playable.
        col.addView(a.groupTitle("Playback"))
        col.addView(a.group().apply {
            val external = Store.externalPlayer(a)
            val toggle = com.google.android.material.materialswitch.MaterialSwitch(a).apply {
                isChecked = external; isClickable = false; isFocusable = false
                val on = intArrayOf(android.R.attr.state_checked)
                trackTintList = android.content.res.ColorStateList(arrayOf(on, intArrayOf()), intArrayOf(C.onSurface, C.containerHighest))
                thumbTintList = android.content.res.ColorStateList(arrayOf(on, intArrayOf()), intArrayOf(C.ground, C.variant))
                trackDecorationTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            }
            groupRow("Use another player app", if (external) "Streams open in VLC, mpv or any video app" else "Plays in Kino, with subtitles, audio tracks and resume",
                trailing = toggle) { Store.setExternalPlayer(a, !external); refresh() }
            val debrid = Debrid.connected(a)?.accounts.orEmpty()
            debrid.forEach { (service, key) ->
                groupRow(service.label, plans[service] ?: "Cached torrents from your addons play through it",
                    leading = a.initial(service.label), trailing = a.pill("Connected", solid = true)) { debridKey(service) }
                // The plan and expiry come from the service, once per visit to Settings.
                if (asked.add(service)) a.load({ service.plan(key) }) { r -> r.getOrNull()?.let { plans[service] = it; refresh() } }
            }
            if (debrid.size < Debrid.entries.size) groupRow(if (debrid.isEmpty()) "Debrid service" else "Add a debrid service",
                if (debrid.isEmpty()) "Connect TorBox, Real-Debrid and others so torrents can play" else "Torrents play through whichever has them",
                leading = a.initial(if (debrid.isEmpty()) "D" else "+"), trailing = a.chevron()) { chooseDebrid() }
        })

        // About, quietly at the end.
        col.addView(ImageView(a).apply { setImageResource(R.drawable.kino_logo); alpha = 0.9f },
            lp(a.dp(80), a.dp(24)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = a.dp(36) })
        col.addView(a.text(T.META, C.variant, s = "Version ${a.packageManager.getPackageInfo(a.packageName, 0).versionName}\n" +
            "Plays what your addons provide. Kino hosts no content and isn't affiliated with Nuvio or Stremio.").apply {
            gravity = Gravity.CENTER; setLineSpacing(0f, 1.3f)
        }, lp().apply { setMargins(a.dp(16), a.dp(10), a.dp(16), 0) })

        if (keep >= 0) col.getChildAt(keep.coerceAtMost(col.childCount - 1))?.requestFocus()
    }

    private fun sync() {
        a.say("Syncing with Nuvio…")
        a.load({ Nuvio.pull(a) }) { r ->
            r.onSuccess { a.say("Synced with Nuvio"); refresh(); a.reloadHome() }
                .onFailure { a.say("Sync failed: ${it.message?.take(80)}"); refresh() }
        }
    }


    /** What an addon provides, as pills: Catalogs, Streams, Subtitles (or Details when it's none of those). */
    private fun rolePills(addon: Addon) = com.google.android.material.chip.ChipGroup(a).apply {
        chipSpacingHorizontal = a.dp(6); chipSpacingVertical = a.dp(6)
        val resources = addon.manifest.optJSONArray("resources")?.toString().orEmpty()
        listOfNotNull(
            "Catalogs".takeIf { addon.catalogs.isNotEmpty() },
            "Streams".takeIf { "stream" in resources },
            "Subtitles".takeIf { "subtitles" in resources },
        ).ifEmpty { listOf("Details") }.forEach { addView(a.pill(it)) }
    }

    /** The addon's logo, whole, inside a circle; its initial until (or unless) the logo loads. */
    private fun badge(addon: Addon, size: Int) = a.initial(addon.name, size).apply {
        val letter = getChildAt(0)
        val logo = ImageView(a).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER; visibility = View.GONE
            val p = a.dp(size / 7); setPadding(p, p, p, p)
        }
        addView(logo, FrameLayout.LayoutParams(-1, -1))
        outlineProvider = MorphOutline(a.dp(size / 2).toFloat()); clipToOutline = true
        addon.manifest.optString("logo").takeIf { it.startsWith("http") }?.let { url ->
            Images.load(logo, url, a.dp(size), alpha = true) { ok ->
                logo.visibility = if (ok) View.VISIBLE else View.GONE
                letter.visibility = if (ok) View.GONE else View.VISIBLE
            }
        }
    }

    /** One addon up close: what it is and does, then moving, copying and removing it. */
    private fun addonSheet(addon: Addon) {
        val all = Addons.list(a)
        val i = all.indexOfFirst { it.url == addon.url }
        // "0.1.1793454912113" reads as "v0.1.1": the parts after the third are build stamps.
        val version = addon.manifest.optString("version").takeIf { it.isNotBlank() }?.let { "v" + it.split('.').take(3).joinToString(".") }
        val sheet = a.sheet(addon.name, listOfNotNull(Uri.parse(addon.url).host, version).joinToString("  ·  "))
        val pad = a.dp(if (a.isTv) 32 else 20)
        sheet.body.addView(LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, 0)
            addView(LinearLayout(a).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(badge(addon, 56), lp(a.dp(56), a.dp(56)).apply { marginEnd = a.dp(14) })
                addView(rolePills(addon), LinearLayout.LayoutParams(0, -2, 1f))
            })
            addon.manifest.optString("description").takeIf { it.isNotBlank() }?.let {
                addView(a.text(T.BODY, C.variant, lines = 4, s = it.trim()), lp().apply { topMargin = a.dp(14) })
            }
            fun done(message: String) { sheet.dialog.dismiss(); a.say(message); refresh(); a.reloadHome() }
            addView(a.group().apply {
                if (i > 0) groupRow("Move up", "Its catalogs come earlier on Home", trailing = a.chevron()) { Addons.move(a, addon, -1); done("Moved ${addon.name} up") }
                if (i in 0 until all.lastIndex) groupRow("Move down", "Its catalogs come later on Home", trailing = a.chevron()) { Addons.move(a, addon, 1); done("Moved ${addon.name} down") }
                groupRow("Copy link", "The full manifest link, with its settings") {
                    a.getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(android.content.ClipData.newPlainText(addon.name, addon.url))
                    sheet.dialog.dismiss(); a.say("Link copied")
                }
                groupRow(android.text.SpannableString("Remove").apply { setSpan(android.text.style.ForegroundColorSpan(C.danger), 0, length, 0) }, null) {
                    Addons.remove(a, addon); done("Removed ${addon.name}")
                }
            }, lp().apply { topMargin = a.dp(18) })
            if (Nuvio.signedIn(a)) addView(a.text(T.META, C.variant, s = "Changes here stay on this device; your Nuvio list replaces them on the next sync.").apply {
                setPadding(a.dp(4), a.dp(12), a.dp(4), 0)
            })
        })
        sheet.dialog.show()
    }

    /** A sheet with a field for the link, a Paste button (links usually come from the clipboard) and Add. */
    private fun addAddon() {
        val sheet = a.sheet("Add an addon", "Paste a Stremio addon link (it ends in manifest.json)")
        val pad = a.dp(if (a.isTv) 32 else 20)
        val box = a.textField("https://…/manifest.json")
        val field = box.editText!!
        fun add() {
            val url = field.text.toString().trim()
            if (url.isBlank()) return
            sheet.dialog.dismiss()
            a.say("Adding addon…")
            a.load({ Addons.add(a, url) }) { r ->
                r.onSuccess { a.say("Added ${it.name}"); refresh(); a.reloadHome() }
                    .onFailure { a.say("Couldn't add that addon. Check the link and try again.") }
            }
        }
        sheet.body.addView(LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, 0)
            addView(box)
            addView(LinearLayout(a).apply {
                addView(a.button("Paste") {
                    a.getSystemService(android.content.ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(a)?.let {
                        field.setText(it.toString().trim()); field.setSelection(field.text.length)
                    }
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = a.dp(10) })
                addView(a.button("Add", primary = true) { add() }, LinearLayout.LayoutParams(0, -2, 1f))
            }, lp().apply { topMargin = a.dp(14) })
        })
        field.setOnEditorActionListener { _, _, _ -> add(); true }
        sheet.dialog.show()
    }

    /** The debrid services to connect; any number can be, and they're tried in this order. */
    private fun chooseDebrid() {
        val now = Debrid.connected(a)?.accounts.orEmpty().map { it.service }
        val sheet = a.sheet("Debrid service", "Connect any you have an account with. Kino tries them in this order.")
        val pad = a.dp(if (a.isTv) 32 else 20)
        sheet.body.addView(a.group().apply {
            Debrid.entries.forEach { s ->
                groupRow(s.label, "Your API key is at ${s.keyAt}", leading = a.initial(s.label), trailing = if (s in now) a.pill("Connected", solid = true) else a.chevron()) { sheet.dialog.dismiss(); debridKey(s) }
            }
        }, lp().apply { setMargins(pad, 0, pad, 0) })
        sheet.dialog.show()
    }

    /** Asks for [service]'s API key in a sheet, checks it with the service, and keeps it on this device. */
    private fun debridKey(service: Debrid) {
        val name = service.label
        val connected = Debrid.connected(a)?.accounts.orEmpty().any { it.service == service }
        val sheet = a.sheet(name, "Find your API key at ${service.keyAt}. It stays on this device.")
        val pad = a.dp(if (a.isTv) 32 else 20)
        val box = a.textField(if (connected) "Paste a new API key" else "$name API key").apply {
            endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
            setEndIconTintList(android.content.res.ColorStateList.valueOf(C.variant))
            editText!!.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            editText!!.typeface = android.graphics.Typeface.DEFAULT
        }
        sheet.body.addView(LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, 0, pad, 0)
            addView(box)
            addView(LinearLayout(a).apply {
                if (connected) addView(a.button("Remove key") {
                    Debrid.connect(a, service, null); plans.remove(service); asked.remove(service); sheet.dialog.dismiss(); a.say("$name removed"); refresh()
                }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = a.dp(10) })
                addView(a.button("Save", primary = true) {
                    val key = box.editText!!.text.toString().trim()
                    if (key.isBlank()) return@button
                    sheet.dialog.dismiss()
                    a.say("Checking your $name key…")
                    a.load({ service.plan(key) }) { r ->
                        r.onSuccess { Debrid.connect(a, service, key); it?.let { p -> plans[service] = p }; asked.add(service); a.say("$name connected"); refresh() }
                            // A 401 or an error in the reply's own envelope is the key; anything else is the network.
                            .onFailure { a.say(if (it is HttpException && it.code in 401..403 || it is IllegalStateException) "$name didn't accept that key." else "Couldn't reach $name. Try again.") }
                    }
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }, lp().apply { topMargin = a.dp(14) })
        })
        sheet.dialog.show()
    }
}

/** A snackbar: the one light surface, above the phone's bottom bar, gone after a few seconds. */
fun MainActivity.say(message: String) {
    Snackbar.make(findViewById(android.R.id.content), message, 3500).apply {
        if (!isTv) anchorView = navBar
        view.background = rounded(C.inverse, dp(Shape.S).toFloat())
        view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).apply { styled(T.LABEL); setTextColor(C.inverseOn); maxLines = 2 }
    }.show()
}

