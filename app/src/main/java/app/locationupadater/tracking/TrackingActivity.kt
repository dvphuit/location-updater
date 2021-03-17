package app.locationupadater.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Base64
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.locationupadater.BuildConfig
import app.locationupadater.MyApp
import app.locationupadater.R
import app.locationupadater.printer.ConnectBluetoothActivity
import app.locationupadater.printer.models.PrintAlignment
import app.locationupadater.printer.models.PrintFont
import app.locationupadater.printer.models.ThermalPrinter
import app.locationupadater.tracking.LocationService.LocalBinder
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_printer.*
import kotlinx.android.synthetic.main.activity_web.*
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.*
import java.util.*

class TrackingActivity : AppCompatActivity() {
    private val TAG = TrackingActivity::class.java.simpleName
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 34
    private var myReceiver: MyReceiver? = null
    private var mService: LocationService? = null
    private var mBound = false

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var imageUri: Uri? = null
    private val CAMERA_REQUEST = 11111

    private lateinit var updater: Updater
    private var billJsonString: String? = null

    // Monitors the state of the connection to the service.
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as LocalBinder
            mService = binder.service
            mBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mService = null
            mBound = false
        }
    }

    inner class JavaScriptInterface(
        private val listener: (String?, String?, String?) -> Unit
    ) {
        @JavascriptInterface
        fun prepareTrackingVar(url: String?, user: String?, distanceAllow: String?) {
            Log.d("TEST", "prepareTrackingVar -> url: $url, user: $user, $distanceAllow")
            listener(url, user, distanceAllow)
            startTracking()
        }

        @JavascriptInterface
        fun printBill(json: String) {
            billJsonString = json
            startActivityForResult(
                Intent(this@TrackingActivity, ConnectBluetoothActivity::class.java),
                ConnectBluetoothActivity.CONNECT_BLUETOOTH
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        myReceiver = MyReceiver()
        setContentView(R.layout.activity_tracking)
        initWebView()

        updater = Updater(webview)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        val jsInterface = JavaScriptInterface { url, user, distance ->
            Log.d("TEST", "JSInterface: url $url, user $user, dist $distance")
            MyApp.user = user
            MyApp.urlToRequest = url
            MyApp.distanceAllow = distance?.toDouble() ?: 0.001
        }

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
                fileChooserCallback = filePathCallback
                accessCamera(fileChooserParams?.isCaptureEnabled ?: true)
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

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                startTracking()
            }
        }

        webview.settings.setGeolocationEnabled(true)
        webview.loadUrl("https://catminh.biz/ongbau/")
    }

    override fun onStart() {
        super.onStart()
        Utils.showSettingGPSIfNeeded(this)
        bindService(
            Intent(this, LocationService::class.java),
            mServiceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun startTracking() {
        if (!checkPermissions()) {
            requestPermissions()
        } else {
            mService!!.requestLocationUpdates()
        }
    }

    private fun stopTracking() {
        mService!!.removeLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            myReceiver!!,
            IntentFilter(LocationService.ACTION_BROADCAST)
        )
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(myReceiver!!)
        super.onPause()
    }

    override fun onStop() {
        if (mBound) {
            unbindService(mServiceConnection)
            mBound = false
        }
        super.onStop()
    }

    /**
     * Returns the current state of the permissions needed.
     */
    private fun checkPermissions(): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun accessCamera(isCapture: Boolean) {
        if (Utils.canAccessCamera(this)) {
            if (isCapture)
                takePhoto()
            else
                choosePhoto()
        } else {
            Utils.requestCameraPermission(this)
        }
    }

    private fun requestPermissions() {
        val shouldProvideRationale =
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )

        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.")
            Snackbar.make(
                findViewById(R.id.activity_tracking),
                R.string.permission_rationale,
                Snackbar.LENGTH_INDEFINITE
            ).setAction(R.string.ok) { // Request permission
                ActivityCompat.requestPermissions(
                    this@TrackingActivity,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_PERMISSIONS_REQUEST_CODE
                )
            }.show()
        } else {
            ActivityCompat.requestPermissions(
                this@TrackingActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        Log.i(TAG, "onRequestPermissionResult")
        if (requestCode == Utils.CAM_REQUEST_CODE) {
            when {
                grantResults.isEmpty() -> {
                    Log.i(TAG, "User interaction was cancelled.")
                }
                grantResults[0] == PackageManager.PERMISSION_GRANTED -> {
                    startTracking()
                }
                else -> {
                    // Permission denied.
                    Snackbar.make(
                        findViewById(R.id.activity_tracking),
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE
                    )
                        .setAction(R.string.settings) { // Build intent that displays the App settings screen.
                            val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            val uri = Uri.fromParts(
                                "package",
                                BuildConfig.APPLICATION_ID, null
                            )
                            intent.data = uri
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }
                        .show()
                }
            }
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

        // scan through all pixels
        for (x in 0 until width) {
            for (y in 0 until height) {
                pixel = getPixel(x, y)
                alpha = Color.alpha(pixel)
                red = Color.red(pixel)
                green = Color.green(pixel)
                blue = Color.blue(pixel)

                if (alpha == 0) {
                    // if pixel is full transparent then
                    // replace it by solid background color
                    bitmap.setPixel(x, y, backgroundColor)
                } else {
                    // if pixel is partially transparent then
                    // set pixel full opaque
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
        val bills = JSONArray(json.substring(1, json.lastIndex).replace("\\", ""))
        val printer = ThermalPrinter.instance
        for (i in 0 until bills.length()) {
            printer.writeImage(BitmapFactory.decodeResource(resources, R.drawable.head))
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
                            value.getString(0).toLowerCase(Locale.ROOT).capitalize(Locale.ROOT),
                            value.getString(1)
                        )
                    }
                    "img" -> {
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

    inner class MyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra<Location>(LocationService.EXTRA_LOCATION)
            location?.let {
                updater.tracking(it.latitude, it.longitude)
            }
        }
    }


}