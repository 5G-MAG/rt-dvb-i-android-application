/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.adapter.fivegms

/**
 * Where the DVB-I client, acting as 5GMSd-Aware Application, will reach the 5GMSd Client through
 * the 5G-MAG 5GMS client libraries (rt-5gms-media-stream-handler and its Media Session Handler,
 * rt-5gms-common-android-library).
 *
 * Not implemented. No 5GMS library is linked, and a DVB-I service list carries no 5GMS Service
 * Access Information to start a session with: ETSI TR 103 972 V1.1.1 clause 6.3.4 records the
 * missing element as gaps 1 and 2, and ETSI TS 103 770 V1.2.1 defines none. Nothing is invented
 * for it here.
 */
object FiveGmsClient {
    const val STATUS = "5GMS adapter not implemented: no 5GMS library is linked, and DVB-I carries no 5GMS Service Access Information"
}
