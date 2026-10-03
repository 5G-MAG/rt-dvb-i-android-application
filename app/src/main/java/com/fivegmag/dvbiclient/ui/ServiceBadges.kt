/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.ui

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.fivegmag.dvbiclient.R
import com.fivegmag.dvbiclient.mbms.MbmsUrl
import com.fivegmag.dvbiclient.servicelist.AccessServices
import com.fivegmag.dvbiclient.servicelist.Delivery
import com.fivegmag.dvbiclient.servicelist.Instances
import com.fivegmag.dvbiclient.servicelist.LinkedApps
import com.fivegmag.dvbiclient.servicelist.Service
import com.fivegmag.dvbiclient.servicelist.ServiceListRules
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * An information badge: what the service list signals for a service, shown as an icon (or a short
 * label) with its meaning as the tooltip and content description. The set and the tooltip texts
 * follow the channel badges of the browser client (rt-dvb-i-application public/app.js,
 * renderChannelList), worded for this client.
 */
data class Badge(
    val kind: Kind,
    @DrawableRes val icon: Int?,
    val label: String?,
    @ColorRes val color: Int,
    val description: String,
) {
    enum class Kind { LIVE, ON_DEMAND, LINKED_APP, FIVE_G_MS, FIVE_G, FIVE_G_BAD, AGE, PROGRAMME_AGE, AUDIO_DESCRIPTION, SUBTITLES, HARD_OF_HEARING, SIGNING,
        DIALOGUE_ENHANCEMENT, SPOKEN_SUBTITLES, SUBSCRIPTION, CONDITIONAL_ACCESS, DRM, REGION, OFF_AIR,
        BROADCAST_ONLY, MULTICAST_ONLY, DASH, HLS, GUIDE, RESTRICTED }
}

object ServiceBadges {

