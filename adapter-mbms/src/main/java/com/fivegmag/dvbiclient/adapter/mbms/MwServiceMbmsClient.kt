/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.adapter.mbms

import com.fivegmag.dvbiclient.mbms.IMbmsStreamingClient
import com.fivegmag.dvbiclient.mbms.NoMbmsClient

/**
 * The MBMS Client of the 5G-MAG MBMS Middleware for Android (5G-MAG/rt-mbms-mw-android), behind the
 * [IMbmsStreamingClient] interface of dvbi-core (3GPP TS 26.347 V18.1.0 clause 6.3 method names).
 *
 * Not implemented. The middleware's MwService is a started service and does not yet offer an
 * interface to bind to: its onBind() is TODO("Not yet implemented") in rt-mbms-mw-android main
 * 03a4a63 and development cf85791 (MwService.kt:195). Until it does, every call is answered as
 * [NoMbmsClient] answers it: registration fails, so 5G Broadcast instances are not played and
 * another instance of the service plays.
 */
object MwServiceMbmsClient : IMbmsStreamingClient by NoMbmsClient {
    const val STATUS = "MBMS adapter not implemented: rt-mbms-mw-android MwService offers no interface to bind to yet"
}
