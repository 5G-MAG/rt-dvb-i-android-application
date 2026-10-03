/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.mbms

/**
 * This client's use of the Media Streaming Service API of 3GPP TS 26.347 V18.1.0 clause 6.3, as an
 * MBMS-Aware Application (MAA): one registration while the user interface is open, the latest
 * service list, and at most one streaming service started at a time.
 *
 * - Registration once, not per selection: clause 6.3.3.2.3, pre-conditions of registerStreamingApp():
 *   "The MAA may use this method at launch or after a deregisterStreamingApp() has been called."
 *   and "The MBMS client is in IDLE state."
 * - The service list: clause 6.3.3.4.5: "The MAA should use this call right after the
 *   registerStreamingResponse() notification as defined in clause 6.3.3.3 is received or after the
 *   streamingServiceListUpdate() notification as defined in clause 6.3.3.6 is received." It is also
 *   read again on serviceStarted(), before the entry point is taken from it.
 * - Stopping: clause 6.3.3.9.4: "If an MAA is no longer interested in consuming the Media service,
 *   it should call the stopStreamingService() API call." That is when another instance or service
 *   is played, and when the player is left.
 * - Deregistration: clause 6.3.3.10.4: "MAA registered with the MBMS client via the
 *   registerStreamingApp() API should invoke the deregisterStreamingApp() before exiting." Its
 *   pre-condition (clause 6.3.3.10.3) is "The MBMS client is in REGISTERED state for this MAA.", so
 *   a started service is stopped first (clause 6.3.3.9.6: "The MBMS client is in REGISTERED state.").
 *
 * Callbacks may arrive on any thread; every method is synchronized.
 */
class MbmsSession(
    private val client: IMbmsStreamingClient,
    private val appId: String,
    private val serviceClasses: List<String>,
) {
    /** What the player is told about the service it asked for. */
    interface Listener {
        /** The entry point (ServiceMimeType, ManifestURI) to pass to the media player. */
        fun started(entry: Pair<String, String>)

        fun failed(message: String)
    }

    /** Whether the MBMS Client answered REGISTER_SUCCESS to the current registration. */
    @Volatile
    var registered = false
        private set

    /** The services of the latest getStreamingServices() call. */
    @Volatile
    var services: List<IMbmsStreamingClient.StreamingServiceInfo> = emptyList()
        private set

    private var registering = false
    private var openViews = 0
    private var active: String? = null
    private var activeLocator: String? = null
    private var listener: Listener? = null
    private var onRegistration: ((Boolean, String) -> Unit)? = null

    private val callback = object : IMbmsStreamingClient.Callback {
        override fun registerStreamingResponse(success: Boolean, message: String) = onRegistered(success, message)
        override fun streamingServiceListUpdate() = onListUpdate()
        override fun serviceStarted(serviceId: String) = onStarted(serviceId)
        override fun streamingServiceError(serviceId: String, message: String) = onError(serviceId, message)
    }

    /** Registers unless registered or registering; [result] gets the registration response. */
    @Synchronized
    fun register(result: (Boolean, String) -> Unit = { _, _ -> }) {
        if (registered || registering) return
        registering = true
        onRegistration = result
        client.registerStreamingApp(appId, serviceClasses, callback)
    }

    /** Stops a started service, then deregisters. */
    @Synchronized
    fun deregister() {
        if (!registered && !registering) return
        stop()
        client.deregisterStreamingApp()
        registered = false
        registering = false
        services = emptyList()
    }

    /** A view of this client's user interface was opened: the first one registers. */
    @Synchronized
    fun viewOpened(result: (Boolean, String) -> Unit = { _, _ -> }) {
        openViews++
        register(result)
    }

    /**
     * A view was closed. When the last one closes and not merely to be recreated
     * ([changingConfigurations]), the client is exiting and deregisters.
     */
    @Synchronized
    fun viewClosed(changingConfigurations: Boolean) {
        openViews = maxOf(0, openViews - 1)
        if (openViews == 0 && !changingConfigurations) deregister()
    }

    /**
     * Starts the MBMS User Service that [locator] names, stopping the one started before if it is
     * another. Its serviceId is the locator's prefix (clause 8.2.2).
     */
    @Synchronized
    fun start(locator: String, listener: Listener) {
        if (!registered) {
            listener.failed("not registered with an MBMS Client")
            return
        }
        val id = MbmsUrl.serviceId(locator)
        if (active != null && active != id) stop()
        this.listener = listener
        activeLocator = locator
        if (active == id) return
        active = id
        client.startStreamingService(id)
    }

    /**
     * Stops the started service, if any (clause 6.3.3.9). With [owner], only when it was started
     * for that listener, so that a view being left does not stop what another view started.
     */
    @Synchronized
    fun stop(owner: Listener? = null) {
        if (owner != null && owner !== listener) return
        val id = active ?: return
        active = null
        activeLocator = null
        listener = null
        client.stopStreamingService(id)
    }

    @Synchronized
    private fun onRegistered(success: Boolean, message: String) {
        if (!registering) return
        registering = false
        registered = success
        if (success) services = client.getStreamingServices()
        onRegistration?.invoke(success, message)
        onRegistration = null
    }

    @Synchronized
    private fun onListUpdate() {
        if (registered) services = client.getStreamingServices()
    }

    @Synchronized
    private fun onStarted(serviceId: String) {
        if (serviceId != active) return
        services = client.getStreamingServices()
        val l = listener ?: return
        val entry = MbmsReception.entryPoint(services, activeLocator ?: return)
        if (entry == null) l.failed("the MBMS Client lists no entry point for $serviceId") else l.started(entry)
    }

    @Synchronized
    private fun onError(serviceId: String, message: String) {
        if (serviceId != active) return
        val l = listener
        active = null
        activeLocator = null
        listener = null
        l?.failed(message)
    }
}
