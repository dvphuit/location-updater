package app.locationupadater

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_web.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.ticker
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


/**
 * @author dvphu on 10,March,2021
 */

class WebActivity : AppCompatActivity() {
    private lateinit var preferences: SharedPreferences
    private var gpsService: LocationService? = null
    private var mBackPressed: Long = 0
    private var mLatitude: Double? = null
    private var mLongtitude: Double? = null
    private lateinit var mListener: LocationListener
    private lateinit var tickerChannel: ReceiveChannel<Unit>
    private var count: Int = 0
    private var mUrl: String? = null
    private var mUser: String? = null
    private var mDistanceAllow: Double = DEFAULT_DISTANCE
    private var mList: String = ""
    private var mUploadCallbackAboveL: ValueCallback<Array<Uri>>? = null
    private var imageUri: Uri? = null

    private val calendar by lazy {
        Calendar.getInstance()
    }

    private val mHandler = CoroutineExceptionHandler { _, ex ->
        ex.printStackTrace()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.e("LocationService", "onServiceDisconnected")
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val classname = name?.className
            if (classname?.endsWith("LocationService") == true) {
                gpsService = (service as LocationService.LocationBinder).service
                gpsService?.startTracking()
            }
        }
    }

    companion object {
        private val PERMS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        private const val LOCATION_REQUEST = 12345
        private const val CAMERA_REQUEST = 11111
        private const val TIME_INTERVAL = 2000
        private const val DEFAULT_DISTANCE = 0.001
        private const val CURRENT_URL = "CURRENT_URL"
    }

    private val mLocationReceiver = LocationBroadcast { lat, long ->

        if (mLatitude == null || mLongtitude == null) {
            mLatitude = lat
            mLongtitude = long
        } else {
            val distance = calculateDistance(mLatitude ?: 0.0, mLongtitude ?: 0.0, lat, long)
            Log.d("TEST", "lat: $lat, long: $long ")

            if (distance >= mDistanceAllow) {
                mLatitude = lat
                mLongtitude = long
                val time = formatter.format(calendar.timeInMillis)
                mList += "$lat,$long;$time|"
                Log.d("TEST", "start tracking")
//                tracking()
            }
        }
    }


    @ObsoleteCoroutinesApi
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web)
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        tickerChannel = ticker(delayMillis = 2_000L, initialDelayMillis = 0)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager?
        requestPermission()
