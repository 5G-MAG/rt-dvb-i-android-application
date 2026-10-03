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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.fivegmag.dvbiclient.R

/**
 * Badges on one line that never wraps, so every channel card has the same height: the badges that
 * fit are shown, and the rest are counted in a "+n" badge whose tooltip lists them.
 */
class BadgeRow @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup(context, attrs) {

    private val spacing = (4 * resources.displayMetrics.density).toInt()
    private val rowHeight = (18 * resources.displayMetrics.density).toInt()
    private val overflow: TextView =
        LayoutInflater.from(context).inflate(R.layout.view_badge_label, this, false) as TextView
    private var badges: List<Badge> = emptyList()
    private var badgeViews: List<View> = emptyList()
    private var shown = 0

    fun setBadges(list: List<Badge>) {
        removeAllViews()
        badges = list
        val holder = ArrayList<View>()
        ServiceBadges.bind(this, list)
        for (i in 0 until childCount) holder.add(getChildAt(i))
        badgeViews = holder
        overflow.setTextColor(context.getColor(R.color.on_surface_secondary))
        overflow.setOnClickListener { ServiceBadges.explain(context, badges.drop(shown).joinToString("\n\n") { b -> b.description }) }
        addView(overflow)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY)
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        badgeViews.forEach { v ->
            val w = v.layoutParams?.width ?: 0
            v.measure(if (w > 0) MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY) else free, h)
        }
        fun widthOf(n: Int) = badgeViews.take(n).sumOf { it.measuredWidth } + spacing * maxOf(0, n - 1)
        shown = badgeViews.size
        if (widthOf(shown) > width) {
            // Leave room for the "+n" badge.
            shown = badgeViews.size - 1
            while (shown > 0) {
                overflow.text = "+${badgeViews.size - shown}"
                overflow.measure(free, h)
                if (widthOf(shown) + spacing + overflow.measuredWidth <= width) break
                shown--
            }
        }
        val hidden = badges.drop(shown)
        overflow.visibility = if (hidden.isEmpty()) View.GONE else View.VISIBLE
        if (hidden.isNotEmpty()) {
            overflow.text = "+${hidden.size}"
            overflow.measure(free, h)
            val text = hidden.joinToString("\n") { it.description }
            overflow.tooltipText = text
            overflow.contentDescription = text
        }
        badgeViews.forEachIndexed { i, v -> v.visibility = if (i < shown) View.VISIBLE else View.GONE }
        setMeasuredDimension(width, rowHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var x = 0
        for (v in badgeViews.take(shown) + listOfNotNull(overflow.takeIf { it.visibility == View.VISIBLE })) {
            v.layout(x, 0, x + v.measuredWidth, rowHeight)
            x += v.measuredWidth + spacing
        }
    }
}
