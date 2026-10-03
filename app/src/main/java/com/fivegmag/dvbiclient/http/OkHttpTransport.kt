/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.http

import com.fivegmag.dvbiclient.App
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.TlsVersion
import java.io.IOException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * [HttpTransport] over OkHttp, without OkHttp's own cache: caching and conditional requests are
 * done by [DvbiHttpClient] as clause 4.3.2 describes. Redirects are followed (clause 4.3.3.6
 * judges the final status). Plain HTTP is refused by Android for hosts the network security
 * configuration does not list, which surfaces as a connection failure naming the host. Timeouts
 * are OkHttp's defaults; no DVB-I clause sets them. TLS follows [TlsProfile] (see [clientBuilder]).
 */
class OkHttpTransport(private val client: OkHttpClient = clientBuilder().build()) : HttpTransport {

    companion object {
        /**
         * TLS 1.3 and 1.2 with the cipher suites of [TlsProfile]; plain HTTP stays possible for the
         * hosts the network security configuration lists. OkHttp keeps the platform's order of the
         * suites it enables, so the order of table 15a ("should") is not imposed here.
         */
        val TLS_SPEC: ConnectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(*TlsProfile.TLS_VERSIONS.map { TlsVersion.forJavaName(it) }.toTypedArray())
            .cipherSuites(*TlsProfile.CIPHER_SUITES.toTypedArray())
            .build()

        /**
         * Fails a request whose TLS connection has a certificate chain [TlsProfile.certificateProblem]
         * rejects: ETSI TS 102 796 V1.8.1 clause 11.2.1, "Terminals shall deem a TLS connection to
         * have failed" when "Any signature required for certificate chain validation uses an
         * algorithm or key size that is forbidden by the present document." The chain is OkHttp's
         * validated one, ending at the trust anchor.
         */
        val CERTIFICATE_CHECK = Interceptor { chain ->
            chain.connection()?.handshake()?.let { hs ->
                TlsProfile.certificateProblem(hs.peerCertificates.filterIsInstance<X509Certificate>())?.let {
                    throw SSLPeerUnverifiedException("TLS connection to ${chain.request().url.host} failed: $it")
                }
            }
            chain.proceed(chain.request())
        }

        /** An OkHttp client for DVB-I metadata endpoints (service lists, registries, content guides and their images). */
        fun clientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
            .connectionSpecs(listOf(TLS_SPEC, ConnectionSpec.CLEARTEXT))
            .addNetworkInterceptor(CERTIFICATE_CHECK)
    }

    override fun get(url: String, headers: Map<String, String>): HttpResponse {
        val builder = try {
            Request.Builder().url(url)
        } catch (e: IllegalArgumentException) {
            throw IOException("not an HTTP URL: $url", e)
        }
        builder.header("User-Agent", App.USER_AGENT)
        for ((k, v) in headers) builder.header(k, v)
        client.newCall(builder.build()).execute().use { res ->
            val h = LinkedHashMap<String, String>()
            for (name in res.headers.names()) res.header(name)?.let { h[name] = it }
            return HttpResponse(res.code, h, res.body?.string() ?: "")
        }
    }
}
