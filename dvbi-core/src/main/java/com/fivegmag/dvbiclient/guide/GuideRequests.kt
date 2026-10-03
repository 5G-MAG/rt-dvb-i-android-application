/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.guide

/**
 * Content guide request URLs of ETSI TS 103 770 V1.2.1 clauses 6.5 to 6.8, and the contextual
 * parameters of an XML AIT request (clause 5.2.4.4.6), encoded as clause 6.2.2
 * says. Ported from the browser client (rt-dvb-i-application public/guide.js).
 */
object GuideRequests {

    /** Clause 6.5.2.1: "the Unix timestamp shall be a whole multiple of 10 800" */
    const val THREE_HOURS_S = 10_800L

    /** Clause 6.5.2.1: end is start plus 21 600 or 43 200 seconds; the longer window is used. */
    const val WINDOW_S = 43_200L

    /** A schedule window in Unix seconds. */
    data class Window(val start: Long, val end: Long)

    /**
     * One key or value of a query string. Clauses 5.1.3.2 and 6.2.2: any "reserved" characters of
     * IETF RFC 3986 clause 2.2 within key/value pairs "shall be percent-encoded as defined in clause
     * 2.1 of IETF RFC 3986". Everything but the unreserved characters of RFC 3986 clause 2.3 is
     * encoded, as UTF-8 octets with the uppercase hexadecimal digits clause 2.1 recommends.
     */
    fun encodeQueryComponent(v: String): String {
        val sb = StringBuilder()
        for (b in v.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch == '-' || ch == '.' || ch == '_' || ch == '~') {
                sb.append(ch)
            } else {
                sb.append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 0xF])
            }
        }
        return sb.toString()
    }

    /** Query string of (name, value) pairs, each encoded as above; empty values are left out. */
    fun query(pairs: List<Pair<String, String?>>): String =
        pairs.filter { !it.second.isNullOrEmpty() }
            .joinToString("&") { "${encodeQueryComponent(it.first)}=${encodeQueryComponent(it.second!!)}" }

    /**
     * [base] with [pairs] added to its query. IETF RFC 3986 clause 3.4: "The query component is
     * indicated by the first question mark ("?") character and terminated by a number sign ("#")
     * character or by the end of the URI." So the pairs go before the first "#", as ETSI TS 102 796
     * V1.8.1 clause 6.2.2.6.2 has it for lloc: "This string is added before the first number sign
     * (#) character in the URL if there is one, or at the end if there is not, using either a "?" or
     * a "&" character in order to maintain a legal URL structure as defined in IETF RFC 3986 [27]."
     */
    fun withQuery(base: String, pairs: List<Pair<String, String?>>): String {
        val q = query(pairs)
        if (q.isEmpty()) return base
        val hash = base.indexOf('#')
        val head = if (hash < 0) base else base.substring(0, hash)
        val fragment = if (hash < 0) "" else base.substring(hash)
        return head + (if (head.contains('?')) "&" else "?") + q + fragment
    }

    /** [url] with the launch context parameter "lloc=<launch location>" of TS 102 796 clause 6.2.2.6.2; unchanged when [launchLocation] is empty. */
    fun withLaunchLocation(url: String, launchLocation: String): String = withQuery(url, listOf("lloc" to launchLocation))

    /**
     * The 12-hour windows, each starting on a 3-hour boundary, that together cover [fromMs, toMs]
     * (clause 6.5.2.1). "Combining the results of multiple calls is the responsibility of the DVB-I
     * client." (clause 6.5.2.2).
     */
    fun scheduleWindows(fromMs: Long, toMs: Long): List<Window> {
        val out = ArrayList<Window>()
        var start = Math.floorDiv(Math.floorDiv(fromMs, 1000L), THREE_HOURS_S) * THREE_HOURS_S
        val last = toMs / 1000.0
        do {
            out.add(Window(start, start + WINDOW_S))
            start += WINDOW_S
        } while (start < last)
        return out
    }

    /** Clause 6.5.2.2: <ScheduleInfoEndpoint>?start=<start_unixtime>&end=<end_unixtime>&sid=<service_id> */
    fun scheduleUrl(endpoint: String, sid: String, win: Window): String =
        withQuery(endpoint, listOf("start" to win.start.toString(), "end" to win.end.toString(), "sid" to sid))

    /** Clause 6.5.3.1: <ScheduleInfoEndpoint>?sid=<service_id>&now_next=<window_type>, true or window. */
    fun nowNextUrl(endpoint: String, sid: String, windowType: String = "true"): String =
        withQuery(endpoint, listOf("sid" to sid, "now_next" to windowType))

    /** Clause 6.6.2: <ProgramInfoEndpoint>?pid=<program_id> */
    fun programUrl(endpoint: String, pid: String): String = withQuery(endpoint, listOf("pid" to pid))

    private fun regionPairs(regions: List<String>) = regions.map { "regionID[]" to it }
    private fun sidPairs(sids: List<String>) = sids.map { "sid[]" to it }

    /** Clause 6.7.2: <MoreEpisodesEndpoint>?pid=<program_id>&type=ondemand&regionID[]=... */
    fun moreEpisodesUrl(endpoint: String, pid: String, regions: List<String>): String =
        withQuery(endpoint, listOf("pid" to pid, "type" to "ondemand") + regionPairs(regions))

    /** Clause 6.8.2.2: <GroupInfoEndpoint>categories?sid[]=...&regionID[]=... */
    fun boxSetCategoriesUrl(groupEndpoint: String, sids: List<String>, regions: List<String>): String =
        withQuery("${groupEndpoint}categories", sidPairs(sids) + regionPairs(regions))

    /** Clause 6.8.3.2: <GroupInfoEndpoint>?groupId=<group_id>&sid[]=...&regionID[]=... */
    fun boxSetListsUrl(groupEndpoint: String, groupId: String, sids: List<String>, regions: List<String>): String =
        withQuery(groupEndpoint, listOf<Pair<String, String?>>("groupId" to groupId) + sidPairs(sids) + regionPairs(regions))

    /**
     * Clause 6.8.4.2: <GroupInfoEndpoint>contents?groupId=<group_id>&format=...&regionID[]=...; the
     * paginated format is asked for, since this client pages (clause 6.9).
     */
    fun boxSetContentsUrl(groupEndpoint: String, groupId: String, regions: List<String>): String =
        withQuery("${groupEndpoint}contents", listOf<Pair<String, String?>>("groupId" to groupId, "format" to "paginated") + regionPairs(regions))

    /**
     * Clause 5.2.4.4.6: "Client devices shall append all of the following parameters to the XML AIT
     * URL provided in the metadata before attempting to retrieve the document": the regionID values
     * of the device and the UI location the application is launched from.
     */
    fun aitUrl(url: String, regions: List<String>, launchLocation: String): String =
        withQuery(url, regionPairs(regions) + listOf("lloc" to launchLocation))
}
