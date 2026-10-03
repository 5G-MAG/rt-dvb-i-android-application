/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import com.fivegmag.dvbiclient.adapter.fivegms.FiveGmsClient
import com.fivegmag.dvbiclient.mbms.IMbmsStreamingClient
import com.fivegmag.dvbiclient.mbms.NoMbmsClient

/**
 * The DVB-I client acting as 5GMSd-Aware Application, with the 5GMSd Client reached through
 * adapter-5gms. This role receives no 5G Broadcast, so it has no MBMS Client.
 */
object Role {
    const val NAME = "DVB-I client as 5GMSd-Aware Application"

    /** Shown in About so that the build is identifiable; null for the plain DVB-I client. */
    val VARIANT_LABEL: String? = "5G Media Streaming variant"

    fun mbmsClient(): IMbmsStreamingClient = NoMbmsClient

    /** Whether the playing session runs through 5GMS (the 5GMS badge). */
    fun fiveGmsSession(): Boolean = FiveGmsClient.sessionActive()

    fun status(): String = FiveGmsClient.STATUS
}
