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
import com.fivegmag.dvbiclient.xml.child
import com.fivegmag.dvbiclient.xml.descendant
import com.fivegmag.dvbiclient.xml.descendants
import com.fivegmag.dvbiclient.xml.text

/**
 * The mhp:mhpVersion of an application (ETSI TS 102 809 V1.3.1 clause 5.4.4.8): profile,
 * versionMajor, versionMinor and versionMicro, each an ipi:Hexadecimal16bit or ipi:Hexadecimal8bit.
 */
data class MhpVersion(val profile: Int, val major: Int, val minor: Int, val micro: Int)

/**
 * An application of an XML AIT: mhp:OtherApp, mhp:priority, URLBase followed by applicationLocation,
 * and mhp:mhpVersion, null when it is absent or not hexadecimal.
 */
data class AitApplication(val type: String, val priority: Int, val url: String, val mhpVersion: MhpVersion? = null)

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

    /**
     * The platform profiles this client supports. ETSI TS 102 796 V1.8.1 clause 7.2.3.1, table 5,
     * row 5.2.5: "All terminals shall support the basic profile (0x0000) in addition to profiles
     * corresponding to the other features supported by the terminal." This client has neither the
     * A/V content download feature (0x0001) nor the PVR feature (0x0002).
     */
    val SUPPORTED_PROFILES = setOf(0x0000)

    /**
     * Table 5, row 5.2.5: "Additionally terminals shall launch applications signalled with the
     * following values for major, minor and micro - [1.1.1], [1.2.1], [1.3.1], [1.4.1], [1.5.1],
     * [1.6.1], [1.7.1] and [1.8.1] - and run them as defined by the requirements in the present
     * document." TS 103 770 V1.2.1 clause 5.2.4.2 launches these and "shall ignore applications
     * listed with other values", so the list is applied as it stands, not as a minimum.
     */
    val LAUNCHABLE_VERSIONS = listOf(1 to 1, 1 to 2, 1 to 3, 1 to 4, 1 to 5, 1 to 6, 1 to 7, 1 to 8)
        .map { (major, minor) -> Triple(major, minor, 1) }.toSet()

    /** The applications of an XML AIT document. Throws [XmlFormatException] when it is not XML. */
    fun parse(text: String): List<AitApplication> {
        val root = Xml.parse(text).documentElement
        return root.descendants("Application").map { a ->
            AitApplication(
                type = a.descendant("OtherApp")?.text ?: "",
                // mis_xmlait.xsd ApplicationDescriptor: priority is ipi:Hexadecimal8bit.
                priority = a.descendant("priority")?.text?.let { hex(it, 2) } ?: 0,
                // Clause 5.2.4.3: "the concatenation of URLBase and applicationLocation shall form a URL"
                url = (a.descendant("URLBase")?.text ?: "") + (a.descendant("applicationLocation")?.text ?: ""),
                mhpVersion = a.descendant("mhpVersion")?.let { v ->
                    val f = listOf("profile" to 4, "versionMajor" to 2, "versionMinor" to 2, "versionMicro" to 2)
                        .map { (n, digits) -> v.child(n)?.text?.let { hex(it, digits) } }
                    if (f.any { it == null }) null else MhpVersion(f[0]!!, f[1]!!, f[2]!!, f[3]!!)
                },
            )
        }
    }

    // ipi:Hexadecimal8bit is the pattern [0-9a-fA-F]{1,2}, ipi:Hexadecimal16bit [0-9a-fA-F]{1,4}
    // (sdns_v1.4r13.xsd); anything else is null.
    private fun hex(v: String, digits: Int): Int? = if (v.matches(Regex("[0-9a-fA-F]{1,$digits}"))) v.toInt(16) else null

    /**
     * The platform profile criterion of clause 5.2.4.2: "The platform profile value shall be
     * specified in the child elements of the mhp:mhpVersion element. This shall be as defined in
     * clause 7.2.3.1, table 5 of ETSI TS 102 796 [21]. The client shall launch applications signalled
     * with values of version.major, version.minor, and version.micro according to table 5 of ETSI
     * TS 102 796 [21]. The client shall ignore applications listed with other values." An
     * application without an mhp:mhpVersion signals no value and is ignored.
     */
    fun platformSupported(app: AitApplication): Boolean {
        val v = app.mhpVersion ?: return false
        return v.profile in SUPPORTED_PROFILES && Triple(v.major, v.minor, v.micro) in LAUNCHABLE_VERSIONS
    }

    /** Both criteria of clause 5.2.4.2: a type this client starts and a platform profile it supports. */
    fun compatible(app: AitApplication): Boolean =
        app.type.trim().lowercase() in STARTABLE_TYPES && platformSupported(app)

    /**
     * Clause 5.2.4.2: "select the application with the highest mhp:priority value that meets all of
     * the following criteria" ([compatible]). Returns null when none can be started, in which case
     * "the client shall not issue an error to the user but instead shall show a service or content
     * item as unavailable".
     */
    fun select(apps: List<AitApplication>): AitApplication? =
        apps.filter { compatible(it) && it.url.isNotEmpty() }.maxByOrNull { it.priority }

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