    /**
     * The badges of [service]. [minimumAge] is its ParentalRating for the user's country (clause
     * 5.5.28), [programmeAge] the content guide's rating of the programme on now, when known.
     */
    fun of(
        service: Service,
        minimumAge: Int?,
        programmeAge: Int? = null,
        parentalThreshold: Int = 0,
        mbmsClient: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
    ): List<Badge> {
        val out = ArrayList<Badge>()
        val instances = service.instances

        // 5G Broadcast: the locator checked against TS 26.347 clause 8.2.2 (clause 9.3.3).
        instances.firstOrNull { it.delivery is Delivery.Mbms }?.let { inst ->
            val locator = (inst.delivery as Delivery.Mbms).locator
            val problem = MbmsUrl.problem(locator)
            val others = instances.any { it.delivery !is Delivery.Mbms }
            out += if (problem != null) {
                Badge(Badge.Kind.FIVE_G_BAD, R.drawable.ic_cell_tower, null, R.color.badge_5g_bad,
                    "5G Broadcast signalling is wrong: $locator $problem (3GPP TS 26.347 clause 8.2.2).")
            } else {
                Badge(Badge.Kind.FIVE_G, R.drawable.ic_cell_tower, null, R.color.badge_5g_ok,
                    "5G Broadcast, priority ${inst.priority}: $locator, MBMS User Service ${MbmsUrl.serviceId(locator)}. " +
                        if (mbmsClient) "An MBMS Client is registered on this device."
                        else "No MBMS Client is available on this device; " +
                            if (others) "another instance of this service plays." else "no other instance is listed.")
            }
        }

        // Parental rating (clause 5.5.28), the programme's from the content guide when known.
        // A MinimumAge of 0 restricts nothing and is not shown, as the channel list did before.
        minimumAge?.takeIf { it > 0 }?.let { out += Badge(Badge.Kind.AGE, null, "$it+", R.color.badge_age, "Minimum parental age rating of the service: $it") }
        if (programmeAge != null && programmeAge != minimumAge) {
            out += Badge(Badge.Kind.PROGRAMME_AGE, null, "$programmeAge+", R.color.badge_age,
                "Minimum age rating of the programme on now, from the content guide: $programmeAge")
        }
        if (ServiceListRules.restricted(parentalThreshold, minimumAge, programmeAge)) {
            out += Badge(Badge.Kind.RESTRICTED, R.drawable.ic_lock, null, R.color.badge_off, "Restricted by your parental setting")
        }

        val access = instances.map { it.accessibility }
        val ad = access.flatMap { it.audioDescription }
        if (ad.isNotEmpty()) out += Badge(Badge.Kind.AUDIO_DESCRIPTION, R.drawable.ic_audio_description, null, R.color.badge_access,
            "Audio description: narration for visually impaired viewers" + languages(ad))
        val subtitles = access.flatMap { it.subtitles }.filter { AccessServices.subtitlesKnown(it) }
        if (subtitles.isNotEmpty()) out += Badge(Badge.Kind.SUBTITLES, R.drawable.ic_subtitles, null, R.color.badge_subtitles,
            "Subtitles: " + subtitles.joinToString("; ") { s ->
                listOfNotNull(s.language.ifEmpty { null }, AccessServices.purposeName(s), AccessServices.carriageName(s)).joinToString(", ")
            })
        if (subtitles.any { AccessServices.hardOfHearing(it) }) out += Badge(Badge.Kind.HARD_OF_HEARING, R.drawable.ic_hearing, null,
            R.color.badge_subtitles, "Subtitles for the hard of hearing" + languages(subtitles.filter { AccessServices.hardOfHearing(it) }.map { it.language }))
        val signing = access.flatMap { it.signing }
        if (signing.isNotEmpty()) out += Badge(Badge.Kind.SIGNING, R.drawable.ic_sign_language, null, R.color.badge_access,
            "In-vision sign language" + languages(signing))
        val de = access.flatMap { it.dialogueEnhancement }
        if (de.isNotEmpty()) out += Badge(Badge.Kind.DIALOGUE_ENHANCEMENT, R.drawable.ic_graphic_eq, null, R.color.badge_access,
            "Dialogue enhancement" + languages(de))
        val spoken = access.flatMap { it.spokenSubtitles }
        if (spoken.isNotEmpty()) out += Badge(Badge.Kind.SPOKEN_SUBTITLES, R.drawable.ic_record_voice_over, null, R.color.badge_access,
            "Spoken subtitles" + languages(spoken))

        // Delivery: broadcast-only or multicast-only services cannot be received by this client.
        val deliveries = instances.map { it.delivery }
        if (deliveries.isNotEmpty() && deliveries.all { it is Delivery.Broadcast }) {
            out += Badge(Badge.Kind.BROADCAST_ONLY, R.drawable.ic_settings_input_antenna, null, R.color.badge_delivery,
                "Broadcast delivery only (" + deliveries.map { (it as Delivery.Broadcast).system }.distinct().joinToString("/") +
                    "): no broadband stream is listed, and this client cannot receive broadcast")
        } else if (deliveries.isNotEmpty() && deliveries.all { it is Delivery.Mbms }) {
            out += Badge(Badge.Kind.BROADCAST_ONLY, R.drawable.ic_settings_input_antenna, null, R.color.badge_delivery,
                "5G Broadcast only: no other instance is listed")
        }
        if (deliveries.isNotEmpty() && deliveries.all { it is Delivery.Managed && it.system == "multicast" }) {
            out += Badge(Badge.Kind.MULTICAST_ONLY, R.drawable.ic_lan, null, R.color.badge_delivery,
                "Multicast delivery only: not receivable by this client")
        }
        if (deliveries.any { it is Delivery.Dash || it is Delivery.DashPlaylist }) out += Badge(Badge.Kind.DASH, null, "DASH",
            R.color.badge_neutral, "Delivered with MPEG-DASH")
        if (deliveries.any { it is Delivery.Hls }) out += Badge(Badge.Kind.HLS, null, "HLS", R.color.badge_neutral,
            "Delivered with HLS (annex G)")

        // Subscription packages (table 16) and content protection (clause 5.5.20).
        val packages = instances.flatMap { it.packages }.distinct()
        if (packages.isNotEmpty()) out += Badge(Badge.Kind.SUBSCRIPTION, R.drawable.ic_card_membership, "SUB", R.color.badge_age,
            "Instances in subscription packages: " + packages.joinToString(", "))
        val ca = instances.flatMap { it.protection?.caSystems ?: emptyList() }.distinct()
        if (ca.isNotEmpty()) out += Badge(Badge.Kind.CONDITIONAL_ACCESS, null, "CA", R.color.badge_age,
            "Conditional access: " + ca.joinToString(", "))
        val drm = instances.flatMap { it.protection?.drmSystems ?: emptyList() }.distinct()
        if (drm.isNotEmpty()) out += Badge(Badge.Kind.DRM, R.drawable.ic_key, null, R.color.badge_age,
            "Content protection, DRM systems: " + drm.joinToString(", "))

        // TargetRegion (table 15) and scheduled service hours (clause 5.2.5.2).
        if (service.targetRegions.isNotEmpty()) out += Badge(Badge.Kind.REGION, R.drawable.ic_location_on, null, R.color.badge_neutral,
            "Restricted to region: " + service.targetRegions.joinToString(", "))
        if (instances.isNotEmpty() && instances.none { Instances.isAvailable(it.availability, nowMs) }) {
            out += Badge(Badge.Kind.OFF_AIR, R.drawable.ic_tv_off, null, R.color.badge_off, "Service currently off-air")
        }

        // Linked applications this client can start (clause 5.2.3, table 7).
        val apps = (service.linkedApps + instances.flatMap { it.linkedApps }).filter { it.startable }.map { it.term }.distinct()
        if (apps.isNotEmpty()) out += Badge(Badge.Kind.LINKED_APP, R.drawable.ic_apps, null, R.color.badge_subtitles,
            "Linked application: " + apps.joinToString(", ") { appTerm(it) })

        if (service.guide != null) out += Badge(Badge.Kind.GUIDE, R.drawable.ic_event_note, null, R.color.badge_neutral,
            "Content guide available")
        return out
    }

