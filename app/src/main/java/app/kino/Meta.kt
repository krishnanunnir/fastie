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

/** One subtitle file an addon offers: its language as the addon wrote it ("eng", "en", "English"), and who offered it. */
class Subtitle(val lang: String, val url: String, val source: String)

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

    private val about get() = "$name $title $filename"

    /** "4K", "1080p", or null. */
    val quality: String? get() = height.takeIf { it > 0 }?.let { if (it == 2160) "4K" else "${it}p" }

    /** Where the video came from: "Remux", "Blu-ray", "WEB-DL", "WEBRip", "HDTV", "CAM" (filmed in a cinema), or null. */
    val source: String? by lazy {
        val t = about
        listOf("""\b(HD)?CAM(-?Rip)?\b|\bHD-?TS\b|TELESYNC|\bTS\b""" to "CAM", "REMUX" to "Remux", "WEB-?DL" to "WEB-DL", "WEB-?Rip" to "WEBRip", "Blu-?Ray|BDRip|BRRip" to "Blu-ray", "HDTV" to "HDTV", "\\bWEB\\b" to "WEB")
            .firstOrNull { (re, _) -> Regex(re, RegexOption.IGNORE_CASE).containsMatchIn(t) }?.second
    }

    /** "16.0 GB" or "880 MB". */
    val size: String? get() = gigabytes?.let { if (it >= 1) "%.1f GB".format(it) else "%.0f MB".format(it * 1024) }

    /** What's worth knowing at a glance: codec, HDR, then audio, as the addon describes them. */
    val tags: List<String> by lazy {
        val t = about
        fun has(re: String) = Regex(re, RegexOption.IGNORE_CASE).containsMatchIn(t)
        listOfNotNull(
            when { has("""\bAV1\b""") -> "AV1"; has("""HEVC|[xh]\.?265""") -> "HEVC"; has("""AVC|[xh]\.?264""") -> "H.264"; else -> null },
            when { has("""\bDV\b|DoVi|Dolby.?Vision""") -> "Dolby Vision"; has("""HDR10\+|HDR10P""") -> "HDR10+"; has("""\bHDR""") -> "HDR"; else -> null },
            when { has("Atmos") -> "Atmos"; has("TrueHD") -> "TrueHD"; has("""DTS""") -> "DTS"; has("""DDP|DD\+|E-?AC-?3""") -> "DD+"; has("""\bAAC\b""") -> "AAC"; else -> null },
            when { has("""7[ .]1""") -> "7.1"; has("""5[ .]1""") -> "5.1"; else -> null },
        )
    }

    /** The file's name, from the addon's hints or its description, without the emoji addons decorate it with. */
    val fileName: String? by lazy {
        filename ?: title.lineSequence().firstOrNull { Regex("""\.(mkv|mp4|avi|m4v|ts)\b""", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            ?.replace(Regex("""^[^\p{L}\p{N}\[(]+"""), "")?.trim()
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
 * Best first for this screen and connection: one row per torrent, CAM recordings last, then streams at or under [maxHeight] whose bitrate
 * fits [maxMbps] (the higher-quality of those first), then the rest, smallest first so they're likelier to keep up.
 * ponytail: bitrate is size over an assumed [minutes]; pass the real runtime if addons start sending it reliably.
 */
fun rankStreams(streams: List<Stream>, maxHeight: Int, maxMbps: Double, minutes: Int): List<Stream> {
    fun mbps(s: Stream) = s.gigabytes?.let { it * 8 * 1024 / (minutes * 60) }
    fun fits(s: Stream) = s.height in 1..maxHeight && (mbps(s)?.let { it <= maxMbps } ?: true)
    return streams.distinctBy { it.hash ?: it.url }.sortedWith(
        compareBy<Stream> { it.source == "CAM" } // a cinema recording only when there's nothing else
            .thenByDescending { fits(it) }
            .thenByDescending { if (fits(it)) it.height else 0 }
            .thenBy { if (fits(it)) -(it.gigabytes ?: 0.0) else it.gigabytes ?: Double.MAX_VALUE }
    )
}

private val iso2 by lazy { java.util.Locale.getISOLanguages().toSet() }
private val iso3 by lazy { java.util.Locale.getISOLanguages().associateBy { java.util.Locale(it).isO3Language } }
// The older ISO 639-2/B codes OpenSubtitles and others still send ("fre" for French), which Java's table doesn't know.
private val bibliographic = mapOf(
    "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my", "chi" to "zh", "cze" to "cs", "dut" to "nl", "fre" to "fr",
    "geo" to "ka", "ger" to "de", "gre" to "el", "ice" to "is", "mac" to "mk", "may" to "ms", "per" to "fa", "rum" to "ro",
    "slo" to "sk", "wel" to "cy", "pob" to "pt", // pob: OpenSubtitles' Brazilian Portuguese
)
private val names by lazy { java.util.Locale.getISOLanguages().associateBy { java.util.Locale(it).getDisplayLanguage(java.util.Locale.ENGLISH).lowercase() } }

/** "eng", "en", "English", "pt-BR" → "en"/"pt": the 2-letter code, or null when it isn't a language. */
fun langCode(raw: String?): String? {
    val s = raw?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_') ?: return null
    return when {
        s.length == 2 -> s.takeIf { it in iso2 }
        s.length == 3 -> iso3[s] ?: bibliographic[s]
        else -> names[s]
    }
}

/**
 * Which subtitles to offer: in [langs] (your languages, in order), up to 4 of each, each link once; when none are in
 * your languages, the first 10 of whatever there is, so a film still has something.
 */
fun pickSubtitles(all: List<Subtitle>, langs: List<String>): List<Subtitle> {
    val unique = all.distinctBy { it.url }
    val mine = langs.flatMap { l -> unique.filter { langCode(it.lang) == l }.take(4) }
    return mine.ifEmpty { unique.take(10) }
}
