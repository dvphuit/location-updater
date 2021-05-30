package cuongdev.app.smartview.tracking

import android.util.Log
import com.google.gson.Gson
import cuongdev.app.smartview.MyApp
import cuongdev.app.smartview.ext.logDebug
import cuongdev.app.smartview.ext.logError
import cuongdev.app.smartview.model.BaseResponse
import cuongdev.app.smartview.model.TrackingOption
import cuongdev.app.smartview.model.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * @author dvphu on 17,March,2021
 */

class Updater {
    private val mDebugTag = this.javaClass.simpleName

    private val calendar by lazy { Calendar.getInstance() }
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private lateinit var reqUrl: String
    private lateinit var compareLoc: String
    private lateinit var originLoc: String
    private lateinit var option: TrackingOption
    private var allowDist: Double = 0.05

    private var isPushedStartLoc = false

    private var reqJob: Job? = null
    private var tickerChannel: ReceiveChannel<Unit>? = null

    companion object {
        private var preLat: Double = 0.0
        private var preLng: Double = 0.0
        private var listLoc: String = ""
        private var uploadInterval = Long.MAX_VALUE
    }

    init {
        if (MyApp.trackingOpt == null || MyApp.trackingOpt?.on != 1) {
            logError(msg = "Tracking option is null or Off. Can't start tracking service!")
            tickerChannel?.cancel()
        } else {
            this.option = MyApp.trackingOpt!!
            this.reqUrl = MyApp.trackingOpt?.url ?: ""
            this.allowDist = MyApp.trackingOpt?.distanceAllow ?: 0.01
            this.originLoc = MyApp.trackingOpt?.origin ?: ""
            uploadInterval = MyApp.trackingOpt?.uploadInterval ?: 5 * 60 * 1000
        }
    }

    fun startTicker() {
        startTicker(uploadInterval)
    }

    fun onLocationChanged(lat: Double, lng: Double) {
        if (option.on != 1) return
        pushStartLoc(lat, lng)
        logDebug(msg = "on Location changed | ticker ${tickerChannel?.isClosedForReceive}")
        option.mode.let {
            when (it) {
                "FULL" -> fullTracking(lat, lng)
                "HALF" -> halfTracking(lat, lng)
                "AROUND" -> aroundTracking(lat, lng)
            }
        }
    }

    private fun pushStartLoc(lat: Double, lng: Double) {
        if (!isPushedStartLoc) {
            reqJob = GlobalScope.launch(Dispatchers.IO) {
                listLoc += "1;$lat,$lng;${formatter.format(calendar.timeInMillis)}|"
                val params = urlEncodeString("user", option.user) +
                        "&" + urlEncodeString("list", listLoc) +
                        "&" + urlEncodeString("gps", "$lat,$lng") +
                        "&" + urlEncodeString("extra", option.extra) +
                        "&" + urlEncodeString("shift", option.shift) +
                        "&" + urlEncodeString("token", option.token)

                val result = post(reqUrl, params)

                if (result.contains("result:1")) {
                    logError(msg = "Post first data failed")
                } else {
                    logDebug(msg = "Post fist data successful: params -> $params")
                    isPushedStartLoc = true
                    listLoc = ""
                }
            }
        }
    }

    private fun fullTracking(lat: Double, lng: Double) {
        logDebug(msg = "Mode -> FULL")
        preLat = lat
        preLng = lng
        listLoc += "1;$lat,$lng;${formatter.format(calendar.timeInMillis)}|"
    }

    private fun halfTracking(lat: Double, lng: Double) {
        val allowDistance = allowDist
        val distance = calculateDistance(preLat, preLng, lat, lng)
        logDebug(msg = "Mode -> HALF ---- dist: $distance | allowDist: $allowDistance")
        if (distance >= allowDistance) {
            listLoc += "1;$lat,$lng;${formatter.format(calendar.timeInMillis)}|"
            preLat = lat
            preLng = lng
            logDebug(msg = "Store list location: $listLoc")
        }
    }

    private fun aroundTracking(lat: Double, lng: Double) {
        logDebug(msg = "Mode -> AROUND")

        val originLoc = (option.origin ?: "0,0").split(",").map { it.toDouble() }
        val allowDistance = allowDist
        val distance = calculateDistance(originLoc[0], originLoc[1], lat, lng)
        Log.d("TEST", "AROUND mode distance -> $distance")
        if (distance >= allowDistance) {
            listLoc += "1;$lat,$lng;${formatter.format(calendar.timeInMillis)}|"
            preLat = lat
            preLng = lng
            logDebug(msg = "Store list location: $listLoc")
        }
    }

    private fun startTicker(interval: Long) {
        logDebug("startTicker", msg = "interval $interval")
        tickerChannel = ticker(delayMillis = interval, initialDelayMillis = interval)
        reqJob = GlobalScope.launch(Dispatchers.IO) {
            for (event in tickerChannel!!) {
                logDebug(msg = "===========================================================")
                logDebug(msg = "Start post data $listLoc")
                if (listLoc.isEmpty()) {
                    logDebug(msg = "No data to post")
                } else {
                    val params = urlEncodeString("user", option.user) +
                            "&" + urlEncodeString("list", listLoc) +
                            "&" + urlEncodeString("gps", "$preLat,$preLng") +
                            "&" + urlEncodeString("extra", option.extra) +
                            "&" + urlEncodeString("shift", option.shift) +
                            "&" + urlEncodeString("token", option.token)

                    val response = Gson().fromJson<BaseResponse>(
                        post(reqUrl, params),
                        BaseResponse::class.java
                    )

                    if (response.isSuccess()) {
                        logDebug(msg = "Post data successful: params -> $params")
                        listLoc = ""
                    } else {
                        logError(msg = "post data failed")
                    }
                }
            }
        }
    }

    fun offline(result: (() -> Unit)? = null) {
        reqJob = GlobalScope.launch {
            listLoc = "0;EXIT_APP;${formatter.format(calendar.timeInMillis)}|"
            val params = urlEncodeString("user", option.user) +
                    "&" + urlEncodeString("list", listLoc) +
                    "&" + urlEncodeString("gps", "$preLat,$preLng") +
                    "&" + urlEncodeString("extra", option.extra) +
                    "&" + urlEncodeString("shift", option.shift) +
                    "&" + urlEncodeString("token", option.token)

            val response = Gson().fromJson<BaseResponse>(
                post(reqUrl, params),
                BaseResponse::class.java
            )

            if (response.isSuccess()) {
                logDebug(msg = "App is offline: params -> $params")
                result?.invoke()
                dispose()
            } else {
                logError(msg = "post data failed")
                result?.invoke()
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

    private fun post(urlStr: String, params: String): String {

        val response: String

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
            response = if (s.hasNext()) s.next() else "0"
        } catch (e: Exception) {
            Log.e("TEST", e.message ?: "empty")
            return "-1"
        } finally {
            dos?.close()
            inputStream?.close()
            conn?.disconnect()
        }
        return response
    }

    private fun urlEncodeString(param: String, value: String?): String {
        return URLEncoder.encode(param, "UTF8") + "=" + URLEncoder.encode(value, "UTF8")
    }

    private fun dispose() {
        reqJob?.cancel()
        tickerChannel?.cancel()
        MyApp.trackingOpt = null
    }
}