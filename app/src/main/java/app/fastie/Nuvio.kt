package app.fastie

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * An unofficial client for a Nuvio account (Supabase underneath). The server publishes its own publishable key at
 * /.well-known/nuvio, so nothing is baked in. Everything here does network work; call it off the main thread.
 * Only the primary profile (1) is read and written.
 */
object Nuvio {
    const val OFFICIAL = "https://api.nuvio.tv"

    /** Set after sign-in so Home reloads with the account's addons. */
    @Volatile var changed = false
    private const val PROFILE = 1

    class TvCode(val deviceCode: String, val userCode: String, val url: String, val intervalMs: Long, val nonce: String)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("nuvio", Context.MODE_PRIVATE)

    fun signedIn(ctx: Context) = prefs(ctx).contains("refresh")
    fun email(ctx: Context): String? = prefs(ctx).getString("email", null)

    fun signOut(ctx: Context) = prefs(ctx).edit().clear().apply()

    /** Reads the server's discovery document and remembers its backend and key. */
    private fun discover(ctx: Context, server: String = OFFICIAL) {
        val doc = JSONObject(Net.get(server.trimEnd('/') + "/.well-known/nuvio"))
        require(doc.optString("service").equals("nuvio", true)) { "That isn't a Nuvio server" }
        prefs(ctx).edit().putString("backend", doc.getString("backend_url").trimEnd('/'))
            .putString("key", doc.getString("publishable_key")).apply()
    }

    private fun backend(ctx: Context) = prefs(ctx).getString("backend", null) ?: discover(ctx).let { prefs(ctx).getString("backend", null)!! }
    private fun key(ctx: Context) = prefs(ctx).getString("key", null) ?: discover(ctx).let { prefs(ctx).getString("key", null)!! }

    private fun anonHeaders(ctx: Context) = key(ctx).let { mapOf("apikey" to it, "Authorization" to "Bearer $it") }

    private fun authHeaders(ctx: Context) = mapOf("apikey" to key(ctx), "Authorization" to "Bearer ${token(ctx)}")

    private fun saveSession(ctx: Context, s: JSONObject) {
        prefs(ctx).edit()
            .putString("access", s.getString("access_token"))
            .putString("refresh", s.getString("refresh_token"))
            .putLong("expires", System.currentTimeMillis() + s.optLong("expires_in", 3600) * 1000 - 60_000)
            .putString("email", s.optJSONObject("user")?.optString("email")?.takeIf { it.isNotBlank() } ?: email(ctx))
            .apply()
    }

    fun signIn(ctx: Context, email: String, password: String) {
        val body = JSONObject().put("email", email.trim()).put("password", password).toString()
        saveSession(ctx, JSONObject(Net.post("${backend(ctx)}/auth/v1/token?grant_type=password", body, anonHeaders(ctx))))
    }

    /** A valid access token, refreshed when it's about to expire. */
    private fun token(ctx: Context): String = synchronized(this) {
        val p = prefs(ctx)
        if (System.currentTimeMillis() < p.getLong("expires", 0)) return p.getString("access", null)!!
        val refresh = p.getString("refresh", null) ?: error("Not signed in")
        val body = JSONObject().put("refresh_token", refresh).toString()
        saveSession(ctx, JSONObject(Net.post("${backend(ctx)}/auth/v1/token?grant_type=refresh_token", body, anonHeaders(ctx))))
        p.getString("access", null)!!
    }

    // TV sign-in: show a code, the user approves it on their phone, we exchange it for a session.

    fun startTvLogin(ctx: Context): TvCode {
        val nonce = UUID.randomUUID().toString()
        val params = JSONObject().put("p_device_nonce", nonce).put("p_redirect_base_url", "https://nuvio.tv/link")
            .put("p_device_name", "Fastie on ${Build.MODEL}").put("p_device_type", "mobile")
        val r = JSONArray(Net.post("${backend(ctx)}/rest/v1/rpc/start_device_login_session", params.toString(), anonHeaders(ctx)))
            .getJSONObject(0)
        return TvCode(r.getString("device_code"), r.getString("user_code"), r.getString("verification_uri_complete"),
            r.optLong("poll_interval_seconds", 3).coerceIn(2, 10) * 1000, nonce)
    }

