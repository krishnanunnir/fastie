package app.fastie

import android.app.AlertDialog
import android.content.Intent
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

private fun MainActivity.side() = dp(if (isTv) 40 else 16)

class SearchPage(private val a: MainActivity) {
    val view = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    private val field = EditText(a)
    private val status = a.text(T.BODY, C.variant)
    private val adapter = RowsAdapter(object : PosterHost {
        override fun open(item: Item, progress: Progress?) = openItem(a, item, null)
    })
    private var query = ""
    private val run = Runnable { search(field.text.toString().trim()) }

    init {
        val top = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        field.apply {
            hint = "Search movies and series"
            setHintTextColor(C.variant)
            setTextColor(C.onSurface)
            textSize = if (a.isTv) 20f else 17f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(a.dp(24), a.dp(16), a.dp(24), a.dp(16))
            background = android.graphics.drawable.StateListDrawable().apply {
                // Material search bar: a full pill in the high container color.
                addState(intArrayOf(android.R.attr.state_focused), rounded(C.containerHigh, a.dp(Shape.FULL).toFloat(), a.dp(2), C.primary))
                addState(intArrayOf(), rounded(C.containerHigh, a.dp(Shape.FULL).toFloat()))
            }
            setCompoundDrawablesRelativeWithIntrinsicBounds(a.icon(R.drawable.ic_search).apply { setTint(C.variant) }, null, null, null)
            compoundDrawablePadding = a.dp(12)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { removeCallbacks(run); postDelayed(run, 450) }
            })
            setOnEditorActionListener { _, id, e ->
                if (id == EditorInfo.IME_ACTION_SEARCH || e?.keyCode == KeyEvent.KEYCODE_ENTER) { removeCallbacks(run); run.run(); hideKeyboard(); true } else false
            }
        }
        top.addView(field, LinearLayout.LayoutParams(0, -2, 1f))
        // Remotes have a mic; typing with a D-pad is slow.
        val voice = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        if (a.isTv && voice.resolveActivity(a.packageManager) != null) {
            top.addView(a.button("Voice", R.drawable.ic_mic) { runCatching { a.startActivityForResult(voice, VOICE) } },
                lp(-2, -2).apply { marginStart = a.dp(12) })
        }
        val s = a.side()
        view.addView(top, lp().apply { setMargins(s, a.dp(if (a.isTv) 32 else 12), s, a.dp(8)) })
        view.addView(status, lp().apply { setMargins(s, a.dp(24), s, 0) })
        view.addView(RecyclerView(a).apply {
            layoutManager = SnapRowsLayout(a)
            adapter = this@SearchPage.adapter
            itemAnimator = null
            clipToPadding = false
            setPadding(0, 0, 0, a.dp(120))
        }, lp(-1, -1))
        status.text = "Find a title across all your addons."
        view.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION") v.setPadding(0, insets.systemWindowInsetTop, 0, 0); insets
        }
    }

    fun focus() {
        field.requestFocus()
        if (!a.isTv) field.post { (a.getSystemService(InputMethodManager::class.java)).showSoftInput(field, 0) }
    }

    private fun hideKeyboard() = a.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken, 0)

    fun voice(text: String) { field.setText(text); field.setSelection(text.length); field.removeCallbacks(run); run.run() }

    private fun search(q: String) {
        if (q == query) return
        query = q
        if (q.length < 2) { adapter.set(emptyList()); status.text = "Find a title across all your addons."; status.visibility = View.VISIBLE; return }
        status.text = "Searching for “$q”…"
        status.visibility = View.VISIBLE
        val catalogs = Addons.list(a).flatMap { it.searchCatalogs }
        val slots = arrayOfNulls<Row>(catalogs.size)
        var answered = 0
        catalogs.forEachIndexed { i, c ->
            a.load({ Addons.catalog(c, q) }) { r ->
                if (q != query) return@load
                val type = if (c.type == "movie") "Movies" else if (c.type == "series") "Series" else c.type.replaceFirstChar(Char::uppercase)
                slots[i] = Row(if (catalogs.count { it.type == c.type } > 1) "$type · ${c.addon.name}" else type, r.getOrDefault(emptyList()))
                adapter.set(slots.filterNotNull())
                answered++
                status.visibility = if (adapter.rows.isEmpty()) View.VISIBLE else View.GONE
                if (adapter.rows.isEmpty() && answered == catalogs.size) status.text = "Nothing found for “$q”. Try another spelling."
            }
        }
        if (catalogs.isEmpty()) status.text = "None of your addons can search. Add one in Settings."
    }

    companion object { const val VOICE = 7 }
}

class LibraryPage(private val a: MainActivity) {
    val view = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
    private val grid = RecyclerView(a)
    private val empty = a.text(T.BODY, C.variant, s = "Titles you add to My List show up here.")
    private val host = object : PosterHost { override fun open(item: Item, progress: Progress?) = openItem(a, item, null) }

    init {
        val s = a.side()
        view.addView(a.text(T.DISPLAY, s = "My List"), lp().apply { setMargins(s, a.dp(if (a.isTv) 32 else 16), s, a.dp(8)) })
        view.addView(empty, lp().apply { setMargins(s, a.dp(12), s, 0) })
        val cell = a.posterWidth() + a.dp(if (a.isTv) 14 else 8)
        val available = a.resources.displayMetrics.widthPixels - (if (a.isTv) a.dp(MainActivity.RAIL) else 0) - 2 * s
        grid.layoutManager = GridLayoutManager(a, (available / cell).coerceAtLeast(3))
        grid.clipToPadding = false
        grid.setPadding(s - a.dp(6), 0, s - a.dp(6), a.dp(120))
        view.addView(grid, lp(-1, -1))
        view.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION") v.setPadding(0, insets.systemWindowInsetTop, 0, 0); insets
        }
        refresh()
    }

    fun refresh() {
        val items = Store.library(a)
        grid.adapter = PosterAdapter(items, host, fill = true)
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }
}

