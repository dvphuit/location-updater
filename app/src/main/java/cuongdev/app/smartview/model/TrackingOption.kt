package cuongdev.app.smartview.model

import com.google.gson.Gson

/**
 * @author dvphu on 26,March,2021
 */

data class TrackingOption(
    val on: Int = 0,
    val url: String? = null,
    val mode: String? = null,
    val interval: Long = 0L,
    val distanceAllow: Double = 0.01,
    val origin: String? = null,
    val uploadInterval: Long? = null,
    val user: String? = null,
    val extra: String? = null,
    val shift: String? = null,
    val token: String? = null
) {

    override fun toString(): String {
        return Gson().toJson(this)
    }

}
