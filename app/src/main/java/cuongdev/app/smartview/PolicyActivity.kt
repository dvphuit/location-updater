package cuongdev.app.smartview

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import cuongdev.app.smartview.tracking.TrackingActivity
import kotlinx.android.synthetic.main.activity_policy.*

const val IS_ACCEPTED_POLICY = "IS_ACCEPTED_POLICY"

class PolicyActivity : AppCompatActivity() {

    private val pref: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkState()
        setContentView(R.layout.activity_policy)

        webView.loadUrl("https://smartview.store/t&c/")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                cbAccept.isEnabled = true
            }
        }

        cbAccept.setOnCheckedChangeListener { _, isChecked ->
            btAccept.isEnabled = isChecked
        }

        btDeny.setOnClickListener { finish() }

        btAccept.setOnClickListener {
            gotoMain()
            pref.edit {
                putBoolean(IS_ACCEPTED_POLICY, true).apply()
            }
        }
    }

    private fun gotoMain() {
        startActivity(Intent(this, TrackingActivity::class.java))
        finish()
    }

    private fun checkState() {
        val isAccepted = pref.getBoolean(IS_ACCEPTED_POLICY, false)
        if (isAccepted) {
            gotoMain()
        }
    }
}