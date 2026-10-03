/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.http

import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The OkHttp configuration carries the TLS profile of ETSI TS 103 770 V1.2.1 clause 7.3 ([TlsProfile]). */
class OkHttpTransportTest {

    @Test
    fun clause7_3ConnectionSpecIsTheTlsProfile() {
        val client = OkHttpTransport.clientBuilder().build()
        assertEquals(listOf(OkHttpTransport.TLS_SPEC, ConnectionSpec.CLEARTEXT), client.connectionSpecs)
        val spec = client.connectionSpecs[0]
        assertEquals(listOf(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2), spec.tlsVersions)
        // OkHttp names TLS_RSA_WITH_3DES_EDE_CBC_SHA by its Java name, SSL_RSA_WITH_3DES_EDE_CBC_SHA, and matches either.
        assertEquals("every name is one OkHttp knows, in the profile's order", TlsProfile.CIPHER_SUITES,
            spec.cipherSuites!!.map { it.javaName.replaceFirst(Regex("^SSL_"), "TLS_") })
        assertTrue(client.networkInterceptors.contains(OkHttpTransport.CERTIFICATE_CHECK))
    }
}
