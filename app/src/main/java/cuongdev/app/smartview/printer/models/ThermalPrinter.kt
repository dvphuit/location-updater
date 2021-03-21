package cuongdev.app.smartview.printer.models

import android.graphics.Bitmap
import cuongdev.app.smartview.printer.printer.PrinterCommands
import cuongdev.app.smartview.printer.utils.BitmapHelper
import cuongdev.app.smartview.printer.utils.Utils

class ThermalPrinter {

    private val charsPerRow = 32

    companion object {
        val instance = ThermalPrinter()
    }

    fun fillLineWith(char: Char): ThermalPrinter {
        write(char.toString().repeat(charsPerRow), PrintAlignment.CENTER, PrintFont.BOLD)
        return this
    }

    fun writeImage(bitmap: Bitmap): ThermalPrinter {
        try {
            callPrinter(alignmentToCommand(PrintAlignment.CENTER))
            callPrinter(BitmapHelper.decodeBitmap(bitmap))
            printAndLine()
        } catch (e: IllegalStateException) {
            write("*Error: image might be too large or not black & white format*")
        }
        return this
    }

    fun write(key: String, value: String, separator: Char = '.'): ThermalPrinter {
        val ans = key + value;
        if (ans.length <= charsPerRow) {
            write(
                (key + separator.toString().repeat(charsPerRow - ans.length) + value),
                PrintAlignment.CENTER
            )
        } else {
            write("$key : $value");
        }
        return this
    }

    fun writeWrap(key: String, value: String): ThermalPrinter {
        val wrap = Utils.wrap(key)
        for (i in wrap.indices) {
            if (i == wrap.lastIndex) write(wrap[i], value)
            else write(wrap[i])
        }
        return this
    }

    fun write(
        text: String,
        alignment: PrintAlignment = PrintAlignment.LEFT,
        font: PrintFont = PrintFont.NORMAL
    ): ThermalPrinter {
        callPrinter(alignmentToCommand(alignment))
        callPrinter(fontToCommand(font))
        callPrinter(text.toByteArray())
        printAndLine()
        return this
    }

    fun newLine(): ThermalPrinter {
        callPrinter(PrinterCommands.FEED_LINE)
        return this
    }

    private fun alignmentToCommand(alignment: PrintAlignment): ByteArray {
        return when (alignment) {
            PrintAlignment.CENTER -> PrinterCommands.ESC_ALIGN_CENTER
            PrintAlignment.LEFT -> PrinterCommands.ESC_ALIGN_LEFT
            PrintAlignment.RIGHT -> PrinterCommands.ESC_ALIGN_RIGHT
        }
    }

    private fun fontToCommand(font: PrintFont): ByteArray {
        return when (font) {
            PrintFont.NORMAL -> PrinterCommands.FONT_NORMAL
            PrintFont.MEDIUM -> PrinterCommands.FONT_MEDIUM
            PrintFont.LARGE -> PrinterCommands.FONT_LARGE
            PrintFont.BOLD -> PrinterCommands.FONT_BOLD
        }
    }

    private fun callPrinter(bytes: ByteArray) {
        callSecure {
            BluetoothConnection.bluetoothSocket.outputStream.write(bytes, 0, bytes.size)
        }
    }

    private fun printAndLine() {
        callSecure {
            BluetoothConnection.bluetoothSocket.outputStream.write(PrinterCommands.LF)
        }
    }

    fun print() {
        newLine()
        newLine()
        newLine()
        callSecure {
            BluetoothConnection.bluetoothSocket.outputStream.flush()
        }
    }

    private fun callSecure(call: () -> Unit) {
        try {
            call()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}