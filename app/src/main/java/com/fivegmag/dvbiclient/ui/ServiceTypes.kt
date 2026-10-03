/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.ui

import com.fivegmag.dvbiclient.R

/** The terms of ServiceTypeCS:2019 (ETSI TS 103 770 V1.2.1 annex D.4), in the scheme's order, and their labels. */
object ServiceTypes {

    private const val CS = "urn:dvb:metadata:cs:ServiceTypeCS:2019:"

    val LABELS: Map<String, Int> = linkedMapOf(
        CS + "linear" to R.string.category_tv,
        CS + "linear-radio" to R.string.category_radio,
        CS + "ondemand" to R.string.category_ondemand_tv,
        CS + "ondemand-radio" to R.string.category_ondemand_radio,
        CS + "data" to R.string.category_data,
        CS + "mosaic" to R.string.category_mosaic,
        CS + "other" to R.string.category_other,
    )

    /** The label of [href], or "Other services" for a term outside the scheme. */
    fun label(href: String): Int = LABELS[href] ?: R.string.category_other

    /** A linear service: "linear" or "linear-radio"; its programme on now is LIVE. */
    fun isLinear(href: String): Boolean = href == CS + "linear" || href == CS + "linear-radio"
}