    /** True once approved and signed in, false while pending; throws when the code expired or was refused. */
    fun pollTvLogin(ctx: Context, code: TvCode): Boolean {
        val params = JSONObject().put("p_code", code.deviceCode).put("p_device_nonce", code.nonce)
        val status = JSONArray(Net.post("${backend(ctx)}/rest/v1/rpc/poll_tv_login_session", params.toString(), anonHeaders(ctx)))
            .getJSONObject(0).optString("status").lowercase()
        when (status) {
            "pending" -> return false
            "approved" -> {
                val body = JSONObject().put("code", code.deviceCode).put("device_nonce", code.nonce).toString()
                saveSession(ctx, JSONObject(Net.post("${backend(ctx)}/functions/v1/tv-logins-exchange", body, anonHeaders(ctx))))
                return true
            }
            else -> error("The code expired. Start again.")
        }
    }

    private fun rpc(ctx: Context, name: String, params: JSONObject): String =
        Net.post("${backend(ctx)}/rest/v1/rpc/$name", params.put("p_profile_id", PROFILE).toString(), authHeaders(ctx))

    private fun clientId(ctx: Context) = prefs(ctx).getString("client", null)
        ?: UUID.randomUUID().toString().also { prefs(ctx).edit().putString("client", it).apply() }

    /** Pulls addons, My List and progress into the local stores. */
    fun pull(ctx: Context) {
        val rows = JSONArray(Net.get("${backend(ctx)}/rest/v1/addons?select=url,enabled&profile_id=eq.$PROFILE&order=sort_order.asc", authHeaders(ctx)))
        val urls = List(rows.length()) { rows.getJSONObject(it) }.filter { it.optBoolean("enabled", true) }.map { it.getString("url") }
        if (urls.isNotEmpty()) Addons.replaceAll(ctx, urls)

        val lib = JSONArray(rpc(ctx, "sync_pull_library", JSONObject().put("p_limit", 500).put("p_offset", 0)))
        Store.replaceLibrary(ctx, List(lib.length()) { lib.getJSONObject(it) }.map { o ->
            Item(id = o.getString("content_id"), type = o.optString("content_type", "movie"), name = o.optString("name"),
                poster = o.optString("poster").ifBlank { null }, background = o.optString("background").ifBlank { null },
                logo = o.optString("logo").ifBlank { null }, description = o.optString("description").ifBlank { null },
                releaseInfo = o.optString("release_info").ifBlank { null },
                rating = if (o.isNull("imdb_rating")) null else o.optString("imdb_rating")) to o.optLong("added_at")
        })

        val prog = JSONArray(rpc(ctx, "sync_pull_watch_progress", JSONObject().put("p_limit", 300)))
        Store.mergeProgress(ctx, List(prog.length()) { Progress.from(prog.getJSONObject(it)) })
    }

    fun pushProgress(ctx: Context, p: Progress) {
        val entry = p.toJson().apply { remove("name") }
        rpc(ctx, "sync_push_watch_progress", JSONObject().put("p_entries", JSONArray().put(entry)).put("p_origin_client_id", clientId(ctx)))
    }

    fun pushLibrary(ctx: Context, item: Item, on: Boolean) {
        if (on) {
            val o = JSONObject().put("content_id", item.id).put("content_type", item.type).put("name", item.name)
                .put("poster", item.poster).put("background", item.background).put("logo", item.logo)
                .put("description", item.description).put("release_info", item.releaseInfo)
                .put("imdb_rating", item.rating?.toFloatOrNull()).put("genres", JSONArray(item.genres))
                .put("poster_shape", "POSTER").put("added_at", System.currentTimeMillis())
            rpc(ctx, "sync_push_library_items", JSONObject().put("p_items", JSONArray().put(o)).put("p_origin_client_id", clientId(ctx)))
        } else {
            val k = JSONObject().put("content_id", item.id).put("content_type", item.type)
            rpc(ctx, "sync_delete_library_items", JSONObject().put("p_keys", JSONArray().put(k)).put("p_origin_client_id", clientId(ctx)))
        }
    }

    /** Fire-and-forget push when signed in; a failure only costs this one sync. */
    fun later(ctx: Context, push: (Context) -> Unit) {
        if (!signedIn(ctx)) return
        val app = ctx.applicationContext
        Net.pool.execute { runCatching { push(app) } }
    }
}
