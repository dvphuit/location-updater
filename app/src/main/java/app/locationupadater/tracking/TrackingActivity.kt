package app.locationupadater.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.locationupadater.BuildConfig
import app.locationupadater.R
import app.locationupadater.WebActivity
import app.locationupadater.tracking.LocationService.LocalBinder
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_web.*
import java.io.File
import java.util.*

class TrackingActivity : AppCompatActivity() {
    private val TAG = TrackingActivity::class.java.simpleName
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 34
    private var myReceiver: MyReceiver? = null
    private var mService: LocationService? = null
    private var mBound = false

    private var mUrl: String? = null
    private var mUser: String? = null
    private var mDistanceAllow: Double = .001

    private var mUploadCallbackAboveL: ValueCallback<Array<Uri>>? = null
    private var imageUri: Uri? = null
    private val CAMERA_REQUEST = 11111

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        myReceiver = MyReceiver()
        setContentView(R.layout.activity_tracking)

        if (Utils.requestingLocationUpdates(this)) {
            if (!checkPermissions()) {
                requestPermissions()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        val jsInterface = WebActivity.JavaScriptInterface { url, user, distance ->
            Log.d("TEST", "url $url, user $user, dist $distance")
            mUrl = url
            mUser = user
            try {
                mDistanceAllow = distance?.toDouble() ?: mDistanceAllow
            } catch (e: Exception) {
            }
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
    }

    override fun onStart() {
        super.onStart()
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
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
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
            if (data != null) {
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


    inner class MyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra<Location>(LocationService.EXTRA_LOCATION)
            if (location != null) {
                Toast.makeText(
                    this@TrackingActivity,
                    Utils.getLocationText(location),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

}