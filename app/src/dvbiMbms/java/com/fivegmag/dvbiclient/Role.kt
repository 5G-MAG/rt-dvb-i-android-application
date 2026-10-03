/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import com.fivegmag.dvbiclient.adapter.mbms.MwServiceMbmsClient
import com.fivegmag.dvbiclient.mbms.IMbmsStreamingClient

/**
 * The DVB-I client acting as MBMS-Aware Application, ETSI TS 103 770 V1.2.1 clause 9.3.3, with the
 * MBMS Client reached through adapter-mbms.
 */
object Role {
    const val NAME = "DVB-I client as MBMS-Aware Application"

    /** Shown in About so that the build is identifiable; null for the plain DVB-I client. */
    val VARIANT_LABEL: String? = "5G Broadcast variant"

    fun mbmsClient(): IMbmsStreamingClient = MwServiceMbmsClient

    /** Whether the playing session runs through 5GMS (the 5GMS badge). */
    fun fiveGmsSession(): Boolean = false

    fun status(): String = MwServiceMbmsClient.STATUS
}
