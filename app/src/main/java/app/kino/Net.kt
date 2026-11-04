package app.kino

import android.app.Activity
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class HttpException(val code: Int, body: String) : IOException("HTTP $code ${body.take(200)}")

/** Debug builds log each step from Play to the first frame: adb logcat -s KinoTime */
object Timing {
    @Volatile var start = 0L
    fun mark(stage: String) { if (BuildConfig.DEBUG) android.util.Log.d("KinoTime", "+${android.os.SystemClock.elapsedRealtime() - start}ms $stage") }
}

object Net {
    val pool = Executors.newFixedThreadPool(6)
    // Fan-out requests (one per addon) wait inside pool tasks; their own pool keeps that from starving.
    val fan = Executors.newCachedThreadPool()
    val main = Handler(Looper.getMainLooper())

    fun get(url: String, headers: Map<String, String> = emptyMap()) = request("GET", url, null, headers)

    fun post(url: String, body: String, headers: Map<String, String> = emptyMap()) = request("POST", url, body, headers)

    /** A multipart/form-data POST of plain text fields. */
    fun form(url: String, fields: Map<String, String>, headers: Map<String, String> = emptyMap()): String {
        val b = "kino" + System.nanoTime()
        val body = fields.entries.joinToString("") { (k, v) -> "--$b\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n" } + "--$b--\r\n"
        return request("POST", url, body, headers, "multipart/form-data; boundary=$b")
    }

    /** An urlencoded POST; a list of pairs so a field can repeat (items[]=a&items[]=b). */
    fun urlForm(url: String, fields: List<Pair<String, String>>, headers: Map<String, String> = emptyMap()) = request("POST", url,
        fields.joinToString("&") { (k, v) -> URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8") }, headers, "application/x-www-form-urlencoded")

    fun delete(url: String, headers: Map<String, String> = emptyMap()) = request("DELETE", url, null, headers)

    private fun request(method: String, url: String, body: String?, headers: Map<String, String>, type: String = "application/json"): String {
        val c = URL(url).openConnection() as HttpURLConnection
        val t = android.os.SystemClock.elapsedRealtime()
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 20_000
            c.requestMethod = method
            headers.forEach(c::setRequestProperty)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", type)
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code >= 400) throw HttpException(code, text)
            return text
        } finally {
            if (BuildConfig.DEBUG) Timing.mark("${android.os.SystemClock.elapsedRealtime() - t}ms $method ${c.url.host} …/${c.url.path.substringAfterLast('/')}") // addon paths carry keys
            c.disconnect()
        }
    }
}

/** Runs [work] off the main thread, then [done] on it, unless the activity has gone. */
fun <T> Activity.load(work: () -> T, done: (Result<T>) -> Unit) {
    Net.pool.execute {
        val r = runCatching(work)
        Net.main.post { if (!isDestroyed) done(r) }
    }
}
