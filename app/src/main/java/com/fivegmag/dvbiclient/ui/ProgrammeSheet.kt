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
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import coil.load
import com.fivegmag.dvbiclient.R
import com.fivegmag.dvbiclient.guide.GuideEvent
import com.fivegmag.dvbiclient.guide.ProgrammeInfo
import com.fivegmag.dvbiclient.servicelist.Service
import com.fivegmag.dvbiclient.servicelist.ServiceListRules
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.ChipGroup
import java.text.DateFormat
import java.util.Date

/**
 * Programme information (ETSI TS 103 770 V1.2.1 clause 6.6) of an event in a bottom sheet: the
 * image, title, times, genre, minimum age (clause 6.10.15) and synopsis.
 */
object ProgrammeSheet {

    /**
     * Shows [info] for [event] of [service]. [fallback] says the programme information request
     * failed and what the schedule carried is shown.
     */
    fun show(activity: Activity, service: Service, event: GuideEvent, info: ProgrammeInfo?, country: String?, fallback: Boolean): BottomSheetDialog {
        val view = LayoutInflater.from(activity).inflate(R.layout.sheet_programme, null)
        val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        val now = System.currentTimeMillis()
        val minutes = ((event.end - event.start) / 60_000L).toInt()

        view.findViewById<ImageView>(R.id.programmeImage).also { img ->
            val url = info?.image
            if (url != null) {
                img.visibility = View.VISIBLE
                img.load(url) { crossfade(true); error(R.drawable.bg_poster_placeholder) }
            }
        }
        view.findViewById<TextView>(R.id.programmeTitle).text = info?.title?.ifEmpty { null } ?: event.title
        view.findViewById<TextView>(R.id.programmeTime).text =
            "${fmt.format(Date(event.start))} to ${fmt.format(Date(event.end))} · " + activity.getString(R.string.minutes, minutes)
        val age = info?.let { ServiceListRules.minimumAgeFor(it.ratings, country) }
        view.findViewById<TextView>(R.id.programmeMeta).also {
            it.text = listOfNotNull(info?.genre?.let { g -> "Genre: $g" }, age?.let { a -> "Minimum age: $a" }).joinToString(" · ")
            it.visibility = if (it.text.isEmpty()) View.GONE else View.VISIBLE
        }
        val synopsis = info?.synopsis ?: ""
        view.findViewById<TextView>(R.id.programmeSynopsis).text = synopsis
        view.findViewById<View>(R.id.programmeSynopsisHeading).visibility = if (synopsis.isEmpty()) View.GONE else View.VISIBLE
        view.findViewById<TextView>(R.id.programmeNote).also {
            if (fallback) {
                it.visibility = View.VISIBLE
                it.text = activity.getString(R.string.programme_fallback)
            }
        }

        // LIVE: the event on now of a linear service.
        view.findViewById<View>(R.id.programmeLive).visibility =
            if (event.start <= now && event.end > now && ServiceTypes.isLinear(service.serviceType)) View.VISIBLE else View.GONE
        val badges = ArrayList<Badge>()
        age?.takeIf { it > 0 }?.let { badges += Badge(Badge.Kind.PROGRAMME_AGE, null, "$it+", R.color.badge_age, "Minimum age rating of the programme, from the content guide: $it") }
        ServiceBadges.bind(view.findViewById<ChipGroup>(R.id.programmeBadges), badges)

        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(view)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.show()
        return dialog
    }
}
