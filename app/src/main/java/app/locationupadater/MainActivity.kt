package app.locationupadater

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.ticker
import java.time.LocalDateTime


@ObsoleteCoroutinesApi
class MainActivity : AppCompatActivity() {
//    private lateinit var tickerChannel: ReceiveChannel<Unit>
//    private var isRunning = false
//
//    private val adapter: Adapter = Adapter()
//    private lateinit var listView: RecyclerView;
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//
//        setContentView(R.layout.activity_main)
//        findViewById<View>(R.id.btStart).setOnClickListener {
//            if (isRunning) {
//                tickerChannel.cancel()
//                isRunning = false
//            } else {
//                isRunning = true
//                runJob()
//            }
//        }
//        listView = findViewById(R.id.listView)
//        listView.adapter = adapter
//        adapter.addItem("INIT STRING")
//    }
//
//    private fun runJob() {
//        tickerChannel = ticker(delayMillis = 1_000, initialDelayMillis = 0)
//        GlobalScope.launch {
//            for (event in tickerChannel) {
//                val currentTime = LocalDateTime.now()
//                println(currentTime)
//                updateUI(currentTime.toString())
//            }
//        }
//    }
//
//    private suspend fun updateUI(text: String) = withContext(Dispatchers.Main) {
//        adapter.addItem(text)
//    }


    private var webView: WebView? = null
    private var fabRight: FloatingActionButton? = null
    private var pageIsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        webView = findViewById<View>(R.id.webview) as WebView
        setupWebView()
        fabRight = findViewById<View>(R.id.fab) as FloatingActionButton
        fabRight!!.setOnClickListener {
            if (pageIsLoaded) {
                printWebViewContent()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Page not completely loaded",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        webView!!.loadUrl("file:///android_asset/demo_bill.html")
    }

    /**
     * API 19 REQUIRED
     */
    private fun printWebViewContent() {
        val printAdapter = webView!!.createPrintDocumentAdapter()
        val printManager = this
            .getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = getString(R.string.app_name) + " Print Test"
        printManager.print(
            jobName, printAdapter,
            PrintAttributes.Builder().build()
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView!!.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                pageIsLoaded = true
            }
        }
        webView!!.settings.javaScriptEnabled = true
        webView!!.setInitialScale(1)
        webView!!.settings.loadWithOverviewMode = false
        webView!!.settings.useWideViewPort = false
    }
}

class Adapter : RecyclerView.Adapter<Adapter.ViewHolder>() {

    private val list = mutableListOf<String>()

    fun addItem(text: String) {
        list.add(text)
        notifyItemInserted(list.size - 1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_view, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int = list.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textVew = view.findViewById<TextView>(R.id.textView)

        fun bind(text: String) {
            textVew.text = text;
        }
    }

}