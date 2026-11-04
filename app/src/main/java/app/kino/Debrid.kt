package app.kino

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val TB = "https://api.torbox.app/v1/api"
private const val RD = "https://api.real-debrid.com/rest/1.0"
private const val AD = "https://api.alldebrid.com/v4"
private const val PM = "https://www.premiumize.me/api"
private const val DL = "https://debrid-link.com/api/v2"

/**
 * The debrid services that turn the torrents your addons find into direct video links. One is connected at a time, and
 * its API key is kept on this device only. Everything here does network work; call it off the main thread.
 */
enum class Debrid(val label: String, val keyAt: String) {
    TORBOX("TorBox", "torbox.app/settings"),
    REAL_DEBRID("Real-Debrid", "real-debrid.com/apitoken"),
    ALLDEBRID("AllDebrid", "alldebrid.com/apikeys"),
    PREMIUMIZE("Premiumize", "premiumize.me/account"),
    DEBRID_LINK("Debrid-Link", "debrid-link.com/webapp/apikey");

    /** The connected service and its key. */
    class Account(val service: Debrid, val key: String) {
        fun cached(hashes: List<String>) = service.cached(key, hashes)
        fun resolve(hash: String, filename: String?, fileIdx: Int?) = service.resolve(key, hash, filename, fileIdx)
    }

