package app.kino

import android.app.Activity
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class HttpException(val code: Int, body: String) : IOException("HTTP $code ${body.take(200)}")

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

    private fun request(method: String, url: String, body: String?, headers: Map<String, String>, type: String = "application/json"): String {
        val c = URL(url).openConnection() as HttpURLConnection
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
