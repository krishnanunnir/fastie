package app.kino

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import org.json.JSONArray
import org.json.JSONObject

/** What a search for streams turned up: the playable ones ranked best first, and why there are none when there are none. */
class Found(val streams: List<Stream>, val torrents: Int, val debrid: Boolean) {
    fun emptyMessage() = when {
        torrents > 0 && !debrid -> "Your addons found $torrents torrents for this one. Connect TorBox in Settings to play them."
        torrents > 0 -> "Your addons found $torrents torrents, but none are cached on TorBox yet."
        else -> "No playable streams for this one. Add a stream addon in Settings."
    }
}

/**
 * Every stream your addons have for [videoId] that can play now, best for this device first. Torrents only play through
 * debrid, so just the ones TorBox already has are kept. Network; call off the main thread.
 */
fun findStreams(ctx: Context, type: String, videoId: String, episode: Boolean): Found {
    val all = Addons.streams(ctx, type, videoId)
    val key = TorBox.key(ctx)
    val torrents = all.filter { it.hash != null }
    val cached = if (key != null && torrents.isNotEmpty()) TorBox.cached(key, torrents.map { it.hash!! }) else emptySet()
    val playable = all.filter { it.url != null || it.hash in cached }
    // Phones get 1080p at a bitrate Wi-Fi or mobile data keeps up with; TVs get 4K short of remux bitrates.
    val ranked = rankStreams(playable, if (ctx.isTv) 2160 else 1080, if (ctx.isTv) 40.0 else 20.0, if (episode) 50 else 120)
    return Found(ranked, torrents.distinctBy { it.hash }.size, key != null)
}

fun subsJson(s: Stream) = JSONArray().apply { s.subtitles.forEach { (lang, url) -> put(JSONObject().put("lang", lang).put("url", url)) } }.toString()

/**
 * Plays [s]: a direct link or an already-fetched [link] at once; any other cached torrent opens the player straight away
 * and it gets the link from TorBox behind the poster. Only an external player has to wait here for the link.
 */
fun Activity.playStream(item: Item, s: Stream, link: String?, type: String, id: String, season: Int, episode: Int) {
    (s.url ?: link)?.let { return playLink(item, it, subsJson(s), type, id, season, episode) }
    val key = TorBox.key(this) ?: return
    if (!Store.externalPlayer(this)) return startActivity(playerIntent(item, subsJson(s), type, id, season, episode)
        .putExtra("hash", s.hash).putExtra("filename", s.filename).putExtra("fileIdx", s.fileIdx ?: -1))
    val root = findViewById<View>(android.R.id.content)
    val wait = Snackbar.make(root, "Getting the video from TorBox…", Snackbar.LENGTH_INDEFINITE).apply { show() }
    load({ TorBox.resolve(key, s.hash!!, s.filename, s.fileIdx) }) { r ->
        wait.dismiss()
        r.onSuccess { playLink(item, it, subsJson(s), type, id, season, episode) }
            .onFailure { Snackbar.make(root, it.message ?: "TorBox couldn't play this one. Try another stream.", 5000).show() }
    }
}

fun Activity.playLink(item: Item, url: String, subs: String, type: String, id: String, season: Int, episode: Int) {
    Store.saveLink(this, id, url, subs)
    if (Store.externalPlayer(this)) {
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*").putExtra("title", item.name)
        runCatching { startActivity(Intent.createChooser(view, "Play with")) }
        return
    }
    startActivity(playerIntent(item, subs, type, id, season, episode).putExtra("url", url))
}

fun Activity.playerIntent(item: Item, subs: String, type: String, id: String, season: Int, episode: Int) =
    Intent(this, PlayerActivity::class.java).putExtra("subs", subs)
        .putExtra("type", type).putExtra("id", id).putExtra("item", item.toJson().toString())
        .putExtra("season", season).putExtra("episode", episode)

/**
 * Plays the best stream for a movie or episode without showing any list: the link it last played from if that's fresh,
 * otherwise the player opens at once and finds the best stream itself, behind the poster.
 */
fun Activity.playBest(item: Item, type: String, id: String, season: Int, episode: Int) {
    Store.lastLink(this, id)?.let { (url, subs) -> return playLink(item, url, subs, type, id, season, episode) }
    if (!Store.externalPlayer(this)) return startActivity(playerIntent(item, "[]", type, id, season, episode))
    val root = findViewById<View>(android.R.id.content)
    val wait = Snackbar.make(root, "Finding the best stream…", Snackbar.LENGTH_INDEFINITE).apply { show() }
    load({ findStreams(this, type, id, season >= 0) }) { r ->
        wait.dismiss()
        val found = r.getOrDefault(Found(emptyList(), 0, false))
        found.streams.firstOrNull()?.let { playStream(item, it, null, type, id, season, episode) }
            ?: Snackbar.make(root, found.emptyMessage(), 5000).show()
    }
}

/**
 * A sheet of every stream for one movie or episode, best first, in the same cards as Details. It opens at once with a
 * loading line and fills in when the addons answer; the pick replaces the remembered link and plays.
 */
fun Activity.chooseStream(item: Item, type: String, id: String, season: Int, episode: Int) {
    val h = resources.displayMetrics.heightPixels
    val pad = dp(if (isTv) 32 else 16)
    val shell = sheet("Choose a stream", listOfNotNull(item.name, if (season >= 0) "S$season:E$episode" else null).joinToString("  ·  "))
    val sheet = shell.dialog
    val sub = shell.sub
    val loading = progressLine().apply { isIndeterminate = true }
    val list = RecyclerView(this).apply {
        layoutManager = LinearLayoutManager(context)
        clipToPadding = false
        setPadding(pad, 0, pad, dp(8))
    }
    shell.body.addView(loading, lp().apply { setMargins(pad, 0, pad, dp(8)) })
    shell.body.addView(list, lp(-1, (h * 0.7f).toInt()))
    sheet.show()
    load({ findStreams(this, type, id, season >= 0) }) { r ->
        loading.visibility = View.GONE
        val found = r.getOrDefault(Found(emptyList(), 0, false))
        if (found.streams.isEmpty()) return@load run { sub.text = found.emptyMessage() }
        sub.text = "${sub.text}  ·  ${found.streams.size} streams"
        list.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = found.streams.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                object : RecyclerView.ViewHolder(FrameLayout(parent.context).apply { layoutParams = RecyclerView.LayoutParams(-1, -2) }) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val box = holder.itemView as FrameLayout
                box.removeAllViews()
                val s = found.streams[position]
                box.addView(streamCard(s, best = position == 0) {
                    sheet.dismiss()
                    Store.saveLink(this@chooseStream, id, null)
                    playStream(item, s, null, type, id, season, episode)
                }, FrameLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
            }
        }
        list.post { list.findViewHolderForAdapterPosition(0)?.itemView?.let { (it as ViewGroup).getChildAt(0).requestFocus() } }
    }
}