    /** The 5GMS badge, shown in the player only while the variant's adapter reports a 5GMS session. */
    fun fiveGms(): Badge = Badge(Badge.Kind.FIVE_G_MS, R.drawable.ic_stream, null, R.color.badge_5g_ok,
        "5G Media Streaming: this session is delivered through 5GMS")

    /** Every badge with what it means in general, for the legend of the About screen. */
    fun legend(): List<Badge> = listOf(
        Badge(Badge.Kind.LIVE, null, "LIVE", R.color.flix_red, "The programme on now of a linear service (ServiceTypeCS linear or linear-radio)"),
        Badge(Badge.Kind.ON_DEMAND, null, "ON-DEMAND", R.color.badge_on_demand, "Can be watched on demand: an OnDemandProgram in its availability window whose XML AIT has an application this client can start (clause 5.2.4)"),
        Badge(Badge.Kind.AGE, null, "12+", R.color.badge_age, "Minimum parental age rating of the service (ParentalRating, clause 5.5.28)"),
        Badge(Badge.Kind.PROGRAMME_AGE, null, "16+", R.color.badge_age, "Minimum age rating of the programme on now, from the content guide (clause 6.10.15); it takes precedence over the service's"),
        Badge(Badge.Kind.RESTRICTED, R.drawable.ic_lock, null, R.color.badge_off, "Restricted by your parental setting"),
        Badge(Badge.Kind.FIVE_G, R.drawable.ic_cell_tower, null, R.color.badge_5g_ok, "5G Broadcast: an instance with an mbms:// locator (clause 9.3.3); reception needs an MBMS Client, not available yet"),
        Badge(Badge.Kind.FIVE_G_MS, R.drawable.ic_stream, null, R.color.badge_5g_ok, "5G Media Streaming: the session is delivered through 5GMS (the 5G Media Streaming variant; not available yet)"),
        Badge(Badge.Kind.FIVE_G_BAD, R.drawable.ic_cell_tower, null, R.color.badge_5g_bad, "5G Broadcast signalling is wrong: the locator is not a valid MBMS URL (3GPP TS 26.347 clause 8.2.2)"),
        Badge(Badge.Kind.AUDIO_DESCRIPTION, R.drawable.ic_audio_description, null, R.color.badge_access, "Audio description (clause 4.5.2.4)"),
        Badge(Badge.Kind.SUBTITLES, R.drawable.ic_subtitles, null, R.color.badge_subtitles, "Subtitles, with their language, purpose and carriage (clause 4.5.2.3)"),
        Badge(Badge.Kind.HARD_OF_HEARING, R.drawable.ic_hearing, null, R.color.badge_subtitles, "Subtitles for the hard of hearing (SubtitlePurposeCS)"),
        Badge(Badge.Kind.SIGNING, R.drawable.ic_sign_language, null, R.color.badge_access, "In-vision sign language (clause 4.5.2.2)"),
        Badge(Badge.Kind.DIALOGUE_ENHANCEMENT, R.drawable.ic_graphic_eq, null, R.color.badge_access, "Dialogue enhancement (clause 4.5.2.5)"),
        Badge(Badge.Kind.SPOKEN_SUBTITLES, R.drawable.ic_record_voice_over, null, R.color.badge_access, "Spoken subtitles (clause 4.5.2.6)"),
        Badge(Badge.Kind.DASH, null, "DASH", R.color.badge_neutral, "Delivered with MPEG-DASH"),
        Badge(Badge.Kind.HLS, null, "HLS", R.color.badge_neutral, "Delivered with HLS (annex G)"),
        Badge(Badge.Kind.BROADCAST_ONLY, R.drawable.ic_settings_input_antenna, null, R.color.badge_delivery, "Broadcast delivery only (DVB-T/S/C or 5G Broadcast): no stream this client can receive is listed"),
        Badge(Badge.Kind.MULTICAST_ONLY, R.drawable.ic_lan, null, R.color.badge_delivery, "Multicast delivery only: not receivable by this client"),
        Badge(Badge.Kind.SUBSCRIPTION, null, "SUB", R.color.badge_age, "Instances in subscription packages (table 16)"),
        Badge(Badge.Kind.CONDITIONAL_ACCESS, null, "CA", R.color.badge_age, "Conditional access systems (clause 5.5.20)"),
        Badge(Badge.Kind.DRM, R.drawable.ic_key, null, R.color.badge_age, "Content protection with DRM systems (clause 5.5.20)"),
        Badge(Badge.Kind.REGION, R.drawable.ic_location_on, null, R.color.badge_neutral, "Restricted to target regions (TargetRegion, table 15)"),
        Badge(Badge.Kind.OFF_AIR, R.drawable.ic_tv_off, null, R.color.badge_off, "Off-air: no instance is within its scheduled service hours (clause 5.2.5.2)"),
        Badge(Badge.Kind.LINKED_APP, R.drawable.ic_apps, null, R.color.badge_subtitles, "Linked application this client can open: with media in parallel, controlling media presentation, for outside the availability period, or the home page (clause 5.2.3)"),
        Badge(Badge.Kind.GUIDE, R.drawable.ic_event_note, null, R.color.badge_neutral, "Content guide available (clause 6)"),
    )

