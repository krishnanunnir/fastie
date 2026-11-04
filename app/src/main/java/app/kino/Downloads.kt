package app.kino

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.view.View
import com.google.android.material.snackbar.Snackbar
import org.json.JSONObject

/** One saved movie or episode, as Android's DownloadManager last reported it. */
class Download(
    val id: Long, val item: Item, val type: String, val videoId: String, val season: Int, val episode: Int,
    val label: String, val status: Int, val done: Long, val total: Long, val file: String?,
) {
    val finished get() = status == DownloadManager.STATUS_SUCCESSFUL
    val running get() = status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED
    val fraction get() = if (total > 0) done.toFloat() / total else 0f
}

/**
 * Downloads go through Android's own DownloadManager, which keeps going with the app closed, retries after a dropped
 * connection and shows progress in the notification shade. Files land in the app's Downloads folder
 * (Android/data/app.kino/files/Download) and are removed with the app. What each download is lives here, by its id.
 */
object Downloads {
    private fun dm(ctx: Context) = ctx.getSystemService(DownloadManager::class.java)
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    fun label(item: Item, season: Int, episode: Int) = item.name + if (season >= 0) " S%02dE%02d".format(season, episode) else ""

    /** Starts saving [url], which must be freshly resolved: debrid links expire, and DownloadManager resumes from the same one. */
    fun start(ctx: Context, item: Item, url: String, filename: String?, type: String, videoId: String, season: Int, episode: Int) {
        val label = label(item, season, episode)
        val ext = filename?.substringAfterLast('.', "")?.takeIf { it.length in 2..4 } ?: "mkv"
        val request = DownloadManager.Request(Uri.parse(url)).setTitle(label)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, label.replace(Regex("""[^\w .-]"""), "") + ".$ext")
        val id = dm(ctx).enqueue(request)
        prefs(ctx).edit().putString(id.toString(), JSONObject().put("item", item.toJson()).put("type", type).put("video_id", videoId)
            .put("season", season).put("episode", episode).put("label", label).toString()).apply()
    }

    /** Newest first. Downloads cleared from the system's own list drop out. */
    fun list(ctx: Context): List<Download> {
        val meta = prefs(ctx).all.mapNotNull { (k, v) -> runCatching { k.toLong() to JSONObject(v as String) }.getOrNull() }.toMap()
        if (meta.isEmpty()) return emptyList()
        val out = mutableListOf<Download>()
        dm(ctx).query(DownloadManager.Query().setFilterById(*meta.keys.toLongArray()))?.use { c ->
            fun long(col: String) = c.getLong(c.getColumnIndexOrThrow(col))
            while (c.moveToNext()) {
                val id = long(DownloadManager.COLUMN_ID)
                val m = meta[id] ?: continue
                out += Download(id, Item.from(m.getJSONObject("item")), m.getString("type"), m.getString("video_id"),
                    m.optInt("season", -1), m.optInt("episode", -1), m.getString("label"),
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR), long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES),
                    c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)))
            }
        }
        return out.sortedByDescending { it.id }
    }

    /** Stops it if it's still going, and deletes the file. */
    fun delete(ctx: Context, id: Long) {
        dm(ctx).remove(id)
        prefs(ctx).edit().remove(id.toString()).apply()
    }
}

/** Gets a fresh link for [s] and starts downloading it. */
fun Activity.download(item: Item, s: Stream, type: String, id: String, season: Int, episode: Int) {
    val root = findViewById<View>(android.R.id.content)
    load({ s.url ?: (Debrid.connected(this) ?: error("Connect a debrid service in Settings to download torrents.")).resolve(s.hash!!, s.filename, s.fileIdx) }) { r ->
        r.mapCatching { Downloads.start(this, item, it, s.filename, type, id, season, episode) }
            .onSuccess { Snackbar.make(root, "Downloading ${Downloads.label(item, season, episode)}. It'll be in My List.", 5000).show() }
            .onFailure { Snackbar.make(root, it.message ?: "Couldn't start the download. Try another stream.", 5000).show() }
    }
}

/** Plays a finished download from its file. Kino's own player always, since other apps can't open a file:// link. */
fun Activity.playDownload(d: Download) {
    startActivity(playerIntent(d.item, "[]", d.type, d.videoId, d.season, d.episode).putExtra("url", d.file))
}
