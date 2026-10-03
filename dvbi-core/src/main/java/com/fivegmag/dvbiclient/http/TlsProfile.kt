/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.http

import java.security.PublicKey
import java.security.cert.X509Certificate
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey

/**
 * The TLS profile of the connections to DVB-I metadata endpoints. ETSI TS 103 770 V1.2.1 clause 7.3:
 * HTTP over TLS "using root certificates, cipher suites, signature algorithms, key sizes and
 * elliptic curves as defined in clause 11.2 of ETSI TS 102 796 [21], as applicable for the TLS
 * version used", and "A DVB-I client shall support TLS version 1.3 defined in IETF RFC 8446 [25] or
 * later, and TLS version 1.2 defined in IETF RFC 5246 [26] for interoperability." Reference [21] is
 * undated; ETSI TS 102 796 V1.8.1 is its latest issue.
 *
 * The names are IANA cipher suite names, as OkHttp and the platform TLS provider take them. The
 * front end builds its TLS configuration from these lists and checks each handshake with
 * [certificateProblem].
 */
object TlsProfile {

    /** TS 103 770 clause 7.3: TLS 1.3 and TLS 1.2; nothing earlier ("Terminals shall not negotiate sessions using SSL 3.0 or earlier", TS 102 796 clause 11.2.1). */
    val TLS_VERSIONS = listOf("TLSv1.3", "TLSv1.2")

    /**
     * TLS 1.3. TS 102 796 clause 11.2.2: "Terminals shall support all of the mandatory to implement
     * cipher suites for TLS 1.3 as specified in IETF RFC 8446 [73], clause 9.1." RFC 8446 clause
     * 9.1: "A TLS-compliant application MUST implement the TLS_AES_128_GCM_SHA256 [GCM] cipher
     * suite and SHOULD implement the TLS_AES_256_GCM_SHA384 [GCM] and TLS_CHACHA20_POLY1305_SHA256
     * [RFC8439] cipher suites".
     */
    val TLS13_SUITES = listOf("TLS_AES_128_GCM_SHA256", "TLS_AES_256_GCM_SHA384", "TLS_CHACHA20_POLY1305_SHA256")

    /**
     * TLS 1.2, table 15a of TS 102 796 clause 11.2.2 in its order ("Terminals should prioritize
     * these cipher suites in the order shown."): three Mandatory, two Recommended.
     */
    val TABLE_15A = listOf(
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
        "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_RSA_WITH_AES_128_CBC_SHA",
    )

    /** The suites table 15a marks Mandatory: "Terminals shall implement all cipher suites marked mandatory". */
    val TABLE_15A_MANDATORY = listOf(
        "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256",
        "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_RSA_WITH_AES_128_CBC_SHA",
    )

    /**
     * TLS 1.2 suites after table 15a: those OkHttp 4.12.0 enables by default (ConnectionSpec
     * MODERN_TLS) that table 15a neither lists nor forbids, and whose bulk cipher [CIPHER_BITS]
     * sizes, kept so that a server choosing one of them still connects. TLS_RSA_WITH_3DES_EDE_CBC_SHA
     * is among them: table 15a forbids "Cipher suites using encryption or signing algorithms offering
     * less than 112 bits of security", and NIST SP 800-57 Part 1 Rev 5 clause 5.6.1.1, table 2, puts
     * 3TDEA in the row of 112 bits (footnote 68: "Although 3TDEA is listed as providing 112 bits of
     * security strength, its use has been deprecated (see SP 800-131A)"), which is not less than 112.
     * OkHttp's TLS 1.2 ChaCha20 suites are left out: table 2 does not list ChaCha20, so nothing held
     * says whether it reaches 112 bits.
     */
    val OTHER_TLS12_SUITES = listOf(
        "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
        "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
        "TLS_RSA_WITH_AES_128_GCM_SHA256",
        "TLS_RSA_WITH_AES_256_GCM_SHA384",
        "TLS_RSA_WITH_AES_256_CBC_SHA",
        "TLS_RSA_WITH_3DES_EDE_CBC_SHA",
    )

    /** Every suite offered, TLS 1.3 first, then table 15a in its order, then the others. */
    val CIPHER_SUITES = TLS13_SUITES + TABLE_15A + OTHER_TLS12_SUITES

