package app.fastie

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

        load({ Addons.streams(this, type, id) }) { r ->
            val streams = r.getOrDefault(emptyList())
            if (streams.isEmpty()) {
                status.text = "No playable streams for this one. Add a stream addon in Settings, or sign in to Nuvio to bring yours."
                return@load
            }
            status.visibility = View.GONE
            var first: View? = null
            streams.groupBy { it.addon }.forEach { (addon, list) ->
                col.addView(sectionTitle(addon))
                list.forEach { s ->
                    val row = listRow(s.name.replace('\n', ' '), s.title.ifBlank { null }) { play(s, type, id, season, episode) }
                    col.addView(row, lp().apply { bottomMargin = dp(8) })
                    if (first == null) first = row
                }
            }
            first?.requestFocus()
        }
    }

    private fun play(s: Stream, type: String, id: String, season: Int, episode: Int) {
        if (Store.externalPlayer(this)) {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(s.url), "video/*").putExtra("title", item.name)
            runCatching { startActivity(Intent.createChooser(view, "Play with")) }
            return
        }
        val subs = JSONArray().apply { s.subtitles.forEach { (lang, url) -> put(JSONObject().put("lang", lang).put("url", url)) } }
        startActivity(Intent(this, PlayerActivity::class.java)
            .putExtra("url", s.url).putExtra("subs", subs.toString())
            .putExtra("type", type).putExtra("id", id).putExtra("item", item.toJson().toString())
            .putExtra("season", season).putExtra("episode", episode))
    }
}
