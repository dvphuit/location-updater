package cuongdev.app.smartview

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.util.Log
import cuongdev.app.smartview.model.TrackingOption
import cuongdev.app.smartview.tracking.MakeOfflineService
import cuongdev.app.smartview.tracking.TrackingActivity


/**
 * @author dvphu on 17,March,2021
 */
class MyApp : Application(), Application.ActivityLifecycleCallbacks {
    companion object {
        var trackingOpt: TrackingOption? = null
    }

    override fun onCreate() {
        super.onCreate()
        this.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityPaused(activity: Activity) {

    }

    override fun onActivityStarted(activity: Activity) {

    }

    override fun onActivityDestroyed(activity: Activity) {
        Log.d("TEST", "activity ${activity.javaClass.simpleName}: is destroyed")
        if(activity is TrackingActivity && trackingOpt != null){
            startService(Intent(this, MakeOfflineService::class.java))
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

    }

    override fun onActivityStopped(activity: Activity) {

    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {

    }

    override fun onActivityResumed(activity: Activity) {

    }

}