//        startService()
        runJob()
        val jsInterface = JavaScriptInterface { url, user, distance ->
            Log.d("TEST", "url $url, user $user, dist $distance")
            mUrl = url
            mUser = user
            try {
                mDistanceAllow = distance?.toDouble() ?: DEFAULT_DISTANCE
            } catch (e: Exception) {
            }
        }

        webview.settings.allowFileAccessFromFileURLs = true
        webview.settings.allowUniversalAccessFromFileURLs = true
        webview.settings.javaScriptEnabled = true
        webview.addJavascriptInterface(jsInterface, "JSInterface")
        webview.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                callback?.invoke(origin, true, false)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                mUploadCallbackAboveL = filePathCallback
                takePhoto()
                return true
            }
        }

        webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?) = false

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return false
            }
        }

        webview.settings.setGeolocationEnabled(true)

        webview.loadUrl("https://catminh.biz/ongbau/")

        val intentFilter = IntentFilter()
        intentFilter.addAction(LocationService.BROADCAST_ACTION)
        registerReceiver(mLocationReceiver, intentFilter)
    }

    private var locationManager: LocationManager? = null


    @SuppressLint("MissingPermission")
    private fun runJob() {
        tickerChannel = ticker(delayMillis = 2_000, initialDelayMillis = 0)
        GlobalScope.launch {
            for (event in tickerChannel) {
                val currentTime = LocalDateTime.now()
                println(currentTime)

                getLocation()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLocation() = withContext(Dispatchers.Main){
//        webview.evaluateJavascript("javascript:CallMe('abcaaaadkjfhsjkfsdf jsdflkjsdfksdfkjsd')") {
//            Log.d("TEST", "call js $it")
//        }

//       val loc = locationManager?.requestLocationUpdates()
//        println("lat ${loc?.latitude} -- lng ${loc?.longitude}")
    }

    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            tracking(location)
        }

        override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun tracking(loc: Location?) {
        if (mUser == null || mUrl == null) return
        val params = urlEncodeString("user", mUser) +
                "&" + urlEncodeString("list", mList) +
                "&" + urlEncodeString("gps", "${loc?.latitude},${loc?.longitude}")
        val url = URL(mUrl)
        Log.d("TEST", "tracking -> $url, $params")
        webview.evaluateJavascript("javascript:getLocation()") {
            Log.d("TEST", "call js $it")
        }
        CoroutineScope(Dispatchers.IO).launch(mHandler) {
            try {
                Log.d("TEST", "request ${url}")
                val conn = url.openConnection() as HttpURLConnection
                conn.readTimeout = 15000
                conn.connectTimeout = 15000
                conn.doOutput = true
                conn.requestMethod = "POST"

                val os = conn.outputStream
                val writer = BufferedWriter(OutputStreamWriter(os, "UTF-8"))
                writer.write(params)

                val wr = OutputStreamWriter(conn.outputStream)
                wr.write(params)
                wr.flush()
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = StringBuilder()
                val line: String? = null

                mList = ""
            } catch (e: Exception) {
                val ex = e
            }

        }
    }

    private fun urlEncodeString(param: String, value: String?): String {
        return URLEncoder.encode(param, "UTF8") + "=" + URLEncoder.encode(value, "UTF8")
    }


    private fun startService() {
        Log.d("TEST", " ${this.javaClass.simpleName} startService")
        val intent = Intent(this, LocationService::class.java)
        startService(intent)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun requestPermission() {
        Log.d("TEST", " ${this.javaClass.simpleName} requestPermission")
        if (!PermissionUtils.canAccessPermission(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(PERMS, LOCATION_REQUEST)
        } else {
            startService()
        }
    }


    @SuppressLint("MissingPermission")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == LOCATION_REQUEST) {
            if (PermissionUtils.canAccessPermission(this)) {
                startService()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST) {
            if (mUploadCallbackAboveL != null) {
                chooseAbove(resultCode, data)
            }
        }
    }

    private fun chooseAbove(resultCode: Int, data: Intent?) {
        if (Activity.RESULT_OK == resultCode) {
            updatePhotos()
            if (data != null) {
                //Here is the processing for selecting pictures from files
                val results: Array<Uri>
                val uriData = data.data
                if (uriData != null) {
                    results = arrayOf(uriData)

                    mUploadCallbackAboveL?.onReceiveValue(results)
                } else {
                    mUploadCallbackAboveL?.onReceiveValue(null)
                }
            } else {
                imageUri?.let {
                    mUploadCallbackAboveL?.onReceiveValue(arrayOf(it))
                }
            }
        } else {
            mUploadCallbackAboveL?.onReceiveValue(null)
        }
        mUploadCallbackAboveL = null
    }

    private fun updatePhotos() {
        //It doesn't matter if the broadcast is sent multiple times (i.e. when the photos are selected successfully), but it just wakes up the system to refresh the media files
        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
        intent.data = imageUri
        sendBroadcast(intent)
    }

    override fun onBackPressed() {
        if (mBackPressed + TIME_INTERVAL > System.currentTimeMillis()) {
            super.onBackPressed()
        } else {
            Toast.makeText(this, "Press more to exit", Toast.LENGTH_LONG).show()
        }

        mBackPressed = System.currentTimeMillis()
    }

    override fun onDestroy() {
        gpsService?.stopTracking()
        unbindService(serviceConnection)
        unregisterReceiver(mLocationReceiver)
        super.onDestroy()
    }

    private fun hideKeyboard() {
        val imm: InputMethodManager =
            getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        var view = currentFocus
        if (view == null) {
            view = View(this)
        }
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    class JavaScriptInterface(
        private val listener: (String?, String?, String?) -> Unit
    ) {
        @JavascriptInterface
        fun prepareTrackingVar(url: String?, user: String?, distanceAllow: String?) {
            Log.d("TEST", "url $url, user: $user, $distanceAllow")
            listener(url, user, distanceAllow)
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

    private fun takePhoto() {
        //Set up the camera by specifying the location of the photo storage
        val filePath: String =
            (getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.absolutePath + File.separator)
        val fileName =
            "IMG_" + DateFormat.format("yyyyMMdd_hhmmss", Calendar.getInstance(Locale.getDefault()))
                .toString() + ".jpg"

        imageUri = FileProvider.getUriForFile(
            this,
            this.applicationContext.packageName.toString() + ".provider",
            File(filePath + fileName)
        )

        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)

        startActivityForResult(captureIntent, CAMERA_REQUEST)
    }
}