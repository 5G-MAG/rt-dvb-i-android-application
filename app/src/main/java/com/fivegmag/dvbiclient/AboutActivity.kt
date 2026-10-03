/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy (5G-MAGflix design), Jordi J. Gimenez (DVB-I adaptation)
Copyright: (C) 2023-2026 Fraunhofer FOKUS; (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.fivegmag.dvbiclient.ui.ServiceBadges
import com.google.android.material.appbar.MaterialToolbar

/**
 * About: what the app is, its version and variant, the project, contact and author links of
 * 5G-MAGflix's About dialog (rt-5gms-application fivegmag_5GMSdAwareApplication MainActivity
 * actionAbout), the credit to 5G-MAGflix, the licences and the legend of the badges.
 */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        findViewById<MaterialToolbar>(R.id.aboutToolbar).setNavigationOnClickListener { finish() }

        val name = getString(R.string.app_full_name)
        val part1 = getString(R.string.logo_text_5gmag)
        val part2 = getString(R.string.logo_text_flix)
        findViewById<TextView>(R.id.appNameText).text = SpannableString(name).also {
            if (name.startsWith(part1 + part2)) {
                it.setSpan(ForegroundColorSpan(getColor(R.color.fivegmag_blue)), 0, part1.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                it.setSpan(ForegroundColorSpan(getColor(R.color.flix_red)), part1.length, part1.length + part2.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        findViewById<TextView>(R.id.versionNumberView).text =
            getString(R.string.version_text_field, BuildConfig.VERSION_NAME) + " · " + Role.NAME
        findViewById<TextView>(R.id.descriptionText).text = getString(R.string.description_text)

        val project = findViewById<LinearLayout>(R.id.projectLinks)
        link(project, R.drawable.github_mark, getString(R.string.github_label), getString(R.string.github_url))
        link(project, R.drawable.ic_public, getString(R.string.project_page_label), getString(R.string.project_page_url))

        val contact = findViewById<LinearLayout>(R.id.contactLinks)
        link(contact, R.drawable.ic_action_linked_in, getString(R.string.linked_in_label), getString(R.string.linked_in_url))
        link(contact, R.drawable.ic_action_slack, getString(R.string.slack_label), getString(R.string.slack_url))
        link(contact, R.drawable.ic_public, getString(R.string.website_label), getString(R.string.website_url))

        val authors = findViewById<LinearLayout>(R.id.authorLinks)
        link(authors, R.drawable.github_mark, getString(R.string.contributors_label), getString(R.string.authors_url))
        link(authors, R.drawable.github_mark, getString(R.string.credits_link_label), getString(R.string.fivegmagflix_url))

        val licences = findViewById<LinearLayout>(R.id.licenceLinks)
        link(licences, R.drawable.ic_gavel, getString(R.string.action_license), null) {
            startActivity(LicenseActivity.intent(this, LicenseActivity.Kind.LICENSE))
        }
        link(licences, R.drawable.ic_description, getString(R.string.action_attribution_notice), null) {
            startActivity(LicenseActivity.intent(this, LicenseActivity.Kind.OPEN_SOURCE))
        }

        val legend = findViewById<LinearLayout>(R.id.iconLegend)
        for (b in ServiceBadges.legend()) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_legend, legend, false)
            val holder = row.findViewById<FrameLayout>(R.id.legendBadge)
            ServiceBadges.bind(holder, listOf(b))
            holder.getChildAt(0)?.let { it.isClickable = false; it.isLongClickable = false; it.importantForAccessibility = ViewGroup.IMPORTANT_FOR_ACCESSIBILITY_NO }
            row.findViewById<TextView>(R.id.legendText).text = b.description
            legend.addView(row)
        }
    }

    // Icons are drawn in the text colour, so the brand marks read on the dark surface.
    private fun link(parent: LinearLayout, icon: Int, label: String, url: String?, onClick: (() -> Unit)? = null) {
        val row = LayoutInflater.from(this).inflate(R.layout.item_about_link, parent, false)
        row.findViewById<ImageView>(R.id.linkIcon).also {
            it.setImageResource(icon)
            it.imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.on_surface_primary))
        }
        row.findViewById<TextView>(R.id.linkLabel).text = label
        row.contentDescription = label
        row.setOnClickListener {
            if (onClick != null) onClick() else url?.let { u -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
        }
        parent.addView(row)
    }
}