    // The names of the LinkedApplicationCS:2019 terms (DVBLinkedApplicationCS-2019.xml, annex D.2).
    private fun appTerm(term: String): String = when (term) {
        LinkedApps.WITH_MEDIA -> "app with media in parallel"
        LinkedApps.CONTROLLING -> "app controlling media presentation"
        LinkedApps.OUTSIDE_AVAILABILITY -> "app for outside availability period"
        LinkedApps.HOME_PAGE -> "the service provider's home page"
        else -> term
    }

    private fun languages(list: List<String>): String {
        val l = list.filter { it.isNotEmpty() }.distinct()
        return if (l.isEmpty()) "" else " (" + l.joinToString(", ") + ")"
    }

    /** Fills [group] with one view per badge; a tap or a long press shows the meaning. */
    fun bind(group: ViewGroup, badges: List<Badge>) {
        group.removeAllViews()
        val inflater = LayoutInflater.from(group.context)
        for (b in badges) {
            val v: View = if (b.icon != null && b.label == null) {
                (inflater.inflate(R.layout.view_badge_icon, group, false) as ImageView).also {
                    it.setImageResource(b.icon)
                    it.imageTintList = ColorStateList.valueOf(group.context.getColor(b.color))
                }
            } else {
                (inflater.inflate(R.layout.view_badge_label, group, false) as TextView).also {
                    it.text = b.label
                    it.setTextColor(group.context.getColor(b.color))
                }
            }
            v.contentDescription = b.description
            v.tooltipText = b.description
            v.setOnClickListener { explain(it.context, b.description) }
            group.addView(v)
        }
    }

    /** The whole meaning of a badge on a tap; a long press shows it as a tooltip, which may be cut short. */
    fun explain(context: Context, text: String) {
        MaterialAlertDialogBuilder(context).setMessage(text).setPositiveButton(android.R.string.ok, null).show()
    }

    /** All badge meanings as one sentence, for the content description of a card. */
    fun describe(context: Context, badges: List<Badge>): String = badges.joinToString(". ") { it.description }
}
