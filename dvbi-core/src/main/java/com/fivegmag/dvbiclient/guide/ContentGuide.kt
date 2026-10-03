/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.guide

import com.fivegmag.dvbiclient.ait.AitApplication
import com.fivegmag.dvbiclient.ait.XmlAit
import com.fivegmag.dvbiclient.http.DvbiHttpClient
import com.fivegmag.dvbiclient.http.HttpResult
import com.fivegmag.dvbiclient.servicelist.Service
import com.fivegmag.dvbiclient.xml.XmlFormatException

/**
 * Content guide requests for the services of a service list, through the [DvbiHttpClient], which
 * applies the caching and retry rules of ETSI TS 103 770 V1.2.1 clause 4.3 that clauses 6.2.3 and
 * 6.2.4 refer to. Blocking; call it off the main thread.
 *
 * Clause 4.3.3.4: "If a 404 (Not Found) HTTP response code is received on a request to any API URL
 * listed in the ContentGuideSource object (see clause 5.5.7) then the client shall re-acquire the
 * Service List (see clause 5.5.1), in order to re-acquire the ContentGuideSource." A 404 sets
 * [Result.reacquireServiceList] the first time; a 404 again after that applies the back-off.
 */
class ContentGuide(private val http: DvbiHttpClient) {

    /** [events] or [programme] when the request succeeded; [http] is the answer of the failing or last request. */
    data class Result<T>(val value: T?, val http: HttpResult?, val reacquireServiceList: Boolean = false, val error: String? = null)

    private val reacquired = HashSet<String>()

    /** Now and next, clause 6.5.3.1, with now_next=true or window. */
    fun nowNext(service: Service, windowType: String = "true"): Result<List<GuideEvent>> {
        val g = service.guide ?: return Result(null, null)
        return events(GuideRequests.nowNextUrl(g.schedule, service.guideSid, windowType), "${g.schedule}|${service.guideSid}")
    }

    /** The schedule for [fromMs, toMs] in the windows of clause 6.5.2.1, combined (clause 6.5.2.2). */
    fun schedule(service: Service, fromMs: Long, toMs: Long): Result<List<GuideEvent>> {
        val g = service.guide ?: return Result(null, null)
        val all = ArrayList<GuideEvent>()
        var last: Result<List<GuideEvent>> = Result(null, null)
        for (win in GuideRequests.scheduleWindows(fromMs, toMs)) {
            last = events(GuideRequests.scheduleUrl(g.schedule, service.guideSid, win), "${g.schedule}|${service.guideSid}")
            val evs = last.value ?: return last
            all.addAll(evs)
        }
        val seen = HashSet<Long>()
        return last.copy(value = all.sortedBy { it.start }.filter { seen.add(it.start) })
    }

    /** Programme information by CRID, clause 6.6.2. */
    fun programme(service: Service, pid: String): Result<ProgrammeInfo> {
        val endpoint = service.guide?.program ?: return Result(null, null)
        val url = GuideRequests.programUrl(endpoint, pid)
        val r = http.get(url)
        val reacquire = outcome(r, url, "$endpoint|$pid")
        if (!r.ok) return Result(null, r, reacquire)
        return try {
            Result(GuideParser.parseProgramme(r.body, pid), r)
        } catch (e: XmlFormatException) {
            Result(null, r, error = e.message)
        }
    }

    /**
     * A page of More Episodes (clause 6.7) or Box Set (clause 6.8) results from [url], a URL built by
     * [GuideRequests] or a pagination link, which "A DVB-I client shall only use ... without
     * modification" (clause 6.9).
     */
    fun results(url: String): Result<Results> {
        val r = http.get(url)
        val reacquire = outcome(r, url, url)
        if (!r.ok) return Result(null, r, reacquire)
        return try {
            Result(GuideParser.parseResults(r.body), r)
        } catch (e: XmlFormatException) {
            Result(null, r, error = e.message)
        }
    }

