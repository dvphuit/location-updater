package cuongdev.app.smartview.ext

import android.util.Log
import cuongdev.app.smartview.BuildConfig

/**
 * @author dvphu on 26,March,2021
 */
private val enableLog = BuildConfig.DEBUG

fun Any.logDebug(tag: String? = null, msg: String) {
    if (enableLog) Log.d(tag ?: this.javaClass.simpleName, msg)
}

fun Any.logError(tag: String? = null, msg: String) {
    if (enableLog) Log.e(tag ?: this.javaClass.simpleName, msg)
}