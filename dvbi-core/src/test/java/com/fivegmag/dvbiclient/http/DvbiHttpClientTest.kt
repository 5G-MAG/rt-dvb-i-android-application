/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Ported from rt-dvb-i-application test/dvbi-http.test.js: a fake transport and a fake clock. */
class DvbiHttpClientTest {

    private class Queued(val status: Int = 200, val headers: Map<String, String> = emptyMap(), val body: String = "", val throws: String? = null)

    private class FakeTransport(vararg responses: Queued) : HttpTransport {
        val queue = ArrayDeque(responses.toList())
        val calls = ArrayList<Pair<String, Map<String, String>>>()
        override fun get(url: String, headers: Map<String, String>): HttpResponse {
            calls.add(url to headers)
            val r = queue.removeFirstOrNull() ?: throw AssertionError("no response queued")
            if (r.throws != null) throw IOException(r.throws)
            return HttpResponse(r.status, r.headers, r.body)
        }
    }

    private class Clock(var t: Long = 1_000_000) {
        fun advance(ms: Long) { t += ms }
    }

    @Test
    fun clause4_3_3_7MinwaitAndMaxwaitForEachRetryCappedAtTheTenth() {
        assertEquals(100L to 400L, DvbiHttpClient.backoffRange(1))        // "up to 400ms before the first retry"
        assertEquals(400L to 1600L, DvbiHttpClient.backoffRange(2))       // "up to 1 600 ms before the second"
        assertEquals(26_214_400L to 104_857_600L, DvbiHttpClient.backoffRange(10)) // "104 857 600 ms"
        assertEquals("CurrentRetry is not incremented past 10", DvbiHttpClient.backoffRange(10), DvbiHttpClient.backoffRange(11))
    }

    @Test
    fun clause4_3_3_7TheWaitIsRandomBetweenMinwaitAndMaxwait() {
        assertEquals(1600L, DvbiHttpClient.backoffDelay(3) { 0.0 })
        assertEquals(1600L + 2400L, DvbiHttpClient.backoffDelay(3) { 0.5 })
        assertTrue(DvbiHttpClient.backoffDelay(3) { 0.999999 } < 6400)
    }

