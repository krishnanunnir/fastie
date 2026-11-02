package app.fastie

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
