/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy (5G-MAGflix design), Jordi J. Gimenez (DVB-I client and adaptation)
Copyright: (C) 2023-2026 Fraunhofer FOKUS; (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.facebook.shimmer.ShimmerFrameLayout
import com.fivegmag.dvbiclient.guide.GuideParser
import com.fivegmag.dvbiclient.http.TlsCheck
import com.fivegmag.dvbiclient.servicelist.ServiceList
import com.fivegmag.dvbiclient.servicelist.ServiceListRules
import com.fivegmag.dvbiclient.ui.CategoryRowAdapter
import com.fivegmag.dvbiclient.ui.ChannelCategory
import com.fivegmag.dvbiclient.ui.ChannelRow
import com.fivegmag.dvbiclient.ui.ServiceBadges
import com.fivegmag.dvbiclient.ui.ServiceTypes
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * The home screen, in the layout of 5G-MAGflix's landing page (rt-5gms-application
 * fivegmag_5GMSdAwareApplication MainActivity): a hero banner for the selected or first service
 * with what is on now, then one row per ServiceType (table 15) of the installed service list.
 *
 * Services are numbered by the LCN table of the user's region (ETSI TS 103 770 V1.2.1 clause
 * 5.5.12), shown with logo, now/next (clause 6.5.3) and the badges of what the list signals.
 * Services with LCN@visible false are left out and reached by entering their channel number
 * (table 23).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var settings: Settings
    private lateinit var adapter: CategoryRowAdapter
    private lateinit var listText: TextView
    private lateinit var noticeBar: View
    private lateinit var noticeSummary: TextView
    private lateinit var noticeDetail: TextView
    private lateinit var noticeIcon: ImageView
    private lateinit var noticeExpand: ImageView
    private lateinit var emptyState: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var shimmerLayout: ShimmerFrameLayout
    private var numbering: Map<String, ServiceListRules.Numbering> = emptyMap()
    private var rows: List<ChannelRow> = emptyList()
    private var isContentLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Keep the splash screen until the first load has an outcome, as 5G-MAGflix does.
        splashScreen.setKeepOnScreenCondition { !isContentLoaded }
        settings = Settings(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.mainToolbar)
        toolbar.inflateMenu(R.menu.menu_main)
        toolbar.setOnMenuItemClickListener { onMenuItemSelected(it) }
        setupLogo()
        listText = findViewById(R.id.listText)

        noticeBar = findViewById(R.id.noticeBar)
        noticeSummary = findViewById(R.id.noticeSummary)
        noticeDetail = findViewById(R.id.noticeDetail)
        noticeIcon = findViewById(R.id.noticeIcon)
        noticeExpand = findViewById(R.id.noticeExpand)
        noticeBar.setOnClickListener { setNoticeExpanded(noticeDetail.visibility != View.VISIBLE) }

        emptyState = findViewById(R.id.emptyState)
        findViewById<Button>(R.id.retryButton).setOnClickListener { load() }
        findViewById<Button>(R.id.settingsButton).setOnClickListener { openSettings() }

        swipeRefresh = findViewById(R.id.swipeRefresh)
        shimmerLayout = findViewById(R.id.shimmerLayout)
        swipeRefresh.setColorSchemeResources(R.color.fivegmag_blue)
        swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.surface_dark_elevated)
        swipeRefresh.setOnRefreshListener { load() }

        adapter = CategoryRowAdapter { select(it) }
        findViewById<RecyclerView>(R.id.contentGrid).also {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = adapter
        }
    }

    // "5G-MAG" in 5G-MAG blue and "flix" in red, as 5G-MAGflix's logo text, then "for DVB-I".
    private fun setupLogo() {
        val part1 = getString(R.string.logo_text_5gmag)
        val part2 = getString(R.string.logo_text_flix)
        val full = part1 + part2 + getString(R.string.logo_text_dvbi)
        val spannable = SpannableString(full)
        val flixEnd = part1.length + part2.length
        spannable.setSpan(ForegroundColorSpan(getColor(R.color.fivegmag_blue)), 0, part1.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(getColor(R.color.flix_red)), part1.length, flixEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(getColor(R.color.on_surface_primary)), flixEnd, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        findViewById<TextView>(R.id.logoText).also {
            it.text = spannable
            it.contentDescription = getString(R.string.app_full_name)
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun onMenuItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_reload -> { load(); true }
        R.id.action_settings -> { openSettings(); true }
        R.id.action_channel_number -> { askChannelNumber(); true }
        R.id.action_guide -> { startActivity(GuideActivity.intent(this)); true }
        R.id.action_about -> { startActivity(Intent(this, AboutActivity::class.java)); true }
        else -> false
    }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun load() {
        val urls = settings.serviceListUrls
        if (urls.isEmpty()) {
            showEmptyState(R.drawable.ic_live_tv, getString(R.string.no_service_list), getString(R.string.no_service_list_hint))
            return
        }
        if (DvbiSession.serviceList == null && !swipeRefresh.isRefreshing) showLoading()
        DvbiRepository.loadServiceList(urls, settings.expectedListId, settings.language) { r ->
            swipeRefresh.isRefreshing = false
            val list = r.list
            if (list == null) {
                showNotices(urls.first(), null, emptyList())
                showEmptyState(R.drawable.ic_wifi_off, getString(R.string.load_failed), r.problems.joinToString("\n"))
                return@loadServiceList
            }
            showNotices(r.url ?: urls.first(), list, r.problems)
            listText.text = list.name.ifEmpty { "(unnamed service list)" } + (list.version?.let { " · version $it" } ?: "")
            listText.visibility = View.VISIBLE
            show(list)
        }
    }

    private fun showLoading() {
        emptyState.visibility = View.GONE
        swipeRefresh.visibility = View.GONE
        shimmerLayout.visibility = View.VISIBLE
        shimmerLayout.startShimmer()
    }

    private fun showEmptyState(icon: Int, title: String, text: String) {
        shimmerLayout.stopShimmer()
        shimmerLayout.visibility = View.GONE
        swipeRefresh.isRefreshing = false
        swipeRefresh.visibility = View.GONE
        listText.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        findViewById<ImageView>(R.id.emptyStateIcon).setImageResource(icon)
        findViewById<TextView>(R.id.emptyStateTitle).text = title
        findViewById<TextView>(R.id.emptyStateText).also {
            it.text = text
            it.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
        isContentLoaded = true
    }

    // Clause 7.3 warnings for the service list URL and each content guide endpoint on plain HTTP,
    // and the problems of the load (for example a ServiceList@id that is not the registry's
    // ServiceListId). One line; the full text on a tap.
    private fun showNotices(listUrl: String, list: ServiceList?, problems: List<String>) {
        val local = NetworkInfo.localAddresses(this)
        val urls = listOf(listUrl) + (list?.services?.mapNotNull { it.guide?.schedule } ?: emptyList())
        val warnings = urls.distinctBy { origin(it) }.mapNotNull { TlsCheck.plainHttpWarning(it, local) }
        val listProblems = if (list != null) problems else emptyList()
        if (warnings.isEmpty() && listProblems.isEmpty()) {
            noticeBar.visibility = View.GONE
            return
        }
        noticeBar.visibility = View.VISIBLE
        noticeIcon.setImageResource(if (listProblems.isNotEmpty()) R.drawable.ic_warning else R.drawable.ic_lock_open)
        noticeSummary.text = listOfNotNull(
            warnings.size.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.notice_plain_http, it, it) },
            listProblems.size.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.notice_problems, it, it) },
        ).joinToString(" · ")
        noticeDetail.text = (listProblems + warnings).joinToString("\n\n")
        setNoticeExpanded(noticeDetail.visibility == View.VISIBLE)
    }

    private fun setNoticeExpanded(expanded: Boolean) {
        noticeDetail.visibility = if (expanded) View.VISIBLE else View.GONE
        noticeExpand.setImageResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more)
        noticeBar.contentDescription = noticeSummary.text.toString() + ". " +
            getString(if (expanded) R.string.notice_collapse else R.string.notice_expand)
    }

    private fun origin(url: String): String = try {
        java.net.URI(url).let { "${it.scheme}://${it.authority}" }
    } catch (_: Exception) {
        url
    }

    private fun show(list: ServiceList) {
        val table = ServiceListRules.selectLcnTable(list.lcnTables, settings.region, settings.packages)
        numbering = ServiceListRules.assignChannelNumbers(table, list.services)
        val country = settings.country.ifEmpty { null }
        rows = list.services
            .filter { ServiceListRules.inRegion(it.targetRegions, settings.region) }
            // Table 23, @visible: hidden services are not listed.
            .filter { numbering[it.uid]?.visible != false }
            .sortedWith(compareBy({ numbering[it.uid]?.lcn ?: Int.MAX_VALUE }, { it.docOrder }))
            .map { ChannelRow(it, numbering[it.uid]?.lcn, ServiceListRules.minimumAgeFor(it.ratings, country)) }
        for (row in rows) row.badges = badgesOf(row)

        shimmerLayout.stopShimmer()
        shimmerLayout.visibility = View.GONE
        if (rows.isEmpty()) {
            showEmptyState(R.drawable.ic_live_tv, getString(R.string.no_services), "")
            return
        }
        emptyState.visibility = View.GONE
        swipeRefresh.visibility = View.VISIBLE
        adapter.setHeroItem(rows.firstOrNull { it.service.uid == lastSelectedUid } ?: rows.first())
        adapter.updateCategories(categories(rows))
        isContentLoaded = true
        for (row in rows) loadNowNext(row)
    }

    private fun badgesOf(row: ChannelRow) = ServiceBadges.of(
        row.service, row.minimumAge, row.programmeAge, settings.parentalThreshold, DvbiSession.mbmsRegistered,
    )

    // One row per ServiceType, in the order of the terms of ServiceTypeCS (annex D.4); a type
    // outside it last.
    private fun categories(rows: List<ChannelRow>): List<ChannelCategory> {
        val order = ServiceTypes.LABELS.keys.toList()
        return rows.groupBy { it.service.serviceType }
            .entries
            .sortedBy { e -> order.indexOf(e.key).let { if (it < 0) Int.MAX_VALUE else it } }
            .map { (type, r) -> ChannelCategory(getString(ServiceTypes.label(type)), type, r) }
    }

    private fun loadNowNext(row: ChannelRow) {
        if (row.service.guide == null) return
        DvbiRepository.background({ DvbiRepository.guide.nowNext(row.service) }) { r ->
            if (r.reacquireServiceList) load()
            row.guideLoaded = true
            val events = r.value
            if (events == null) {
                adapter.rowChanged(row.service.uid)
                return@background
            }
            val (now, next) = GuideParser.nowNext(events, System.currentTimeMillis())
            row.now = now
            row.next = next
            row.programmeAge = now?.info?.let { ServiceListRules.minimumAgeFor(it.ratings, settings.country.ifEmpty { null }) }
            row.badges = badgesOf(row)
            adapter.rowChanged(row.service.uid)
        }
    }

    // Table 23: a service with @visible false is reachable by its number unless @selectable is false.
    private fun askChannelNumber() {
        val input = EditText(this).also {
            it.inputType = InputType.TYPE_CLASS_NUMBER
            it.imeOptions = EditorInfo.IME_ACTION_GO
            it.hint = getString(R.string.channel_number_hint)
        }
        val box = FrameLayout(this).also {
            val pad = resources.getDimensionPixelSize(R.dimen.dialog_padding)
            it.setPadding(pad, pad / 2, pad, 0)
            it.addView(input)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.channel_number_hint)
            .setView(box)
            .setPositiveButton(R.string.go) { _, _ -> goToChannel(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO) { goToChannel(input.text.toString()); dialog.dismiss(); true } else false
        }
        dialog.show()
        input.requestFocus()
    }

    private fun goToChannel(text: String) {
        val n = text.trim().toIntOrNull() ?: return
        val list = DvbiSession.serviceList ?: return
        val uid = numbering.entries.firstOrNull { it.value.lcn == n }?.key
        val service = list.services.firstOrNull { it.uid == uid }
        if (service == null || !ServiceListRules.directlySelectable(numbering[uid])) {
            Toast.makeText(this, "No selectable service on channel $n", Toast.LENGTH_SHORT).show()
            return
        }
        select(ChannelRow(service, n, ServiceListRules.minimumAgeFor(service.ratings, settings.country.ifEmpty { null })))
    }

    // The parental check is made by the player, where the programme's rating is known (clause 5.5.28).
    private fun select(row: ChannelRow) {
        lastSelectedUid = row.service.uid
        startActivity(PlayerActivity.intent(this, row.service.uid, settings.packages))
    }

    companion object {
        /** The service last selected while the process runs; the hero banner shows it. */
        private var lastSelectedUid: String? = null
    }
}
