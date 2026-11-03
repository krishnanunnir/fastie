package app.kino

import org.json.JSONArray
import org.json.JSONObject

private fun JSONObject.str(key: String): String? = optString(key).takeIf { it.isNotBlank() && it != "null" }

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { optString(it) }

/** A title as addons describe it: a catalog preview, or a full meta with [videos]. */
class Item(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val rating: String? = null,
    val genres: List<String> = emptyList(),
    val runtime: String? = null,
    val videos: List<Video> = emptyList(),
) {
    val isSeries get() = type == "series" || videos.any { it.season > 0 }

    /** "2024 · Drama, Crime · 1h 49m": whatever the addon gave us, in Netflix's order. */
    val metaLine: String
        get() = listOfNotNull(releaseInfo, genres.take(3).joinToString(", ").ifBlank { null }, runtime).joinToString("  ·  ")

    // Metahub hosts art for every IMDb id, so items that came without art (synced progress) still get some.
    val posterOrHub get() = poster ?: if (id.startsWith("tt")) "https://images.metahub.space/poster/small/$id/img" else null
    val backgroundOrHub get() = background ?: if (id.startsWith("tt")) "https://images.metahub.space/background/medium/$id/img" else null

    fun toJson(): JSONObject = JSONObject().put("id", id).put("type", type).put("name", name)
        .put("poster", poster).put("background", background).put("logo", logo).put("description", description)
        .put("releaseInfo", releaseInfo).put("imdbRating", rating).put("genres", JSONArray(genres))

    companion object {
        fun from(o: JSONObject) = Item(
            id = o.str("id") ?: o.str("imdb_id") ?: "",
            type = o.str("type") ?: "movie",
            name = o.str("name") ?: "",
            poster = o.str("poster"),
            background = o.str("background"),
            logo = o.str("logo"),
            description = o.str("description"),
            releaseInfo = o.str("releaseInfo") ?: o.str("year"),
            rating = o.str("imdbRating"),
            genres = (o.optJSONArray("genres") ?: o.optJSONArray("genre")).strings(),
            runtime = o.str("runtime"),
            videos = o.optJSONArray("videos")?.let { a -> List(a.length()) { Video.from(a.getJSONObject(it)) } }
                ?.sortedWith(compareBy({ it.season }, { it.episode })).orEmpty(),
        )
    }
}

class Video(val id: String, val title: String, val season: Int, val episode: Int, val thumbnail: String?, val overview: String?) {
    companion object {
        fun from(o: JSONObject) = Video(
            id = o.str("id") ?: "",
            title = o.str("name") ?: o.str("title") ?: "",
            season = o.optInt("season"),
            episode = o.optInt("episode", o.optInt("number")),
            thumbnail = o.str("thumbnail"),
            overview = o.str("overview") ?: o.str("description"),
        )
    }
}

/** A direct [url], or a torrent ([hash]) that only plays through debrid. */
class Stream(
    val addon: String, val name: String, val title: String, val url: String?, val subtitles: List<Pair<String, String>>,
    val hash: String? = null, val fileIdx: Int? = null, val filename: String? = null,
) {
    /** The vertical resolution the addon advertises ("2160p", "4K"), or 0. */
    val height: Int by lazy {
        Regex("""\b(2160|1440|1080|720|576|480)p\b|\b4k\b""", RegexOption.IGNORE_CASE).find("$name $title")
            ?.let { if (it.groupValues[1].isEmpty()) 2160 else it.groupValues[1].toInt() } ?: 0
    }

    /** The file size the addon advertises ("💾 45.3 GB"), in GB, or null. */
    val gigabytes: Double? by lazy {
        Regex("""(\d+(?:[.,]\d+)?)\s*(GB|GiB|MB|MiB)\b""").find(title)?.let { m ->
            m.groupValues[1].replace(',', '.').toDouble() / if (m.groupValues[2].startsWith("M")) 1024 else 1
        }
    }

    companion object {
        /** Null for streams Kino can't play at all, such as YouTube ids or links out to other apps. */
        fun from(addon: String, o: JSONObject): Stream? {
            val url = o.str("url")
            val hash = o.str("infoHash")?.lowercase()
            if (url == null && hash == null) return null
            val subs = o.optJSONArray("subtitles")?.let { a ->
                List(a.length()) { a.getJSONObject(it) }.mapNotNull { s -> s.str("url")?.let { (s.str("lang") ?: "") to it } }
            }.orEmpty()
            return Stream(addon, o.str("name") ?: addon, o.str("title") ?: o.str("description") ?: "", url, subs,
                hash = if (url == null) hash else null,
                fileIdx = if (o.has("fileIdx")) o.optInt("fileIdx") else null,
                filename = o.optJSONObject("behaviorHints")?.str("filename"))
        }
    }
}

/**
 * Best first for this screen and connection: one row per torrent, then streams at or under [maxHeight] whose bitrate
 * fits [maxMbps] (the higher-quality of those first), then the rest, smallest first so they're likelier to keep up.
 * ponytail: bitrate is size over an assumed [minutes]; pass the real runtime if addons start sending it reliably.
 */
fun rankStreams(streams: List<Stream>, maxHeight: Int, maxMbps: Double, minutes: Int): List<Stream> {
    fun mbps(s: Stream) = s.gigabytes?.let { it * 8 * 1024 / (minutes * 60) }
    fun fits(s: Stream) = s.height in 1..maxHeight && (mbps(s)?.let { it <= maxMbps } ?: true)
    return streams.distinctBy { it.hash ?: it.url }.sortedWith(
        compareByDescending<Stream> { fits(it) }
            .thenByDescending { if (fits(it)) it.height else 0 }
            .thenBy { if (fits(it)) -(it.gigabytes ?: 0.0) else it.gigabytes ?: Double.MAX_VALUE }
    )
}
