/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy (5G-MAGflix design), Jordi J. Gimenez (DVB-I adaptation)
Copyright: (C) 2023-2026 Fraunhofer FOKUS; (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.fivegmag.dvbiclient.R
import com.fivegmag.dvbiclient.guide.GuideEvent
import com.fivegmag.dvbiclient.servicelist.Service
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.DateFormat
import java.util.Date

/**
 * One service of the home screen: its channel number (clause 5.5.12), now and next (clause
 * 6.5.3), the service's rating for the user's country (clause 5.5.28) and its badges.
 */
data class ChannelRow(
    val service: Service,
    val lcn: Int?,
    val minimumAge: Int?,
    var now: GuideEvent? = null,
    var next: GuideEvent? = null,
    var programmeAge: Int? = null,
    var badges: List<Badge> = emptyList(),
    /** Now/next has been answered (or failed); until then nothing is said about the programme. */
    var guideLoaded: Boolean = false,
) {
    /** A linear service: ServiceTypeCS:2019 "linear" or "linear-radio" (annex D.4; table 15 default linear). */
    fun isLinear(): Boolean = ServiceTypes.isLinear(service.serviceType)

    /** How far the programme on now has run, 0 to 100. */
    fun progressPercent(nowMs: Long = System.currentTimeMillis()): Int {
        val n = now ?: return 0
        if (n.end <= n.start) return 0
        return (((nowMs - n.start) * 100) / (n.end - n.start)).toInt().coerceIn(0, 100)
    }
}

/**
 * The channel cards of one category row, after 5G-MAGflix's ContentCardAdapter (rt-5gms-application
 * fivegmag_5GMSdAwareApplication adapter/ContentCardAdapter.kt): the service logo in the poster
 * area with the name over the gradient; below it, now, next and the information badges.
 */
class ContentCardAdapter(
    private val onItemClick: (ChannelRow) -> Unit,
) : RecyclerView.Adapter<ContentCardAdapter.ContentViewHolder>() {

    private val items: ArrayList<ChannelRow> = ArrayList()

    fun updateItems(newItems: List<ChannelRow>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun rowChanged(uid: String) {
        val i = items.indexOfFirst { it.service.uid == uid }
        if (i >= 0) notifyItemChanged(i)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContentViewHolder =
        ContentViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_content_card, parent, false))

    override fun onBindViewHolder(holder: ContentViewHolder, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    inner class ContentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card = itemView as MaterialCardView
        private val posterImage: ImageView = itemView.findViewById(R.id.posterImage)
        private val contentTitle: TextView = itemView.findViewById(R.id.contentTitle)
        private val lcnBadge: TextView = itemView.findViewById(R.id.lcnBadge)
        private val lockIcon: ImageView = itemView.findViewById(R.id.lockIcon)
        private val nowProgress: LinearProgressIndicator = itemView.findViewById(R.id.nowProgress)
        private val nowText: TextView = itemView.findViewById(R.id.nowText)
        private val nextText: TextView = itemView.findViewById(R.id.nextText)
        private val badges: BadgeRow = itemView.findViewById(R.id.badges)

        init {
            // A focus ring for D-pad and keyboard navigation.
            val ring = itemView.resources.getDimensionPixelSize(R.dimen.card_focus_stroke)
            card.setOnFocusChangeListener { _, focused -> card.strokeWidth = if (focused) ring else 0 }
        }

        fun bind(item: ChannelRow) {
            val ctx = itemView.context
            val s = item.service
            val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
            contentTitle.text = s.name
            lcnBadge.text = item.lcn?.toString() ?: ""
            lcnBadge.visibility = if (item.lcn != null) View.VISIBLE else View.GONE

            // The logo, or without one (or when it cannot be loaded) the television or radio icon.
            val logo = s.logo
            val fallback = if (s.serviceType.endsWith("radio")) R.drawable.ic_radio else R.drawable.ic_live_tv
            fun showFallback() {
                val pad = (36 * ctx.resources.displayMetrics.density).toInt()
                posterImage.setPadding(pad, pad, pad, pad)
                posterImage.imageTintList = ColorStateList.valueOf(ctx.getColor(R.color.placeholder_icon))
                posterImage.setImageResource(fallback)
            }
            if (logo != null) {
                posterImage.setPadding(0, 0, 0, 0)
                posterImage.imageTintList = null
                posterImage.load(logo.url) {
                    crossfade(true)
                    placeholder(R.drawable.bg_poster_placeholder)
                    listener(onError = { _, _ -> showFallback() })
                }
            } else {
                showFallback()
            }

            val now = item.now
            val next = item.next
            nowText.text = now?.title ?: if (s.guide != null && item.guideLoaded) ctx.getString(R.string.no_programme_now) else ""
            nextText.text = next?.let { ctx.getString(R.string.next_line, fmt.format(Date(it.start)), it.title) } ?: ""
            nowProgress.visibility = if (now != null) View.VISIBLE else View.INVISIBLE
            nowProgress.progress = item.progressPercent()

            lockIcon.visibility = if (item.badges.any { it.kind == Badge.Kind.RESTRICTED }) View.VISIBLE else View.GONE
            lockIcon.contentDescription = ctx.getString(R.string.restricted)
            lockIcon.tooltipText = ctx.getString(R.string.restricted)
            badges.setBadges(item.badges.filter { it.kind != Badge.Kind.RESTRICTED })

            card.contentDescription = listOfNotNull(
                s.name + (item.lcn?.let { ", " + ctx.getString(R.string.channel_n, it) } ?: ""),
                now?.let { ctx.getString(R.string.now_label, it.title) },
                next?.let { ctx.getString(R.string.next_label, fmt.format(Date(it.start)), it.title) },
                ServiceBadges.describe(ctx, item.badges).ifEmpty { null },
            ).joinToString(". ")
            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}
