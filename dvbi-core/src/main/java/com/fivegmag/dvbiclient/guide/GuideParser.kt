/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.guide

import com.fivegmag.dvbiclient.servicelist.Image
import com.fivegmag.dvbiclient.servicelist.ParentalRating
import com.fivegmag.dvbiclient.servicelist.ServiceListParser
import com.fivegmag.dvbiclient.xml.Xml
import com.fivegmag.dvbiclient.xml.XmlFormatException
import com.fivegmag.dvbiclient.xml.attr
import com.fivegmag.dvbiclient.xml.child
import com.fivegmag.dvbiclient.xml.childText
import com.fivegmag.dvbiclient.xml.children
import com.fivegmag.dvbiclient.xml.descendant
import com.fivegmag.dvbiclient.xml.descendants
import com.fivegmag.dvbiclient.xml.local
import com.fivegmag.dvbiclient.xml.text
import org.w3c.dom.Element

/** Programme information (clause 6.10), the fields this client shows. */
data class ProgrammeInfo(
    val programId: String,
    val title: String,
    val synopsis: String,
    val genre: String?,
    val image: String?,
    /** ParentalGuidance/MinimumAge with CountryCodes (clause 6.10.15, table 61). */
    val ratings: List<ParentalRating>,
    /** The now/next structural group (clause 6.5.4.4): "now", "later" or "earlier", and its index. */
    val structural: String?,
    val structuralIndex: Int?,
    /** Title with @type secondary (tables 42, 43). */
    val secondaryTitle: String? = null,
    /** Keyword elements (table 43). */
    val keywords: List<String> = emptyList(),
    /** MemberOf elements other than the now/next structural groups (table 41), with the group's title when the response has it. */
    val memberOf: List<Membership> = emptyList(),
    /** EpisodeOf@crid: Box Set Lists the programme is an episode of (table 41). */
    val episodeOf: List<String> = emptyList(),
    /** The OnDemandProgram of the same response whose Program@crid is this programme's (clause 6.6.3, table 52). */
    val onDemand: OnDemandProgram? = null,
)

/**
 * A MemberOf of a ProgramInformation (table 41): the group's CRID, "The @index attribute defines the
 * programme's position within the list defined by @crid", and the Title of the GroupInformation with
 * that groupId when the response carries one (clause 6.10.17).
 */
data class Membership(val crid: String, val index: Int?, val groupTitle: String?)

/**
 * OnDemandProgram (clause 6.10.8.2, table 52): ProgramURL is "A URL location of a content
 * deep-linked XML AIT for the on-demand programme", AuxiliaryURL "A URL location of a Template XML
 * AIT", and the availability window. Instants in milliseconds since the epoch.
 */
data class OnDemandProgram(
    val serviceIdRef: String,
    val crid: String,
    val programUrl: String,
    val programUrlType: String,
    val auxiliaryUrl: String?,
    val start: Long?,
    val end: Long?,
    val durationMs: Long,
    val free: Boolean?,
) {
    /** Within StartOfAvailability and EndOfAvailability (table 52) at [ms]. */
    fun availableAt(ms: Long): Boolean = (start == null || ms >= start) && (end == null || ms < end)
}

/** A programme of a More Episodes or Box Set Contents response (clauses 6.7.3, 6.8.4.3). */
data class ResultItem(
    val programId: String,
    val title: String,
    /** Title with @type secondary. */
    val subtitle: String,
    val synopsis: String,
    val image: String?,
    val ratings: List<ParentalRating>,
    /** MemberOf@index, the position in the results (clause 6.7.3). */
    val index: Int?,
    val onDemand: OnDemandProgram?,
)

/** A group of a Box Set Categories or Box Set Lists response (clauses 6.8.2.3, 6.8.3.3, 6.10.17.2). */
data class ResultGroup(
    val groupId: String,
    val title: String,
    val image: String?,
    /** The Template XML AIT of a Box Set (clause 5.2.4.4.4), RelatedMaterial with HowRelated templateAIT. */
    val templateAit: String?,
)

/**
 * A page of More Episodes or Box Set results. [links] are the pagination links of table 40 by their
 * relative page name (first, prev, next, last): "the presence of these links shall be used to
 * determine whether there are further pages of results available" (clause 6.9).
 */
data class Results(val items: List<ResultItem>, val groups: List<ResultGroup>, val links: Map<String, String>)

