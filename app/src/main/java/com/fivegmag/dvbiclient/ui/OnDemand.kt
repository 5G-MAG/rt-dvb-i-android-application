/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.ui

import android.app.Activity
import android.widget.Toast
import com.fivegmag.dvbiclient.AppActivity
import com.fivegmag.dvbiclient.DvbiRepository
import com.fivegmag.dvbiclient.R
import com.fivegmag.dvbiclient.Settings
import com.fivegmag.dvbiclient.guide.OnDemandProgram
import com.fivegmag.dvbiclient.servicelist.ParentalRating
import com.fivegmag.dvbiclient.servicelist.ServiceListRules

/**
 * Starts an on-demand programme, as the browser client's playOnDemand (rt-dvb-i-application
 * public/app.js): its rating checked against the parental setting (clause 6.10.15, clause 5.5.28),
 * then the player its content deep-linked XML AIT selects (clause 5.2.4.3).
 */
object OnDemand {

    /** The regionID values "specific to the device" (clause 5.2.4.4.6): the region of the settings. */
    fun regions(settings: Settings): List<String> = listOfNotNull(settings.region.ifEmpty { null })

    fun launch(activity: Activity, od: OnDemandProgram, ratings: List<ParentalRating>) {
        val settings = Settings(activity)
        val age = ServiceListRules.minimumAgeFor(ratings, settings.country.ifEmpty { null })
        if (ServiceListRules.restricted(settings.parentalThreshold, null, age)) {
            Toast.makeText(activity, activity.getString(R.string.restricted_programme, age), Toast.LENGTH_LONG).show()
            return
        }
        DvbiRepository.background({ DvbiRepository.guide.onDemandPlayer(od, regions(settings)) }) { url ->
            if (activity.isFinishing || activity.isDestroyed) return@background
            // "If the content deep-linked XML AIT is unavailable the client device shall consider the
            // content to be unavailable and behave gracefully." (clause 5.2.4.3)
            if (url == null || !AppActivity.isWebUrl(url)) {
                Toast.makeText(activity, R.string.programme_unavailable, Toast.LENGTH_SHORT).show()
            } else {
                activity.startActivity(AppActivity.intent(activity, url))
            }
        }
    }
}