    /**
     * The applications of the XML AIT at [url], requested with the contextual parameters of clause
     * 5.2.4.4.6 ([regions], [launchLocation]).
     */
    fun ait(url: String, regions: List<String>, launchLocation: String): Result<List<AitApplication>> {
        val r = http.get(GuideRequests.aitUrl(url, regions, launchLocation))
        if (!r.ok) return Result(null, r)
        return try {
            Result(XmlAit.parse(r.body), r)
        } catch (e: XmlFormatException) {
            Result(null, r, error = e.message)
        }
    }

    private val templates = HashMap<String, Pair<Boolean, Long>>()

    /**
     * Whether the Template XML AIT at [url] lists an application this client can run: "the client
     * device shall assume it can run the application if any of the applications listed meet the
     * compatibility criteria" (clause 5.2.4.4.1), those of clause 5.2.4.2, the applicationLocation
     * not being used. Results are
     * kept by URL ("Client devices shall perform a textual comparison of the Template XML AIT URL")
     * until the expiry of clause 5.2.4.4.5; a result past its expiry is still used while a new one
     * cannot be had.
     */
    fun templateCompatible(url: String, regions: List<String>, nowMs: Long): Boolean {
        val known = synchronized(templates) { templates[url] }
        if (known != null && known.second > nowMs) return known.first
        val r = ait(url, regions, LAUNCH_LOCATION_EPG)
        val apps = r.value ?: return known?.first ?: false
        val ok = apps.any { XmlAit.compatible(it) }
        synchronized(templates) { templates[url] = ok to XmlAit.templateExpiry(nowMs, r.http?.maxAgeMs, r.http?.expires) }
        return ok
    }

    /**
     * Whether an on-demand programme may be offered: "A DVB-I client shall determine both content
     * availability and its capability of playing the content before an item of content is indicated
     * as available to the user." (clause 5.2.4.1): inside its availability window (table 52), with a
     * content deep-linked XML AIT, and playable by its Template XML AIT when it has one.
     */
    fun onDemandOffered(od: OnDemandProgram, regions: List<String>, nowMs: Long): Boolean {
        if (!od.availableAt(nowMs)) return false
        if (od.programUrlType.trim().lowercase() != XmlAit.CONTENT_TYPE) return false
        return od.auxiliaryUrl?.let { templateCompatible(it, regions, nowMs) } ?: true
    }

    /**
     * The URL of the on-demand player for [od]: the application clause 5.2.4.2 selects from its
     * content deep-linked XML AIT, or null: "If the content deep-linked XML AIT is unavailable the
     * client device shall consider the content to be unavailable" (clause 5.2.4.3).
     */
    fun onDemandPlayer(od: OnDemandProgram, regions: List<String>): String? =
        ait(od.programUrl, regions, LAUNCH_LOCATION_EPG).value?.let { XmlAit.select(it) }?.url

    private fun events(url: String, key: String): Result<List<GuideEvent>> {
        val r = http.get(url)
        val reacquire = outcome(r, url, key)
        if (!r.ok) return Result(null, r, reacquire)
        return try {
            Result(GuideParser.parseSchedule(r.body), r)
        } catch (e: XmlFormatException) {
            Result(null, r, error = e.message)
        }
    }

    // Returns whether the service list is to be re-acquired.
    @Synchronized
    private fun outcome(r: HttpResult, url: String, key: String): Boolean {
        if (r.ok) {
            reacquired.remove(key)
            return false
        }
        if (r.status != 404 || r.skipped) return false
        if (key in reacquired) {
            // "If a 404 (Not found) HTTP response is still received after re-acquiring the Service
            // List the receiver shall use the back-off timing model described in clause 4.3.3.7."
            http.backOff(url)
            return false
        }
        reacquired.add(key)
        return true
    }

    companion object {
        /** The lloc value for a launch from the content guide (clause 5.2.4.4.6). */
        const val LAUNCH_LOCATION_EPG = "epg"
    }
}
