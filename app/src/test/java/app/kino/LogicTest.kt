package app.kino

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The bits that decide what shows up: which addon serves what, link cleanup, and Nuvio's progress keys. */
class LogicTest {
    private val torrentio = Addon("https://t.example/manifest.json", JSONObject("""
        {"name":"T","types":["movie","series"],"idPrefixes":["tt"],
         "resources":["stream",{"name":"meta","types":["anime"],"idPrefixes":["kitsu:"]}],
         "catalogs":[{"type":"movie","id":"top","extra":[{"name":"search"}]},
                     {"type":"movie","id":"genre","extra":[{"name":"genre","isRequired":true}]}]}
    """))

    @Test fun serves() {
        assertTrue(torrentio.serves("stream", "series", "tt0903747:1:2"))
        assertFalse(torrentio.serves("stream", "movie", "kitsu:1"))
        assertTrue(torrentio.serves("meta", "anime", "kitsu:1"))
        assertFalse(torrentio.serves("meta", "movie", "tt1"))
    }

    @Test fun catalogs() {
        assertEquals(listOf("top"), torrentio.catalogs.map { it.id })
        assertEquals(listOf("top"), torrentio.searchCatalogs.map { it.id })
    }

    @Test fun normalize() {
        assertEquals("https://a.io/x/manifest.json", Addons.normalize("stremio://a.io/x/manifest.json"))
        assertEquals("https://a.io/x/manifest.json", Addons.normalize(" a.io/x/ "))
    }

    @Test fun progressKey() {
        assertEquals("tt1_s2e3", Progress("tt1", "series", "tt1:2:3", 2, 3, 0, 0, 0).key)
        assertEquals("tt1", Progress("tt1", "movie", "tt1", null, null, 0, 0, 0).key)
        val p = Progress.from(Progress("tt1", "series", "tt1:2:3", 2, 3, 50, 100, 7, "X").toJson())
        assertEquals(2, p.season); assertEquals(50L, p.position); assertEquals("X", p.name)
    }

    @Test fun torrents() {
        val t = Stream.from("C", JSONObject("""{"name":"C","infoHash":"ABC","fileIdx":2,"behaviorHints":{"filename":"S01E02.mkv"}}"""))!!
        assertEquals("abc", t.hash); assertEquals(2, t.fileIdx); assertEquals("S01E02.mkv", t.filename)
        assertEquals(null, Stream.from("W", JSONObject("""{"name":"W","externalUrl":"https://x"}""")))

        val files = listOf("""{"id":0,"short_name":"sample.mkv","size":5}""", """{"id":1,"short_name":"S01E01.mkv","size":900}""",
            """{"id":2,"short_name":"S01E02.mkv","size":800}""", """{"id":3,"short_name":"cover.jpg","size":9000}""").map(::JSONObject)
        assertEquals(2, TorBox.pick(files, "s01e02.MKV", null)!!.optInt("id"))
        assertEquals(1, TorBox.pick(files, null, 1)!!.optInt("id"))
        assertEquals(1, TorBox.pick(files, "missing.mkv", null)!!.optInt("id")) // largest video, not the bigger jpg
    }

    @Test fun ranking() {
        fun s(name: String, size: String, hash: String) = Stream.from("A", JSONObject("""{"name":"$name","description":"x 💾 $size","infoHash":"$hash"}"""))!!
        val remux = s("Comet 2160p", "45.3 GB", "a"); val web4k = s("Comet 2160p", "14 GB", "b")
        val hd = s("Comet 1080p", "6.2 GB", "c"); val hdBig = s("Meteor 1080p", "30 GB", "d"); val sd = s("Comet 720p", "900 MB", "e")
        assertEquals(1080, hd.height); assertEquals(2160, remux.height); assertEquals(0.87890625, sd.gigabytes!!, 1e-9)
        // Phone: 1080p within 20 Mbps first, then 720p, then what doesn't fit, smallest first; duplicates dropped.
        val phone = rankStreams(listOf(remux, hdBig, sd, hd, web4k, s("Meteor 1080p", "6.2 GB", "c")), 1080, 20.0, 120)
        assertEquals(listOf("c", "e", "b", "d", "a"), phone.map { it.hash })
        // TV: 4K under 40 Mbps first; a 52 Mbps remux would stutter on most Wi-Fi.
        assertEquals("b", rankStreams(listOf(remux, hd, web4k), 2160, 40.0, 120).first().hash)
    }

    @Test fun streamFacts() {
        val c = Stream.from("Comet", JSONObject("""{"name":"Comet 2160p","infoHash":"a","description":"📄 Moana.2026.2160p.UHD.BluRay.REMUX.TrueHD.Atmos.7.1.DoVi.HDR10.HEVC.mkv\n💾 47.3 GB"}"""))!!
        assertEquals("4K", c.quality); assertEquals("Remux", c.source); assertEquals("47.3 GB", c.size)
        assertEquals(listOf("HEVC", "Dolby Vision", "Atmos", "7.1"), c.tags)
        assertEquals("Moana.2026.2160p.UHD.BluRay.REMUX.TrueHD.Atmos.7.1.DoVi.HDR10.HEVC.mkv", c.fileName)
        val t = Stream.from("Torz", JSONObject("""{"name":"[P2P] Torz 1080p","infoHash":"b","description":"💿 WEB-DLRip 🎞️ H264\n🎧 5.1\n📦 16 GB"}"""))!!
        assertEquals("1080p", t.quality); assertEquals("WEB-DL", t.source); assertEquals(listOf("H.264", "5.1"), t.tags); assertEquals(null, t.fileName)
    }

    @Test fun camLast() {
        val cam = Stream.from("T", JSONObject("""{"name":"Torz 1080p","infoHash":"c","behaviorHints":{"filename":"Film.2026.camrip.1080p.mkv"},"description":"x 💾 4.6 GB"}"""))!!
        val hd = Stream.from("T", JSONObject("""{"name":"Torz 720p","infoHash":"d","description":"WEB-DL 💾 3 GB"}"""))!!
        assertEquals("CAM", cam.source)
        assertEquals(listOf("d", "c"), rankStreams(listOf(cam, hd), 1080, 20.0, 120).map { it.hash })
    }
}

