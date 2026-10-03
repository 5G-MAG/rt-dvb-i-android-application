/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import com.fivegmag.dvbiclient.mbms.IMbmsStreamingClient
import com.fivegmag.dvbiclient.mbms.MbmsSession
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.fivegmag.dvbiclient.http.OkHttpTransport

/**
 * Application class. Configures the Coil [ImageLoader] used for service logos with a descriptive
 * User-Agent, as the 5GMSd-Aware Application does.
 */
class App : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // The user interface is dark only, as 5G-MAGflix's.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        // The MBMS Client comes from the build variant's role (src/<flavor>/.../Role.kt).
        // TS 103 770 V1.2.1 clause 9.3.3: the DVB-I client acts as an MBMS-Aware Application. It
        // registers when its first activity opens and deregisters when the last one closes
        // (MbmsSession); the registration result says whether 5G Broadcast instances can be received.
        DvbiSession.mbms = MbmsSession(Role.mbmsClient(), packageName, listOf(IMbmsStreamingClient.DVBI_SERVICE_INSTANCE_CLASS))
        Log.i(TAG_APP, "${Role.NAME}: ${Role.status()}")
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                DvbiSession.mbms.viewOpened { success, message -> if (!success) Log.i(TAG_APP, "MBMS Client: $message") }
            }

            override fun onActivityDestroyed(activity: Activity) {
                DvbiSession.mbms.viewClosed(activity.isChangingConfigurations)
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    override fun newImageLoader(): ImageLoader {
        // Images come from service list and content guide servers (ETSI TS 103 770 clause 5.2.8), so
        // the same TLS profile applies (clause 7.3).
        val client = OkHttpTransport.clientBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                )
            }
            .build()
        return ImageLoader.Builder(this).okHttpClient(client).build()
    }

    companion object {
        const val TAG_APP = "DVB-I Client"
        val USER_AGENT = "5G-MAG-DVBIClient/${BuildConfig.VERSION_NAME} (Android; +https://www.5g-mag.com)"
    }
}
