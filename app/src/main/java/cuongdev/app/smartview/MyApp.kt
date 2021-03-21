package cuongdev.app.smartview

import android.app.Application

/**
 * @author dvphu on 17,March,2021
 */
class MyApp : Application() {
    companion object {
        var user: String? = null
        var urlToRequest: String? = null
        var distanceAllow: Double = 0.001
    }
}