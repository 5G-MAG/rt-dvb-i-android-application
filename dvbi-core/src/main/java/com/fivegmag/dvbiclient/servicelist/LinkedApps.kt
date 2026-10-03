/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.servicelist

import com.fivegmag.dvbiclient.guide.GuideRequests

/**
 * A linked application (ETSI TS 103 770 V1.2.1 clause 5.2.3.1): the LinkedApplicationCS:2019 term
 * of its HowRelated@href ("1.1", "1.2", "2", "3"), its MediaUri and MediaUri@contentType.
 * [startable] says whether this client can start its type (table 7).
 */
data class LinkedApp(val term: String, val url: String, val contentType: String, val startable: Boolean = LinkedApps.startableType(contentType))

/**
 * Linked application rules of clause 5.2.3. Ported from the browser client (rt-dvb-i-application
 * public/servicelist.js linkedAppTerm, effectiveApps).
 */
object LinkedApps {

    const val CS = "urn:dvb:metadata:cs:LinkedApplicationCS:2019:"

    /** "App with media in parallel" (DVBLinkedApplicationCS-2019, term 1.1). */
    const val WITH_MEDIA = "1.1"

    /** "App controlling media presentation" (term 1.2). */
    const val CONTROLLING = "1.2"

    /** "App for outside availability period" (term 2). */
    const val OUTSIDE_AVAILABILITY = "2"

    /** "A service provider's home page" (term 3). */
    const val HOME_PAGE = "3"

    /**
     * Table 7: application/vnd.dvb.ait+xml, an XML AIT; text/html or application/xhtml+xml, an HTML5
     * webpage. This client starts HTML pages, and an XML AIT that leads to one.
     */
    val STARTABLE_TYPES = setOf("text/html", "application/xhtml+xml", "application/vnd.dvb.ait+xml")

    /**
     * Where a linked application is launched from, with the launch location a home page (term 3)
     * gets there. Clause 5.2.3.1: "if the application is launched from a DVB-I content guide then
     * "epg" shall be used, otherwise if the application is launched from a UI showing a list of
     * DVB-I services (without guide data) then "channellist" shall be used, otherwise the most
     * appropriate value of the "Defined launch location terms" from that clause shall be used."
     */
    enum class LaunchView(val homePageLocation: String) {
        CONTENT_GUIDE("epg"),
        SERVICE_LIST("channellist"),

        /**
         * The player. It is neither a content guide nor a list of services, nor "within the
         * terminal's electronic programme guide", which the miniguide row of ETSI TS 102 796 V1.8.1
         * clause 6.2.2.6.2, table 2a requires, even where it shows the service's now and next. So
         * the row that applies is: "Any view that does not fall within the categories defined
         * above and for which no platform-specific or local term is defined.", other.
         */
        PLAYER("other"),
    }

    /**
     * The launch location of clause 5.2.3.1: "service" for 1.2, "availability" for 2 (both
     * "should"), none for 1.1 ("the launch location is not used"), and for 3 the one of [view].
     */
    fun launchLocation(term: String, view: LaunchView): String = when (term) {
        CONTROLLING -> "service"
        OUTSIDE_AVAILABILITY -> "availability"
        HOME_PAGE -> view.homePageLocation
        else -> ""
    }

    /**
     * The URL to open for a linked application whose MediaUri is an HTML page: the page with the
     * launch location added. ETSI TS 102 796 V1.8.1 clause 6.2.2.6.2: "the application URL (which
     * may refer to either an HTML page or an XML AIT) is modified to add a launch context query
     * parameter of the form "lloc=<launch location>"."
     */
    fun pageUrl(url: String, term: String, view: LaunchView): String =
        GuideRequests.withLaunchLocation(url, launchLocation(term, view))

    fun startableType(contentType: String): Boolean = contentType.trim().lowercase() in STARTABLE_TYPES

    /** The LinkedApplicationCS term of a HowRelated@href, or null when it is not one. */
    fun term(href: String?): String? = href?.trim()?.takeIf { it.startsWith(CS) }?.removePrefix(CS)?.ifEmpty { null }

    /**
     * The applications that apply to a service instance, clause 5.2.3.4: "A RelatedMaterial element
     * within a ServiceInstance element referencing an application with a HowRelated@href attribute
     * set to ...:1.1 or ...:1.2 overrides any RelatedMaterial element in the Service element that has
     * either of those HowRelated@href values and has the same MediaUri@contentType"; an instance's 2
     * overrides a service 2 of the same type; 3 is used only at service level. Applications of a type
     * this client cannot start are left out ("The DVB-I client may ignore any signalled application
     * that has a MediaUri@contentType attribute that they do not understand.", clause 5.2.3.1), except
     * one controlling media presentation, which is kept so that its instance is discarded (clause
     * 5.2.13, NOTE 1 i)) rather than played from the delivery parameters clause 5.2.3.2 has the client
     * ignore.
     */
    fun effective(serviceApps: List<LinkedApp>, instanceApps: List<LinkedApp>): List<LinkedApp> {
        fun kept(a: LinkedApp) = a.startable || a.term == CONTROLLING
        fun live(t: String) = t == WITH_MEDIA || t == CONTROLLING
        val inst = instanceApps.filter { kept(it) && it.term != HOME_PAGE }
        val svc = serviceApps.filter { kept(it) }.filter { s ->
            inst.none { i -> i.contentType == s.contentType && ((live(i.term) && live(s.term)) || (i.term == OUTSIDE_AVAILABILITY && s.term == OUTSIDE_AVAILABILITY)) }
        }
        return inst + svc
    }

    /** The application controlling media presentation among [apps]: one this client can start first. */
    fun controlling(apps: List<LinkedApp>): LinkedApp? {
        val c = apps.filter { it.term == CONTROLLING }
        return c.firstOrNull { it.startable } ?: c.firstOrNull()
    }
}
