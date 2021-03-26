package cuongdev.app.smartview

import android.app.Application
import cuongdev.app.smartview.model.TrackingOption

/**
 * @author dvphu on 17,March,2021
 */
class MyApp : Application() {
    companion object {
        var trackingOpt: TrackingOption? = null
    }
}