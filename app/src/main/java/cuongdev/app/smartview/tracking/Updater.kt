package cuongdev.app.smartview.tracking

import android.util.Log
import android.webkit.WebView
import cuongdev.app.smartview.MyApp
import kotlinx.coroutines.*
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * @author dvphu on 17,March,2021
 */
class Updater(private val webView: WebView? = null) {
    private val calendar by lazy { Calendar.getInstance() }
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var mLatitude: Double = 0.0
    private var mLongitude: Double = 0.0

    private var mList: String = ""

    private fun urlEncodeString(param: String, value: String?): String {
        return URLEncoder.encode(param, "UTF8") + "=" + URLEncoder.encode(value, "UTF8")
    }

    fun tracking(lat: Double, lng: Double) {
        if (MyApp.user == null || MyApp.urlToRequest == null) return
        val distance = calculateDistance(mLatitude, mLongitude, lat, lng)
        Log.d(
            "TEST",
            "new loc [${lat}, ${lng}] => distance = $distance | distanceAllow ${MyApp.distanceAllow}"
        )

        if (distance >= MyApp.distanceAllow) {
            mLatitude = lat
            mLongitude = lng
            mList += "$lat,$lng;${formatter.format(calendar.timeInMillis)}|"

            val params = urlEncodeString("user", MyApp.user) +
                    "&" + urlEncodeString("list", mList) +
                    "&" + urlEncodeString("gps", "${lat},${lng}")


            webView?.evaluateJavascript("javascript:getLocation()") {
                Log.d("TEST", "call js $it")
            }
            CoroutineScope(Dispatchers.IO).launch {
                if (MyApp.user != null && MyApp.urlToRequest != null) {
                    val result = post(MyApp.urlToRequest!!, params)
                    Log.d("TEST", "request result: $result")
                }

            }
        }
    }

    private fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val earthRadius = 6371
        val dLat = Math.toRadians(lat2 - lat1).toFloat()
        val dLon = Math.toRadians(lon2 - lon1).toFloat()
        val a =
            (sin(dLat / 2.toDouble()) * sin(dLat / 2.toDouble()) + (cos(Math.toRadians(lat1)) * cos(
                Math.toRadians(lat2)
            ) * sin(dLon / 2.toDouble()) * sin(dLon / 2.toDouble()))).toFloat()

        val c = (2 * atan2(sqrt(a.toDouble()), sqrt(1 - a.toDouble()))).toFloat()
        return earthRadius * c
    }

    private suspend fun postLocation(params: String) = withContext(Dispatchers.IO) {
        val url = URL(MyApp.urlToRequest)
        try {
            val conn = url.openConnection() as HttpURLConnection
            conn.readTimeout = 15000
            conn.connectTimeout = 15000
            conn.doOutput = true
            conn.requestMethod = "POST"

            val os = conn.outputStream
            val writer = BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8))
            writer.write(params)

            val wr = OutputStreamWriter(conn.outputStream)
            wr.write(params)
            wr.flush()




            mList = ""
        } catch (e: IOException) {
            Log.e("TEST", e.localizedMessage ?: "request error")
        }
    }


    private fun postData(urlStr: String, params: String) {
        Log.d("TEST", "tracking -> ${urlStr}?$params")
        val postData: ByteArray = params.toByteArray(StandardCharsets.UTF_8)
        val postDataLength = postData.size
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.doOutput = true
        conn.instanceFollowRedirects = false
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("charset", "utf-8")
        conn.setRequestProperty("Content-Length", postDataLength.toString())
        conn.useCaches = false
        DataOutputStream(conn.outputStream).use { wr -> wr.write(postData) }
        mList = ""


    }

    private fun post(urlStr: String, params: String): String? {
        var response: String? = null

        var conn: HttpURLConnection? = null
        var dos: DataOutputStream? = null
        var inputStream: InputStream? = null

        try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.doInput = true
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("charset", "utf-8")
            conn.setRequestProperty("Content-Length", params.toByteArray().size.toString())

            dos = DataOutputStream(conn.outputStream)
            dos.writeBytes(params)
            dos.flush()

            inputStream = conn.inputStream;
            val s = Scanner(inputStream).useDelimiter("\\A")
            response = if (s.hasNext()) s.next() else null
        } finally {
            dos?.close()
            inputStream?.close()
            conn?.disconnect()
        }

        return response;
    }


    private fun getQuery(params: List<Pair<String, String>>): String {
        val result = StringBuilder()
        var first = true
        for (pair in params) {
            if (first) first = false else result.append("&")
            result.append(URLEncoder.encode(pair.first, "UTF-8"))
            result.append("=")
            result.append(URLEncoder.encode(pair.second, "UTF-8"))
        }
        return result.toString()
    }

}