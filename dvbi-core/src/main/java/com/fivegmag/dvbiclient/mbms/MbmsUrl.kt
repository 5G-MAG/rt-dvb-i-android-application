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
 * MBMS URL check, 3GPP TS 26.347 V18.1.0 clause 8.2.2:
 *
 *     mbms-URI = "mbms:" "//" authority path-abempty *( "&" mid-label "=" mid-value ) [ "&label=" resourceURI ]
 *
 * "There are no currently defined mid-part pairs; they shall not be present in URLs", except the
 * Receive-only Mode form of clause 8.2.4 on mbms://rom.3gpp.org, whose pairs are not checked here.
 * The prefix "is the serviceId of the service", which only the MBMS Client can confirm.
 *
 * A service list carries the URL in IdentifierBasedDeliveryParameters (TS 103 770 V1.2.1 clause
 * 5.5.4, table 16). Ported from the browser client (rt-dvb-i-application 000e460,
 * public/mbms-url.js).
 */
object MbmsUrl {

    private const val ROM_AUTHORITY = "rom.3gpp.org"

    // RFC 3986 character classes for the parts of the MBMS URL (clauses 2.1 to 2.3, 3.2 and 3.3).
    // The prefix "shall not contain the character "&"" (TS 26.347 clause 8.2.2), so "&" is taken
    // out of sub-delims there; mid-value is TS 26.347's own uchar set.
    private const val U = "A-Za-z0-9\\-._~"                 // unreserved
    private const val PCT = "%[0-9A-Fa-f]{2}"               // pct-encoded
    private const val SUB = "!$'()*+,;="                    // sub-delims without "&"
    private const val USERINFO = "(?:[$U$SUB:]|$PCT)*"
    private const val REG_NAME = "(?:[$U$SUB]|$PCT)+"
    private const val IPV4 = "(?:\\d{1,3}\\.){3}\\d{1,3}"

    // RFC 3986 clause 3.2.2: IP-literal = "[" ( IPv6address / IPvFuture ) "]", IPv6address in its
    // nine forms over h16 = 1*4HEXDIG and ls32 = ( h16 ":" h16 ) / IPv4address, dec-octet for that
    // IPv4address, and IPvFuture = "v" 1*HEXDIG "." 1*( unreserved / sub-delims / ":" ). "v" is
    // matched in either case (the clause: "starts with "v" (case-insensitive)"); "&" stays out of
    // sub-delims, as everywhere in the prefix. The same productions as rt-dvb-i-application
    // public/mbms-url.js (f0c69f5).
    private const val DEC_OCTET = "(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]\\d|\\d)"
    private const val H16 = "[0-9A-Fa-f]{1,4}"
    private const val LS32 = "(?:$H16:$H16|$DEC_OCTET(?:\\.$DEC_OCTET){3})"
    private fun h16c(n: Int) = "(?:$H16:){$n}"                       // n( h16 ":" )
    private fun before(n: Int) = "(?:(?:$H16:){0,$n}$H16)?"          // [ *n( h16 ":" ) h16 ]
    private val IPV6 = listOf(
        "${h16c(6)}$LS32",
        "::${h16c(5)}$LS32",
        "(?:$H16)?::${h16c(4)}$LS32",
        "${before(1)}::${h16c(3)}$LS32",
        "${before(2)}::${h16c(2)}$LS32",
        "${before(3)}::$H16:$LS32",
        "${before(4)}::$LS32",
        "${before(5)}::$H16",
        "${before(6)}::",
    ).joinToString("|", "(?:", ")")
    private const val IPV_FUTURE = "[vV][0-9A-Fa-f]+\\.[$U$SUB:]+"
    private val IP_LITERAL = "\\[(?:$IPV6|$IPV_FUTURE)\\]"
    private val HOST = "(?:$IP_LITERAL|$IPV4|$REG_NAME)"
    private val AUTHORITY = "(?:$USERINFO@)?$HOST(?::\\d*)?"
    private const val PATH_ABEMPTY = "(?:/(?:[$U$SUB:@]|$PCT)*)*"
    private const val MID_VALUE = "(?:[$U;?:@=+$,/]|$PCT)+"
    private const val RESOURCE_URI = "[A-Za-z][A-Za-z0-9+.\\-]*:(?:[$U:/?#\\[\\]@!$&'()*+,;=]|$PCT)*"

    private val PREFIX = Regex("^mbms://($AUTHORITY)$PATH_ABEMPTY$")
    private val MID = Regex("^[A-Za-z][A-Za-z0-9]*=$MID_VALUE$")
    private val LABEL = Regex("^$RESOURCE_URI$")

    /** Null for a valid MBMS URL, otherwise a sentence saying what is wrong. */
    fun problem(url: String?): String? {
        val u = url ?: ""
        if (!u.startsWith("mbms://")) return "is not an MBMS URL: it must start with mbms://"
        val at = u.indexOf("&label=")
        val head = if (at < 0) u else u.substring(0, at)
        val label = if (at < 0) null else u.substring(at + "&label=".length)
        val parts = head.split('&')
        val prefix = parts[0]
        val mid = parts.drop(1)
        val m = PREFIX.find(prefix)
            ?: return "is not an MBMS URL: after mbms:// it needs an RFC 3986 authority and an optional path, with no \"&\", query or fragment"
        val authorityHost = m.groupValues[1].replace(Regex("^[^@]*@"), "").replace(Regex(":\\d*$"), "")
        if (mid.isNotEmpty() && authorityHost != ROM_AUTHORITY) {
            return "carries &name=value pairs, which are not allowed outside the Receive-only Mode form on mbms://$ROM_AUTHORITY"
        }
        if (mid.any { !MID.matches(it) }) return "has a mid-part that is not &name=value"
        if (label != null && !LABEL.matches(label)) return "has an &label= suffix that is not a URI"
        return null
    }

    /** The serviceId is the prefix: "the substring of the URI before the first "&"" (clause 8.2.2). */
    fun serviceId(url: String): String = url.substringBefore('&')
}
