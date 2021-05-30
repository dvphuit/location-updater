package cuongdev.app.smartview.tracking

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import cuongdev.app.smartview.MyApp
import cuongdev.app.smartview.ext.logDebug
import cuongdev.app.smartview.ext.logError
import cuongdev.app.smartview.model.BaseResponse
import cuongdev.app.smartview.model.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*


class MakeOfflineService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TEST", "MakeOfflineService started")
        offline()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val calendar by lazy { Calendar.getInstance() }
    private val option = MyApp.trackingOpt


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

    private fun offline() {
        GlobalScope.launch(Dispatchers.IO) {
            val listLoc = "0;EXIT_APP;${formatter.format(calendar.timeInMillis)}|"
            val params = urlEncodeString("user", option!!.user) +
                    "&" + urlEncodeString("list", listLoc) +
                    "&" + urlEncodeString("extra", option.extra) +
                    "&" + urlEncodeString("shift", option.shift) +
                    "&" + urlEncodeString("token", option.token)

//            val response = Gson().fromJson<BaseResponse>(
//                post(option.url!!, params),
//                BaseResponse::class.java
//            )
            val response = post(option.url!!, params)
            Log.d("TEST", "Offline $response")
//            if (response.isSuccess()) {
//                logDebug(msg = "App is offline: params -> $params")
//            } else {
//                logError(msg = "post data failed")
//            }
            Log.d("TEST", "doing background")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("TEST", "MakeOfflineService stopped")
    }

}