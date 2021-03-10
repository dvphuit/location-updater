package app.locationupadater

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.print.PdfPrint
import android.print.PrintAttributes
import android.print.PrintAttributes.Resolution
import android.print.PrintJob
import android.print.PrintManager
import android.text.format.DateFormat
import android.util.Log
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.android.synthetic.main.activity_printer.*
import kotlinx.android.synthetic.main.activity_printer.webview
import kotlinx.android.synthetic.main.activity_web.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.*


class PrinterActivity : AppCompatActivity() {
    private var printJobs: List<PrintJob> = mutableListOf()
    lateinit var bluetoothAdapter: BluetoothAdapter
    lateinit var bluetoothSocket: BluetoothSocket
    lateinit var bluetoothDevice: BluetoothDevice
    lateinit var mReceiver: BroadcastReceiver

    // needed for communication to bluetooth device / network
    private lateinit var mOutputStream: OutputStream
    lateinit var mInputStream: InputStream

    lateinit var workerThread: Thread

    lateinit var readBuffer: ByteArray
    var readBufferPosition: Int = 0
    var stopWorker: Boolean = false


    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_printer)
        initBroadcastReceiver()
        btnPair.setOnClickListener {
            findBluetooth()
        }

        btnPrint.setOnClickListener {
//            sendData()

            createWebPrintJob(webview)
        }

        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)

        registerReceiver(mReceiver, filter)

        webview.settings.javaScriptEnabled = true
        webview.loadUrl("https://catminh.biz/shopper/bill?c=s006")
    }


    private fun createWebPrintJob(webView: WebView) {
        val jobName = getString(R.string.app_name) + " Document"
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(Resolution("pdf", "pdf", 600, 600))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS).build()
//        val path: File =
//            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM.toString() + "/PDFTest/")

        val path: String =
            (getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.absolutePath + File.separator)
//        val fileName =
//            "IMG_" + DateFormat.format("yyyyMMdd_hhmmss", Calendar.getInstance(Locale.getDefault()))
//                .toString() + ".jpg"

//        imageUri = FileProvider.getUriForFile(
//            this,
//            this.applicationContext.packageName.toString() + ".provider",
//            File(path + fileName)
//        )
        val file = File(path)
        val pdfPrint = PdfPrint(this, attributes)
        pdfPrint.print(
            webView.createPrintDocumentAdapter(jobName),
            file,
            "output_" + System.currentTimeMillis() + ".pdf"
        )
    }

    private fun sendData() {
        mOutputStream.write(edtInput.text.toString().toByteArray())
        pushInfo("Data sent!")
    }

    private fun initBroadcastReceiver() {
        mReceiver = object : BroadcastReceiver() {
            override fun onReceive(p0: Context?, intent: Intent?) {
                val action = intent?.action
                print("xxx on receive...")
                if (BluetoothDevice.ACTION_FOUND == action) {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice
                    println("xxx found devicex : " + device.name)
                }
                when (action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device =
                            intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) as BluetoothDevice
                        println("xxx found device : " + device.name)
                    }

                }
            }
        }
    }

    fun pushInfo(info: String) {
        txtInfo.text = txtInfo.text.toString() + "\n" + info
    }

    private fun findBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val pairedDevice = bluetoothAdapter.bondedDevices
        if (pairedDevice.isNotEmpty()) {
            for (device in pairedDevice) {
                println("xxxx printer name : " + device.name)
                //EP5802AI is the name for your bluetooth printer
                //the printer should be paired in order to scanable
                if (device.name.equals("GPTV-58B1")) {
                    bluetoothDevice = device
                    pushInfo("Bluetooth device found!")
                    openBluetooth()
                }
            }
        }
        bluetoothAdapter.startDiscovery()
    }

    private fun openBluetooth() {
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(uuid)
        bluetoothSocket.connect()
        mOutputStream = bluetoothSocket.outputStream
        mInputStream = bluetoothSocket.inputStream

        pushInfo("Bluetooth opened...")
        beginListenerForData()
    }

    private fun beginListenerForData() {
        val handler = Handler()
        val delimiter: Byte = 10

        stopWorker = false
        readBufferPosition = 0
        readBuffer = ByteArray(1024)

        workerThread = Thread(Runnable {
            while (!Thread.currentThread().isInterrupted && !stopWorker) {
                val bytesAvaliable = mInputStream.available()
                if (bytesAvaliable > 0) {
                    val packetBytes = ByteArray(bytesAvaliable)
                    mInputStream.read(packetBytes)

                    for (i in 0 until bytesAvaliable - 1) {
                        val b = packetBytes[i]
                        if (b == delimiter) {
                            val encodedBytes = ByteArray(readBufferPosition)
                            System.arraycopy(
                                readBuffer, 0,
                                encodedBytes, 0,
                                encodedBytes.size
                            )

//                            val data = String(encodedBytes, Charset("US-ASCII"))
                            readBufferPosition = 0

//                            handler.post { pushInfo(data) }
                        } else {
                            readBuffer[readBufferPosition++] = b
                        }
                    }
                }
            }
        })

        workerThread.start()
    }
}