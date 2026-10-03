/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fivegmag.dvbiclient.guide.GuideEvent
import com.fivegmag.dvbiclient.http.ServiceListFetch
import com.fivegmag.dvbiclient.ui.ProgrammeSheet
import com.fivegmag.dvbiclient.ui.ScheduleAdapter
import com.fivegmag.dvbiclient.ui.ServiceTypes
import com.google.android.material.appbar.MaterialToolbar

/**
 * The schedule of one service (ETSI TS 103 770 V1.2.1 clause 6.5.2), from an hour ago to twelve
 * hours ahead, grouped by day with the programme on now highlighted, and the programme
 * information of a selected event (clause 6.6).
 */
class ScheduleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule)
        val toolbar = findViewById<MaterialToolbar>(R.id.scheduleToolbar)
        toolbar.setNavigationOnClickListener { finish() }
        val status = findViewById<TextView>(R.id.scheduleStatus)
        val loading = findViewById<View>(R.id.scheduleLoading)
        val listView = findViewById<RecyclerView>(R.id.scheduleList)
        listView.layoutManager = LinearLayoutManager(this)
        val service = DvbiSession.serviceList?.services?.firstOrNull { it.uid == intent.getStringExtra(EXTRA_SERVICE_UID) }
        if (service == null) {
            loading.visibility = View.GONE
            status.text = getString(R.string.service_not_found)
            return
        }
        title = "${service.name}: ${getString(R.string.schedule)}"
        toolbar.title = service.name
        toolbar.subtitle = getString(R.string.schedule)
        status.text = getString(R.string.loading)
        val now = System.currentTimeMillis()
        DvbiRepository.background({ DvbiRepository.guide.schedule(service, now - HOUR_MS, now + 12 * HOUR_MS) }) { r ->
            loading.visibility = View.GONE
            val evs = r.value
            if (evs == null) {
                status.text = "No schedule: " + (r.error ?: r.http?.let { ServiceListFetch.describe(it) } ?: "no content guide source")
                return@background
            }
            status.text = if (evs.isEmpty()) "No events in the schedule." else "${evs.size} events"
            val adapter = ScheduleAdapter(evs, ServiceTypes.isLinear(service.serviceType), System.currentTimeMillis()) { e ->
                showProgramme(service, e)
            }
            listView.adapter = adapter
            adapter.positionOfNow()?.let { (listView.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(it, 0) }
        }
    }

    // Clause 6.6.2, <ProgramInfoEndpoint>?pid=<program_id>; what the schedule carried when the
    // service has no ProgramInfoEndpoint or the request fails.
    private fun showProgramme(service: com.fivegmag.dvbiclient.servicelist.Service, e: GuideEvent) {
        DvbiRepository.background({ DvbiRepository.guide.programme(service, e.crid) }) { r ->
            val info = r.value ?: e.info
            val fallback = r.value == null && service.guide?.program != null
            ProgrammeSheet.show(this, service, e, info, Settings(this).country.ifEmpty { null }, fallback)
        }
    }

    companion object {
        private const val HOUR_MS = 3_600_000L
        const val EXTRA_SERVICE_UID = "serviceUid"

        fun intent(context: Context, uid: String): Intent = Intent(context, ScheduleActivity::class.java).putExtra(EXTRA_SERVICE_UID, uid)
    }
}
