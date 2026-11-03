package app.kino

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * TorBox debrid: turns the torrents your addons find into direct video links. The API key is kept on this device only.
 * Everything here does network work; call it off the main thread.
 */
object TorBox {
    private const val API = "https://api.torbox.app/v1/api"
    private val VIDEO = Regex("""\.(mkv|mp4|avi|mov|m4v|webm|ts|m2ts)$""", RegexOption.IGNORE_CASE)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("debrid", Context.MODE_PRIVATE)
    fun key(ctx: Context): String? = prefs(ctx).getString("torbox", null)
    fun setKey(ctx: Context, key: String?) = prefs(ctx).edit().putString("torbox", key?.trim()?.ifBlank { null }).apply()

    private fun auth(key: String) = mapOf("Authorization" to "Bearer $key")
    private fun data(json: String) = JSONObject(json).also {
        if (!it.optBoolean("success", true)) error(it.optString("detail").ifBlank { "TorBox refused the request" })
    }.opt("data")

    /** Throws if TorBox doesn't accept [key]. */
    fun check(key: String) { data(Net.get("$API/user/me", auth(key))) }

    /** Which of [hashes] TorBox already has, so they play at once. */
    fun cached(key: String, hashes: List<String>): Set<String> = hashes.distinct().chunked(100).map { chunk ->
        Net.fan.submit(java.util.concurrent.Callable {
            when (val d = runCatching { data(Net.get("$API/torrents/checkcached?hash=${chunk.joinToString(",")}&format=list", auth(key))) }.getOrNull()) {
                is JSONArray -> List(d.length()) { d.getJSONObject(it).optString("hash") }
                is JSONObject -> d.keys().asSequence().toList()
                else -> emptyList()
            }
        })
    }.flatMap { it.get() }.map { it.lowercase() }.toSet()

    /**
     * A direct link to the video in torrent [hash]. Picks the file by the addon's [filename], then its [fileIdx],
     * then the largest video, which is how a season pack lands on the right episode.
     */
    fun resolve(key: String, hash: String, filename: String?, fileIdx: Int?): String {
        val created = data(Net.form("$API/torrents/createtorrent", mapOf("magnet" to "magnet:?xt=urn:btih:$hash"), auth(key))) as JSONObject
        val id = created.optInt("torrent_id", created.optInt("id"))
        val t = data(Net.get("$API/torrents/mylist?id=$id&bypass_cache=true", auth(key))) as JSONObject
        val files = t.optJSONArray("files")?.let { a -> List(a.length()) { a.getJSONObject(it) } }.orEmpty()
        val file = pick(files, filename, fileIdx) ?: error("TorBox is still fetching this one. Try another stream.")
        val link = data(Net.get("$API/torrents/requestdl?token=$key&torrent_id=$id&file_id=${file.optInt("id")}&redirect=false"))
        return link as? String ?: error("TorBox didn't return a link")
    }

    fun pick(files: List<JSONObject>, filename: String?, fileIdx: Int?): JSONObject? {
        fun name(f: JSONObject) = f.optString("short_name").ifBlank { f.optString("name").substringAfterLast('/') }
        return filename?.let { n -> files.firstOrNull { name(it).equals(n, true) } }
            ?: fileIdx?.let { i -> files.firstOrNull { it.optInt("id", -1) == i } }
            ?: files.filter { VIDEO.containsMatchIn(name(it)) }.maxByOrNull { it.optLong("size") }
            ?: files.maxByOrNull { it.optLong("size") }
    }
}
