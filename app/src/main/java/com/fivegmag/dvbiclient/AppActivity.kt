/*
License: 5G-MAG Public License (v1.0)
Author: Jordi J. Gimenez (5G-MAG)
Copyright: (C) 2026 5G-MAG Association
For full license terms please see the LICENSE file distributed with this
program. If this file is missing then the license can be retrieved from
https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
*/

package com.fivegmag.dvbiclient

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * An HTML application in a WebView over the whole screen: the on-demand player an XML AIT selects
 * (ETSI TS 103 770 V1.2.1 clause 5.2.4.3), or a linked application (clause 5.2.3). The browser
 * client shows these in a frame over its player (rt-dvb-i-application public/app.js showAppFrame);
 * closing is how the application exits here.
 */
class AppActivity : AppCompatActivity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app)
        WindowCompat.getInsetsController(window, window.decorView).also {
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsetsCompat.Type.systemBars())
        }
        val url = intent.getStringExtra(EXTRA_URL)
        val loading = findViewById<View>(R.id.appLoading)
        web = findViewById(R.id.appWebView)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                loading.visibility = View.GONE
            }

            // The application stays in this frame for http and https; nothing else is opened.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !isWebUrl(request.url.toString())
        }
        findViewById<View>(R.id.appClose).setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })
        if (url == null || !isWebUrl(url)) {
            finish()
            return
        }
        web.loadUrl(url)
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_URL = "url"

        /** Only http and https URLs are opened, as the browser client's appLaunchUrl. */
        fun isWebUrl(url: String): Boolean = Uri.parse(url).scheme?.lowercase() in setOf("http", "https")

        fun intent(context: Context, url: String): Intent = Intent(context, AppActivity::class.java).putExtra(EXTRA_URL, url)
    }
}
