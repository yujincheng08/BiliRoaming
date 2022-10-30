package me.iacn.biliroaming

import android.app.Activity
import android.app.AndroidAppHelper
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object ActivityHolder {
    private val activities = CopyOnWriteArrayList<WeakReference<Activity>>()

    val topActivity get() = activities.lastOrNull()?.get()

    fun init() {
        AndroidAppHelper.currentApplication()
            .registerActivityLifecycleCallbacks(ActivityLifecycleCallback)
    }

    private object ActivityLifecycleCallback : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            activities.add(WeakReference(activity))
        }

        override fun onActivityDestroyed(activity: Activity) {
            for (reference in activities) {
                if (reference.get() === activity) {
                    activities.remove(reference)
                    break
                }
            }
        }

        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    }
}
