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
}

/** Stream rows are built from addon free text; these are the shapes the popular addons send. */
class StreamInfoTest {
    @Test fun torrentioStyle() {
        val i = StreamInfo.parse("Torrentio\n4k DV | HDR",
            "The.Bear.S02E01.2160p.WEB-DL.DDP5.1.Atmos.DV.HDR.H.265-FLUX.mkv\n👤 12 💾 15.2 GB ⚙️ ThePirateBay")
        assertEquals("4K", i.quality)
        assertEquals(listOf("DV", "HDR", "HEVC", "WEB-DL", "Atmos", "DD+", "5.1"), i.tags)
        assertEquals("15.2 GB", i.size)
        assertEquals(12, i.seeders)
        assertEquals("ThePirateBay", i.source)
        assertEquals("The Bear S02E01 2160p WEB-DL DDP5.1 Atmos DV HDR H 265-FLUX", i.label)
    }

    @Test fun hintsAndFallbacks() {
        val i = StreamInfo.parse("[RD+] Comet 1080p", "💾 1.1 GiB", filename = "Movie_2024_1080p_BluRay_x264.mp4", bytes = 3L shl 30)
        assertEquals("1080p", i.quality)
        assertEquals("3.0 GB", i.size) // the byte count wins over the text
        assertEquals("Movie 2024 1080p BluRay x264", i.label)
        assertEquals(listOf("BluRay"), i.tags)

        val bare = StreamInfo.parse("Direct\nServer 2", "")
        assertEquals(null, bare.quality)
        assertEquals("Direct  Server 2", bare.label)
        assertEquals("", bare.details)
    }

    @Test fun oneOfEachFamily() {
        val i = StreamInfo.parse("x", "Film 1080p BluRay REMUX HDR10+ HDR DTS-HD MA 7.1")
        assertEquals(listOf("HDR10+", "Remux", "DTS-HD", "7.1"), i.tags)
        assertEquals("HDR10+  ·  Remux  ·  DTS-HD  ·  7.1", i.details)
    }
}
