package app.kino

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONArray
import org.json.JSONObject
import com.google.android.material.snackbar.Snackbar

/** Every stream your addons offer for one movie or episode, grouped by addon. */
class StreamsActivity : Activity() {
    private lateinit var item: Item
    private lateinit var col: LinearLayout
    private lateinit var status: android.widget.TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        edgeToEdge()
        item = Item.from(JSONObject(intent.getStringExtra("item")!!))
        val type = intent.getStringExtra("type")!!
        val id = intent.getStringExtra("id")!!
        val season = intent.getIntExtra("season", -1)
        val episode = intent.getIntExtra("episode", -1)

        val root = FrameLayout(this).apply { setBackgroundColor(C.ground) }
        val layers = backdrop(root, if (isTv) 1f else 0.4f)
        crossfade(layers, item.backgroundOrHub, resources.displayMetrics.widthPixels)
        // Dim the art further: this screen is for reading.
        root.addView(View(this).apply { setBackgroundColor(C.alpha(C.ground, 0.7f)) }, FrameLayout.LayoutParams(-1, -1))
        val side = dp(if (isTv) 48 else 16)
        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(side, dp(if (isTv) 40 else 56), side, dp(48)) }
        col.addView(text(T.DISPLAY, lines = 2, s = item.name.ifBlank { "Streams" }))
        if (season >= 0) {
            val ep = intent.getStringExtra("episodeTitle")?.takeIf { it.isNotBlank() }
            col.addView(text(T.META, C.variant, s = listOfNotNull("Season $season, Episode $episode", ep).joinToString("  ·  "))
                .apply { setPadding(0, dp(8), 0, 0) })
        }
        status = text(T.BODY, C.variant, s = "Asking your addons for streams…").apply { setPadding(0, dp(28), 0, 0) }
        col.addView(status)
        root.addView(ScrollView(this).apply {
            addView(col, FrameLayout.LayoutParams(if (isTv) dp(720) else -1, -2))
        }, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        // Resume within a few hours goes straight back to the link it played from; the list still loads underneath.
        Store.lastLink(this, id)?.let { (url, subs) -> play(url, subs, type, id, season, episode) }

        // Torrents only play through debrid, so keep just the ones TorBox already has.
        load({
            val all = Addons.streams(this, type, id)
            val key = TorBox.key(this)
            val torrents = all.filter { it.hash != null }
            val cached = if (key != null && torrents.isNotEmpty()) TorBox.cached(key, torrents.map { it.hash!! }) else emptySet()
            val playable = all.filter { it.url != null || it.hash in cached }
            // Phones get 1080p at a bitrate Wi-Fi or mobile data keeps up with; TVs get 4K short of remux bitrates.
            val ranked = rankStreams(playable, if (isTv) 2160 else 1080, if (isTv) 40.0 else 20.0, if (season >= 0) 50 else 120)
            Triple(ranked, torrents.distinctBy { it.hash }.size, key != null)
        }) { r ->
            val (streams, torrents, debrid) = r.getOrDefault(Triple(emptyList(), 0, false))
            if (streams.isEmpty()) {
                status.text = when {
                    torrents > 0 && !debrid -> "Your addons found $torrents torrents for this one. Connect TorBox in Settings to play them."
                    torrents > 0 -> "Your addons found $torrents torrents, but none are cached on TorBox yet."
                    else -> "No playable streams for this one. Add a stream addon in Settings."
                }
                return@load
            }
            status.visibility = View.GONE
            streams.forEachIndexed { i, s ->
                if (i == 0) col.addView(sectionTitle("Best for this ${if (isTv) "TV" else "phone"}"))
                if (i == 1) col.addView(sectionTitle("More streams"))
                val sub = listOfNotNull(s.addon, "TorBox".takeIf { s.hash != null }, s.title.ifBlank { null }).joinToString("  ·  ")
                val row = listRow(s.name.replace('\n', ' '), sub) { open(s, type, id, season, episode) }
                col.addView(row, lp().apply { bottomMargin = dp(8) })
                if (i == 0) row.requestFocus()
            }
            // Get the best pick's link ready while you read, so choosing it starts at once.
            streams.first().let { s -> if (s.hash != null) load({ resolve(s) }) {} }
        }
    }

    // Links already fetched from TorBox on this screen, by torrent hash.
    private val links = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun resolve(s: Stream): String = links[s.hash!!] ?: TorBox.resolve(TorBox.key(this)!!, s.hash, s.filename, s.fileIdx).also { links[s.hash] = it }

    private fun subsJson(s: Stream) = JSONArray().apply { s.subtitles.forEach { (lang, url) -> put(JSONObject().put("lang", lang).put("url", url)) } }.toString()

    /** Direct links play at once; a cached torrent first gets its link from TorBox, unless it's already fetched. */
    private fun open(s: Stream, type: String, id: String, season: Int, episode: Int) {
        if (s.url != null || TorBox.key(this) == null) return play(s.url ?: return, subsJson(s), type, id, season, episode)
        links[s.hash!!]?.let { return play(it, subsJson(s), type, id, season, episode) }
        val root = findViewById<View>(android.R.id.content)
        val wait = Snackbar.make(root, "Getting the video from TorBox…", Snackbar.LENGTH_INDEFINITE).apply { show() }
        load({ resolve(s) }) { r ->
            wait.dismiss()
            r.onSuccess { play(it, subsJson(s), type, id, season, episode) }
                .onFailure { Snackbar.make(root, it.message ?: "TorBox couldn't play this one. Try another stream.", 5000).show() }
        }
    }

    private fun play(url: String, subs: String, type: String, id: String, season: Int, episode: Int) {
        Store.saveLink(this, id, url, subs)
        if (Store.externalPlayer(this)) {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*").putExtra("title", item.name)
            runCatching { startActivity(Intent.createChooser(view, "Play with")) }
            return
        }
        startActivity(Intent(this, PlayerActivity::class.java)
            .putExtra("url", url).putExtra("subs", subs)
            .putExtra("type", type).putExtra("id", id).putExtra("item", item.toJson().toString())
            .putExtra("season", season).putExtra("episode", episode))
    }
}
