package app.locationupadater

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.ticker
import java.time.LocalDateTime

@ObsoleteCoroutinesApi
class MainActivity : AppCompatActivity() {
    private lateinit var tickerChannel: ReceiveChannel<Unit>
    private var isRunning = false

    private val adapter: Adapter = Adapter()
    private lateinit var listView: RecyclerView;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.btStart).setOnClickListener {
            if (isRunning) {
                tickerChannel.cancel()
                isRunning = false
            } else {
                isRunning = true
                runJob()
            }
        }
        listView = findViewById(R.id.listView)
        listView.adapter = adapter
        adapter.addItem("INIT STRING")
    }

    private fun runJob() {
        tickerChannel = ticker(delayMillis = 1_000, initialDelayMillis = 0)
        GlobalScope.launch {
            for (event in tickerChannel) {
                val currentTime = LocalDateTime.now()
                println(currentTime)
                updateUI(currentTime.toString())
            }
        }
    }

    private suspend fun updateUI(text: String) = withContext(Dispatchers.Main) {
        adapter.addItem(text)
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