    @Test
    fun maxAgeAndRetryAfterAreParsed() {
        assertEquals(3_600_000L, DvbiHttpClient.maxAgeMs("max-age=3600"))
        assertEquals(5000L, DvbiHttpClient.maxAgeMs("public, max-age=5"))
        assertNull(DvbiHttpClient.maxAgeMs("no-cache"))
        assertNull("the quoted form is not generated, and not accepted", DvbiHttpClient.maxAgeMs("max-age=\"5\""))
        assertNull(DvbiHttpClient.maxAgeMs(null))
        assertEquals("RFC 9111 clause 1.2.2: too large is 2^31 seconds", 2_147_483_648_000L, DvbiHttpClient.maxAgeMs("max-age=99999999999999999999"))
        assertEquals(120_000L, DvbiHttpClient.retryAfterMs("120", 0))
        val now = ZonedDateTime.parse("Fri, 31 Dec 1999 23:58:59 GMT", DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        assertEquals(60_000L, DvbiHttpClient.retryAfterMs("Fri, 31 Dec 1999 23:59:59 GMT", now))
        assertNull(DvbiHttpClient.retryAfterMs("soon", 0))
    }

    @Test
    fun clause4_3_2_2IfModifiedSinceIsOmittedWithoutALastModifiedTimeThenSentWithIt() {
        val lm = "Wed, 19 Jun 2019 19:43:31 GMT"
        val f = FakeTransport(Queued(200, mapOf("Last-Modified" to lm), "<a/>"), Queued(304))
        val c = DvbiHttpClient(f)
        assertEquals(200, c.get("https://sl.example/list.xml").status)
        assertNull(f.calls[0].second["If-Modified-Since"])
        val second = c.get("https://sl.example/list.xml")
        assertEquals(lm, f.calls[1].second["If-Modified-Since"])
        assertEquals(304, second.status)
        assertTrue(second.notModified)
        assertEquals("a 304 keeps the cached body", "<a/>", second.body)
    }

    @Test
    fun ts102796Clause7_3_2_6IfNoneMatchIsOmittedWithoutAnETagThenSentWithIt() {
        val f = FakeTransport(
            Queued(200, body = "<a/>"),
            Queued(200, mapOf("ETag" to "\"v1\""), "<a/>"),
            Queued(304, mapOf("ETag" to "\"v2\"")),
            Queued(304),
        )
        val c = DvbiHttpClient(f)
        c.get("https://sl.example/list.xml")
        assertNull("none held: omitted", f.calls[0].second["If-None-Match"])
        c.get("https://sl.example/list.xml")
        assertNull("the first response had no ETag", f.calls[1].second["If-None-Match"])
        val third = c.get("https://sl.example/list.xml")
        assertEquals("\"v1\"", f.calls[2].second["If-None-Match"])
        assertTrue(third.notModified)
        assertEquals("<a/>", third.body)
        c.get("https://sl.example/list.xml")
        assertEquals("an ETag on a 304 replaces the one held", "\"v2\"", f.calls[3].second["If-None-Match"])
    }

    @Test
    fun clause4_3_2_1NoRequestWhileMaxAgeHasNotPassedAndTheHeaderIsReadOnEveryResponse() {
        val t = Clock()
        val f = FakeTransport(
            Queued(200, mapOf("Cache-Control" to "max-age=60"), "v1"),
            Queued(200, mapOf("cache-control" to "max-age=10"), "v2"),
            Queued(200, body = "v3"),
        )
        val c = DvbiHttpClient(f, now = { t.t })
        c.get("https://cg.example/s")
        t.advance(59_000)
        val cached = c.get("https://cg.example/s")
        assertEquals("answered from the local cache while fresh", 1, f.calls.size)
        assertTrue(cached.fromCache)
        assertEquals("v1", cached.body)
        assertEquals(1000L, c.freshFor("https://cg.example/s"))
        t.advance(1000)
        assertEquals("requested once expired", "v2", c.get("https://cg.example/s").body)
        t.advance(10_000)
        assertEquals("the shorter max-age of the second response applies", "v3", c.get("https://cg.example/s").body)
        assertEquals(3, f.calls.size)
    }

    private fun date(ms: Long) = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneOffset.UTC))

    @Test
    fun rfc7234Clause5_2_2_3NoStoreIsNotStored() {
        val t = Clock()
        val f = FakeTransport(
            Queued(200, mapOf("Cache-Control" to "max-age=60", "ETag" to "\"a\""), "v1"),
            Queued(200, mapOf("Cache-Control" to "no-store, max-age=60", "ETag" to "\"b\""), "v2"),
            Queued(200, body = "v3"),
        )
        val c = DvbiHttpClient(f, now = { t.t })
        c.get("https://cg.example/s")
        t.advance(61_000)
        assertEquals("v2", c.get("https://cg.example/s").body)
        assertEquals("not reused although max-age is given", "v3", c.get("https://cg.example/s").body)
        assertNull("nothing held, so no validator is sent, the older copy included", f.calls[2].second["If-None-Match"])
    }

    @Test
    fun rfc7234Clause5_2_2_2NoCacheIsValidatedEveryTime() {
        val t = Clock()
        val f = FakeTransport(
            Queued(200, mapOf("Cache-Control" to "no-cache, max-age=60", "ETag" to "\"a\""), "v1"),
            Queued(304, emptyMap()),
        )
        val c = DvbiHttpClient(f, now = { t.t })
        c.get("https://cg.example/s")
        val again = c.get("https://cg.example/s")
        assertEquals("sent again at once, with the validator", 2, f.calls.size)
        assertEquals("\"a\"", f.calls[1].second["If-None-Match"])
        assertEquals("v1", again.body)
    }

    @Test
    fun rfc7234Clauses4_2_1And4_2_3FreshnessFromExpiresAndAgeFromAgeAndDate() {
        val t = Clock(1_790_000_000_000)
        val f = FakeTransport(
            Queued(200, mapOf("Cache-Control" to "max-age=60", "Age" to "50"), "aged"),
            Queued(200, mapOf("Expires" to date(t.t + 120_000), "Date" to date(t.t + 10_000)), "expires"),
            Queued(200, mapOf("Expires" to "0"), "invalid"),
            Queued(200, mapOf("Cache-Control" to "max-age=60, max-age=30"), "twice"),
            Queued(200, body = "last"),
        )
        val c = DvbiHttpClient(f, now = { t.t })
        c.get("https://cg.example/s")
        assertEquals("max-age 60 less Age 50", 10_000L, c.freshFor("https://cg.example/s"))
        t.advance(10_000)
        assertEquals("expires", c.get("https://cg.example/s").body)
        assertEquals("Expires minus Date", 110_000L, c.freshFor("https://cg.example/s"))
        t.advance(110_000)
        assertEquals("invalid", c.get("https://cg.example/s").body)
        assertEquals("Expires 0 is already expired", 0L, c.freshFor("https://cg.example/s"))
        assertEquals("twice", c.get("https://cg.example/s").body)
        assertEquals("two max-age directives: invalid, stale", 0L, c.freshFor("https://cg.example/s"))
        assertEquals("last", c.get("https://cg.example/s").body)
        val now = 1_790_000_000_000
        assertEquals("apparent age from Date when larger than Age", now + 30_000,
            DvbiHttpClient.expiresAt("max-age=60", null, date(now - 30_000), "5", now, now))
        assertEquals("the response delay counts", now + 55_000, DvbiHttpClient.expiresAt("max-age=60", null, null, "3", now - 2_000, now))
        assertEquals("no freshness information, no heuristic", 0L, DvbiHttpClient.expiresAt(null, null, null, null, now, now))
    }

    @Test
    fun rfc7234Clause4_3_4A304ReplacesTheStoredHeaderFields() {
        val t = Clock()
        val f = FakeTransport(
            Queued(200, mapOf("Cache-Control" to "max-age=0", "ETag" to "\"a\""), "v1"),
            Queued(304, mapOf("Cache-Control" to "max-age=30")),
            Queued(304, emptyMap()),
            Queued(304, mapOf("Cache-Control" to "no-store")),
            Queued(200, body = "v2"),
        )
        val c = DvbiHttpClient(f, now = { t.t })
        c.get("https://cg.example/s")
        c.get("https://cg.example/s")
        assertEquals("the 304's max-age replaces the stored one", 30_000L, c.freshFor("https://cg.example/s"))
        t.advance(30_000)
        c.get("https://cg.example/s")
        assertEquals("a 304 without Cache-Control keeps the stored max-age=30, from the new validation", 30_000L, c.freshFor("https://cg.example/s"))
        t.advance(30_000)
        assertEquals("v1", c.get("https://cg.example/s").body)
        assertEquals("v2", c.get("https://cg.example/s").body)
        assertNull("no-store on the 304 removed the stored response", f.calls[4].second["If-None-Match"])
    }

    @Test
    fun rfc7230Clause3_4AnIncompleteResponseIsNotStored() {
        val t = Clock()
        val f = FakeTransport(
            Queued(200, mapOf("ETag" to "\"a\""), "v1"),
            Queued(throws = "unexpected end of stream"),
            Queued(304, emptyMap()),
        )
        val c = DvbiHttpClient(f, now = { t.t })
        c.get("https://cg.example/s")
        assertFalse("the transport's failure is a failed request", c.get("https://cg.example/s").ok)
        t.advance(1_000)
        assertEquals("the earlier complete response is still the one held", "v1", c.get("https://cg.example/s").body)
        assertEquals("\"a\"", f.calls[2].second["If-None-Match"])
    }

    @Test
    fun clause4_3_3_2After400Or406TheSameRequestIsNotSentAgain() {
        for (status in listOf(400, 406)) {
            val f = FakeTransport(Queued(status))
            val c = DvbiHttpClient(f)
            assertTrue(c.get("https://cg.example/bad").final)
            val again = c.get("https://cg.example/bad")
            assertFalse(again.ok)
            assertTrue(again.skipped)
            assertEquals("nothing sent after $status", 1, f.calls.size)
            assertEquals(Long.MAX_VALUE, c.nextAllowed("https://cg.example/bad"))
        }
    }

    @Test
    fun clause4_3_3_3After401Or403TheRequestWaitsForRetryAfter() {
        val t = Clock()
        val f = FakeTransport(Queued(401, mapOf("Retry-After" to "120")), Queued(200, body = "ok"))
        val c = DvbiHttpClient(f, now = { t.t })
        val r = c.get("https://sl.example/private.xml")
        assertEquals(401, r.status)
        assertEquals(t.t + 120_000, r.retryAt)
        t.advance(119_000)
        assertTrue(c.get("https://sl.example/private.xml").skipped)
        assertEquals("not sent before Retry-After", 1, f.calls.size)
        t.advance(1000)
        assertEquals("ok", c.get("https://sl.example/private.xml").body)

        val f403 = FakeTransport(Queued(403), Queued(403))
        val c403 = DvbiHttpClient(f403, now = { t.t })
        c403.get("https://sl.example/x")
        c403.get("https://sl.example/x")
        assertEquals("without Retry-After nothing holds the request back", 2, f403.calls.size)
    }

    @Test
    fun clause4_3_3_5ServerErrorsAndConnectionFailureRetryNoFasterThanTheBackOff() {
        for (q in listOf(Queued(500), Queued(502), Queued(504), Queued(throws = "ECONNREFUSED"))) {
            val t = Clock()
            val f = FakeTransport(q, q, Queued(200, body = "ok"))
            val c = DvbiHttpClient(f, now = { t.t }, random = { 1.0 })
            assertEquals("first retry within 100 to 400 ms", t.t + 400, c.get("https://sl.example/list.xml").retryAt)
            t.advance(399)
            assertTrue(c.get("https://sl.example/list.xml").skipped)
            t.advance(1)
            assertEquals("second retry within 400 to 1 600 ms", t.t + 1600, c.get("https://sl.example/list.xml").retryAt)
            t.advance(1600)
            assertEquals("ok", c.get("https://sl.example/list.xml").body)
            assertEquals(3, f.calls.size)
        }
    }

    @Test
    fun clause4_3_3_7BackOffAppliedOnRequestByTheCallerCountedPerRequestKey() {
        val t = Clock()
        val c = DvbiHttpClient(FakeTransport(), now = { t.t }, random = { 0.0 })
        assertEquals(t.t + 100, c.backOff("cg|svc"))
        assertEquals(t.t + 400, c.backOff("cg|svc"))
        assertEquals("another request has its own count", t.t + 100, c.backOff("other"))
        repeat(20) { c.backOff("cg|svc") }
        assertEquals("held at CurrentRetry 10", t.t + 26_214_400, c.nextAllowed("cg|svc"))
    }

    @Test
    fun clause4_3_3ServiceListFetchTriesTheNextListedUri() {
        val f = FakeTransport(Queued(404), Queued(throws = "ECONNREFUSED"), Queued(200, mapOf("Content-Type" to "application/vnd.dvb.dvbisl+xml"), "<ServiceList/>"))
        val out = ServiceListFetch.fetch(DvbiHttpClient(f), listOf("http://a/1.xml", "http://b/2.xml", "http://c/3.xml"))
        assertEquals(ServiceListFetch.Outcome.Fetched("http://c/3.xml", "<ServiceList/>", "application/vnd.dvb.dvbisl+xml", false), out)
        val failed = ServiceListFetch.fetch(DvbiHttpClient(FakeTransport(Queued(403), Queued(500))), listOf("http://a/1.xml", "http://b/2.xml"))
        assertTrue(failed is ServiceListFetch.Outcome.Failed)
        assertEquals(listOf("http://a/1.xml: HTTP 403", "http://b/2.xml: HTTP 500"), (failed as ServiceListFetch.Outcome.Failed).reasons)
    }
}
