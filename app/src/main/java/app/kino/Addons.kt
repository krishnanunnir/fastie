package app.kino

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

class Catalog(val addon: Addon, val type: String, val id: String, val name: String)

/** One Stremio addon: its manifest URL and the manifest itself. */
class Addon(val url: String, val manifest: JSONObject) {
    val base = url.removeSuffix("/manifest.json")
    val name: String = manifest.optString("name", base)

    /** Catalogs that work without extra input; the ones needing a genre or an id list are skipped. */
    val catalogs: List<Catalog>
        get() = catalogsWhere { c ->
            val extra = c.optJSONArray("extra")
            val required = (0 until (extra?.length() ?: 0)).any { extra!!.getJSONObject(it).optBoolean("isRequired") } ||
                (c.optJSONArray("extraRequired")?.length() ?: 0) > 0
            !required
        }

    val searchCatalogs: List<Catalog>
        get() = catalogsWhere { c ->
            val extra = c.optJSONArray("extra")
            (0 until (extra?.length() ?: 0)).any { extra!!.getJSONObject(it).optString("name") == "search" } ||
                c.optJSONArray("extraSupported")?.toString()?.contains("\"search\"") == true
        }

    private fun catalogsWhere(keep: (JSONObject) -> Boolean): List<Catalog> {
        val a = manifest.optJSONArray("catalogs") ?: return emptyList()
        return List(a.length()) { a.getJSONObject(it) }.filter(keep).map { c ->
            val type = c.optString("type")
            Catalog(this, type, c.optString("id"), c.optString("name").ifBlank { type.replaceFirstChar(Char::uppercase) })
        }
    }

    /** Whether this addon serves [resource] (catalog/meta/stream) for this type and id. */
    fun serves(resource: String, type: String, id: String): Boolean {
        val resources = manifest.optJSONArray("resources") ?: return false
        for (i in 0 until resources.length()) {
            val r = resources.get(i)
            val (name, types, prefixes) = when (r) {
                is JSONObject -> Triple(r.optString("name"), r.optJSONArray("types") ?: manifest.optJSONArray("types"),
                    r.optJSONArray("idPrefixes") ?: manifest.optJSONArray("idPrefixes"))
                else -> Triple(r.toString(), manifest.optJSONArray("types"), manifest.optJSONArray("idPrefixes"))
            }
            if (name != resource) continue
            if (types != null && types.toString().let { !it.contains("\"$type\"") }) continue
            if (prefixes == null || prefixes.length() == 0) return true
            if ((0 until prefixes.length()).any { id.startsWith(prefixes.getString(it)) }) return true
        }
        return false
    }
}

object Addons {
    const val CINEMETA = "https://v3-cinemeta.strem.io/manifest.json"

    private var cache: List<Addon>? = null

    private fun file(ctx: Context) = File(ctx.filesDir, "addons.json")

    fun list(ctx: Context): List<Addon> = cache ?: synchronized(this) {
        cache ?: runCatching {
            val a = JSONArray(file(ctx).readText())
            List(a.length()) { a.getJSONObject(it) }.map { Addon(it.getString("url"), it.getJSONObject("manifest")) }
        }.getOrNull().also { cache = it } ?: emptyList()
    }

    /** First run: Cinemeta only. Kino ships no stream sources. Network; call off the main thread. */
    fun ensureDefaults(ctx: Context) {
        if (file(ctx).exists()) return
        save(ctx, listOf(fetch(CINEMETA)))
    }

    /** Accepts the stremio:// links addon sites hand out, and bare base URLs. */
    fun normalize(input: String): String {
        var u = input.trim().replaceFirst(Regex("^stremio://"), "https://")
        if (!u.contains("://")) u = "https://$u"
        return if (u.endsWith("/manifest.json")) u else u.trimEnd('/') + "/manifest.json"
    }

    fun fetch(url: String) = normalize(url).let { Addon(it, JSONObject(Net.get(it))) }

    fun add(ctx: Context, url: String): Addon {
        val addon = fetch(url)
        save(ctx, list(ctx).filter { it.url != addon.url } + addon)
        return addon
    }

    fun remove(ctx: Context, addon: Addon) = save(ctx, list(ctx).filter { it.url != addon.url })

    fun move(ctx: Context, addon: Addon, by: Int) {
        val l = list(ctx).toMutableList()
        val i = l.indexOfFirst { it.url == addon.url }
        val j = (i + by).coerceIn(0, l.lastIndex)
        if (i < 0 || i == j) return
        l.add(j, l.removeAt(i))
        save(ctx, l)
    }

    /** Replaces the list with these manifest URLs (a Nuvio account's addons), keeping order; unreachable ones are dropped. */
    fun replaceAll(ctx: Context, urls: List<String>) {
        val known = list(ctx).associateBy { it.url }
        val fetched = urls.map(::normalize).distinct().map { u ->
            known[u]?.let { Net.fan.submit(Callable { it }) } ?: Net.fan.submit(Callable { runCatching { fetch(u) }.getOrNull() })
        }.mapNotNull { runCatching { it.get(20, TimeUnit.SECONDS) }.getOrNull() }
        if (fetched.isNotEmpty()) save(ctx, fetched)
    }

    fun save(ctx: Context, addons: List<Addon>) = synchronized(this) {
        val a = JSONArray()
        addons.forEach { a.put(JSONObject().put("url", it.url).put("manifest", it.manifest)) }
        file(ctx).writeText(a.toString())
        cache = addons
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    fun catalog(c: Catalog, search: String? = null): List<Item> {
        val extra = search?.let { "/search=${enc(it)}" } ?: ""
        val metas = JSONObject(Net.get("${c.addon.base}/catalog/${c.type}/${enc(c.id)}$extra.json")).optJSONArray("metas")
            ?: return emptyList()
        return List(metas.length()) { Item.from(metas.getJSONObject(it)) }.filter { it.id.isNotBlank() }
    }

    fun meta(ctx: Context, type: String, id: String): Item {
        val addons = list(ctx).filter { it.serves("meta", type, id) }.ifEmpty { listOf(fetch(CINEMETA)) }
        var last: Throwable? = null
        for (a in addons) {
            runCatching { JSONObject(Net.get("${a.base}/meta/$type/${enc(id)}.json")).getJSONObject("meta") }
                .onSuccess { return Item.from(it) }.onFailure { last = it }
        }
        throw last ?: IllegalStateException("No addon has details for $id")
    }

    /** Streams from every addon that serves them, in addon order. A slow or broken addon only loses its own rows. */
    fun streams(ctx: Context, type: String, videoId: String): List<Stream> =
        list(ctx).filter { it.serves("stream", type, videoId) }.map { a ->
            Net.fan.submit(Callable {
                val arr = JSONObject(Net.get("${a.base}/stream/$type/${enc(videoId)}.json")).optJSONArray("streams") ?: JSONArray()
                List(arr.length()) { Stream.from(a.name, arr.getJSONObject(it)) }.filterNotNull()
            })
        }.flatMap { runCatching { it.get(25, TimeUnit.SECONDS) }.getOrDefault(emptyList()) }
}
