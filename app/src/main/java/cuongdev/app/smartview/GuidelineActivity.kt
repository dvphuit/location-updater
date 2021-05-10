package cuongdev.app.smartview

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import cuongdev.app.smartview.tracking.TrackingActivity
import cuongdev.app.smartview.tracking.Utils
import kotlinx.android.synthetic.main.activity_guildline.*


const val IS_READ_GUIDE = "IS_READ_GUIDE"

class GuidelineActivity : AppCompatActivity() {

    private val pref: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    private var slideIndex: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guildline)
        updateSlide()
        btNext.setOnClickListener {
            updateSlide()
        }
        btPermission.setOnClickListener {
            Utils.requestGPSPermissions(this)
        }
    }

    private fun updateSlide() {
        when (slideIndex) {
            0 -> {
                btPermission.visibility = View.GONE
                ivBackground.setImageResource(R.drawable.intro_1)
                slideIndex++
            }
            1 -> {
                checkPermission()
                btPermission.visibility = View.VISIBLE
                ivBackground.setImageResource(R.drawable.intro_2)
            }
            2 -> {
                btPermission.visibility = View.GONE
                ivBackground.setImageResource(R.drawable.intro_3)
                slideIndex++
            }
            3 -> {
                gotoMain()
                pref.edit {
                    putBoolean(IS_READ_GUIDE, true).apply()
                }
            }
        }
    }

    private fun gotoMain() {
        startActivity(Intent(this, TrackingActivity::class.java))
        finish()
    }

    private fun checkPermission() {
        if (Utils.canAccessGPS(this)) {
            btPermission.isEnabled = false
            btNext.isEnabled = true
            slideIndex = 2
        } else {
            btNext.isEnabled = false
            btPermission.isEnabled = true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            Utils.GPS_REQUEST_CODE -> {
                if (Utils.canAccessGPS(this)) {
                    slideIndex++
                    updateSlide()
                } else {
                    if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        Snackbar.make(
                            activity_intro,
                            "Bạn cần cấp quyền truy cập vị trí để có thể truy cập được ứng dụng!",
                            Snackbar.LENGTH_INDEFINITE
                        )
                            .setAction("Cấp quyền") {
                                btPermission.performClick()
                            }
                            .show()
                    } else {


                        Snackbar.make(
                            activity_intro,
                            "Bạn cần cấp quyền truy cập vị trí lại trong phần cài đặt!",
                            Snackbar.LENGTH_INDEFINITE
                        )
                            .setAction("Cài đặt") {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                val uri: Uri = Uri.fromParts("package", packageName, null)
                                intent.data = uri
                                startActivityForResult(intent, 33333)
                            }
                            .show()
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 33333) {
            checkPermission()
        }
    }

}