/** A scheduled event joined with its programme information by CRID. */
data class GuideEvent(val crid: String, val start: Long, val end: Long, val info: ProgrammeInfo?) {
    val title: String get() = info?.title?.ifEmpty { null } ?: crid
}

/**
 * Parses content guide responses (TV-Anytime TVAMain, ETSI TS 103 770 V1.2.1 clauses 6.5.4, 6.6.3
 * and 6.10). Ported from the browser client (rt-dvb-i-application public/epg.js). Elements are
 * matched by local name, so a response is read whatever its element order and whatever elements it
 * adds (clause 6.5.4.1).
 */
object GuideParser {

    private const val PROMOTIONAL_STILL = "urn:tva:metadata:cs:HowRelatedCS:2012:19"

    /** Table 40: HowRelated@href of the pagination links, before the relative page name. */
    private const val PAGINATION = "urn:fvc:metadata:cs:HowRelatedCS:2015-12:pagination:"

    /** HowRelated@href of a Box Set's Template XML AIT. */
    private const val TEMPLATE_AIT = "urn:fvc:metadata:cs:HowRelatedCS:2018:templateAIT"

    private val STRUCTURAL = mapOf(
        "crid://dvb.org/metadata/schedules/now-next/now" to "now",
        "crid://dvb.org/metadata/schedules/now-next/later" to "later",
        "crid://dvb.org/metadata/schedules/now-next/earlier" to "earlier",
    )

    private fun root(text: String): Element {
        val root = Xml.parse(text).documentElement
        if (root.local != "TVAMain" || !(root.namespaceURI ?: "").startsWith("urn:tva:metadata:")) {
            throw XmlFormatException("not a TV-Anytime content guide response: the root element is ${root.local}")
        }
        return root
    }

    /** ISO 8601 duration as used by PublishedDuration (days, hours, minutes, seconds) in ms, or 0. */
    fun parseDuration(s: String?): Long {
        val m = Regex("^P(?:(\\d+(?:\\.\\d+)?)D)?(?:T(?:(\\d+(?:\\.\\d+)?)H)?(?:(\\d+(?:\\.\\d+)?)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?$", RegexOption.IGNORE_CASE)
            .find(s?.trim() ?: "") ?: return 0
        fun g(i: Int) = m.groupValues[i].toDoubleOrNull() ?: 0.0
        return ((g(1) * 86400 + g(2) * 3600 + g(3) * 60 + g(4)) * 1000).toLong()
    }

    private fun parseInfo(pi: Element, groups: Map<String, String> = emptyMap(), onDemand: Map<String, OnDemandProgram> = emptyMap()): ProgrammeInfo {
        val bd = pi.child("BasicDescription") ?: pi
        val titles = bd.children("Title")
        val title = (titles.firstOrNull { (it.attr("type") ?: "main") == "main" } ?: titles.firstOrNull())?.text ?: ""
        val programId = pi.attr("programId") ?: ""
        // The longest synopsis held; the guide shows one.
        val synopsis = bd.children("Synopsis").map { it.text }.maxByOrNull { it.length } ?: ""
        val genre = bd.child("Genre")?.let { g -> g.child("Name")?.text?.ifEmpty { null } ?: g.attr("href")?.substringAfterLast(':') }
        val image = promotionalStill(bd)
        val ratings = bd.children("ParentalGuidance").mapNotNull { pg ->
            val age = pg.descendant("MinimumAge")?.text?.toIntOrNull() ?: return@mapNotNull null
            ParentalRating(age, pg.childText("CountryCodes").split(',').map { it.trim() }.filter { it.isNotEmpty() })
        }
        // MemberOf is a child of ProgramInformation (clause 6.10.4); a structural now/next group
        // gives the position (clause 6.5.4.4).
        val pos = pi.children("MemberOf").firstOrNull { STRUCTURAL.containsKey(it.attr("crid")) }
        val members = pi.children("MemberOf").filter { !STRUCTURAL.containsKey(it.attr("crid")) && !it.attr("crid").isNullOrEmpty() }
            .map { m -> val crid = m.attr("crid")!!; Membership(crid, m.attr("index")?.trim()?.toIntOrNull(), groups[crid]) }
        return ProgrammeInfo(
            programId = programId,
            title = title,
            synopsis = synopsis,
            genre = genre,
            image = image,
            ratings = ratings,
            structural = pos?.let { STRUCTURAL[it.attr("crid")] },
            structuralIndex = pos?.let { it.attr("index")?.toIntOrNull() ?: 1 },
            secondaryTitle = titles.firstOrNull { it.attr("type") == "secondary" }?.text?.ifEmpty { null },
            keywords = bd.children("Keyword").map { it.text }.filter { it.isNotEmpty() },
            memberOf = members,
            episodeOf = pi.children("EpisodeOf").mapNotNull { it.attr("crid")?.ifEmpty { null } },
            onDemand = onDemand[programId],
        )
    }

