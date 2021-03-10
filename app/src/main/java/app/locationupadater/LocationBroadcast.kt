package app.locationupadater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * @author dvphu on 10,March,2021
 */
open class LocationBroadcast(val listener: (Double, Double) -> Unit): BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val lat = intent?.getDoubleExtra(LocationService.LAT, 0.0) ?: 0.0
        val long = intent?.getDoubleExtra(LocationService.LONG, 0.0) ?: 0.0
        listener(lat, long)
    }
}