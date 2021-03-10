package app.locationupadater


import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat

/**
 * @author dvphu on 10,March,2021
 */

class LocationService : Service() {
    private var locationManager: LocationManager? = null
    private lateinit var listener: MyLocationListener
    var intent: Intent? = null
    var notificationManager: NotificationManager? = null
    private val binder: IBinder = LocationBinder()
    private var count: Int = 0

    open inner class LocationBinder : Binder() {
        val service: LocationService
            get() = this@LocationService
    }

    private lateinit var locationListener: LocationListener

    override fun onCreate() {
        super.onCreate()
        locationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        intent = Intent(BROADCAST_ACTION)
        startForeground(NOTIFICATION_ID, getNotification())
        Log.d("TEST", " ${this.javaClass.simpleName} onCreate")
    }

    private fun getNotification(): Notification? {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "channel_123",
                "Tracking Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.setSound(null, null)
            notificationManager?.createNotificationChannel(channel)
        }

        val builder: NotificationCompat.Builder =
            NotificationCompat.Builder(applicationContext, "channel_123").setAutoCancel(true)
        return builder.build()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d("TEST", " ${this.javaClass.simpleName} onStartCommand")
        return START_NOT_STICKY
    }


    @SuppressLint("MissingPermission")
    fun startTracking() {
        listener = MyLocationListener()
        Log.d("TEST", " ${this.javaClass.simpleName} startTracking")
        try {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                LOCATION_INTERVAL,
                LOCATION_DISTANCE,
                listener
            )

        } catch (ex: SecurityException) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (ex: Exception) { //
            Log.d(TAG, "gps provider does not exist " + ex.message);
        }
    }

    fun stopTracking() {
        onDestroy()
        Log.d("TEST", " ${this.javaClass.simpleName} stopTracking")
    }

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.v("STOP_SERVICE", "DONE")
        locationManager?.removeUpdates(listener)
        stopSelf()
    }

    inner class MyLocationListener : LocationListener {
        override fun onLocationChanged(loc: Location) {
            Log.i("LocationChanged", loc.toString())
            intent?.putExtra(LAT, loc.latitude)
            intent?.putExtra(LONG, loc.longitude)
            intent?.putExtra("Provider", loc.provider)
            sendBroadcast(intent)
        }

        override fun onStatusChanged(
            provider: String,
            status: Int,
            extras: Bundle
        ) {
            val a = provider
        }

        override fun onProviderDisabled(provider: String) {
            Toast.makeText(applicationContext, "Gps Disabled", Toast.LENGTH_SHORT).show()
        }

        override fun onProviderEnabled(provider: String) {
            Toast.makeText(applicationContext, "Gps Enabled", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 12345
        private const val LOCATION_INTERVAL = 1000L
        private const val LOCATION_DISTANCE = 0f
        const val BROADCAST_ACTION = "LocationBroadcast"
        const val LAT = "LAT"
        const val LONG = "LONG"
        const val COUNT = "count"
    }
}