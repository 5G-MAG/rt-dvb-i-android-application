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
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.fivegmag.dvbiclient.guide.GuideParser
import com.fivegmag.dvbiclient.guide.GuideRequests
import com.fivegmag.dvbiclient.guide.ResultGroup
import com.fivegmag.dvbiclient.guide.ResultItem
import com.fivegmag.dvbiclient.guide.Results
import com.fivegmag.dvbiclient.http.ServiceListFetch
import com.fivegmag.dvbiclient.servicelist.Service
import com.fivegmag.dvbiclient.ui.OnDemand
import com.google.android.material.appbar.MaterialToolbar

/**
 * More Episodes (ETSI TS 103 770 V1.2.1 clause 6.7) and Box Sets (clause 6.8): Box Set Categories,
 * their Box Set Lists and a Box Set's contents, one page at a time (clause 6.9), as the browser
 * client's browse panel (rt-dvb-i-application public/app.js showBrowse, loadBrowsePage). A
 * programme with an on-demand entry that can be offered carries the ON-DEMAND badge and starts its
 * player.
 */
class BrowseActivity : AppCompatActivity() {

    enum class Kind { PROGRAMMES, CATEGORIES, LISTS }

    private lateinit var service: Service
    private lateinit var kind: Kind
    private lateinit var settings: Settings
    private lateinit var list: LinearLayout
    private lateinit var more: Button
    private lateinit var status: TextView
    private lateinit var loading: View
    private var next: String? = null
    private var shown = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browse)
        val toolbar = findViewById<MaterialToolbar>(R.id.browseToolbar)
        toolbar.setNavigationOnClickListener { finish() }
        list = findViewById(R.id.browseList)
        more = findViewById(R.id.browseMore)
        status = findViewById(R.id.browseStatus)
        loading = findViewById(R.id.browseLoading)
        settings = Settings(this)
        val s = DvbiSession.serviceList?.services?.firstOrNull { it.uid == intent.getStringExtra(EXTRA_SERVICE_UID) }
        val url = intent.getStringExtra(EXTRA_URL)
        if (s == null || url == null) {
            showStatus(getString(R.string.service_not_found))
            return
        }
        service = s
        kind = Kind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: Kind.PROGRAMMES.name)
        toolbar.title = intent.getStringExtra(EXTRA_TITLE)
        toolbar.subtitle = service.name
        more.setOnClickListener { next?.let { load(it) } }
        load(url)
    }

    private fun showStatus(text: String) {
        loading.visibility = View.GONE
        status.text = text
        status.visibility = View.VISIBLE
    }

    // One page of results. "The DVB-I client shall only request pages of results from a Content
    // Guide Server as required for display to the user." (clause 6.9): on request, or at once when a
    // page leaves nothing to show.
    private fun load(url: String) {
        loading.visibility = View.VISIBLE
        more.isEnabled = false
        val regions = OnDemand.regions(settings)
        DvbiRepository.background({
            val r = DvbiRepository.guide.results(url)
            val results = r.value
            // Decided off the main thread, since each may request a Template XML AIT.
            val offered = results?.items?.associate { it.programId to (it.onDemand?.let { od ->
                DvbiRepository.guide.onDemandOffered(od, regions, System.currentTimeMillis()) } ?: false) } ?: emptyMap()
            val compatible = results?.groups?.associate { it.groupId to (it.templateAit?.let { t ->
                DvbiRepository.guide.templateCompatible(t, regions, System.currentTimeMillis()) } ?: true) } ?: emptyMap()
            Triple(r, offered, compatible)
        }) { (r, offered, compatible) ->
            loading.visibility = View.GONE
            more.isEnabled = true
            val results = r.value
            if (results == null) {
                if (shown == 0) showStatus(getString(R.string.not_available) + (r.http?.let { ": " + ServiceListFetch.describe(it) } ?: ""))
                more.visibility = View.GONE
                return@background
            }
            show(results, offered, compatible)
        }
    }

    private fun show(results: Results, offered: Map<String, Boolean>, compatible: Map<String, Boolean>) {
        var count = 0
        if (kind == Kind.PROGRAMMES) {
            for (item in GuideParser.orderResults(results.items)) {
                // "In the event that this process indicates incompatibility between the content and
                // the DVB-I client or the XML AIT request fails, the result shall be hidden from the
                // user." (clause 6.7.3)
                if (item.onDemand != null && offered[item.programId] != true) continue
                addProgramme(item)
                count++
            }
        } else {
            // Groups with a title; the results group of a page carries none. "Every Box Set in a Box
            // Set List response shall have an associated Template XML AIT" (clause 5.2.4.4.4).
            for (g in results.groups.filter { it.groupId.isNotEmpty() && it.title.isNotEmpty() }) {
                if (kind == Kind.LISTS && compatible[g.groupId] != true) continue
                addGroup(g)
                count++
            }
        }
        shown += count
        next = results.links["next"]
        more.visibility = if (next != null) View.VISIBLE else View.GONE
        if (count == 0 && next != null && shown == 0) load(next!!)
        else if (shown == 0 && next == null) showStatus(getString(R.string.nothing_to_show))
    }

    private fun row(title: String, subtitle: String, image: String?, onDemand: Boolean, chevron: Boolean, onClick: (() -> Unit)?) {
        val v = LayoutInflater.from(this).inflate(R.layout.item_browse, list, false)
        v.findViewById<TextView>(R.id.browseTitle).text = title
        v.findViewById<TextView>(R.id.browseSubtitle).also {
            it.text = subtitle
            it.visibility = if (subtitle.isEmpty()) View.GONE else View.VISIBLE
        }
        v.findViewById<ImageView>(R.id.browseImage).also { img ->
            if (image != null) img.load(image) { crossfade(true); error(R.drawable.bg_poster_placeholder) } else img.setImageResource(R.drawable.ic_video_library)
        }
        v.findViewById<View>(R.id.browseBadge).visibility = if (onDemand) View.VISIBLE else View.GONE
        v.findViewById<View>(R.id.browseChevron).visibility = if (chevron) View.VISIBLE else View.GONE
        v.contentDescription = listOfNotNull(title, subtitle.ifEmpty { null }, if (onDemand) getString(R.string.badge_on_demand) else null).joinToString(", ")
        if (onClick != null) v.setOnClickListener { onClick() } else v.isClickable = false
        list.addView(v)
    }

    private fun addProgramme(item: ResultItem) {
        val od = item.onDemand
        row(item.title, item.subtitle.ifEmpty { item.synopsis }, item.image, od != null, false,
            od?.let { { OnDemand.launch(this, it, item.ratings) } })
    }

    private fun addGroup(g: ResultGroup) {
        val endpoint = service.guide?.group ?: return
        val regions = OnDemand.regions(settings)
        row(g.title, "", g.image, false, true) {
            startActivity(
                if (kind == Kind.CATEGORIES) intent(this, service, Kind.LISTS, g.title, GuideRequests.boxSetListsUrl(endpoint, g.groupId, listOf(service.guideSid), regions))
                else intent(this, service, Kind.PROGRAMMES, g.title, GuideRequests.boxSetContentsUrl(endpoint, g.groupId, regions))
            )
        }
    }

    companion object {
        private const val EXTRA_SERVICE_UID = "serviceUid"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_URL = "url"

        fun intent(context: Context, service: Service, kind: Kind, title: String, url: String): Intent =
            Intent(context, BrowseActivity::class.java)
                .putExtra(EXTRA_SERVICE_UID, service.uid)
                .putExtra(EXTRA_KIND, kind.name)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_URL, url)

        /** More Episodes of [pid] (clause 6.7.2), or null when the service's source has no endpoint. */
        fun moreEpisodes(context: Context, service: Service, pid: String): Intent? {
            val endpoint = service.guide?.moreEpisodes ?: return null
            return intent(context, service, Kind.PROGRAMMES, context.getString(R.string.more_episodes),
                GuideRequests.moreEpisodesUrl(endpoint, pid, OnDemand.regions(Settings(context))))
        }

        /** Box Set Categories of the service (clause 6.8.2.2), or null when its source has no GroupInfoEndpoint. */
        fun boxSets(context: Context, service: Service): Intent? {
            val endpoint = service.guide?.group ?: return null
            return intent(context, service, Kind.CATEGORIES, context.getString(R.string.box_set_categories),
                GuideRequests.boxSetCategoriesUrl(endpoint, listOf(service.guideSid), OnDemand.regions(Settings(context))))
        }
    }
}
