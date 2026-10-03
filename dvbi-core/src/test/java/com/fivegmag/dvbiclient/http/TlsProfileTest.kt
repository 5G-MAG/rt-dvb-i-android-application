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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** ETSI TS 102 796 V1.8.1 clause 11.2, as TS 103 770 V1.2.1 clause 7.3 applies it. */
class TlsProfileTest {

    private fun cert(name: String): X509Certificate {
        val text = javaClass.getResource("/tls/$name")!!.readText()
        val pem = text.substring(text.indexOf("-----BEGIN CERTIFICATE-----"))
        return CertificateFactory.getInstance("X.509").generateCertificate(pem.byteInputStream()) as X509Certificate
    }

    @Test
    fun clause7_3Tls13AndTls12Only() {
        assertEquals(listOf("TLSv1.3", "TLSv1.2"), TlsProfile.TLS_VERSIONS)
    }

    @Test
    fun clause11_2_2MandatorySuitesPresentInTableOrder() {
        assertTrue("RFC 8446 clause 9.1: TLS_AES_128_GCM_SHA256", "TLS_AES_128_GCM_SHA256" in TlsProfile.CIPHER_SUITES)
        assertTrue(TlsProfile.CIPHER_SUITES.containsAll(TlsProfile.TABLE_15A_MANDATORY))
        assertEquals("table 15a first among the TLS 1.2 suites, in its order", TlsProfile.TABLE_15A,
            TlsProfile.CIPHER_SUITES.drop(TlsProfile.TLS13_SUITES.size).take(TlsProfile.TABLE_15A.size))
        assertEquals("no suite twice", TlsProfile.CIPHER_SUITES.size, TlsProfile.CIPHER_SUITES.toSet().size)
    }

    @Test
    fun clause11_2_2NoForbiddenSuite() {
        for (s in TlsProfile.CIPHER_SUITES - TlsProfile.TLS13_SUITES.toSet()) {
            assertFalse("$s is not forbidden by table 15a", TlsProfile.forbiddenTls12(s))
        }
        for (s in listOf("TLS_DH_anon_WITH_AES_128_CBC_SHA", "TLS_ECDH_anon_WITH_AES_128_CBC_SHA", "TLS_RSA_WITH_NULL_SHA256",
                "TLS_ECDHE_RSA_WITH_NULL_SHA", "TLS_RSA_WITH_RC4_128_SHA", "TLS_ECDHE_RSA_WITH_RC4_128_SHA", "TLS_RSA_WITH_DES_CBC_SHA",
                "TLS_RSA_EXPORT_WITH_DES40_CBC_SHA", "TLS_RSA_WITH_IDEA_CBC_SHA", "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256")) {
            assertTrue("$s is forbidden, or its strength is not established", TlsProfile.forbiddenTls12(s))
        }
        assertFalse("3TDEA is 112 bits, not less", TlsProfile.forbiddenTls12("TLS_RSA_WITH_3DES_EDE_CBC_SHA"))
    }

    @Test
    fun clauses11_2_3To11_2_5CertificateChains() {
        val root = cert("root.pem")
        assertNull("P-256 server key, SHA-256 signature, RSA 2048 root", TlsProfile.certificateProblem(listOf(cert("leaf-good.pem"), root)))
        assertNull("a self-signed root alone: its own signature is not checked (NOTE 4)", TlsProfile.certificateProblem(listOf(root)))
        assertNull(TlsProfile.certificateProblem(emptyList()))
        for (name in listOf("leaf-sha1.pem", "leaf-md5.pem")) {
            val p = TlsProfile.certificateProblem(listOf(cert(name), root))
            assertNotNull("$name: table 15b forbids it", p)
            assertTrue(p!!, "11.2.4" in p)
        }
        val ecdsaSha1 = TlsProfile.certificateProblem(listOf(cert("leaf-ecdsa-sha1.pem"), cert("rootec.pem")))
        assertTrue("ecdsa-with-SHA1: $ecdsaSha1", ecdsaSha1 != null && "11.2.4" in ecdsaSha1)
        val rsa1024 = TlsProfile.certificateProblem(listOf(cert("leaf-rsa1024.pem"), root))
        assertTrue("RSA 1024 server key: $rsa1024", rsa1024 != null && "11.2.5" in rsa1024)
        val root1024 = TlsProfile.certificateProblem(listOf(cert("root1024.pem")))
        assertTrue("RSA 1024 root: $root1024", root1024 != null && "11.2.5" in root1024)
        assertEquals("a P-256 root is 128 bits", true, TlsProfile.atLeast112Bits(cert("rootec.pem").publicKey))
        assertEquals(false, TlsProfile.atLeast112Bits(cert("root1024.pem").publicKey))
    }
}
