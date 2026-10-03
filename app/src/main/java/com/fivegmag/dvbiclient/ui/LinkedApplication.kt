/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.ui

import com.fivegmag.dvbiclient.AppActivity
import com.fivegmag.dvbiclient.DvbiRepository
import com.fivegmag.dvbiclient.ait.XmlAit
import com.fivegmag.dvbiclient.servicelist.LinkedApp
import com.fivegmag.dvbiclient.servicelist.LinkedApps
import com.fivegmag.dvbiclient.servicelist.Service
import com.fivegmag.dvbiclient.servicelist.ServiceInstance

/**
 * The HTML page a linked application leads to (ETSI TS 103 770 V1.2.1 clause 5.2.3.1, table 7): the
 * page itself, or for an XML AIT the application clause 5.2.4.2 selects. As the browser client's
 * resolveLinkedApp (rt-dvb-i-application public/app.js).
 */
object LinkedApplication {

    /**
     * Blocking: the URL to open for an application, or null when there is no application this
     * client can start. The launch location of [term] launched from [view] (clause 5.2.3.1) goes on
     * the application URL, "which may refer to either an HTML page or an XML AIT" (ETSI TS 102 796
     * V1.8.1 clause 6.2.2.6.2): on the page itself, or on the XML AIT request with the other
     * contextual parameters of clause 5.2.4.4.6.
     */
    fun resolve(url: String, contentType: String, term: String, view: LinkedApps.LaunchView, regions: List<String>): String? {
        val type = contentType.trim().lowercase()
        val lloc = LinkedApps.launchLocation(term, view)
        if (type != XmlAit.CONTENT_TYPE) {
            return url.takeIf { LinkedApps.startableType(type) && AppActivity.isWebUrl(it) }?.let { LinkedApps.pageUrl(it, term, view) }
        }
        val apps = DvbiRepository.guide.ait(url, regions, lloc).value ?: return null
        return XmlAit.select(apps)?.url?.takeIf { AppActivity.isWebUrl(it) }
    }

    /**
     * The application the player offers: "App with media in parallel" of the playing instance (the
     * fallback instance's own after a fallback, clause 5.2.3.2), else the service's home page (3);
     * with no instance playing, the application for outside the availability period (2).
     */
    fun offered(service: Service, playing: ServiceInstance?): LinkedApp? {
        val apps = playing?.linkedApps ?: emptyList()
        return apps.firstOrNull { it.term == LinkedApps.WITH_MEDIA && it.startable }
            ?: service.linkedApps.firstOrNull { it.term == LinkedApps.HOME_PAGE && it.startable }
            ?: if (playing == null) (service.instances.flatMap { it.linkedApps } + service.linkedApps)
                .firstOrNull { it.term == LinkedApps.OUTSIDE_AVAILABILITY && it.startable } else null
    }
}
