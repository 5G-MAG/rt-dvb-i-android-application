/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.guide

import com.fivegmag.dvbiclient.Fixtures
import com.fivegmag.dvbiclient.http.DvbiHttpClient
import com.fivegmag.dvbiclient.http.HttpResponse
import com.fivegmag.dvbiclient.http.HttpTransport
import com.fivegmag.dvbiclient.servicelist.ServiceListParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Requests made for a service of the fixture list, through the clause 4.3 client. */
class ContentGuideTest {

    private val list = ServiceListParser.parse(Fixtures.read("service-list.xml"))
    private val alpha = list.services.first { it.uid.endsWith(":a") }
    private val gamma = list.services.first { it.uid.endsWith(":c") }

    private class Recording(private val answer: (String) -> HttpResponse) : HttpTransport {
        val urls = ArrayList<String>()
        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            urls.add(url)
            return answer(url)
        }
    }

    @Test
    fun nowNextAndProgrammeRequestsUseTheServicesSourceAndSid() {
        val t = Recording { url ->
            if (url.contains("now_next")) HttpResponse(200, emptyMap(), Fixtures.read("now-next.xml"))
            else HttpResponse(200, emptyMap(), Fixtures.read("schedule.xml"))
        }
        val g = ContentGuide(DvbiHttpClient(t))
        assertEquals(2, g.nowNext(alpha).value?.size)
        assertEquals("clause 6.5.3.1 with ContentGuideServiceRef as sid", "https://example.com/epg/schedule?sid=sample-epg&now_next=true", t.urls[0])
        val p = g.programme(alpha, "crid://example.com/prog/1")
        assertEquals("Morning News", p.value?.title)
        assertEquals("https://example.com/epg/nownext?pid=crid%3A%2F%2Fexample.com%2Fprog%2F1", t.urls[1])
        assertNull("no content guide source: no request", g.nowNext(gamma).value)
        assertEquals(2, t.urls.size)
    }

    @Test
    fun clause6_5_2ScheduleCombinesTheWindows() {
        val t = Recording { HttpResponse(200, emptyMap(), Fixtures.read("schedule.xml")) }
        val from = Instant.parse("2026-10-02T11:00:00Z").toEpochMilli()
        // 11:00 UTC falls in the window from 09:00; 23:00 needs the next one, from 21:00.
        val r = ContentGuide(DvbiHttpClient(t)).schedule(alpha, from, from + 12 * 3_600_000)
        assertEquals(listOf(
            "https://example.com/epg/schedule?start=1790931600&end=1790974800&sid=sample-epg",
            "https://example.com/epg/schedule?start=1790974800&end=1791018000&sid=sample-epg",
        ), t.urls)
        assertEquals("the same events in both answers are kept once", 2, r.value?.size)
    }

    @Test
    fun clause4_3_3_4A404ReacquiresTheServiceListThenBacksOff() {
        var now = 1_000_000L
        val t = Recording { HttpResponse(404, emptyMap(), "") }
        val http = DvbiHttpClient(t, now = { now }, random = { 0.0 })
        val g = ContentGuide(http)
        assertTrue("first 404: re-acquire the service list", g.nowNext(alpha).reacquireServiceList)
        val again = g.nowNext(alpha)
        assertFalse("still 404 after re-acquiring: no second re-acquisition", again.reacquireServiceList)
        val url = "https://example.com/epg/schedule?sid=sample-epg&now_next=true"
        assertEquals("and the back-off applies", now + 100, http.nextAllowed(url))
        assertTrue(g.nowNext(alpha).http!!.skipped)
        now += 100
        g.nowNext(alpha)
        assertEquals(3, t.urls.size)
    }

    @Test
    fun clause5_2_4OnDemandOfferedOnlyWhenAvailableAndTheTemplateAitHasAnApplication() {
        val od = GuideParser.parseResults(Fixtures.read("more-episodes.xml")).items.first { it.onDemand != null }.onDemand!!
        val inWindow = Instant.parse("2026-10-02T10:00:00Z").toEpochMilli()
        var template = Fixtures.read("xml-ait.xml")
        val t = Recording { url ->
            when {
                url.startsWith("http://192.168.1.202:4100/ait/template.aitx") -> HttpResponse(200, emptyMap(), template)
                url.startsWith("http://192.168.1.202:4100/ait/ep5.aitx") -> HttpResponse(200, emptyMap(), Fixtures.read("xml-ait.xml"))
                else -> HttpResponse(404, emptyMap(), "")
            }
        }
        val g = ContentGuide(DvbiHttpClient(t))
        assertTrue(g.onDemandOffered(od, listOf("R1"), inWindow))
        assertEquals("clause 5.2.4.4.6 contextual parameters", "http://192.168.1.202:4100/ait/template.aitx?regionID%5B%5D=R1&lloc=epg", t.urls[0])
        assertFalse("table 52: outside the availability window", g.onDemandOffered(od, emptyList(), Instant.parse("2027-01-01T00:00:00Z").toEpochMilli()))
        assertEquals("clause 5.2.4.3: the player of the deep-linked XML AIT",
            "http://192.168.1.202:4100/player.html?pid=5", g.onDemandPlayer(od, emptyList()))

        // A Template XML AIT without an application this client can run hides the item.
        template = Fixtures.read("xml-ait.xml").replace("text/html", "application/vnd.dvbi.non")
        val g2 = ContentGuide(DvbiHttpClient(t))
        assertFalse(g2.onDemandOffered(od, emptyList(), inWindow))
    }

    @Test
    fun clause6_9ResultsPagesAreRequestedAsGiven() {
        val t = Recording { HttpResponse(200, emptyMap(), Fixtures.read("more-episodes.xml")) }
        val g = ContentGuide(DvbiHttpClient(t))
        val next = g.results("http://192.168.1.202:4100/more?pid=p").value!!.links.getValue("next")
        g.results(next)
        assertEquals("the link is used without modification", "http://192.168.1.202:4100/more?page=3", t.urls[1])
    }
}
