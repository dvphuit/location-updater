package cuongdev.app.smartview.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Criteria
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import cuongdev.app.smartview.MyApp
import cuongdev.app.smartview.R
import cuongdev.app.smartview.ext.logDebug
import cuongdev.app.smartview.model.TrackingOption
import cuongdev.app.smartview.printer.ConnectBluetoothActivity
import cuongdev.app.smartview.printer.models.PrintAlignment
import cuongdev.app.smartview.printer.models.PrintFont
import cuongdev.app.smartview.printer.models.ThermalPrinter
import cuongdev.app.smartview.tracking.LocationService.LocalBinder
import kotlinx.android.synthetic.main.activity_guildline.*
import kotlinx.android.synthetic.main.activity_tracking.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.util.*


const val WEB_URL = "WEB_URL"
const val CAMERA_REQUEST = 11111

class TrackingActivity : AppCompatActivity(), LocationListener {
    private val TAG = TrackingActivity::class.java.simpleName
    private var mService: LocationService? = null
    private var mBound = false

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var imageUri: Uri? = null
    private var isCapture = true

    private var billJsonString: String? = null

    private var locationManager: LocationManager? = null

    override fun onLocationChanged(loc: Location) {
        logDebug("TEST", msg = loc.toString())
        GlobalScope.launch(Dispatchers.Main) {
            logDebug("TEST", msg = "javascript:setGPS ${loc.toString()}")
            webView.evaluateJavascript("javascript:setGPS(\"${loc.latitude},${loc.longitude}\")") { }
        }
        locationManager?.removeUpdates(this)
    }

