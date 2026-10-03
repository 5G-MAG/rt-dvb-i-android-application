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
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class XmlAitTest {

    private val apps = XmlAit.parse(Fixtures.read("xml-ait.xml"))

    @Test
    fun clause5_2_4_3UrlBaseFollowedByApplicationLocation() {
        assertEquals(4, apps.size)
        assertEquals(AitApplication("text/html", 3, "http://192.168.1.202:4100/player.html?pid=5"), apps[2])
        assertEquals("no URLBase", "none", apps[3].url)
    }

    @Test
    fun clause5_2_4_2HighestPriorityOfAStartableType() {
        assertEquals("the HbbTV application and application/vnd.dvbi.non are passed over",
            "http://192.168.1.202:4100/player.html?pid=5", XmlAit.select(apps)?.url)
        assertNull(XmlAit.select(apps.filter { it.type != "text/html" }))
        assertNull(XmlAit.select(emptyList()))
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
