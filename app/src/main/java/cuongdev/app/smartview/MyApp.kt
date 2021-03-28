package cuongdev.app.smartview

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.provider.Settings
import cuongdev.app.smartview.model.TrackingOption
import java.security.AccessController.getContext


/**
 * @author dvphu on 17,March,2021
 */
class MyApp : Application() {
    companion object {
        var trackingOpt: TrackingOption? = null
    }

    init {
    }
}