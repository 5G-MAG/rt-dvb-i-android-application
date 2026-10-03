/*
License: 5G-MAG Public License (v1.0)
Author: Daniel Silhavy (5G-MAGflix design), Jordi J. Gimenez (DVB-I adaptation)
Copyright: (C) 2023-2026 Fraunhofer FOKUS; (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/**
 * The 5G-MAG Public License of this app (the repository's LICENSE file, packaged at build time), or
 * the open-source components it is built with and their licences, as 5G-MAGflix's License and
 * Attribution Notice menu entries.
 */
class LicenseActivity : AppCompatActivity() {

    enum class Kind { LICENSE, OPEN_SOURCE }

    /** A component, its licence as its Maven POM names it (or as its project states it), and a link. */
    private data class Component(val name: String, val licence: String, val url: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)
        val toolbar = findViewById<MaterialToolbar>(R.id.licenseToolbar)
        toolbar.setNavigationOnClickListener { finish() }
        val text = findViewById<TextView>(R.id.licenseTextView)
        when (intent.getStringExtra(EXTRA_KIND)?.let { Kind.valueOf(it) } ?: Kind.LICENSE) {
            Kind.LICENSE -> {
                toolbar.title = getString(R.string.action_license)
                // The file is wrapped at a fixed width; its paragraphs are reflowed to the screen.
                text.text = resources.openRawResource(R.raw.license).bufferedReader().use { it.readText() }
                    .replace(Regex("(?<!\n)\n(?!\n)"), " ")
            }
            Kind.OPEN_SOURCE -> {
                toolbar.title = getString(R.string.action_attribution_notice)
                text.text = getString(R.string.open_source_intro)
                val content = findViewById<LinearLayout>(R.id.licenseContent)
                for (c in COMPONENTS) {
                    val row = LayoutInflater.from(this).inflate(R.layout.item_about_link, content, false)
                    row.findViewById<View>(R.id.linkIcon).visibility = View.GONE
                    row.findViewById<TextView>(R.id.linkLabel).also {
                        it.text = "${c.name}\n${c.licence}"
                        (it.layoutParams as LinearLayout.LayoutParams).marginStart = 0
                    }
                    row.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.url))) }
                    content.addView(row)
                }
            }
        }
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val APACHE = "The Apache Software License, Version 2.0"

        private val COMPONENTS = listOf(
            Component("5G-MAGflix (5G-MAG/rt-5gms-application), design", "5G-MAG Public License (v1.0)", "https://github.com/5G-MAG/rt-5gms-application"),
            Component("AndroidX Media3 ExoPlayer and UI 1.10.0", APACHE, "https://github.com/androidx/media"),
            Component("AndroidX Core, AppCompat, RecyclerView, ConstraintLayout, SwipeRefreshLayout, Core SplashScreen", APACHE, "https://developer.android.com/jetpack/androidx"),
            Component("Material Components for Android 1.13.0", APACHE, "https://github.com/material-components/material-components-android"),
            Component("Coil 2.7.0", "The Apache License, Version 2.0", "https://github.com/coil-kt/coil"),
            Component("OkHttp 4.12.0", APACHE, "https://github.com/square/okhttp"),
            Component("Shimmer for Android 0.5.0", "BSD 2-Clause License", "https://github.com/facebook/shimmer-android"),
            Component("Material Symbols (icons)", "Apache License 2.0", "https://github.com/google/material-design-icons"),
            Component("Ubuntu font, downloaded at run time from Google Fonts", "Ubuntu Font Licence 1.0", "https://ubuntu.com/legal/font-licence"),
        )

        fun intent(context: Context, kind: Kind): Intent =
            Intent(context, LicenseActivity::class.java).putExtra(EXTRA_KIND, kind.name)
    }
}
