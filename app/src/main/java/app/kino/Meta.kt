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

class Stream(
    val addon: String,
    val name: String,
    val title: String,
    val url: String,
    val subtitles: List<Pair<String, String>>,
    val filename: String? = null,
    val bytes: Long? = null,
) {
    val info by lazy { StreamInfo.parse(name, title, filename, bytes) }

    companion object {
        /** Null for streams Kino can't play: torrents (infoHash) and YouTube ids need engines we don't ship. */
        fun from(addon: String, o: JSONObject): Stream? {
            val url = o.str("url") ?: return null
            val subs = o.optJSONArray("subtitles")?.let { a ->
                List(a.length()) { a.getJSONObject(it) }.mapNotNull { s -> s.str("url")?.let { (s.str("lang") ?: "") to it } }
            }.orEmpty()
            val hints = o.optJSONObject("behaviorHints")
            return Stream(addon, o.str("name") ?: addon, o.str("title") ?: o.str("description") ?: "", url, subs,
                hints?.str("filename"), hints?.optLong("videoSize")?.takeIf { it > 0 })
        }
    }
}

/**
 * What a stream's name and description say, pulled into parts Kino can lay out: addons pack quality, HDR, codecs,
 * size and source into free text (often a release filename and a line of emoji), and each formats it its own way.
 */
class StreamInfo(
    /** "4K", "1440p", "1080p", "720p", "SD", or null when nothing says. */
    val quality: String?,
    /** Picture, source and sound, in that order: "DV", "HDR10", "HEVC", "Remux", "Atmos", "5.1"… */
    val tags: List<String>,
    val size: String?,
    val seeders: Int?,
    /** Where the file came from, when the addon says (the text after ⚙️ or 🔗). */
    val source: String?,
    /** The line worth reading: the release name tidied up, or the stream's own name. */
    val label: String,
) {
    /** "DV · HEVC · Atmos · 15.2 GB · ThePirateBay". */
    val details: String get() = (tags + listOfNotNull(size, seeders?.let { "$it seeders" }, source)).joinToString("  ·  ")

    companion object {
        /** Quality buckets from best to worst, for ordering filter chips. */
        val QUALITIES = listOf("4K", "1440p", "1080p", "720p", "SD")

        // Release names separate words with dots, dashes and underscores, so "words" are bounded by anything not alphanumeric.
        private fun rx(p: String, end: String = "(?![a-z0-9])") = Regex("(?<![a-z0-9])(?:$p)$end", RegexOption.IGNORE_CASE)
        private fun audio(p: String) = rx(p, "(?![a-z])") // "DDP5.1": a digit may follow

        private val quality = listOf(
            "4K" to rx("2160p|4k|uhd"),
            "1440p" to rx("1440p|2k"),
            "1080p" to rx("1080p|1080i|fhd"),
            "720p" to rx("720p"),
            "SD" to rx("576p|480p|360p|sd|dvdrip|dvd"),
        )
        private val tagRules = listOf(
            "DV" to rx("dv|dovi|dolby[ ._-]?vision"),
            "HDR10+" to rx("hdr10\\+|hdr10plus", "(?![a-z0-9+])"),
            "HDR10" to rx("hdr10"),
            "HDR" to rx("hdr"),
            "HEVC" to rx("x265|h[ .]?265|hevc"),
            "AV1" to rx("av1"),
            "Remux" to rx("remux"),
            "BluRay" to rx("blu-?ray|bdrip|brrip"),
            "WEB-DL" to rx("web-?dl"),
            "WEBRip" to rx("web-?rip"),
            "Atmos" to audio("atmos"),
            "TrueHD" to audio("truehd"),
            "DTS-HD" to audio("dts-?hd|dts-?x"),
            "DTS" to audio("dts"),
            "DD+" to audio("ddp|dd\\+|e-?ac-?3"),
        )
        // Only one of each family: the most specific HDR mark, the best source, one DTS.
        private val exclusive = listOf(setOf("HDR10+", "HDR10", "HDR"), setOf("Remux", "BluRay", "WEB-DL", "WEBRip"), setOf("DTS-HD", "DTS"))
        private val channels = Regex("(?<![0-9.])([257])[ .]1(?![0-9])")
        private val sizeRx = Regex("(\\d+(?:[.,]\\d+)?)\\s?(TB|TiB|GB|GiB|MB|MiB)(?![a-z])", RegexOption.IGNORE_CASE)
        private val seedersRx = Regex("\uD83D\uDC64\\s*(\\d+)") // 👤
        private val sourceRx = Regex("(?:\u2699\uFE0F?|\uD83D\uDD17)\\s*([^\\n\uD83D\uDC64\uD83D\uDCBE]+)") // ⚙️ or 🔗
        private val extension = Regex("\\.(mkv|mp4|avi|m4v|mov|ts|webm)$", RegexOption.IGNORE_CASE)

        fun parse(name: String, title: String, filename: String? = null, bytes: Long? = null): StreamInfo {
            val all = listOfNotNull(name, title, filename).joinToString("\n")
            val tags = tagRules.filter { (_, r) -> r.containsMatchIn(all) }.map { it.first }.toMutableList()
            exclusive.forEach { family -> tags.filter { it in family }.drop(1).forEach(tags::remove) }
            channels.find(all)?.let { tags += "${it.groupValues[1]}.1" }
            val size = bytes?.let(::formatBytes) ?: sizeRx.find(all)?.let { m ->
                "${m.groupValues[1].replace(',', '.')} ${m.groupValues[2].uppercase().replace("I", "")}"
            }
            return StreamInfo(
                quality = quality.firstOrNull { (_, r) -> r.containsMatchIn(all) }?.first,
                tags = tags,
                size = size,
                seeders = seedersRx.find(all)?.groupValues?.get(1)?.toIntOrNull(),
                source = sourceRx.find(all)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() },
                label = label(title, filename) ?: name.lines().map(String::trim).filter(String::isNotEmpty).joinToString("  "),
            )
        }

        /** The first line that reads as words rather than emoji stats, with a release filename's dots turned into spaces (but not 5.1's). */
        private fun label(title: String, filename: String?): String? {
            val line = title.lines().map(String::trim).firstOrNull { it.firstOrNull()?.isLetterOrDigit() == true }
                ?: filename?.trim()?.takeIf { it.isNotEmpty() }
                ?: return null
            val bare = line.replace(extension, "")
            return if (' ' in bare) bare else bare.replace('_', ' ').replace(Regex("(?<!\\d)\\.|\\.(?!\\d(?!\\d))"), " ").trim()
        }

        fun formatBytes(b: Long): String = when {
            b >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", b / (1L shl 30).toDouble())
            else -> "${b / (1L shl 20)} MB"
        }
    }
}
