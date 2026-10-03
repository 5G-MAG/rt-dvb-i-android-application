/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.servicelist

/**
 * The terms of the classification schemes that SubtitleAttributes refers to (ETSI TS 103 770 V1.2.1
 * clause 4.5.2.3), with the names the schemes give them. The schemes are those of the electronic
 * attachments of ETSI TS 102 822-3-1: SubtitleCarriageCS:2023, SubtitleCodingFormatCS:2023 and
 * SubtitlePurposeCS:2023.
 */
object AccessServices {

    const val CARRIAGE_CS = "urn:tva:metadata:cs:SubtitleCarriageCS:2023:"
    const val CODING_CS = "urn:tva:metadata:cs:SubtitleCodingFormatCS:2023:"
    const val PURPOSE_CS = "urn:tva:metadata:cs:SubtitlePurposeCS:2023:"

    /** SubtitleCarriageCS:2023, termID to Name. */
    val CARRIAGE = mapOf(
        "1" to "Application Subtitles",
        "2" to "Subtitles in TS",
        "2.1" to "DVB TTML Subtitles",
        "3" to "Subtitles in ISOBMFF",
        "4" to "Standalone subtitle resource",
        "5" to "Open/In-video subtitles",
        "99" to "Other subtitle carriage",
    )

    /** SubtitleCodingFormatCS:2023, every termID. */
    val CODING_TERMS = setOf(
        "1", "2", "2.1", "2.1.2", "2.1.3", "2.1.4", "2.1.5", "2.2", "3", "3.1", "3.2", "3.2.1", "3.2.2",
        "3.3", "3.4", "3.4.1", "3.4.2", "3.5", "3.6", "3.6.1", "3.6.1.1", "3.6.1.2", "3.6.1.3", "3.6.1.4",
        "3.6.2", "3.6.2.1", "3.6.2.2", "3.6.2.3", "3.6.2.4", "3.7", "4", "5", "8", "9", "99",
    )

    /** SubtitlePurposeCS:2023, termID to Name. */
    val PURPOSE = mapOf(
        "1" to "Translation",
        "2" to "Hard of hearing",
        "3" to "Audio description",
        "4" to "Content related commentary",
        "5" to "Forced Narrative",
    )

    /** The termID of [href] in the scheme [cs], or null when [href] is not a term of it. */
    fun term(href: String?, cs: String): String? =
        href?.trim()?.takeIf { it.startsWith(cs) }?.removePrefix(cs)?.ifEmpty { null }

    /** The name of the Carriage term of [s], or null when it is not a term of SubtitleCarriageCS:2023. */
    fun carriageName(s: SubtitleAttributes): String? = term(s.carriage, CARRIAGE_CS)?.let { CARRIAGE[it] }

    /** The name of the Purpose term of [s], or null when there is none or it is not a term of SubtitlePurposeCS:2023. */
    fun purposeName(s: SubtitleAttributes): String? = term(s.purpose, PURPOSE_CS)?.let { PURPOSE[it] }

    /**
     * Clause 4.5.2.3: "If the Carriage and/or Coding elements contains a classification scheme term
     * that is not known by or is otherwise unsupported by the DVB-I client, subtitles shall be
     * assumed to be unavailable." Known here means a term of the 2023 schemes above; whether the
     * player then finds the subtitle track in the media is not decided by the service list.
     */
    fun subtitlesKnown(s: SubtitleAttributes): Boolean =
        carriageName(s) != null && s.codings.isNotEmpty() &&
            s.codings.all { term(it, CODING_CS)?.let { t -> t in CODING_TERMS } == true }

    /** Whether [s] signals subtitles for the hard of hearing (SubtitlePurposeCS:2023 term 2). */
    fun hardOfHearing(s: SubtitleAttributes): Boolean = term(s.purpose, PURPOSE_CS) == "2"
}
