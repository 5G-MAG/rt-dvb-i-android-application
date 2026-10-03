/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.mbms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 3GPP TS 26.347 V18.1.0 clause 6.3, against a fake MBMS Client that records the calls. */
class MbmsSessionTest {

    private class FakeClient(var accept: Boolean = true) : IMbmsStreamingClient {
        val calls = ArrayList<String>()
        var callback: IMbmsStreamingClient.Callback? = null
        var list = listOf(
            IMbmsStreamingClient.StreamingServiceInfo("mbms://a.example", IMbmsStreamingClient.DVBI_SERVICE_INSTANCE_CLASS,
                listOf("application/dash+xml" to "http://localhost/a.mpd")),
            IMbmsStreamingClient.StreamingServiceInfo("mbms://b.example", IMbmsStreamingClient.DVBI_SERVICE_INSTANCE_CLASS,
                listOf("application/dash+xml" to "http://localhost/b.mpd")),
        )

        override fun registerStreamingApp(appId: String, serviceClassList: List<String>, callback: IMbmsStreamingClient.Callback) {
            calls.add("register $appId"); this.callback = callback
        }
        override fun getStreamingServices(): List<IMbmsStreamingClient.StreamingServiceInfo> { calls.add("getStreamingServices"); return list }
        override fun startStreamingService(serviceId: String) { calls.add("start $serviceId") }
        override fun stopStreamingService(serviceId: String) { calls.add("stop $serviceId") }
        override fun deregisterStreamingApp() { calls.add("deregister") }
    }

    private class Recorder : MbmsSession.Listener {
        val events = ArrayList<String>()
        override fun started(entry: Pair<String, String>) { events.add("started ${entry.second}") }
        override fun failed(message: String) { events.add("failed") }
    }

    private fun registered(c: FakeClient = FakeClient()): Pair<FakeClient, MbmsSession> {
        val s = MbmsSession(c, "app", listOf(IMbmsStreamingClient.DVBI_SERVICE_INSTANCE_CLASS))
        s.viewOpened()
        c.callback!!.registerStreamingResponse(true, "")
        return c to s
    }

    @Test
    fun clause6_3_3_4_5TheServiceListIsReadAfterRegistrationAndAfterEachUpdate() {
        val (c, s) = registered()
        assertEquals(listOf("register app", "getStreamingServices"), c.calls)
        assertTrue(s.registered)
        assertEquals(2, s.services.size)
        c.list = c.list.take(1)
        c.callback!!.streamingServiceListUpdate()
        assertEquals("streamingServiceListUpdate() is followed by getStreamingServices()", "getStreamingServices", c.calls.last())
        assertEquals(1, s.services.size)
    }

    @Test
    fun clause6_3_3_2_3RegisteredOnceForEveryViewAndSelection() {
        val (c, s) = registered()
        s.viewOpened()
        s.start("mbms://a.example", Recorder())
        s.start("mbms://b.example", Recorder())
        assertEquals("one registerStreamingApp()", 1, c.calls.count { it.startsWith("register") })
    }

    @Test
    fun clause6_3_3_9_4ThePreviousServiceIsStoppedOnReselectionAndWhenThePlayerIsLeft() {
        val (c, s) = registered()
        val a = Recorder()
        s.start("mbms://a.example&label=http://x.example/a.mpd", a)
        c.callback!!.serviceStarted("mbms://a.example")
        assertEquals(listOf("started http://localhost/a.mpd"), a.events)
        assertEquals("the list is read again on serviceStarted()", "getStreamingServices", c.calls.last())
        val b = Recorder()
        s.start("mbms://b.example", b)
        assertEquals(listOf("stop mbms://a.example", "start mbms://b.example"), c.calls.takeLast(2))
        s.stop(a)
        assertEquals("a view that did not start the active service does not stop it", "start mbms://b.example", c.calls.last())
        s.stop(b)
        assertEquals("stop mbms://b.example", c.calls.last())
        s.stop(b)
        assertEquals("nothing to stop twice", 1, c.calls.count { it == "stop mbms://b.example" })
        s.start("mbms://b.example", b)
        s.start("mbms://b.example", b)
        assertEquals("the same service is not started twice", 2, c.calls.count { it == "start mbms://b.example" })
    }

    @Test
    fun clause6_3_3_10_4DeregisteredWhenTheLastViewClosesAfterStopping() {
        val (c, s) = registered()
        s.viewOpened()
        s.start("mbms://a.example", Recorder())
        s.viewClosed(changingConfigurations = false)
        assertFalse("a view is still open", c.calls.contains("deregister"))
        s.viewClosed(changingConfigurations = true)
        assertFalse("recreated, not exiting", c.calls.contains("deregister"))
        s.viewOpened()
        s.viewClosed(changingConfigurations = false)
        assertEquals("stopped first, REGISTERED state being the pre-condition of deregisterStreamingApp()",
            listOf("stop mbms://a.example", "deregister"), c.calls.takeLast(2))
        assertFalse(s.registered)
        s.viewOpened()
        assertEquals("registered again after deregisterStreamingApp()", "register app", c.calls.last())
    }

    @Test
    fun aFailedRegistrationOrAnErrorIsReported() {
        val c = FakeClient()
        val s = MbmsSession(c, "app", emptyList())
        s.register()
        c.callback!!.registerStreamingResponse(false, "no MBMS Client")
        assertFalse(s.registered)
        assertFalse("no service list read without registration", c.calls.contains("getStreamingServices"))
        val r = Recorder()
        s.start("mbms://a.example", r)
        assertEquals(listOf("failed"), r.events)
        val (c2, s2) = registered()
        val r2 = Recorder()
        s2.start("mbms://a.example", r2)
        c2.callback!!.streamingServiceError("mbms://a.example", "STREAMING_INVALID_SERVICE")
        assertEquals(listOf("failed"), r2.events)
        s2.stop()
        assertFalse("not stopped after its error", c2.calls.contains("stop mbms://a.example"))
    }
}
