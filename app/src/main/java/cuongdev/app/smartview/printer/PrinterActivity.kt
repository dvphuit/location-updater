package cuongdev.app.smartview.printer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import cuongdev.app.smartview.R
import cuongdev.app.smartview.printer.models.PrintAlignment
import cuongdev.app.smartview.printer.models.PrintFont
import cuongdev.app.smartview.printer.models.ThermalPrinter
import kotlinx.android.synthetic.main.activity_printer.*
import org.json.JSONArray


const val getBillContent = """
    function getBase64Image(img) {
        let canvas = document.createElement("canvas");
    
        let imgWidth = img.naturalWidth;
        let imgHeight = img.naturalHeight;
        let scale = Math.min((250 / imgWidth), (150 / imgHeight));
        canvas.width = imgWidth * scale;
        canvas.height = imgHeight * scale;
    
        let ctx = canvas.getContext("2d");
        ctx.drawImage(img,
            0,
            0,
            imgWidth,
            imgHeight,
            (canvas.width - imgWidth * scale) / 2,
            (canvas.height - imgHeight * scale) / 2,
            imgWidth * scale,
            imgHeight * scale
        )
        return canvas.toDataURL("image/png", 0.7).replace(/^data:image\/(png|jpg|jpeg);base64,/, "");
    }
    
    function parseHtml(){
        let table = document.querySelectorAll('table')
        let bills = []
        table.forEach(tb => {
            let bill = []
            let tr = tb.querySelectorAll('tr')
            for (let i = 0; i < tr.length; i++) {
                let td = tr[i].querySelectorAll('td');
                if (td.length == 2) {
                    bill.push({
                        key: "content",
                        value: [td[0].innerText, td[1].innerText]
                    })
                } else {
                    let firstEle = td[0].firstElementChild
                    if (firstEle) {
                        if (firstEle.nodeName == "HR") {
                            bill.push({
                                key: "hr",
                                value: "-"
                            })
                        } else if (firstEle.nodeName == "B") {
                            bill.push({
                                key: "title",
                                value: firstEle.innerText
                            })
                        } else if (firstEle.nodeName == "IMG") {
                            bill.push({
                                key: "img",
                                value: getBase64Image(firstEle)
                            })
                        }
                    } else {
                        bill.push({
                            key: "center_text",
                            value: td[0].innerText
                        })
                    }
                }
            }
            bills.push(bill)
        })
        return JSON.stringify(bills)
    }
    
    parseHtml();

"""


class PrinterActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var bills: JSONArray

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_printer)
        initViews()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initViews() {
        //webview
        webView.settings.javaScriptEnabled = true
        webView.loadUrl("https://catminh.biz/shopper/bill?c=s006")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                Log.d("TEST1", "onload finished")
                view.evaluateJavascript(getBillContent) {
                    bills = JSONArray(it.substring(1, it.lastIndex).replace("\\", ""))
                }
            }
        }

        //button
        btPair.setOnClickListener(this)
        btStartPrint.setOnClickListener(this)

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

//    [
//    [
//    {
//        "key": "title",
//        "value" : "ABC"
//    },
//    {
//        "key": "hr",
//        "value" : "-"
//    },
//    {
//        "key": "content",
//        "value" : ["k", "v"]
//    },
//    {
//        "key": "center_text",
//        "value" : "adf"
//    },
//    {
//        "key": "signature",
//        "value" : "base64"
//    }
//    ]
//    ]

    @SuppressLint("DefaultLocale")
    private fun doPrint() {
        val printer = ThermalPrinter.instance

//        for (i in 0 until bills.length()) {
        for (i in 0 until 1) {
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
                        printer.writeWrap(value.getString(0).toLowerCase().capitalize(), value.getString(1))
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

    @SuppressLint("SetTextI18n")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == ConnectBluetoothActivity.CONNECT_BLUETOOTH) {
            tvInfo.text = "Connected: ${data?.getStringExtra("device_name")}"
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            btPair -> {
                startActivityForResult(
                    Intent(this, ConnectBluetoothActivity::class.java),
                    ConnectBluetoothActivity.CONNECT_BLUETOOTH
                )
            }

            btStartPrint -> {
                doPrint()
            }
        }
    }

}