    // GroupInformation@groupId to its Title, from the GroupInformationTable of a response (clause 6.10.17).
    private fun groupTitles(root: Element): Map<String, String> =
        root.descendants("GroupInformation").mapNotNull { gi ->
            val id = gi.attr("groupId") ?: return@mapNotNull null
            val bd = gi.child("BasicDescription") ?: gi
            val titles = bd.children("Title")
            val t = (titles.firstOrNull { (it.attr("type") ?: "main") == "main" } ?: titles.firstOrNull())?.text ?: ""
            if (t.isEmpty()) null else id to t
        }.toMap()

    /** The OnDemandProgram elements of a response by Program@crid (clause 6.10.8.2, table 52). */
    private fun onDemandPrograms(root: Element): Map<String, OnDemandProgram> =
        root.descendants("OnDemandProgram").mapNotNull { od ->
            val crid = od.child("Program")?.attr("crid") ?: return@mapNotNull null
            val pu = od.child("ProgramURL") ?: return@mapNotNull null
            crid to OnDemandProgram(
                serviceIdRef = od.attr("serviceIDRef") ?: "",
                crid = crid,
                programUrl = pu.text,
                programUrlType = pu.attr("contentType") ?: "",
                auxiliaryUrl = od.child("AuxiliaryURL")?.text?.ifEmpty { null },
                start = ServiceListParser.parseInstant(od.childText("StartOfAvailability")),
                end = ServiceListParser.parseInstant(od.childText("EndOfAvailability")),
                durationMs = parseDuration(od.childText("PublishedDuration")),
                free = od.child("Free")?.attr("value")?.trim()?.let { it == "true" || it == "1" },
            )
        }.toMap()

    /**
     * The events of a schedule or now/next response, joined with their ProgramInformation by CRID
     * (clause 6.5.4.1). ActualStartTime and ActualEndTime are used when present, else
     * PublishedStartTime and PublishedDuration. "When the GroupInformationTable is provided in a
     * response, the order of previous, present and future programs shall be determined by the
     * structural CRIDs" (clause 6.5.4.1): earlier events count back from the current one, later
     * ones forward; otherwise events are in start order.
     */
    fun parseSchedule(text: String): List<GuideEvent> {
        val root = root(text)
        val groups = groupTitles(root)
        val onDemand = onDemandPrograms(root)
        val info = root.descendants("ProgramInformation").associate { (it.attr("programId") ?: "") to parseInfo(it, groups, onDemand) }
        val events = ArrayList<GuideEvent>()
        for (ev in root.descendants("ScheduleEvent") + root.descendants("BroadcastEvent")) {
            val crid = ev.child("Program")?.attr("crid") ?: ""
            val actualStart = ServiceListParser.parseInstant(ev.childText("ActualStartTime"))
            val actualEnd = ServiceListParser.parseInstant(ev.childText("ActualEndTime"))
            val start = actualStart ?: ServiceListParser.parseInstant(ev.childText("PublishedStartTime")) ?: continue
            var end = if (actualStart != null && actualEnd != null) actualEnd else start + parseDuration(ev.childText("PublishedDuration"))
            if (end <= start) end = start + parseDuration(ev.childText("PublishedDuration"))
            if (end <= start) continue
            events.add(GuideEvent(crid, start, end, info[crid]))
        }
        val structured = events.any { it.info?.structural != null }
        fun rank(e: GuideEvent): Int? {
            val i = e.info ?: return null
            val n = i.structuralIndex ?: 1
            return when (i.structural) {
                "earlier" -> -n
                "now" -> 0
                "later" -> n
                else -> null
            }
        }
        val sorted = if (structured && events.all { rank(it) != null }) events.sortedBy { rank(it) } else events.sortedBy { it.start }
        val seen = HashSet<Long>()
        return sorted.filter { seen.add(it.start) }
    }