class SettingsPage(private val a: MainActivity) {
    val view = ScrollView(a).apply { isFillViewport = true }
    private val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }

    init {
        val s = a.side()
        col.setPadding(s, a.dp(if (a.isTv) 24 else 8), s, a.dp(120))
        view.addView(col, FrameLayout.LayoutParams(if (a.isTv) a.dp(640) else -1, -2))
        refresh()
    }

    private fun row(title: String, sub: String? = null, onClick: (() -> Unit)? = null) =
        col.addView(a.listRow(title, sub, onClick), lp().apply { bottomMargin = a.dp(8) })

    fun refresh() {
        val keep = col.findFocus()?.let { col.indexOfChild(it) } ?: -1
        col.removeAllViews()
        col.addView(a.text(T.DISPLAY, s = "Settings"), lp().apply { bottomMargin = a.dp(4) })

        col.addView(a.sectionTitle("Nuvio account"))
        if (Nuvio.signedIn(a)) {
            row("Signed in as ${Nuvio.email(a) ?: "your Nuvio account"}", "Your addons, My List and Continue Watching sync with Nuvio.")
            row("Sync now", "Get the latest from your Nuvio account.") { sync() }
            row("Sign out", "Keeps what's on this device; stops syncing.") {
                Nuvio.signOut(a); a.say("Signed out of Nuvio"); refresh()
            }
        } else {
            row("Sign in to Nuvio", "Bring your addons, My List and Continue Watching from Nuvio.") {
                a.startActivity(Intent(a, SignInActivity::class.java))
            }
        }

        col.addView(a.sectionTitle("Addons"))
        Addons.list(a).forEach { addon ->
            val streams = addon.manifest.optJSONArray("resources")?.toString()?.contains("stream") == true
            val what = listOfNotNull(
                addon.catalogs.size.takeIf { it > 0 }?.let { "$it catalogs" },
                "streams".takeIf { streams },
            ).joinToString(" · ").ifBlank { "details only" }
            row(addon.name, "$what\n${addon.base.removePrefix("https://")}") { addonActions(addon) }
        }
        row("Add an addon", "Paste a Stremio addon link (manifest.json).") { addAddon() }

        col.addView(a.sectionTitle("Playback"))
        val external = Store.externalPlayer(a)
        row("Player: ${if (external) "another app" else "Fastie"}",
            if (external) "Streams open in VLC, mpv or any video app you choose." else "Plays here, with subtitles, audio tracks and resume.") {
            Store.setExternalPlayer(a, !external); refresh()
        }

        col.addView(a.sectionTitle("About"))
        row("Fastie ${a.packageManager.getPackageInfo(a.packageName, 0).versionName}",
            "Plays what your addons provide. Fastie hosts no content and isn't affiliated with Nuvio or Stremio.")

        groupRows(col)
        if (keep >= 0) col.getChildAt(keep.coerceAtMost(col.childCount - 1))?.requestFocus()
    }

    private fun sync() {
        a.say("Syncing with Nuvio…")
        a.load({ Nuvio.pull(a) }) { r ->
            r.onSuccess { a.say("Synced with Nuvio"); refresh(); a.reloadHome() }
                .onFailure { a.say("Sync failed: ${it.message?.take(80)}") }
        }
    }

    private fun addonActions(addon: Addon) {
        val actions = arrayOf("Move up", "Move down", "Remove")
        AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(addon.name).setItems(actions) { _, i ->
            when (i) {
                0 -> Addons.move(a, addon, -1)
                1 -> Addons.move(a, addon, 1)
                2 -> Addons.remove(a, addon)
            }
            refresh(); a.reloadHome()
        }.show()
    }

    private fun addAddon() {
        val input = EditText(a).apply { hint = "https://…/manifest.json"; isSingleLine = true; setPadding(a.dp(20), a.dp(16), a.dp(20), a.dp(16)) }
        AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Add an addon").setView(input)
            .setPositiveButton("Add") { _, _ ->
                val url = input.text.toString()
                if (url.isBlank()) return@setPositiveButton
                a.say("Adding addon…")
                a.load({ Addons.add(a, url) }) { r ->
                    r.onSuccess { a.say("Added ${it.name}"); refresh(); a.reloadHome() }
                        .onFailure { a.say("Couldn't add that addon. Check the link and try again.") }
                }
            }.setNegativeButton("Cancel", null).show()
    }
}

/** A Material-style snackbar: one line at the bottom, gone after a few seconds. */
fun MainActivity.say(message: String) {
    val root = window.decorView as FrameLayout
    root.findViewWithTag<View>("snack")?.let(root::removeView)
    val bar = text(T.LABEL, C.onPrimary, lines = 2, s = message).apply {
        tag = "snack"
        background = rounded(C.inverse, dp(Shape.L).toFloat())
        setTextColor(C.inverseOn)
        setPadding(dp(18), dp(14), dp(18), dp(14))
        elevation = dp(6).toFloat()
    }
    root.addView(bar, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(96) })
    bar.postDelayed({ root.removeView(bar) }, 3500)
}