    /** "Pro  ·  until 12 Dec 2026": the account's plan and when it runs out. Throws if the service doesn't accept [key]. */
    fun plan(key: String): String? {
        val auth = bearer(key)
        val (name, until) = when (this) {
            TORBOX -> (tb(Net.get("$TB/user/me", auth)) as JSONObject).let { me ->
                when (me.optInt("plan", -1)) { 0 -> "Free"; 1 -> "Essential"; 2 -> "Pro"; 3 -> "Standard"; else -> null } to iso(me.optString("premium_expires_at"))
            }
            REAL_DEBRID -> JSONObject(Net.get("$RD/user", auth)).let { me ->
                me.optString("type").replaceFirstChar(Char::titlecase).ifBlank { null } to iso(me.optString("expiration"))
            }
            ALLDEBRID -> ad(Net.get("$AD/user", auth)).getJSONObject("user").let { me ->
                (if (me.optBoolean("isPremium")) "Premium" else "Free") to me.optLong("premiumUntil").takeIf { it > 0 }?.times(1000)
            }
            PREMIUMIZE -> pm(Net.get("$PM/account/info", auth)).optLong("premium_until").let { t ->
                (if (t > 0) "Premium" else "Free") to t.takeIf { it > 0 }?.times(1000)
            }
            DEBRID_LINK -> dl(Net.get("$DL/account/infos", auth)).let { me ->
                (if (me.optInt("accountType") == 0) "Free" else "Premium") to
                    me.optLong("premiumLeft").takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 1000 }
            }
        }
        val date = until?.let { "until " + SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(it)) }
        return listOfNotNull(name, date).joinToString("  ·  ").ifBlank { null }
    }

    /**
     * Which of [hashes] the service already has, so they play at once. Null when it can't say (Real-Debrid, AllDebrid
     * and Debrid-Link dropped their cache checks), and then [resolve] finds out when one is played.
     */
    fun cached(key: String, hashes: List<String>): Set<String>? = when (this) {
        TORBOX -> each(hashes) { chunk ->
            when (val d = runCatching { tb(Net.get("$TB/torrents/checkcached?hash=${chunk.joinToString(",")}&format=list", bearer(key))) }.getOrNull()) {
                is JSONArray -> List(d.length()) { d.getJSONObject(it).optString("hash") }
                is JSONObject -> d.keys().asSequence().toList()
                else -> emptyList()
            }
        }
        PREMIUMIZE -> each(hashes) { chunk ->
            val hits = runCatching { pm(Net.urlForm("$PM/cache/check", chunk.map { "items[]" to it }, bearer(key))).optJSONArray("response") }.getOrNull()
            chunk.filterIndexed { i, _ -> hits?.optBoolean(i) == true }
        }
        else -> null
    }

    /**
     * A direct link to the video in torrent [hash]. Picks the file by the addon's [filename], then its [fileIdx], then
     * the largest video, which is how a season pack lands on the right episode. A torrent the service doesn't have
     * ready is taken off the account again, so trying uncached ones leaves nothing behind.
     */
    fun resolve(key: String, hash: String, filename: String?, fileIdx: Int?): String {
        val auth = bearer(key)
        val magnet = "magnet:?xt=urn:btih:$hash"
        fun notCached(): Nothing = error("$label doesn't have this one ready. Try another stream.")
        return when (this) {
            TORBOX -> {
                val created = tb(Net.form("$TB/torrents/createtorrent", mapOf("magnet" to magnet), auth)) as JSONObject
                val id = created.optInt("torrent_id", created.optInt("id"))
                val t = tb(Net.get("$TB/torrents/mylist?id=$id&bypass_cache=true", auth)) as JSONObject
                val files = t.optJSONArray("files")?.let { a -> List(a.length()) { a.getJSONObject(it) } }.orEmpty()
                val file = pick(files, filename, fileIdx) ?: error("TorBox is still fetching this one. Try another stream.")
                tb(Net.get("$TB/torrents/requestdl?token=$key&torrent_id=$id&file_id=${file.optInt("id")}&redirect=false")) as? String
                    ?: error("TorBox didn't return a link")
            }
            REAL_DEBRID -> {
                val id = JSONObject(Net.urlForm("$RD/torrents/addMagnet", listOf("magnet" to magnet), auth)).getString("id")
                fun info() = JSONObject(Net.get("$RD/torrents/info/$id", auth))
                runCatching {
                    // Real-Debrid numbers files from 1; addons count from 0.
                    val files = poll { info().optJSONArray("files")?.takeIf { it.length() > 0 } } ?: notCached()
                    val file = pick(List(files.length()) { files.getJSONObject(it) }.map {
                        file(it.getInt("id") - 1, it.optString("path").substringAfterLast('/'), it.optLong("bytes"))
                    }, filename, fileIdx) ?: notCached()
                    Net.urlForm("$RD/torrents/selectFiles/$id", listOf("files" to "${file.getInt("id") + 1}"), auth)
                    val link = poll { info().takeIf { it.optString("status") == "downloaded" }?.optJSONArray("links")?.optString(0) } ?: notCached()
                    JSONObject(Net.urlForm("$RD/unrestrict/link", listOf("link" to link), auth)).getString("download")
                }.getOrElse { runCatching { Net.delete("$RD/torrents/delete/$id", auth) }; throw it }
            }
            ALLDEBRID -> {
                val m = ad(Net.urlForm("$AD/magnet/upload", listOf("magnets[]" to hash), auth)).getJSONArray("magnets").getJSONObject(0)
                m.optJSONObject("error")?.let { error(it.optString("message").ifBlank { "AllDebrid refused this one" }) }
                val id = m.getLong("id")
                runCatching {
                    if (!m.optBoolean("ready")) notCached()
                    val tree = ad(Net.urlForm("$AD/magnet/files", listOf("id[]" to "$id"), auth)).getJSONArray("magnets").getJSONObject(0).getJSONArray("files")
                    val file = pick(flatten(tree), filename, fileIdx) ?: notCached()
                    // ponytail: a "delayed" unlock (a hoster still preparing the file) counts as not ready; poll /link/delayed if that shows up.
                    ad(Net.urlForm("$AD/link/unlock", listOf("link" to file.optString("link")), auth)).optString("link").ifBlank { notCached() }
                }.getOrElse { runCatching { Net.urlForm("$AD/magnet/delete", listOf("id" to "$id"), auth) }; throw it }
            }
            PREMIUMIZE -> {
                // Premiumize hands cached files straight over, without adding anything to the account.
                val content = pm(Net.urlForm("$PM/transfer/directdl", listOf("src" to magnet), auth)).optJSONArray("content") ?: JSONArray()
                val files = List(content.length()) { i -> content.getJSONObject(i).let { file(i, it.optString("path").substringAfterLast('/'), it.optLong("size"), it.optString("link")) } }
                pick(files, filename, fileIdx)?.optString("link")?.ifBlank { null } ?: notCached()
            }
            DEBRID_LINK -> {
                val t = dl(Net.urlForm("$DL/seedbox/add", listOf("url" to magnet), auth))
                val id = t.getString("id")
                runCatching {
                    val files = t.optJSONArray("files")?.let { a ->
                        List(a.length()) { i -> a.getJSONObject(i).let { file(i, it.optString("name"), it.optLong("size"), it.optString("downloadUrl").takeIf { _ -> it.optInt("downloadPercent") == 100 }) } }
                    }.orEmpty()
                    pick(files, filename, fileIdx)?.optString("link")?.ifBlank { null } ?: notCached()
                }.getOrElse { runCatching { Net.delete("$DL/seedbox/$id/remove", auth) }; throw it }
            }
        }
    }

    companion object {
        private val VIDEO = Regex("""\.(mkv|mp4|avi|mov|m4v|webm|ts|m2ts)$""", RegexOption.IGNORE_CASE)

        private fun prefs(ctx: Context) = ctx.getSharedPreferences("debrid", Context.MODE_PRIVATE)
        // TorBox's key keeps the "torbox" slot it always had.
        private val Debrid.slot get() = name.lowercase()

        /** The connected service, if any. */
        fun connected(ctx: Context): Account? = entries.firstNotNullOfOrNull { s -> prefs(ctx).getString(s.slot, null)?.let { Account(s, it) } }

        /** Connects [service] with [key], replacing whichever was connected; a null key disconnects. */
        fun connect(ctx: Context, service: Debrid, key: String?) = prefs(ctx).edit()
            .apply { entries.forEach { remove(it.slot) } }.putString(service.slot, key?.trim()?.ifBlank { null }).apply()

        /** Files in TorBox's shape, which [pick] reads: an id, a name, a size and, where the service gives one, a link. */
        fun file(id: Int, name: String, size: Long, link: String? = null): JSONObject =
            JSONObject().put("id", id).put("short_name", name).put("size", size).put("link", link)

        fun pick(files: List<JSONObject>, filename: String?, fileIdx: Int?): JSONObject? {
            fun name(f: JSONObject) = f.optString("short_name").ifBlank { f.optString("name").substringAfterLast('/') }
            return filename?.let { n -> files.firstOrNull { name(it).equals(n, true) } }
                ?: fileIdx?.let { i -> files.firstOrNull { it.optInt("id", -1) == i } }
                ?: files.filter { VIDEO.containsMatchIn(name(it)) }.maxByOrNull { it.optLong("size") }
                ?: files.maxByOrNull { it.optLong("size") }
        }

        /** AllDebrid's file tree ({n, e} folders, {n, s, l} files) as a flat list, numbered in its order. */
        // ponytail: assumes the tree keeps the torrent's file order, so fileIdx lines up; filename matches come first anyway.
        fun flatten(tree: JSONArray): List<JSONObject> = buildList {
            fun walk(a: JSONArray) {
                for (i in 0 until a.length()) a.getJSONObject(i).let { n -> n.optJSONArray("e")?.let(::walk) ?: add(file(size, n.optString("n"), n.optLong("s"), n.optString("l"))) }
            }
            walk(tree)
        }

        private fun bearer(key: String) = mapOf("Authorization" to "Bearer $key")

        private fun tb(json: String) = JSONObject(json).also {
            if (!it.optBoolean("success", true)) error(it.optString("detail").ifBlank { "TorBox refused the request" })
        }.opt("data")
        private fun ad(json: String) = JSONObject(json).also {
            if (it.optString("status") == "error") error(it.optJSONObject("error")?.optString("message").orEmpty().ifBlank { "AllDebrid refused the request" })
        }.getJSONObject("data")
        private fun pm(json: String) = JSONObject(json).also {
            if (it.optString("status") == "error") error(it.optString("message").ifBlank { "Premiumize refused the request" })
        }
        private fun dl(json: String) = JSONObject(json).also {
            if (!it.optBoolean("success", true)) error(it.optString("error").ifBlank { "Debrid-Link refused the request" })
        }.getJSONObject("value")

        /** "2026-12-12T08:00:00Z" as epoch millis. */
        private fun iso(s: String): Long? = s.takeIf { it.length >= 19 }?.let {
            runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(it.take(19))!!.time }.getOrNull()
        }

        private fun each(hashes: List<String>, check: (List<String>) -> List<String>): Set<String> = hashes.distinct().chunked(100).map { chunk ->
            Net.fan.submit(java.util.concurrent.Callable { check(chunk) })
        }.flatMap { it.get() }.map { it.lowercase() }.toSet()

        /** [get] until it has something, for a few seconds: a cached torrent gets there almost at once. */
        private fun <T> poll(get: () -> T?): T? {
            repeat(5) { i -> get()?.let { return it }; if (i < 4) Thread.sleep(1000) }
            return null
        }
    }
}
