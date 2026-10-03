/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.servicelist

import com.fivegmag.dvbiclient.xml.Xml
import com.fivegmag.dvbiclient.xml.XmlFormatException
import com.fivegmag.dvbiclient.xml.children
import com.fivegmag.dvbiclient.xml.local
import com.fivegmag.dvbiclient.xml.text

/**
 * DVB-I Playlists, ETSI TS 103 770 V1.2.1 clause 5.7.1, table 39: a Playlist of PlaylistEntry
 * elements, each the "Reference to the URL of a DVB-DASH MPD manifest file that is part of the
 * playlist". Ported from the browser client (rt-dvb-i-application public/app.js parsePlaylist).
 */
object Playlists {

    /** The PlaylistEntry URLs of a playlist document, in order. Throws [XmlFormatException] when it is not one. */
    fun parse(text: String): List<String> {
        val root = Xml.parse(text).documentElement
        if (root.local != "Playlist") throw XmlFormatException("not a DVB-I Playlist: the root element is ${root.local}")
        return root.children("PlaylistEntry").map { it.text }.filter { it.isNotEmpty() }
    }
}