    /** The ProgramInformation for [pid] in a programme information response (clause 6.6.3), or null. */
    fun parseProgramme(text: String, pid: String): ProgrammeInfo? {
        val root = root(text)
        return root.descendants("ProgramInformation").firstOrNull { it.attr("programId") == pid }
            ?.let { parseInfo(it, groupTitles(root), onDemandPrograms(root)) }
    }

    // The first promotional still in a format this client shows (Image.FORMATS, clause 5.2.8.3).
    private fun promotionalStill(bd: Element): String? = Image.firstShown(bd.children("RelatedMaterial")
        .filter { it.child("HowRelated")?.attr("href") == PROMOTIONAL_STILL }
        .flatMap { it.descendants("MediaUri") }
        .map { Image(it.text, it.attr("contentType") ?: "") })?.url

    /**
     * A More Episodes or Box Set response (clauses 6.7.3, 6.8.2.3, 6.8.3.3, 6.8.4.3): its programmes
     * with their MemberOf@index and OnDemandProgram, its groups with their Template XML AIT, and the
     * pagination links of table 40. Ported from the browser client (rt-dvb-i-application
     * public/epg.js parseResults).
     */
    fun parseResults(text: String): Results {
        val root = root(text)
        val onDemand = onDemandPrograms(root)
        val items = root.descendants("ProgramInformation").map { pi ->
            val id = pi.attr("programId") ?: ""
            val bd = pi.child("BasicDescription") ?: pi
            val titles = bd.children("Title")
            ResultItem(
                programId = id,
                title = (titles.firstOrNull { (it.attr("type") ?: "main") == "main" } ?: titles.firstOrNull())?.text?.ifEmpty { null } ?: id,
                subtitle = titles.firstOrNull { it.attr("type") == "secondary" }?.text ?: "",
                synopsis = bd.children("Synopsis").map { it.text }.maxByOrNull { it.length } ?: "",
                image = promotionalStill(bd),
                ratings = bd.children("ParentalGuidance").mapNotNull { pg ->
                    val age = pg.descendant("MinimumAge")?.text?.toIntOrNull() ?: return@mapNotNull null
                    ParentalRating(age, pg.childText("CountryCodes").split(',').map { it.trim() }.filter { it.isNotEmpty() })
                },
                index = pi.children("MemberOf").firstOrNull()?.attr("index")?.trim()?.toIntOrNull(),
                onDemand = onDemand[id],
            )
        }
        val links = LinkedHashMap<String, String>()
        val groups = root.descendants("GroupInformation").map { gi ->
            var templateAit: String? = null
            for (rm in gi.descendants("RelatedMaterial")) {
                val href = rm.child("HowRelated")?.attr("href") ?: ""
                val uri = (rm.descendant("MediaUri")?.text ?: "").replace(Regex("\\s+"), "")
                if (href.startsWith(PAGINATION) && uri.isNotEmpty()) links[href.removePrefix(PAGINATION)] = uri
                if (href == TEMPLATE_AIT) templateAit = rm.descendant("AuxiliaryURI")?.text?.ifEmpty { null }
            }
            val bd = gi.child("BasicDescription") ?: gi
            val titles = bd.children("Title")
            ResultGroup(
                groupId = gi.attr("groupId") ?: "",
                title = (titles.firstOrNull { (it.attr("type") ?: "main") == "main" } ?: titles.firstOrNull())?.text ?: "",
                image = promotionalStill(bd),
                templateAit = templateAit,
            )
        }
        return Results(items, groups, links)
    }

    /**
     * Results in display order: "A DVB-I client shall display results in ascending order using the
     * values from the MemberOf@index attribute." (clause 6.7.3); a programme already listed is
     * dropped.
     */
    fun orderResults(items: List<ResultItem>): List<ResultItem> {
        val seen = HashSet<String>()
        return items.sortedBy { it.index ?: Int.MAX_VALUE }.filter { it.programId.isEmpty() || seen.add(it.programId) }
    }

    /** Now and next of [events] at [nowMs]: from the structural groups when present (clause 6.5.4.4), else by time. */
    fun nowNext(events: List<GuideEvent>, nowMs: Long): Pair<GuideEvent?, GuideEvent?> {
        events.firstOrNull { it.info?.structural == "now" }?.let { onAir ->
            return onAir to events.firstOrNull { it.info?.structural == "later" && it.info.structuralIndex == 1 }
        }
        val i = events.indexOfFirst { it.start <= nowMs && it.end > nowMs }
        if (i >= 0) return events[i] to events.getOrNull(i + 1)
        return null to events.firstOrNull { it.start > nowMs }
    }
}
