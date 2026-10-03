/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.servicelist

import com.fivegmag.dvbiclient.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessServicesTest {

    private val service = ServiceListParser.parse(Fixtures.read("service-list-access.xml")).services.single()
    private val access = service.instances[0].accessibility

    @Test
    fun clause4_5_2EveryChildOfAccessibilityAttributesIsRead() {
        assertEquals(2, access.subtitles.size)
        val en = access.subtitles[0]
        assertEquals("urn:tva:metadata:cs:SubtitleCarriageCS:2023:3", en.carriage)
        assertEquals(2, en.codings.size)
        assertEquals("en", en.language)
        assertEquals("urn:tva:metadata:cs:SubtitlePurposeCS:2023:2", en.purpose)
        assertEquals(listOf("en"), access.audioDescription)
        assertEquals(listOf("bfi"), access.signing)
        assertEquals(listOf("es"), access.dialogueEnhancement)
        assertEquals("a SpokenSubtitlesAttributes without AudioLanguage", listOf(""), access.spokenSubtitles)
        assertFalse(access.isEmpty)
    }

    @Test
    fun clause4_5_1NoChildElementMeansNoAccessService() {
        assertTrue(service.instances[1].accessibility.isEmpty)
    }

    @Test
    fun clause4_5_2_3UnknownCarriageTermMeansSubtitlesUnavailable() {
        val (en, fr) = access.subtitles
        assertTrue(AccessServices.subtitlesKnown(en))
        assertEquals("Subtitles in ISOBMFF", AccessServices.carriageName(en))
        assertEquals("Hard of hearing", AccessServices.purposeName(en))
        assertTrue(AccessServices.hardOfHearing(en))
        assertFalse("SubtitleCarriageCS:2023 has no term 7", AccessServices.subtitlesKnown(fr))
        assertNull(AccessServices.carriageName(fr))
        assertNull(AccessServices.purposeName(fr))
        assertFalse(AccessServices.subtitlesKnown(en.copy(codings = listOf("urn:tva:metadata:cs:SubtitleCodingFormatCS:2023:6"))))
        assertFalse(AccessServices.subtitlesKnown(en.copy(codings = emptyList())))
    }
}
