/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.ait

import com.fivegmag.dvbiclient.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class XmlAitTest {

    private val apps = XmlAit.parse(Fixtures.read("xml-ait.xml"))

    @Test
    fun clause5_2_4_3UrlBaseFollowedByApplicationLocation() {
        assertEquals(8, apps.size)
        assertEquals(AitApplication("text/html", 3, "http://192.168.1.202:4100/player.html?pid=5", MhpVersion(0, 1, 8, 1)), apps[2])
        assertEquals("no URLBase", "none", apps[3].url)
    }

    @Test
    fun clause5_2_4_2HighestPriorityOfAStartableType() {
        assertEquals("the HbbTV application, application/vnd.dvbi.non and the platform profiles not launched are passed over",
            "http://192.168.1.202:4100/player.html?pid=5", XmlAit.select(apps)?.url)
        assertNull(XmlAit.select(apps.filter { it.type != "text/html" }))
        assertNull(XmlAit.select(emptyList()))
    }

    @Test
    fun clause5_2_4_2PriorityIsHexadecimal() {
        val doc = Fixtures.read("xml-ait.xml")
            .replace("<mhp:priority>1</mhp:priority>", "<mhp:priority>0a</mhp:priority>")
            .replace("<mhp:priority>3</mhp:priority>", "<mhp:priority>9</mhp:priority>")
        val parsed = XmlAit.parse(doc)
        assertEquals("0a is ten", 10, parsed[1].priority)
        assertEquals("the highest mhp:priority value: 0a above 9", "http://192.168.1.202:4100/low.html", XmlAit.select(parsed)?.url)
        assertEquals("10 is sixteen", 16, XmlAit.parse(doc.replace("<mhp:priority>0a</mhp:priority>", "<mhp:priority>10</mhp:priority>"))[1].priority)
        assertEquals("FF", 255, XmlAit.parse(doc.replace("<mhp:priority>0a</mhp:priority>", "<mhp:priority>FF</mhp:priority>"))[1].priority)
    }

    @Test
    fun clause5_2_4_2PlatformProfileOfTs102796Table5() {
        assertEquals("hexadecimal fields, leading zeros allowed", MhpVersion(0, 1, 1, 1), apps[1].mhpVersion)
        assertEquals("versionMinor 0a is ten", MhpVersion(0, 1, 10, 1), apps[7].mhpVersion)
        assertNull("no mhp:mhpVersion", apps[4].mhpVersion)
        val html = AitApplication("text/html", 1, "https://a.example/")
        for ((major, minor) in (1..8).map { 1 to it }) {
            assertTrue("[1.$minor.1] is launched", XmlAit.compatible(html.copy(mhpVersion = MhpVersion(0, major, minor, 1))))
        }
        for (v in listOf(MhpVersion(0, 1, 2, 0), MhpVersion(0, 1, 9, 1), MhpVersion(0, 2, 0, 0), MhpVersion(0, 1, 0, 0), MhpVersion(0, 1, 8, 2))) {
            assertFalse("other values are ignored: $v", XmlAit.compatible(html.copy(mhpVersion = v)))
        }
        for (profile in listOf(0x0001, 0x0002, 0x0003, 0x2000)) {
            assertFalse("profile $profile needs a feature this client does not have", XmlAit.compatible(html.copy(mhpVersion = MhpVersion(profile, 1, 8, 1))))
        }
        assertFalse("no platform profile signalled", XmlAit.compatible(html))
        assertFalse("a type this client cannot start", XmlAit.compatible(html.copy(type = "application/vnd.hbbtv.xhtml+xml", mhpVersion = MhpVersion(0, 1, 8, 1))))
        val bad = XmlAit.parse(Fixtures.read("xml-ait.xml").replace("<mhp:versionMajor>01</mhp:versionMajor>", "<mhp:versionMajor>x1</mhp:versionMajor>"))
        assertNull("a field that is not hexadecimal", bad[1].mhpVersion)
        val wide = XmlAit.parse(Fixtures.read("xml-ait.xml").replace("<mhp:versionMajor>01</mhp:versionMajor>", "<mhp:versionMajor>001</mhp:versionMajor>"))
        assertNull("versionMajor is ipi:Hexadecimal8bit, at most two digits", wide[1].mhpVersion)
    }

    @Test
    fun clause5_2_4_4_5TemplateExpiryFromMaxAgeElseExpiresElse24Hours() {
        val now = Instant.parse("2026-10-02T10:00:00Z").toEpochMilli()
        assertEquals("max-age wins over Expires", now + 60_000, XmlAit.templateExpiry(now, 60_000, "Fri, 02 Oct 2026 12:00:00 GMT"))
        assertEquals(Instant.parse("2026-10-02T12:00:00Z").toEpochMilli(), XmlAit.templateExpiry(now, null, "Fri, 02 Oct 2026 12:00:00 GMT"))
        assertEquals(now + 86_400_000, XmlAit.templateExpiry(now, null, null))
        assertEquals("an Expires that is not a date", now + 86_400_000, XmlAit.templateExpiry(now, null, "0"))
    }
}
