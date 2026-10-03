/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.ait

import com.fivegmag.dvbiclient.xml.Xml
import com.fivegmag.dvbiclient.xml.XmlFormatException
import com.fivegmag.dvbiclient.xml.descendant
import com.fivegmag.dvbiclient.xml.descendants
import com.fivegmag.dvbiclient.xml.text

/** An application of an XML AIT: mhp:OtherApp, mhp:priority, and URLBase followed by applicationLocation. */
data class AitApplication(val type: String, val priority: Int, val url: String)

/**
 * The XML AIT of ETSI TS 103 770 V1.2.1 clause 5.2.4, as this client uses it to start an on-demand
 * player (clause 5.2.4.3) or a linked application. Ported from the browser client
 * (rt-dvb-i-application public/app.js fetchAit and public/servicelist.js selectAitApplication).
 */
object XmlAit {

    /** The MIME type of an XML AIT (table 7; table 52, ProgramURL@contentType). */
    const val CONTENT_TYPE = "application/vnd.dvb.ait+xml"

    /**
     * Clause 5.2.4.2 lists application/vnd.hbbtv.xhtml+xml, text/html and application/xhtml+xml; this
     * client has no HbbTV engine and starts HTML pages only.
     */
    val STARTABLE_TYPES = setOf("text/html", "application/xhtml+xml")

    /** The applications of an XML AIT document. Throws [XmlFormatException] when it is not XML. */
    fun parse(text: String): List<AitApplication> {
        val root = Xml.parse(text).documentElement
        return root.descendants("Application").map { a ->
            AitApplication(
                type = a.descendant("OtherApp")?.text ?: "",
                priority = a.descendant("priority")?.text?.toIntOrNull() ?: 0,
                // Clause 5.2.4.3: "the concatenation of URLBase and applicationLocation shall form a URL"
                url = (a.descendant("URLBase")?.text ?: "") + (a.descendant("applicationLocation")?.text ?: ""),
            )
        }
    }

    /**
     * Clause 5.2.4.2: "select the application with the highest mhp:priority value that meets all of
     * the following criteria". The platform profile criterion refers to table 5 of ETSI TS 102 796,
     * which is not applied (unverified: not held). Returns null when none can be started, in which
     * case "the client shall not issue an error to the user but instead shall show a service or
     * content item as unavailable".
     */
    fun select(apps: List<AitApplication>): AitApplication? =
        apps.filter { it.type.trim().lowercase() in STARTABLE_TYPES && it.url.isNotEmpty() }.maxByOrNull { it.priority }

    /**
     * When a Template XML AIT result may be used until, clause 5.2.4.4.5: "If no Expires or max-age
     * header is provided the client device shall assume an expiry of 24 hours from retrieval. If both
     * an Expires and max-age header are present the client device shall use the Cache-Control:
     * max-age".
     */
    fun templateExpiry(nowMs: Long, maxAgeMs: Long?, expires: String?): Long {
        if (maxAgeMs != null) return nowMs + maxAgeMs
        val exp = expires?.let { runCatching { java.time.ZonedDateTime.parse(it.trim(), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull() }
        return exp ?: (nowMs + 24 * 3_600_000L)
    }
}
