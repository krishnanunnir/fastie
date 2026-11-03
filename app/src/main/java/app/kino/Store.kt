package app.kino

import android.content.Context
import org.json.JSONObject

/** Where the user stopped in one movie or episode. Times are milliseconds, like Nuvio's. */
class Progress(
    val contentId: String,
    val type: String,
    val videoId: String,
    val season: Int?,
    val episode: Int?,
    val position: Long,
    val duration: Long,
    val lastWatched: Long,
    val name: String? = null,
) {
    // Nuvio's progress_key format.
    val key get() = if (season != null && episode != null) "${contentId}_s${season}e$episode" else contentId
    val fraction get() = if (duration > 0) position.toFloat() / duration else 0f
    val finished get() = fraction > 0.92f

    fun toJson(): JSONObject = JSONObject().put("content_id", contentId).put("content_type", type).put("video_id", videoId)
        .put("season", season ?: JSONObject.NULL).put("episode", episode ?: JSONObject.NULL)
        .put("position", position).put("duration", duration).put("last_watched", lastWatched).put("progress_key", key)
        .put("name", name)

    companion object {
        fun from(o: JSONObject) = Progress(
            contentId = o.getString("content_id"),
            type = o.optString("content_type", "movie"),
            videoId = o.optString("video_id").ifBlank { o.getString("content_id") },
            season = if (o.isNull("season")) null else o.optInt("season"),
            episode = if (o.isNull("episode")) null else o.optInt("episode"),
            position = o.optLong("position"),
            duration = o.optLong("duration"),
            lastWatched = o.optLong("last_watched"),
            name = o.optString("name").takeIf { it.isNotBlank() && it != "null" },
        )
    }
}

object Store {
    private fun prefs(ctx: Context, name: String) = ctx.getSharedPreferences(name, Context.MODE_PRIVATE)

    // Progress, keyed by Nuvio's progress_key.

    fun progress(ctx: Context): List<Progress> =
        prefs(ctx, "progress").all.values.mapNotNull { runCatching { Progress.from(JSONObject(it as String)) }.getOrNull() }

    fun progressFor(ctx: Context, videoId: String) = progress(ctx).firstOrNull { it.videoId == videoId }

    fun saveProgress(ctx: Context, p: Progress) = prefs(ctx, "progress").edit().putString(p.key, p.toJson().toString()).apply()

    /** Newer entry wins, so a sync never rolls back what was watched here. */
    fun mergeProgress(ctx: Context, remote: List<Progress>) {
        val local = progress(ctx).associateBy { it.key }
        val e = prefs(ctx, "progress").edit()
        remote.filter { (local[it.key]?.lastWatched ?: -1) < it.lastWatched }.forEach { e.putString(it.key, it.toJson().toString()) }
        e.apply()
    }

    /** One card per title: its latest unfinished movie or episode. */
    fun continueWatching(ctx: Context): List<Progress> = progress(ctx)
        .sortedByDescending { it.lastWatched }
        .distinctBy { it.contentId }
        .filter { it.position > 0 && !it.finished }

    // My List.

    fun library(ctx: Context): List<Item> = prefs(ctx, "library").all.values
        .mapNotNull { runCatching { JSONObject(it as String) }.getOrNull() }
        .sortedByDescending { it.optLong("added_at") }
        .map(Item::from)

    fun inLibrary(ctx: Context, id: String) = prefs(ctx, "library").contains(id)

    fun setInLibrary(ctx: Context, item: Item, on: Boolean, addedAt: Long = System.currentTimeMillis()) {
        val e = prefs(ctx, "library").edit()
        if (on) e.putString(item.id, item.toJson().put("added_at", addedAt).toString()) else e.remove(item.id)
        e.apply()
    }

    fun replaceLibrary(ctx: Context, items: List<Pair<Item, Long>>) {
        val e = prefs(ctx, "library").edit().clear()
        items.forEach { (item, at) -> e.putString(item.id, item.toJson().put("added_at", at).toString()) }
        e.apply()
    }

    // Settings.

    fun externalPlayer(ctx: Context) = prefs(ctx, "settings").getBoolean("external_player", false)

    fun setExternalPlayer(ctx: Context, on: Boolean) = prefs(ctx, "settings").edit().putBoolean("external_player", on).apply()

    // The link each video last played from, reused for 3 hours (as long as TorBox links last) so Resume starts at once.

    private const val LINK_MS = 3 * 60 * 60 * 1000L

    /** The last link for [videoId] and its subtitles JSON, if it's still fresh. */
    fun lastLink(ctx: Context, videoId: String): Pair<String, String>? = prefs(ctx, "links").getString(videoId, null)
        ?.let { runCatching { JSONObject(it) }.getOrNull() }
        ?.takeIf { System.currentTimeMillis() - it.optLong("at") < LINK_MS }
        ?.let { it.getString("url") to it.optString("subs", "[]") }

    fun saveLink(ctx: Context, videoId: String, url: String?, subs: String = "[]") = prefs(ctx, "links").edit().apply {
        if (url == null) remove(videoId) else putString(videoId, JSONObject().put("url", url).put("subs", subs).put("at", System.currentTimeMillis()).toString())
    }.apply()
}
