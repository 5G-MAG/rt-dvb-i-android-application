/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.servicelist

import com.fivegmag.dvbiclient.Fixtures
import com.fivegmag.dvbiclient.xml.XmlFormatException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LinkedAppsTest {

    private val service = ServiceListParser.parse(Fixtures.read("service-list-apps.xml")).services.single()
    private val caps = Instances.Capabilities()

    @Test
    fun clause5_2_3_1LaunchLocationOfEachTermOnAnHtmlPage() {
        val v = LinkedApps.LaunchView.PLAYER
        assertEquals("1.2: service", "https://a.example/app.html?lloc=service", LinkedApps.pageUrl("https://a.example/app.html", LinkedApps.CONTROLLING, v))
        assertEquals("2: availability", "https://a.example/app.html?x=1&lloc=availability#top",
            LinkedApps.pageUrl("https://a.example/app.html?x=1#top", LinkedApps.OUTSIDE_AVAILABILITY, v))
        assertEquals("1.1: the launch location is not used", "https://a.example/app.html", LinkedApps.pageUrl("https://a.example/app.html", LinkedApps.WITH_MEDIA, v))
        assertEquals("3 from a content guide: epg", "epg", LinkedApps.launchLocation(LinkedApps.HOME_PAGE, LinkedApps.LaunchView.CONTENT_GUIDE))
        assertEquals("3 from a list of services: channellist", "channellist", LinkedApps.launchLocation(LinkedApps.HOME_PAGE, LinkedApps.LaunchView.SERVICE_LIST))
        assertEquals("3 from the player, a view table 2a has no term for: other", "https://a.example/home?lloc=other",
            LinkedApps.pageUrl("https://a.example/home", LinkedApps.HOME_PAGE, LinkedApps.LaunchView.PLAYER))
        assertEquals("1.2 wherever it is launched from", "service", LinkedApps.launchLocation(LinkedApps.CONTROLLING, LinkedApps.LaunchView.SERVICE_LIST))
    }

    @Test
    fun clause5_2_3_4InstanceLevelApplicationsOverrideServiceLevelOnesOfTheSameType() {
        assertEquals("1.2", LinkedApps.term(LinkedApps.CS + "1.2"))
        assertNull(LinkedApps.term("urn:other:1.2"))
        val svc = listOf(
            LinkedApp("1.1", "svc-html", "text/html"),
            LinkedApp("1.1", "svc-ait", "application/vnd.dvb.ait+xml"),
            LinkedApp("2", "svc-off", "text/html"),
            LinkedApp("3", "svc-home", "text/html"),
            LinkedApp("1.1", "svc-apk", "application/vnd.android.package-archive"),
        )
        val inst = listOf(LinkedApp("1.2", "inst-html", "text/html"), LinkedApp("3", "inst-home", "text/html"))
        assertEquals("the instance 1.2 replaces the service 1.1 of the same type; other types stay; 3 only at service level; unknown types ignored",
            listOf("inst-html", "svc-ait", "svc-off", "svc-home"), LinkedApps.effective(svc, inst).map { it.url })
    }

    @Test
    fun clause5_2_13AControllingApplicationOfATypeTheClientCannotStartIsKept() {
        val apk = "application/vnd.android.package-archive"
        val apps = LinkedApps.effective(listOf(LinkedApp("1.1", "svc-apk", apk)), listOf(LinkedApp("1.2", "inst-apk", apk), LinkedApp("1.1", "inst-apk-11", apk)))
        assertEquals(listOf(LinkedApp("1.2", "inst-apk", apk, startable = false)), apps)
    }

    @Test
    fun clause5_2_3_2TheStartableControllingApplicationReplacesTheDeliveryParameters() {
        val first = service.instances[0]
        assertEquals("the XML AIT, not the Android package", Delivery.ControllingApplication("http://192.168.1.202:4100/ait/service.aitx", "application/vnd.dvb.ait+xml"), first.delivery)
        assertTrue("without a linked application engine it is discarded", Instances.cannotPlay(first, caps)!!.contains("cannot start"))
        assertNull("with one it is a candidate", Instances.cannotPlay(first, caps.copy(applications = true)))
        assertTrue(Instances.cannotPlay(first.copy(delivery = Delivery.ControllingApplication("x", "application/x-other")), caps.copy(applications = true))!!
            .contains("application/x-other"))
    }

    @Test
    fun serviceAndInstanceApplicationsAndTheContentFinishedImage() {
        assertEquals(listOf("1.1", "3"), service.linkedApps.map { it.term })
        val playlist = service.instances[1]
        assertEquals("the instance 1.1 of text/html replaces the service's; 3 from the service",
            listOf("http://192.168.1.202:4100/app/companion.html", "http://192.168.1.202:4100/app/home.html"), playlist.linkedApps.map { it.url })
        assertEquals("clause 5.2.7.3: the PNG, from the service", Image("http://192.168.1.202:4100/img/finished.png", "image/png"), playlist.contentFinished)
    }

    @Test
    fun clause5_2_7_2PlaylistServerInstancesPlayWhenTheClientSupportsPlaylists() {
        val playlist = service.instances[1]
        assertEquals(Delivery.DashPlaylist("http://192.168.1.202:4100/playlist.xml"), playlist.delivery)
        assertTrue(Instances.cannotPlay(playlist, caps)!!.contains("playlist server"))
        assertNull(Instances.cannotPlay(playlist, caps.copy(playlists = true)))
        assertFalse(Instances.cannotPlay(playlist, caps.copy(playlists = true, dash = false)) == null)
    }

    @Test
    fun clause5_7_1PlaylistEntries() {
        assertEquals(listOf("http://192.168.1.202:3014/clip1/manifest.mpd", "http://192.168.1.202:3014/clip2/manifest.mpd"),
            Playlists.parse(Fixtures.read("playlist.xml")))
        try {
            Playlists.parse(Fixtures.read("schedule.xml"))
            fail("accepted")
        } catch (_: XmlFormatException) {
        }
    }
}
