package app.locationupadater

import android.os.Environment
import android.util.Log
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import javax.net.ssl.HttpsURLConnection

/**
 * @author dvphu on 08,June,2020
 */

class HttpGet {

    fun getString(url: String): String? {
        var result: String? = null
        try {
            val urlConnection = URL(url).openConnection() as HttpURLConnection
            val inputStream: InputStream = BufferedInputStream(urlConnection.inputStream)
            result = inputStreamToString(inputStream)
        } catch (e: Exception) {
            Log.e("HTTP Get", e.localizedMessage!!)
        }
        return result
    }

    private fun inputStreamToString(inputStream: InputStream): String? {
        var line: String?
        val sb = StringBuilder()
        val isr = InputStreamReader(inputStream)
        val rd = BufferedReader(isr)
        try {
            while (rd.readLine().also { line = it } != null) {
                sb.append(line)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return sb.toString()
    }
}

class HttpPost private constructor(
    url: String,
    private val headers: Map<String, String>,
    private val body: Map<String, Any>,
    private val parts: Map<String, String>
) {

    private val connection: HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            doOutput = true
            doInput = true
            requestMethod = "POST"
        }

    private fun buildHeaders() {
        headers.forEach {
            connection.setRequestProperty(it.key, it.value)
        }
    }

    private fun buildParts() {
        val line = "\r\n"
        val boundary = UUID.randomUUID().toString()
        connection.setRequestProperty(
            "Content-Type",
            "multipart/form-data; boundary=$boundary"
        )
        connection.outputStream.writer().apply {
            parts.forEach {
                append("--$boundary$line")
                append("Content-Disposition: form-data; name=\"${it.key}\"$line")
                append("Content-Type: text/plain; charset=${Charsets.UTF_8}$line$line")
                append(it.value).append(line)
                flush()
            }
            append("--$boundary--$line")
        }.close()
    }

    fun stringResponse(): String? {
        buildHeaders()
        buildParts()
        try {
            if (connection.responseCode == HttpsURLConnection.HTTP_OK) {
                val result = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                var length: Int
                while (connection.inputStream.read(buffer).also { length = it } != -1) {
                    result.write(buffer, 0, length)
                }
                connection.disconnect()
                return result.toString()
            }
        } catch (e: java.lang.Exception) {
            print(e.localizedMessage)
            return null
        }
        return null
    }

    data class Builder(val url: String) {
        private val headers = mutableMapOf<String, String>()
        private val body = mutableMapOf<String, Any>()
        private val parts = mutableMapOf<String, String>()

        fun header(key: String, value: String) = apply { this.headers[key] = value }
        fun param(key: String, value: Any) = apply { this.body[key] = value }
        fun part(key: String, value: String) = apply { this.parts[key] = value }

        fun build() =
            HttpPost(url, headers, body, parts)
    }

}