    /**
     * Security strength of the bulk ciphers used here, NIST SP 800-57 Part 1 Rev 5 clause 5.6.1.1,
     * table 2: 3TDEA in the row of 112 bits, AES-128 in the row of 128, AES-256 in the row of 256.
     */
    val CIPHER_BITS = mapOf("3DES_EDE" to 112, "AES_128" to 128, "AES_256" to 256)

    /**
     * Whether a TLS 1.2 [suite] falls under a Forbidden row of table 15a: "Cipher suites with
     * anonymous key exchange", "Cipher suites with NULL encryption", "Cipher suites using RC4
     * encryption", or a bulk cipher [CIPHER_BITS] does not establish at 112 bits or more.
     */
    fun forbiddenTls12(suite: String): Boolean {
        val s = suite.uppercase()
        if ("_ANON_" in s || "NULL" in s || "RC4" in s) return true
        val cipher = s.substringAfter("_WITH_", "")
        val bits = CIPHER_BITS.entries.firstOrNull { cipher.startsWith(it.key + "_") }?.value ?: return true
        return bits < 112
    }

    /**
     * Security strength of [key] in bits as NIST SP 800-57 Part 1 Rev 5 table 2 gives it for the
     * sizes that bear on 112 bits (row 112: "k = 2048" for IFC (RSA), L = 2048 for FFC (DSA),
     * "f = 224-255" for ECC), or null for a key type this check does not size.
     */
    fun atLeast112Bits(key: PublicKey): Boolean? = when (key) {
        is RSAPublicKey -> key.modulus.bitLength() >= 2048
        is DSAPublicKey -> key.params?.p?.bitLength()?.let { it >= 2048 }
        is ECPublicKey -> key.params.order.bitLength() >= 224
        else -> null
    }

    /**
     * Table 15b, the algorithms designated Forbidden, as the Java names of certificate signature
     * algorithms: md5WithRSAEncryption, sha1WithRSAEncryption and ecdsa-with-SHA1. Compared without
     * case, since providers spell them differently.
     */
    val FORBIDDEN_SIGNATURES = setOf("MD5WITHRSA", "SHA1WITHRSA", "SHA1WITHECDSA")

    /**
     * Why the certificate chain of a TLS connection is not to be trusted, or null when it may be.
     * [chain] is the validated chain, server certificate first, trust anchor last.
     *
     * - TS 102 796 clause 11.2.1: "Terminals shall deem a TLS connection to have failed if any of
     *   the following conditions apply", among them "Any signature required for certificate chain
     *   validation uses an algorithm or key size that is forbidden by the present document." with
     *   NOTE 4: "This requirement relates only to signatures that are actually required to be
     *   verified and does not cover signatures on root certificates". So the signature of every
     *   certificate but the trust anchor is checked against table 15b (clause 11.2.4: "Terminals
     *   shall not trust any signature that uses an algorithm designated as forbidden.").
     * - Clause 11.2.5: "Terminals shall not trust RSA signatures that are less than 2 048 bits in
     *   size." Every RSA key of the chain signs either a certificate or the handshake, so each is
     *   checked.
     * - Clause 11.2.3: "Terminals shall not trust any root certificate with a public key where the
     *   number of bits of security provided by the algorithm is less than 112 bits, as defined by
     *   clause 5.6.1.1 of NIST Special Publication 800-57 Part 1 Rev 5 [49]."
     */
    fun certificateProblem(chain: List<X509Certificate>): String? {
        if (chain.isEmpty()) return null
        chain.forEachIndexed { i, cert ->
            val key = cert.publicKey
            if (key is RSAPublicKey && key.modulus.bitLength() < 2048) {
                return "certificate ${i + 1} (${cert.subjectX500Principal.name}) has an RSA key of ${key.modulus.bitLength()} bits; " +
                    "ETSI TS 102 796 clause 11.2.5: RSA signatures of less than 2 048 bits are not trusted"
            }
            if (i < chain.size - 1 && cert.sigAlgName.uppercase().replace("-", "") in FORBIDDEN_SIGNATURES) {
                return "certificate ${i + 1} (${cert.subjectX500Principal.name}) is signed with ${cert.sigAlgName}, " +
                    "forbidden by ETSI TS 102 796 clause 11.2.4, table 15b"
            }
        }
        val root = chain.last()
        if (atLeast112Bits(root.publicKey) == false) {
            return "the root certificate (${root.subjectX500Principal.name}) has a key of less than 112 bits of security; " +
                "ETSI TS 102 796 clause 11.2.3"
        }
        return null
    }
}
