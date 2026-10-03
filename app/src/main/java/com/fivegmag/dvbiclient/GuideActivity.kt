/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import coil.load
import com.fivegmag.dvbiclient.guide.GuideEvent
import com.fivegmag.dvbiclient.servicelist.Service
import com.fivegmag.dvbiclient.servicelist.ServiceListRules
import com.fivegmag.dvbiclient.ui.ProgrammeSheet
import com.google.android.material.appbar.MaterialToolbar
import java.text.DateFormat
import java.util.Date

/**
 * The programme guide grid, as the browser client's EPG grid (rt-dvb-i-application public/epg.js
 * renderGrid): the services of the channel list down the side, time along the top from half an
 * hour ago, each service's schedule (ETSI TS 103 770 V1.2.1 clause 6.5.2) as blocks, and a line at
 * now. A block opens the programme's information (clause 6.6); a service plays it.
 */
class GuideActivity : AppCompatActivity() {

    private var dpPerMinute = 0f
    private var windowStart = 0L
    private var windowEnd = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)
        val toolbar = findViewById<MaterialToolbar>(R.id.guideToolbar)
        toolbar.setNavigationOnClickListener { finish() }
        dpPerMinute = resources.getDimension(R.dimen.guide_dp_per_minute)

        val settings = Settings(this)
        val list = DvbiSession.serviceList
        if (list == null) {
            status(getString(R.string.no_service_list))
            return
        }
        // The services of the channel list, in its order (clause 5.5.12, table 23).
        val numbering = ServiceListRules.assignChannelNumbers(
            ServiceListRules.selectLcnTable(list.lcnTables, settings.region, settings.packages), list.services)
        val services = list.services
            .filter { ServiceListRules.inRegion(it.targetRegions, settings.region) }
            .filter { numbering[it.uid]?.visible != false }
            .sortedWith(compareBy({ numbering[it.uid]?.lcn ?: Int.MAX_VALUE }, { it.docOrder }))
        if (services.none { it.guide != null }) {
            status(getString(R.string.guide_none))
            return
        }

        val now = System.currentTimeMillis()
        windowStart = (now - BEFORE_MS) / HALF_HOUR_MS * HALF_HOUR_MS
        windowEnd = windowStart + WINDOW_MS
        toolbar.subtitle = DateFormat.getDateInstance(DateFormat.FULL).format(Date(now))

        val canvas = findViewById<FrameLayout>(R.id.guideCanvas)
        val column = findViewById<LinearLayout>(R.id.channelColumn)
        val rowH = resources.getDimensionPixelSize(R.dimen.guide_row_height)
        val rulerH = resources.getDimensionPixelSize(R.dimen.guide_ruler_height)
        val width = x(windowEnd).toInt()
        canvas.layoutParams = canvas.layoutParams.also {
            it.width = width
            it.height = rulerH + rowH * services.size
        }
        column.addView(View(this), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rulerH))
        drawRuler(canvas, rulerH)
        services.forEachIndexed { i, s ->
            column.addView(channelCell(s, numbering[s.uid]?.lcn, settings), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, rowH))
            // Row background and separator
            canvas.addView(View(this).also { it.setBackgroundColor(getColor(if (i % 2 == 0) R.color.surface_dark else R.color.card_dark)) },
                FrameLayout.LayoutParams(width, rowH).also { it.topMargin = rulerH + i * rowH })
        }
        val nowLine = View(this).also { it.setBackgroundColor(getColor(R.color.flix_red)) }
        canvas.addView(nowLine, FrameLayout.LayoutParams(dp(2), rulerH + rowH * services.size).also { it.leftMargin = x(now).toInt() })

        var pending = services.count { it.guide != null }
        val loading = findViewById<View>(R.id.guideLoading)
        services.forEachIndexed { i, s ->
            if (s.guide == null) {
                noData(canvas, rulerH + i * rowH, rowH)
                return@forEachIndexed
            }
            DvbiRepository.background({ DvbiRepository.guide.schedule(s, windowStart, windowEnd) }) { r ->
                val events = r.value?.filter { it.end > windowStart && it.start < windowEnd } ?: emptyList()
                if (events.isEmpty()) noData(canvas, rulerH + i * rowH, rowH)
                for (e in events) canvas.addView(block(s, e, now), blockParams(e, rulerH + i * rowH, rowH))
                nowLine.bringToFront()
                keepTitlesInView(findViewById<HorizontalScrollView>(R.id.guideHorizontal).scrollX)
                if (--pending == 0) loading.visibility = View.GONE
            }
        }
        // Open at a quarter of an hour before now.
        val horizontal = findViewById<HorizontalScrollView>(R.id.guideHorizontal)
        horizontal.setOnScrollChangeListener { _, sx, _, _, _ -> keepTitlesInView(sx) }
        horizontal.post { horizontal.scrollTo(maxOf(0, x(now - QUARTER_MS).toInt()), 0) }
    }

    private val blocks = ArrayList<TextView>()

    // Text in the app's font: the Ubuntu text appearance on an AppCompatTextView.
    private fun text(): TextView = AppCompatTextView(this).also { TextViewCompat.setTextAppearance(it, R.style.GuideText) }

    // A programme that started before the visible part keeps its title in view.
    private fun keepTitlesInView(scrollX: Int) {
        for (b in blocks) {
            val lp = b.layoutParams as FrameLayout.LayoutParams
            val inset = (scrollX - lp.leftMargin).coerceIn(0, maxOf(0, lp.width - dp(48)))
            b.setPadding(dp(8) + inset, b.paddingTop, b.paddingRight, b.paddingBottom)
        }
    }

    private fun x(ms: Long): Float = (ms - windowStart) / 60_000f * dpPerMinute

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun status(text: String) {
        findViewById<View>(R.id.guideLoading).visibility = View.GONE
        findViewById<TextView>(R.id.guideStatus).also { it.text = text; it.visibility = View.VISIBLE }
    }

    // Time labels every half hour.
    private fun drawRuler(canvas: FrameLayout, h: Int) {
        val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        var t = windowStart
        while (t < windowEnd) {
            val label = text().also {
                it.text = fmt.format(Date(t))
                it.setTextColor(getColor(R.color.on_surface_secondary))
                it.textSize = 12f
                it.gravity = Gravity.CENTER_VERTICAL
                it.setPadding(dp(6), 0, 0, 0)
                it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            canvas.addView(label, FrameLayout.LayoutParams((HALF_HOUR_MS / 60_000f * dpPerMinute).toInt(), h).also { it.leftMargin = x(t).toInt() })
            canvas.addView(View(this).also { it.setBackgroundColor(getColor(R.color.divider_dark)) },
                FrameLayout.LayoutParams(dp(1), h).also { it.leftMargin = x(t).toInt() })
            t += HALF_HOUR_MS
        }
    }

    private fun channelCell(s: Service, lcn: Int?, settings: Settings): View {
        val cell = LinearLayout(this).also {
            it.orientation = LinearLayout.VERTICAL
            it.gravity = Gravity.CENTER
            it.setPadding(dp(4), dp(4), dp(4), dp(4))
            it.setBackgroundResource(android.R.drawable.list_selector_background)
            it.isClickable = true
            it.isFocusable = true
            it.contentDescription = s.name + (lcn?.let { n -> ", " + getString(R.string.channel_n, n) } ?: "")
            it.setOnClickListener { startActivity(PlayerActivity.intent(this, s.uid, settings.packages)) }
        }
        val logo = s.logo
        if (logo != null) {
            cell.addView(ImageView(this).also {
                it.scaleType = ImageView.ScaleType.FIT_CENTER
                it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                it.load(logo.url)
            }, LinearLayout.LayoutParams(dp(56), dp(38)))
        }
        cell.addView(text().also {
            it.text = listOfNotNull(lcn?.toString(), if (logo == null) s.name else null).joinToString(" ")
            it.setTextColor(getColor(R.color.on_surface_primary))
            it.textSize = 12f
            it.maxLines = 2
            it.gravity = Gravity.CENTER
            it.ellipsize = TextUtils.TruncateAt.END
            it.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        return cell
    }

    private fun noData(canvas: FrameLayout, top: Int, h: Int) {
        canvas.addView(text().also {
            it.text = getString(R.string.guide_no_data)
            it.setTextColor(getColor(R.color.on_surface_secondary))
            it.textSize = 12f
            it.gravity = Gravity.CENTER_VERTICAL
            it.setPadding(dp(12), 0, 0, 0)
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, h).also { it.topMargin = top; it.leftMargin = x(System.currentTimeMillis()).toInt() })
    }

    private fun blockParams(e: GuideEvent, top: Int, h: Int): FrameLayout.LayoutParams {
        val left = x(maxOf(e.start, windowStart))
        val right = x(minOf(e.end, windowEnd))
        return FrameLayout.LayoutParams(maxOf(dp(2), (right - left).toInt() - dp(2)), h - dp(6)).also {
            it.leftMargin = left.toInt() + dp(1)
            it.topMargin = top + dp(3)
        }
    }

    private fun block(s: Service, e: GuideEvent, now: Long): View {
        val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        val isNow = e.start <= now && e.end > now
        return text().also {
            it.text = "${e.title}\n${fmt.format(Date(e.start))}"
            it.setTextColor(getColor(if (e.end <= now) R.color.on_surface_secondary else R.color.on_surface_primary))
            it.textSize = 12f
            it.maxLines = 2
            it.ellipsize = TextUtils.TruncateAt.END
            it.setPadding(dp(8), dp(4), dp(6), dp(4))
            it.gravity = Gravity.CENTER_VERTICAL
            it.setBackgroundResource(if (isNow) R.drawable.bg_schedule_event_now else R.drawable.bg_schedule_event)
            it.foreground = getDrawable(android.R.drawable.list_selector_background)
            it.isClickable = true
            it.isFocusable = true
            it.contentDescription = "${s.name}, ${fmt.format(Date(e.start))} to ${fmt.format(Date(e.end))}, ${e.title}" +
                if (isNow) ", " + getString(R.string.on_air_now) else ""
            it.setOnClickListener { ProgrammeSheet.request(this, s, e) }
            blocks.add(it)
        }
    }

    companion object {
        private const val HALF_HOUR_MS = 1_800_000L
        private const val BEFORE_MS = 1_800_000L
        private const val QUARTER_MS = 900_000L
        /** The grid covers six hours. */
        private const val WINDOW_MS = 6 * 3_600_000L

        fun intent(context: Context): Intent = Intent(context, GuideActivity::class.java)
    }
}