    private val pref: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as LocalBinder
            mService = binder.service
            mBound = true
            Log.d("TEST", "service is running 1")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mService = null
            mBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracking)

        MobileAds.initialize(this) {
            adView.loadAd(AdRequest.Builder().build())
        }
        getLastKnownLocation()
        initInputs()
        initWebView()
    }

    //region input
    private fun initInputs() {
        btSend.setOnClickListener {
            progress_overlay.visibility = View.VISIBLE

            val url = inLink.text.trim().toString()
            Utils.hideKeyboard(this@TrackingActivity)
            pref.edit {
                putString(WEB_URL, url).apply()
            }
            webView.loadUrl("https://${url.replace("@", ".")}")
//            webView.loadUrl("http://192.168.1.24/core-v2/public/")
        }
        inLink.setText(pref.getString(WEB_URL, ""))
    }
    //endregion


    //region setup webview
    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            userAgentString = "Android-SmartView"
            setGeolocationEnabled(true)
        }
        webView.addJavascriptInterface(JavaScriptInterface(), "JSInterface")
        webView.webChromeClient = object : WebChromeClient() {
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
                fileChooserCallback = filePathCallback
                isCapture = fileChooserParams?.isCaptureEnabled ?: true
                accessCamera()
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?) = false

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.e("TEST", "onPageFinished: $url")
                inputLayout.visibility = View.GONE
                webView.visibility = View.VISIBLE
                progress_overlay.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                Log.d("TEST", "error ${error.toString()}")
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.e("TEST", "http error ${errorResponse.toString()}")
            }

        }
    }

    inner class JavaScriptInterface {
        @JavascriptInterface
        fun tracking(options: String) {
            MyApp.trackingOpt = Gson().fromJson(options, TrackingOption::class.java)
            Log.d("TEST", MyApp.trackingOpt.toString())

            MyApp.trackingOpt?.let {
                if (it.on == 1) {
                    startTracking()
                } else {
                    mService?.removeLocationUpdates()
                }

                Toast.makeText(
                    this@TrackingActivity,
                    "Chế độ ${MyApp.trackingOpt?.mode}: ${if (it.on == 1) "Bật" else "Tắt"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        @JavascriptInterface
        fun printBill(json: String) {
            billJsonString = json
            startActivityForResult(
                Intent(this@TrackingActivity, ConnectBluetoothActivity::class.java),
                ConnectBluetoothActivity.CONNECT_BLUETOOTH
            )
        }

        @SuppressLint("HardwareIds")
        @JavascriptInterface
        fun getUUID() {
            val uuid = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            GlobalScope.launch(Dispatchers.Main) {
                webView.evaluateJavascript("javascript:setUUID(\"$uuid\")") {
                    logDebug(msg = "set uuid $uuid")
                }
            }
        }

        @SuppressLint("MissingPermission")
        @JavascriptInterface
        fun getGPS() {
            if (!Utils.isGPSEnabled(this@TrackingActivity)) {
                Utils.showSettingGPS(this@TrackingActivity)
                return
            }

            if (Utils.canAccessGPS(this@TrackingActivity)) {
                getLocation {
                    val latitude = it.latitude
                    val longitude = it.longitude
                    Log.d("TEST", "setGPS: $latitude - $longitude")
                    GlobalScope.launch(Dispatchers.Main) {
                        webView.evaluateJavascript("javascript:setGPS(\"${latitude},${longitude}\")") { }
                    }
                }
//                val loc = getLastKnownLocation()
            } else {
                Utils.requestGPSPermissions(this@TrackingActivity)
            }
        }

        @JavascriptInterface
        fun getTrackingStatus() {
            GlobalScope.launch(Dispatchers.Main) {
                val isGPSEnabled = if (Utils.isGPSEnabled(this@TrackingActivity)) 1 else 0
                val isServiceRunning = if (Utils.requestingLocationUpdates(this@TrackingActivity)) 1 else 0
                webView.evaluateJavascript("javascript:setTrackingStatus(${MyApp.trackingOpt}, $isGPSEnabled, $isServiceRunning)") { }
            }
        }

    }
    //endregion

    private fun getLocation(cb: (loc: Location) -> Unit) {
        val mFusedLocationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this)
        val mLocationRequest = LocationRequest.create()
        mLocationRequest.apply {
            interval = 0
            fastestInterval = 0
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                cb.invoke(locationResult.lastLocation)
                mFusedLocationClient.removeLocationUpdates(this)
            }
        }

        try {
            mFusedLocationClient.requestLocationUpdates(
                mLocationRequest,
                mLocationCallback, Looper.getMainLooper()
            )
        } catch (unlikely: SecurityException) {
            Log.e("TEST", "Lost location permission. Could not request updates. $unlikely")
        }
    }

    //    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        val mLocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers: List<String> = mLocationManager.getProviders(true)
        var bestLocation: Location? = null
        for (provider in providers) {
            val l: Location = mLocationManager.getLastKnownLocation(provider) ?: continue
            if (bestLocation == null || l.accuracy < bestLocation.accuracy) {
                bestLocation = l
            }
        }
        return bestLocation
    }

    private fun startTracking() {
        if (!Utils.isGPSEnabled(this)) {
            Utils.showSettingGPS(this)
            return
        }

        if (Utils.canAccessGPS(this)) {
            mService!!.requestLocationUpdates()
        } else {
            Utils.requestGPSPermissions(this)
        }
    }

    //region checkin-photo
    private fun accessCamera() {
        if (Utils.canAccessCamera(this)) {
            if (isCapture)
                takePhoto()
            else
                choosePhoto()
        } else {
            Utils.requestCameraPermission(this)
        }
    }

    private fun choosePhoto() {
        val photo = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        photo.type = "image/*"
        startActivityForResult(Intent.createChooser(photo, "Chọn ảnh"), CAMERA_REQUEST)
    }

    private fun takePhoto() {
        val path: String =
            (getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.absolutePath + File.separator)
        val name =
            "IMG_" + DateFormat.format("yyyyMMdd_hhmmss", Calendar.getInstance(Locale.getDefault()))
                .toString() + ".jpg"

        imageUri = FileProvider.getUriForFile(
            this,
            this.applicationContext.packageName.toString() + ".provider",
            File(path + name)
        )

        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)

        startActivityForResult(captureIntent, CAMERA_REQUEST)
    }

    private fun onFileChosenResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CAMERA_REQUEST && Activity.RESULT_OK == resultCode) {
            if (data != null) {
                val results: Array<Uri>
                val uriData = data.data
                if (uriData != null) {
                    results = arrayOf(uriData)
                    fileChooserCallback?.onReceiveValue(results)
                } else {
                    fileChooserCallback?.onReceiveValue(null)
                }
            } else {
                imageUri?.let {
                    fileChooserCallback?.onReceiveValue(arrayOf(it))
                }
            }
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }
    //endregion

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        //permissions denied
        if (grantResults.isEmpty()) return

        when (requestCode) {
            Utils.CAM_REQUEST_CODE -> {
                if (Utils.canAccessCamera(this)) {
                    if (isCapture)
                        takePhoto()
                    else
                        choosePhoto()
                } else {
                    Snackbar.make(
                        activity_tracking,
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE
                    )
                        .setAction(R.string.settings) { // Build intent that displays the App settings screen.
                            Utils.requestCameraPermission(this)
                        }
                        .show()
                }
            }
            Utils.GPS_REQUEST_CODE -> {
                if (Utils.canAccessGPS(this)) {
                    mService!!.requestLocationUpdates()
                } else {
                    Snackbar.make(
                        activity_intro,
                        "Bạn cần cấp quyền truy cập vị trí để có thể truy cập được ứng dụng!",
                        Snackbar.LENGTH_INDEFINITE
                    )
                        .setAction("Cấp quyền") {
                            Utils.requestGPSPermissions(this)
                        }
                        .show()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        onFileChosenResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK && requestCode == ConnectBluetoothActivity.CONNECT_BLUETOOTH) {
            Toast.makeText(
                this,
                "Connected: ${data?.getStringExtra("device_name")}",
                Toast.LENGTH_SHORT
            ).show()
            billJsonString?.let {
                doPrint(it)
            }.also { billJsonString = null }
        }
    }

    //region print
    private fun getBitmapFromBase64(base64: String): Bitmap? {
        val imageAsBytes = Base64.decode(base64, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(
            imageAsBytes, 0,
            imageAsBytes.size
        )
        return bitmap.removeAlpha()
    }

    private fun Bitmap.removeAlpha(backgroundColor: Int = Color.WHITE): Bitmap? {
        val bitmap = copy(config, true)
        var alpha: Int
        var red: Int
        var green: Int
        var blue: Int
        var pixel: Int

        for (x in 0 until width) {
            for (y in 0 until height) {
                pixel = getPixel(x, y)
                alpha = Color.alpha(pixel)
                red = Color.red(pixel)
                green = Color.green(pixel)
                blue = Color.blue(pixel)

                if (alpha == 0) {
                    bitmap.setPixel(x, y, backgroundColor)
                } else {
                    val color = Color.argb(
                        255,
                        red,
                        green,
                        blue
                    )
                    bitmap.setPixel(x, y, color)
                }
            }
        }

        return bitmap
    }

    private fun doPrint(json: String) {
        val bills = JSONArray(json)
        val printer = ThermalPrinter.instance
        for (i in 0 until bills.length()) {
            val bill = bills.getJSONArray(i)
            for (j in 0 until bill.length()) {
                val obj = bill.getJSONObject(j)
                when (obj.getString("key")) {
                    "title" -> {
                        printer.write(
                            obj.getString("value"),
                            PrintAlignment.CENTER,
                            PrintFont.BOLD
                        )
                    }
                    "center_text" -> {
                        printer.write(
                            obj.getString("value"),
                            PrintAlignment.CENTER,
                            PrintFont.NORMAL
                        )
                    }
                    "hr" -> {
                        printer.fillLineWith('-')
                    }
                    "content" -> {
                        val value = obj.getJSONArray("value")
                        printer.writeWrap(
                            value.getString(0),
                            value.getString(1)
                        )
                    }
                    "img", "logo" -> {
                        val bitMap = getBitmapFromBase64(obj.getString("value"))
                        bitMap?.let {
                            printer.writeImage(bitMap)
                        }
                    }
                }
            }
        }
        printer.print()
    }
    //endregion

    //region lifecycle logic
    override fun onStart() {
        super.onStart()
        bindService(
            Intent(this, LocationService::class.java),
            mServiceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onStop() {
        if (mBound) {
            unbindService(mServiceConnection)
            mBound = false
        }
        super.onStop()
    }
    //endregion

}