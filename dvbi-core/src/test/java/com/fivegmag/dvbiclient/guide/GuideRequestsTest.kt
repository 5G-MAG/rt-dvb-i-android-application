/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Ported from rt-dvb-i-application test/guide.test.js (the requests this client makes). */
class GuideRequestsTest {

    @Test
    fun clause6_5_2_1WindowsStartOn3HourBoundariesAndSpan12Hours() {
        val now = Instant.parse("2015-06-02T13:20:00Z").toEpochMilli()
        val wins = GuideRequests.scheduleWindows(now - 3_600_000, now + 12 * 3_600_000)
        for (w in wins) {
            assertEquals(0L, w.start % 10_800)
            assertEquals(0L, w.end % 10_800)
            assertEquals(43_200L, w.end - w.start)
        }
        assertEquals("12:00, the boundary at or before 12:20", Instant.parse("2015-06-02T12:00:00Z").epochSecond, wins[0].start)
        assertTrue("the requested period is covered", wins.last().end * 1000 >= now + 12 * 3_600_000)
        assertEquals(2, wins.size)
    }

    @Test
    fun requestUrlsOfClauses6_5_2_2And6_5_3_1And6_6_2() {
        assertEquals("the example URL of clause 6.5.2.2", "https://cg.example/schedule?start=1433246400&end=1433268000&sid=12345",
            GuideRequests.scheduleUrl("https://cg.example/schedule", "12345", GuideRequests.Window(1433246400, 1433268000)))
        assertEquals("the example URL of clause 6.5.3.1", "https://cg.example/schedule?sid=12345&now_next=true",
            GuideRequests.nowNextUrl("https://cg.example/schedule", "12345"))
        assertEquals("https://cg.example/schedule?sid=12345&now_next=window", GuideRequests.nowNextUrl("https://cg.example/schedule", "12345", "window"))
        assertEquals("reserved characters percent-encoded", "https://cg.example/program?pid=crid%3A%2F%2Fchannel7.co.uk%2Fn19alr19",
            GuideRequests.programUrl("https://cg.example/program", "crid://channel7.co.uk/n19alr19"))
        assertEquals("an endpoint with a query gets &", "https://cg.example/s?x=1&sid=a",
            GuideRequests.withQuery("https://cg.example/s?x=1", listOf("sid" to "a")))
    }

    @Test
    fun clauses5_1_3_2And6_2_2AllRfc3986ReservedCharactersArePercentEncoded() {
        val reserved = ":/?#[]@" + "!$&'()*+,;="
        assertEquals("%3A%2F%3F%23%5B%5D%40%21%24%26%27%28%29%2A%2B%2C%3B%3D", GuideRequests.encodeQueryComponent(reserved))
        assertEquals("unreserved characters are left as they are", "Az09-._~", GuideRequests.encodeQueryComponent("Az09-._~"))
        assertEquals("https://cg.example/program?pid=crid%3A%2F%2Fx.example%2Fit%27s%281%29%2A%21",
            GuideRequests.programUrl("https://cg.example/program", "crid://x.example/it's(1)*!"))
        assertEquals("UTF-8 octets", "%C3%A9", GuideRequests.encodeQueryComponent("é"))
        assertEquals("a space as %20", "a%20b", GuideRequests.encodeQueryComponent("a b"))
    }

    @Test
    fun clause6_2_2SquareBracketsOfRepeatedParametersArePercentEncoded() {
        assertEquals("https://cg.example/more?pid=crid%3A%2F%2Fa%2Fb&type=ondemand&regionID%5B%5D=1234&regionID%5B%5D=5678",
            GuideRequests.moreEpisodesUrl("https://cg.example/more", "crid://a/b", listOf("1234", "5678")))
        assertEquals("https://cg.example/group/categories?sid%5B%5D=s1",
            GuideRequests.boxSetCategoriesUrl("https://cg.example/group/", listOf("s1"), emptyList()))
        assertEquals("https://cg.example/group/?groupId=crid%3A%2F%2Fcat%2F1&sid%5B%5D=s1&regionID%5B%5D=r",
            GuideRequests.boxSetListsUrl("https://cg.example/group/", "crid://cat/1", listOf("s1"), listOf("r")))
        assertEquals("https://cg.example/group/contents?groupId=crid%3A%2F%2Fbox%2F1&format=paginated",
            GuideRequests.boxSetContentsUrl("https://cg.example/group/", "crid://box/1", emptyList()))
        assertEquals("https://cg.example/more?pid=p&type=ondemand&regionID%5B%5D=r%281%29",
            GuideRequests.moreEpisodesUrl("https://cg.example/more", "p", listOf("r(1)")))
    }

    @Test
    fun clause5_2_4_4_6ContextualParametersOnAnXmlAitUrl() {
        assertEquals("https://channel7.co.uk/ait.aitx?pid=b01myjsy&regionID%5B%5D=Piemonte&lloc=epg",
            GuideRequests.aitUrl("https://channel7.co.uk/ait.aitx?pid=b01myjsy", listOf("Piemonte"), "epg"))
        assertEquals("https://channel7.co.uk/ait.aitx?lloc=epg", GuideRequests.aitUrl("https://channel7.co.uk/ait.aitx", emptyList(), "epg"))
        assertEquals("before the first number sign", "https://channel7.co.uk/ait.aitx?pid=1&regionID%5B%5D=R&lloc=epg#x",
            GuideRequests.aitUrl("https://channel7.co.uk/ait.aitx?pid=1#x", listOf("R"), "epg"))
    }

    @Test
    fun ts102796Clause6_2_2_6_2LaunchLocationBeforeTheFirstNumberSign() {
        assertEquals("example 1", "http://www.example.com/hbbtv-application?lloc=playerpage",
            GuideRequests.withLaunchLocation("http://www.example.com/hbbtv-application", "playerpage"))
        assertEquals("example 3", "http://www.example.com/deeplink?cid=is38g7bv&lloc=epg",
            GuideRequests.withLaunchLocation("http://www.example.com/deeplink?cid=is38g7bv", "epg"))
        assertEquals("example 4", "http://www.example.com/hbbtv-application?lloc=playerpage#mode4",
            GuideRequests.withLaunchLocation("http://www.example.com/hbbtv-application#mode4", "playerpage"))
        assertEquals("a ? inside the fragment does not start a query (RFC 3986 clause 3.4)", "https://a.example/p?lloc=other#a?b",
            GuideRequests.withLaunchLocation("https://a.example/p#a?b", "other"))
        assertEquals("the first number sign", "https://a.example/p?q=1&lloc=epg#a#b",
            GuideRequests.withLaunchLocation("https://a.example/p?q=1#a#b", "epg"))
        assertEquals("no launch location, no change", "https://a.example/p#f", GuideRequests.withLaunchLocation("https://a.example/p#f", ""))
        assertEquals("other queries the same way", "https://cg.example/program?pid=p#f", GuideRequests.programUrl("https://cg.example/program#f", "p"))
    }
}
