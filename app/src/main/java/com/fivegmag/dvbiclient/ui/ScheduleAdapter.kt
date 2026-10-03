/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.fivegmag.dvbiclient.R
import com.fivegmag.dvbiclient.guide.GuideEvent
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The events of a schedule grouped by local day, each day under its header. The event on now is
 * highlighted, with a LIVE badge when the service is linear; past events are dimmed.
 */
class ScheduleAdapter(
    events: List<GuideEvent>,
    private val linear: Boolean,
    private val nowMs: Long,
    private val onClick: (GuideEvent) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private sealed class Item {
        data class Day(val startOfDay: Long) : Item()
        data class Event(val event: GuideEvent) : Item()
    }

    private val items: List<Item> = buildList {
        var day = Long.MIN_VALUE
        for (e in events.sortedBy { it.start }) {
            val d = startOfDay(e.start)
            if (d != day) { add(Item.Day(d)); day = d }
            add(Item.Event(e))
        }
    }

    /** Where to scroll to show the event on now, or the first one to come. */
    fun positionOfNow(): Int? {
        val i = items.indexOfFirst { it is Item.Event && it.event.end > nowMs }
        if (i < 0) return null
        // One item before it, so the programme just finished (or the day) is in view too.
        return maxOf(0, i - 1)
    }

    override fun getItemViewType(position: Int): Int = if (items[position] is Item.Day) 0 else 1

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) DayHolder(inflater.inflate(R.layout.item_schedule_day, parent, false))
        else EventHolder(inflater.inflate(R.layout.item_schedule_event, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.Day -> (holder as DayHolder).bind(item.startOfDay)
            is Item.Event -> (holder as EventHolder).bind(item.event)
        }
    }

    private fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    inner class DayHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val label: TextView = v.findViewById(R.id.dayLabel)

        fun bind(day: Long) {
            val date = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(day))
            val ctx = itemView.context
            label.text = when (day) {
                startOfDay(nowMs) -> ctx.getString(R.string.today, date)
                startOfDay(nowMs + DateUtils.DAY_IN_MILLIS) -> ctx.getString(R.string.tomorrow, date)
                startOfDay(nowMs - DateUtils.DAY_IN_MILLIS) -> ctx.getString(R.string.yesterday, date)
                else -> date
            }
        }
    }

    inner class EventHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val time: TextView = v.findViewById(R.id.eventTime)
        private val title: TextView = v.findViewById(R.id.eventTitle)
        private val meta: TextView = v.findViewById(R.id.eventMeta)
        private val badge: View = v.findViewById(R.id.eventBadge)
        private val nowBar: View = v.findViewById(R.id.nowBar)
        private val progress: LinearProgressIndicator = v.findViewById(R.id.eventProgress)

        fun bind(e: GuideEvent) {
            val ctx = itemView.context
            val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
            val isNow = e.start <= nowMs && e.end > nowMs
            val past = e.end <= nowMs
            val minutes = ((e.end - e.start) / 60_000L).toInt()
            time.text = fmt.format(Date(e.start))
            title.text = e.title
            meta.text = listOfNotNull(ctx.getString(R.string.minutes, minutes), e.info?.genre).joinToString(" · ")
            badge.visibility = if (isNow && linear) View.VISIBLE else View.GONE
            nowBar.visibility = if (isNow) View.VISIBLE else View.INVISIBLE
            itemView.setBackgroundResource(if (isNow) R.drawable.bg_schedule_event_now else R.drawable.bg_schedule_event)
            itemView.alpha = if (past) 0.55f else 1f
            progress.visibility = if (isNow) View.VISIBLE else View.GONE
            if (isNow) progress.progress = (((nowMs - e.start) * 100) / (e.end - e.start)).toInt().coerceIn(0, 100)
            itemView.contentDescription = "${fmt.format(Date(e.start))} ${e.title}, " + ctx.getString(R.string.minutes, minutes) +
                if (isNow) ", " + ctx.getString(R.string.on_air_now) else ""
            itemView.setOnClickListener { onClick(e) }
        }
    }
}
