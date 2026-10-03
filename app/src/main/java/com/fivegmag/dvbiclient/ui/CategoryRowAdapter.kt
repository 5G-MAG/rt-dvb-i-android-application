/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy (5G-MAGflix design), Jordi J. Gimenez (DVB-I adaptation)
Copyright: (C) 2023-2026 Fraunhofer FOKUS; (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.fivegmag.dvbiclient.R
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.DateFormat
import java.util.Date

/** The services of one ServiceType (table 15), for one category row. */
data class ChannelCategory(val label: String, val serviceType: String, val rows: List<ChannelRow>)

/**
 * Outer vertical RecyclerView adapter of the home screen, after 5G-MAGflix's CategoryRowAdapter
 * (rt-5gms-application fivegmag_5GMSdAwareApplication adapter/CategoryRowAdapter.kt). Two view
 * types: the hero banner (position 0 when a service is featured) and category rows, horizontal
 * carousels of channel cards. Unlike 5G-MAGflix, each row keeps its card adapter, so that now/next
 * arriving for one service updates its card without resetting the row's scroll position.
 */
class CategoryRowAdapter(
    private val onItemClick: (ChannelRow) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HERO = 0
        private const val VIEW_TYPE_CATEGORY = 1
        private const val PAYLOAD_ROW = "row"
    }

    private val categories: ArrayList<ChannelCategory> = ArrayList()
    private val viewPool = RecyclerView.RecycledViewPool()
    private var heroItem: ChannelRow? = null

    /** Sets the featured service shown at the top of the list; null removes the hero banner. */
    fun setHeroItem(item: ChannelRow?) {
        heroItem = item
    }

    fun updateCategories(newCategories: List<ChannelCategory>) {
        categories.clear()
        categories.addAll(newCategories)
        notifyDataSetChanged()
    }

    /** Rebinds the card of [uid], and the hero banner when it shows that service. */
    fun rowChanged(uid: String) {
        if (heroItem?.service?.uid == uid) notifyItemChanged(0)
        categories.forEachIndexed { i, c ->
            if (c.rows.any { it.service.uid == uid }) notifyItemChanged(i + heroOffset(), PAYLOAD_ROW + uid)
        }
    }

    private fun hasHero(): Boolean = heroItem != null

    private fun heroOffset(): Int = if (hasHero()) 1 else 0

    override fun getItemViewType(position: Int): Int {
        return if (hasHero() && position == 0) VIEW_TYPE_HERO else VIEW_TYPE_CATEGORY
    }

    override fun getItemCount(): Int = categories.size + heroOffset()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_HERO -> HeroViewHolder(inflater.inflate(R.layout.item_hero_banner, parent, false))
            else -> CategoryViewHolder(inflater.inflate(R.layout.item_category_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeroViewHolder -> heroItem?.let { holder.bind(it) }
            is CategoryViewHolder -> holder.bind(categories[position - heroOffset()])
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val uids = payloads.mapNotNull { (it as? String)?.takeIf { p -> p.startsWith(PAYLOAD_ROW) }?.removePrefix(PAYLOAD_ROW) }
        if (holder is CategoryViewHolder && uids.size == payloads.size && payloads.isNotEmpty()) {
            uids.forEach { holder.rowChanged(it) }
        } else {
            onBindViewHolder(holder, position)
        }
    }

    /** The hero banner: the featured service and what is on now. */
    inner class HeroViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val posterImage: ImageView = itemView.findViewById(R.id.heroPosterImage)
        private val logo: ImageView = itemView.findViewById(R.id.heroLogo)
        private val lcn: TextView = itemView.findViewById(R.id.heroLcn)
        private val badge: TextView = itemView.findViewById(R.id.heroBadge)
        private val titleText: TextView = itemView.findViewById(R.id.heroTitle)
        private val descriptionText: TextView = itemView.findViewById(R.id.heroDescription)
        private val progress: LinearProgressIndicator = itemView.findViewById(R.id.heroProgress)

        fun bind(item: ChannelRow) {
            val s = item.service
            val now = item.now
            val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
            titleText.text = now?.title ?: s.name
            // LIVE: the programme on now of a linear service (ServiceTypeCS linear, linear-radio).
            badge.visibility = if (now != null && item.isLinear()) View.VISIBLE else View.GONE
            lcn.text = item.lcn?.toString() ?: ""
            lcn.visibility = if (item.lcn != null) View.VISIBLE else View.GONE
            descriptionText.text = listOfNotNull(
                if (now != null) "${s.name} · ${fmt.format(Date(now.start))} to ${fmt.format(Date(now.end))}" else s.provider.ifEmpty { null },
                item.next?.let { itemView.context.getString(R.string.next_line, fmt.format(Date(it.start)), it.title) },
            ).joinToString("\n")
            progress.visibility = if (now != null) View.VISIBLE else View.INVISIBLE
            progress.progress = item.progressPercent()

            // The programme's image from the content guide fills the banner; without one, the logo.
            val image = now?.info?.image
            val logoUrl = s.logo?.url
            posterImage.setPadding(0, 0, 0, 0)
            posterImage.imageTintList = null
            if (image != null) {
                posterImage.scaleType = ImageView.ScaleType.CENTER_CROP
                posterImage.load(image) {
                    crossfade(true)
                    placeholder(R.drawable.bg_poster_placeholder)
                    error(R.drawable.bg_poster_placeholder)
                }
            } else if (logoUrl != null) {
                posterImage.scaleType = ImageView.ScaleType.FIT_CENTER
                posterImage.load(logoUrl) { crossfade(true) }
            } else {
                // No image and no logo: the television or radio icon, as on the cards.
                val pad = (48 * itemView.resources.displayMetrics.density).toInt()
                posterImage.scaleType = ImageView.ScaleType.FIT_CENTER
                posterImage.setPadding(pad, pad, pad, pad)
                posterImage.imageTintList = android.content.res.ColorStateList.valueOf(itemView.context.getColor(R.color.placeholder_icon))
                posterImage.setImageResource(if (s.serviceType.endsWith("radio")) R.drawable.ic_radio else R.drawable.ic_live_tv)
            }
            if (logoUrl != null) logo.load(logoUrl) { crossfade(true) } else logo.setImageDrawable(null)
            logo.visibility = if (logoUrl != null) View.VISIBLE else View.GONE

            itemView.contentDescription = itemView.context.getString(R.string.hero_description, s.name) +
                (now?.let { ". " + itemView.context.getString(R.string.now_label, it.title) } ?: "")
            itemView.setOnClickListener { onItemClick(item) }
        }
    }

    /** A category row: a header label and a horizontal carousel of channel cards. */
    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val categoryLabel: TextView = itemView.findViewById(R.id.categoryLabel)
        private val categoryRecyclerView: RecyclerView = itemView.findViewById(R.id.categoryRecyclerView)
        private val horizontalAdapter = ContentCardAdapter(onItemClick)

        init {
            categoryRecyclerView.layoutManager = LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
            categoryRecyclerView.adapter = horizontalAdapter
            categoryRecyclerView.setRecycledViewPool(viewPool)
        }

        fun bind(category: ChannelCategory) {
            categoryLabel.text = category.label
            horizontalAdapter.updateItems(category.rows)
        }

        fun rowChanged(uid: String) = horizontalAdapter.rowChanged(uid)
    }
}
