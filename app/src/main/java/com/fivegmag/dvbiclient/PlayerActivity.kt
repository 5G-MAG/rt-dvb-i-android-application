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
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.MediaDrm
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.text.DateFormat
import java.util.Date
import com.fivegmag.dvbiclient.guide.GuideParser
import com.fivegmag.dvbiclient.mbms.MbmsSession
import com.fivegmag.dvbiclient.mbms.MbmsUrl
import com.fivegmag.dvbiclient.servicelist.Delivery
import com.fivegmag.dvbiclient.servicelist.Service
import com.fivegmag.dvbiclient.servicelist.ServiceInstance
import com.fivegmag.dvbiclient.servicelist.ServiceListRules
import com.fivegmag.dvbiclient.servicelist.LinkedApps
import com.fivegmag.dvbiclient.servicelist.Playlists
import com.fivegmag.dvbiclient.servicelist.ServiceSelection
import com.fivegmag.dvbiclient.ui.Badge
import com.fivegmag.dvbiclient.ui.LinkedApplication
import com.fivegmag.dvbiclient.ui.OnDemand
import com.fivegmag.dvbiclient.ui.ServiceBadges
import com.fivegmag.dvbiclient.ui.ServiceTypes

const val TAG_PLAYER = "DVB-I Player"

/**
 * Plays one service of the installed service list with Media3 ExoPlayer, choosing the instance by
 * the precedence of ETSI TS 103 770 V1.2.1 clause 5.2.13 and falling back to the next instance when
 * one fails to play. The screen is 5G-MAGflix's detail page (rt-5gms-application
 * fivegmag_5GMSdAwareApplication DetailActivity) with the player always in the media area, which
 * goes fullscreen from the player's button or by turning the phone to landscape.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var instanceText: TextView
    private lateinit var discardedText: TextView
    private var player: ExoPlayer? = null

    // The listener of the MBMS service this player started, if any; MbmsSession stops only that one.
    private var mbmsListener: MbmsSession.Listener? = null
    private var selection: ServiceSelection? = null
    private var current: Int? = null
    private val handler = Handler(Looper.getMainLooper())
    private val reevaluate = Runnable { onScheduledHoursChanged() }
    private lateinit var settings: Settings
    private var serviceAge: Int? = null
    private var programmeAge: Int? = null
    private var fullscreen = false
    private var liveEdgeOffset: Long? = null
    private var contentFinished: com.fivegmag.dvbiclient.servicelist.Image? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.playerView)
        instanceText = findViewById(R.id.instanceText)
        discardedText = findViewById(R.id.discardedText)
        val toolbar = findViewById<MaterialToolbar>(R.id.detailToolbar)
        toolbar.setNavigationOnClickListener { finish() }
        setupFullscreen()

        val uid = intent.getStringExtra(EXTRA_SERVICE_UID)
        val service = DvbiSession.serviceList?.services?.firstOrNull { it.uid == uid }
        if (service == null) {
            instanceText.text = getString(R.string.service_not_found)
            return
        }
        title = service.name
        toolbar.title = service.name
        findViewById<TextView>(R.id.serviceTitle).text = service.name
        settings = Settings(this)
        serviceAge = ServiceListRules.minimumAgeFor(service.ratings, settings.country.ifEmpty { null })
        showService(service)
        showFiveG(service)
        showLogoWhenAudioOnly(service)
        selection = ServiceSelection(service, DeviceCapabilities.current(clientPackages()))
        BrowseActivity.boxSets(this, service)?.let { boxSets ->
            findViewById<Button>(R.id.boxSetsButton).also {
                it.visibility = View.VISIBLE
                it.setOnClickListener { startActivity(boxSets) }
            }
        }
        // Without a content guide there is no programme to show.
        if (service.guide == null) {
            for (id in listOf(R.id.liveBadge, R.id.nowTitle, R.id.nowTime, R.id.nowProgress, R.id.nowSynopsis, R.id.nextText)) {
                findViewById<View>(id).visibility = View.GONE
            }
        }
        if (service.guide != null) {
            findViewById<Button>(R.id.scheduleButton).also {
                it.visibility = View.VISIBLE
                it.setOnClickListener { startActivity(ScheduleActivity.intent(this, service.uid)) }
            }
        }
    }

    // Channel number, service type, logo and badges.
    private fun showService(service: Service) {
        val list = DvbiSession.serviceList
        val lcn = list?.let { l ->
            ServiceListRules.assignChannelNumbers(ServiceListRules.selectLcnTable(l.lcnTables, settings.region, settings.packages), l.services)[service.uid]?.lcn
        }
        findViewById<TextView>(R.id.lcnBadge).also {
            it.text = lcn?.toString() ?: ""
            it.visibility = if (lcn != null) View.VISIBLE else View.GONE
        }
        findViewById<TextView>(R.id.typeBadge).text = getString(ServiceTypes.label(service.serviceType))
        service.logo?.let { logo ->
            findViewById<ImageView>(R.id.serviceLogo).also {
                it.visibility = View.VISIBLE
                it.load(logo.url) { crossfade(true) }
            }
        }
        showBadges(service)
    }

    private fun showBadges(service: Service) {
        val badges = ServiceBadges.of(service, serviceAge, programmeAge, settings.parentalThreshold, DvbiSession.mbmsRegistered)
            .filter { it.kind != Badge.Kind.RESTRICTED }
        ServiceBadges.bind(findViewById<ChipGroup>(R.id.badges), if (Role.fiveGmsSession()) listOf(ServiceBadges.fiveGms()) + badges else badges)
    }

    private val liveTicker = object : Runnable {
        override fun run() {
            updateLiveUi()
        }
    }

    // A live stream has no programme position to show and nothing to skip: the skip buttons and
    // the position and duration are hidden, a LIVE chip shows at the live edge, and behind it how
    // far behind and a tap back to the live edge; the scrubber stays for the time-shift window. A
    // stream that is not live keeps the full controls.
    private fun updateLiveUi() {
        val exo = player ?: return
        handler.removeCallbacks(liveTicker)
        val live = exo.isCurrentMediaItemLive
        playerView.setShowRewindButton(!live)
        playerView.setShowFastForwardButton(!live)
        playerView.findViewById<View>(androidx.media3.ui.R.id.exo_time)?.visibility = if (live) View.GONE else View.VISIBLE
        val control = findViewById<View>(R.id.liveControl)
        control.visibility = if (live) View.VISIBLE else View.GONE
        if (!live) return
        // The smallest live offset seen since playback (re)started at the default position: the
        // player closes in on the live edge after it starts, so the first offset can be too large.
        val offset = exo.currentLiveOffset
        if (offset != androidx.media3.common.C.TIME_UNSET && exo.playbackState == Player.STATE_READY) {
            liveEdgeOffset = minOf(liveEdgeOffset ?: offset, offset)
        }
        val edge = liveEdgeOffset
        val behind = if (edge == null || offset == androidx.media3.common.C.TIME_UNSET) 0L else offset - edge
        val chip = findViewById<TextView>(R.id.liveChip)
        if (behind > BEHIND_LIVE_MS) {
            val s = behind / 1000
            chip.text = getString(R.string.behind_live, String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60))
            chip.setBackgroundResource(R.drawable.bg_badge_behind_live)
            control.contentDescription = chip.text.toString() + ". " + getString(R.string.go_live)
            control.setOnClickListener {
                liveEdgeOffset = null
                exo.seekToDefaultPosition()
                updateLiveUi()
            }
        } else {
            chip.text = getString(R.string.badge_live)
            chip.setBackgroundResource(R.drawable.bg_badge_featured)
            control.contentDescription = getString(R.string.badge_live)
            control.setOnClickListener(null)
            control.isClickable = false
        }
        handler.postDelayed(liveTicker, 1000)
    }

    // Fullscreen from the player's button, or by turning the phone: the player fills the screen and
    // the system bars are hidden; back leaves fullscreen first.
    private fun setupFullscreen() {
        // A service plays one stream; there is no previous or next item to skip to.
        playerView.setShowPreviousButton(false)
        playerView.setShowNextButton(false)
        playerView.setFullscreenButtonClickListener { enter -> setFullscreen(enter, rotate = true) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreen) setFullscreen(false, rotate = true) else finish()
            }
        })
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) setFullscreen(true, rotate = false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape != fullscreen) setFullscreen(landscape, rotate = false)
    }

    private fun setFullscreen(on: Boolean, rotate: Boolean) {
        fullscreen = on
        playerView.setFullscreenButtonState(on)
        findViewById<View>(R.id.detailToolbar).visibility = if (on) View.GONE else View.VISIBLE
        findViewById<View>(R.id.detailScroll).visibility = if (on) View.GONE else View.VISIBLE
        val media = findViewById<View>(R.id.mediaContainer)
        media.layoutParams = (media.layoutParams as ConstraintLayout.LayoutParams).also {
            it.dimensionRatio = if (on) null else "16:9"
            it.height = if (on) ConstraintLayout.LayoutParams.MATCH_CONSTRAINT else 0
            it.bottomToBottom = if (on) ConstraintLayout.LayoutParams.PARENT_ID else ConstraintLayout.LayoutParams.UNSET
        }
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
        if (rotate) {
            requestedOrientation = if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // A service without video (a radio service) shows its logo in the picture area: PlayerView
    // draws the default artwork only while no video track is selected, so television is unaffected.
    @OptIn(UnstableApi::class)
    private fun showLogoWhenAudioOnly(service: Service) {
        playerView.artworkDisplayMode = PlayerView.ARTWORK_DISPLAY_MODE_FIT
        val logo = service.logo ?: return
        imageLoader.enqueue(ImageRequest.Builder(this).data(logo.url).target { playerView.defaultArtwork = it }.build())
    }

    // Clause 5.5.28: the content guide's rating of the programme on air takes precedence over the
    // service's ParentalRating; until it is known the service's applies.
    private fun parentalAllows(): Boolean = !ServiceListRules.restricted(settings.parentalThreshold, serviceAge, programmeAge)

    private fun showParental() {
        val text = findViewById<TextView>(R.id.parentalText)
        if (parentalAllows()) {
            text.visibility = View.GONE
        } else {
            text.visibility = View.VISIBLE
            text.text = "${getString(R.string.restricted)}: rated ${programmeAge ?: serviceAge}+" +
                (if (programmeAge != null) " (programme rating)" else " (service rating)")
        }
    }

    // Now/next of clause 6.5.3.1, which also gives the programme's rating.
    private fun loadNowNext(service: Service) {
        if (service.guide == null) return
        DvbiRepository.background({ DvbiRepository.guide.nowNext(service) }) { r ->
            val events = r.value ?: return@background
            val (now, next) = GuideParser.nowNext(events, System.currentTimeMillis())
            val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
            showNowNext(service, now, next, fmt)
            val wasAllowed = parentalAllows()
            programmeAge = now?.info?.let { ServiceListRules.minimumAgeFor(it.ratings, settings.country.ifEmpty { null }) }
            showParental()
            showBadges(service)
            val sel = selection ?: return@background
            if (wasAllowed && !parentalAllows()) {
                player?.stop()
            } else if (!wasAllowed && parentalAllows()) {
                play(sel.select(System.currentTimeMillis()))
            }
        }
    }

    private fun showNowNext(service: Service, now: com.fivegmag.dvbiclient.guide.GuideEvent?, next: com.fivegmag.dvbiclient.guide.GuideEvent?, fmt: DateFormat) {
        // LIVE: the programme on now of a linear service (ServiceTypeCS linear, linear-radio).
        findViewById<View>(R.id.liveBadge).visibility =
            if (now != null && ServiceTypes.isLinear(service.serviceType)) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.nowTitle).text = now?.title ?: getString(R.string.no_programme_now)
        findViewById<TextView>(R.id.nowTime).also {
            it.text = now?.let { n -> "${fmt.format(Date(n.start))} to ${fmt.format(Date(n.end))}" } ?: ""
            it.visibility = if (now != null) View.VISIBLE else View.GONE
        }
        findViewById<LinearProgressIndicator>(R.id.nowProgress).also {
            it.visibility = if (now != null) View.VISIBLE else View.GONE
            if (now != null && now.end > now.start) {
                it.progress = (((System.currentTimeMillis() - now.start) * 100) / (now.end - now.start)).toInt().coerceIn(0, 100)
            }
        }
        findViewById<TextView>(R.id.nowSynopsis).also {
            val syn = now?.info?.synopsis ?: ""
            it.text = syn
            it.visibility = if (syn.isEmpty()) View.GONE else View.VISIBLE
        }
        findViewById<TextView>(R.id.nextText).also {
            it.text = next?.let { n -> getString(R.string.next_line, fmt.format(Date(n.start)), n.title) } ?: ""
            it.visibility = if (next != null) View.VISIBLE else View.GONE
        }
    }

    /** Subscription packages the user associated this client with; none until settings set them. */
    private fun clientPackages(): List<String> = intent.getStringArrayListExtra(EXTRA_PACKAGES) ?: emptyList()

    override fun onStart() {
        super.onStart()
        val sel = selection ?: return
        val exo = ExoPlayer.Builder(this).build()
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onInstanceFailed(error)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) showContentFinished()
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                updateLiveUi()
            }
        })
        playerView.player = exo
        player = exo
        showParental()
        if (parentalAllows()) play(sel.select(System.currentTimeMillis()))
        loadNowNext(sel.service)
    }

    override fun onStop() {
        super.onStop()
        // Leaving the player: no longer interested in the service (clause 6.3.3.9.4).
        DvbiSession.mbms.stop(mbmsListener)
        handler.removeCallbacks(reevaluate)
        handler.removeCallbacks(liveTicker)
        playerView.player = null
        player?.release()
        player = null
        current = null
    }

    // 5G Broadcast instances: the locator, its check against TS 26.347 clause 8.2.2, and why it is
    // not received.
    private fun showFiveG(service: Service) {
        val mbms = service.instances.mapNotNull { it.delivery as? Delivery.Mbms }
        if (mbms.isEmpty()) return
        findViewById<View>(R.id.fiveGSection).visibility = View.VISIBLE
        if (mbms.any { MbmsUrl.problem(it.locator) != null }) {
            findViewById<TextView>(R.id.fiveGBadge).compoundDrawableTintList =
                android.content.res.ColorStateList.valueOf(getColor(R.color.badge_5g_bad))
        }
        val detail = findViewById<TextView>(R.id.fiveGDetail)
        detail.text = mbms.joinToString("\n\n") { d ->
            val problem = MbmsUrl.problem(d.locator)
            buildString {
                append("Locator: ${d.locator}\n")
                append("serviceId: ${MbmsUrl.serviceId(d.locator)}\n")
                append(if (problem == null) "Valid MBMS URL (3GPP TS 26.347 V18.1.0 clause 8.2.2)" else "Invalid: $problem")
                if (!DvbiSession.mbmsRegistered) append("\nNot received: no MBMS Client is available on this device.")
            }
        }
    }

    private fun play(index: Int?) {
        val sel = selection ?: return
        val exo = player ?: return
        val now = System.currentTimeMillis()
        current = index
        showDiscarded(sel, now)
        scheduleReevaluation(sel, now)
        findViewById<View>(R.id.contentFinishedImage).visibility = View.GONE
        contentFinished = null
        if (index == null) {
            DvbiSession.mbms.stop(mbmsListener)
            exo.stop()
            instanceText.text = getString(R.string.no_playable_instance)
            showLinkedApp(sel.service, null)
            return
        }
        val inst = sel.service.instances[index]
        // TS 26.347 V18.1.0 clause 6.3.3.9.4: "If an MAA is no longer interested in consuming the
        // Media service, it should call the stopStreamingService() API call."
        if (inst.delivery !is Delivery.Mbms) DvbiSession.mbms.stop(mbmsListener)
        instanceText.text = "Playing: ${inst.label}\n${describe(inst.delivery)}"
        when (val d = inst.delivery) {
            is Delivery.Dash -> start(exo, d.url, MimeTypes.APPLICATION_MPD, inst)
            is Delivery.Hls -> start(exo, d.url, MimeTypes.APPLICATION_M3U8, inst)
            is Delivery.Mbms -> startMbms(exo, d, inst)
            is Delivery.DashPlaylist -> startPlaylist(exo, d, inst)
            is Delivery.ControllingApplication -> startControllingApplication(exo, d)
            else -> play(sel.fail(index, now))
        }
        showLinkedApp(sel.service, inst)
    }

    // Clause 5.2.3.2: "media presentation is to be managed by the linked application and no media
    // stream shall be presented by the DVB-I client when the service is selected". The application
    // takes the screen; if it cannot be had, the next instance is tried.
    private fun startControllingApplication(exo: ExoPlayer, d: Delivery.ControllingApplication) {
        exo.stop()
        val regions = OnDemand.regions(settings)
        // Term 1.2 has the launch location "service" wherever it is launched from.
        DvbiRepository.background({ LinkedApplication.resolve(d.url, d.contentType, LinkedApps.CONTROLLING, LinkedApps.LaunchView.PLAYER, regions) }) { url ->
            if (isFinishing || isDestroyed) return@background
            if (url == null) {
                onInstanceFailed(null)
            } else {
                startActivity(AppActivity.intent(this, url))
                finish()
            }
        }
    }

    // A DVB-I Playlist from a playlist server (clause 5.2.7.2), its PlaylistEntry MPDs played in
    // order (table 39); the playlist server is a DVB-I endpoint (clause 4.3.1).
    private fun startPlaylist(exo: ExoPlayer, d: Delivery.DashPlaylist, inst: ServiceInstance) {
        DvbiRepository.background({
            val r = DvbiRepository.http.get(d.url)
            if (!r.ok) null else runCatching { Playlists.parse(r.body) }.getOrNull()
        }) { entries ->
            if (player !== exo || current == null) return@background
            if (entries.isNullOrEmpty()) {
                onInstanceFailed(null)
                return@background
            }
            exo.setMediaItems(entries.map { MediaItem.Builder().setUri(it).setMimeType(MimeTypes.APPLICATION_MPD).build() })
            exo.prepare()
            exo.playWhenReady = true
            contentFinished = inst.contentFinished
        }
    }

    // Clause 5.2.7.3: "When the DVB-I client has played out the VoD MPD or all of the items in the
    // playlist, it should present a Content Finished image if one is signalled."
    private fun showContentFinished() {
        val image = contentFinished ?: return
        findViewById<ImageView>(R.id.contentFinishedImage).also {
            it.visibility = View.VISIBLE
            it.load(image.url)
        }
    }

    // The linked application the player offers (clauses 5.2.3.1, 5.2.3.2), resolved before the
    // button shows; one with no application this client can start is shown as unavailable, "the
    // client shall not issue an error to the user but instead shall show a service or content item
    // as unavailable" (clause 5.2.4.2).
    private fun showLinkedApp(service: Service, playing: ServiceInstance?) {
        val button = findViewById<Button>(R.id.appButton)
        val app = LinkedApplication.offered(service, playing)
        button.visibility = View.GONE
        if (app == null) return
        val regions = OnDemand.regions(settings)
        DvbiRepository.background({ LinkedApplication.resolve(app.url, app.contentType, app.term, LinkedApps.LaunchView.PLAYER, regions) }) { url ->
            if (isFinishing || isDestroyed) return@background
            button.visibility = View.VISIBLE
            button.isEnabled = url != null
            button.text = getString(if (url != null) R.string.open_application else R.string.application_unavailable)
            button.setOnClickListener { url?.let { startActivity(AppActivity.intent(this, it)) } }
        }
    }

    private fun start(exo: ExoPlayer, url: String, mimeType: String, inst: ServiceInstance) {
        val item = MediaItem.Builder().setUri(url).setMimeType(mimeType)
        // A DRM system the device supports (clause 5.2.13 discarded the instance otherwise). The
        // service list carries no licence server, so the one the media signals is used.
        inst.protection?.drmSystems?.firstNotNullOfOrNull { DeviceCapabilities.drmUuid(it)?.takeIf { u -> MediaDrm.isCryptoSchemeSupported(u) } }
            ?.let { item.setDrmConfiguration(MediaItem.DrmConfiguration.Builder(it).build()) }
        liveEdgeOffset = null
        exo.setMediaItem(item.build())
        exo.prepare()
        exo.playWhenReady = true
    }

    // TS 103 770 clause 9.3.3, through the MBMS Client (MbmsSession: registered once, the service
    // started before stopped first). Only reached when an MBMS Client accepted the registration;
    // with NoMbmsClient the instance is discarded before.
    private fun startMbms(exo: ExoPlayer, d: Delivery.Mbms, inst: ServiceInstance) {
        mbmsListener = object : MbmsSession.Listener {
            override fun started(entry: Pair<String, String>) {
                runOnUiThread { if (player === exo) start(exo, entry.second, entry.first, inst) }
            }

            override fun failed(message: String) {
                Log.w(TAG_PLAYER, "MBMS service ${MbmsUrl.serviceId(d.locator)}: $message")
                runOnUiThread { if (player === exo) onInstanceFailed(null) }
            }
        }.also { DvbiSession.mbms.start(d.locator, it) }
    }

    private fun onInstanceFailed(error: PlaybackException?) {
        val sel = selection ?: return
        val index = current ?: return
        Log.w(TAG_PLAYER, "Instance ${sel.service.instances[index].label} failed: ${error?.errorCodeName}", error)
        play(sel.fail(index, System.currentTimeMillis()))
    }

    // Clause 5.2.13: "When one of the service instances of the currently selected service changes
    // from being inside their scheduled service hours to being outside or vice-versa, the selected
    // service instance shall be re-evaluated."
    private fun scheduleReevaluation(sel: ServiceSelection, now: Long) {
        handler.removeCallbacks(reevaluate)
        val at = sel.nextReevaluation(now) ?: return
        handler.postDelayed(reevaluate, maxOf(0L, at - now) + 1)
    }

    private fun onScheduledHoursChanged() {
        val sel = selection ?: return
        val next = sel.select(System.currentTimeMillis())
        if (next != current) play(next) else scheduleReevaluation(sel, System.currentTimeMillis())
    }

    private fun showDiscarded(sel: ServiceSelection, now: Long) {
        val reasons = sel.reasons(now)
        discardedText.text = sel.service.instances.indices
            .filter { reasons[it] != null }
            .joinToString("\n") { "Not played: ${sel.service.instances[it].label} (priority ${sel.service.instances[it].priority}): ${reasons[it]}" }
    }

    private fun describe(d: Delivery): String = when (d) {
        is Delivery.Dash -> "DASH: ${d.url}"
        is Delivery.Hls -> "HLS (${d.signalledBy}): ${d.url}"
        is Delivery.Mbms -> "5G Broadcast: ${d.locator}"
        is Delivery.DashPlaylist -> "DVB-I Playlist (DASH): ${d.url}"
        is Delivery.ControllingApplication -> "Application controlling media presentation (${d.contentType}): ${d.url}"
        else -> d.toString()
    }

    companion object {
        const val EXTRA_SERVICE_UID = "serviceUid"
        const val EXTRA_PACKAGES = "packages"

        /** Further behind the live edge than this, the chip offers to go back to it (presentation only). */
        private const val BEHIND_LIVE_MS = 10_000L

        fun intent(context: Context, uid: String, packages: List<String>): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_SERVICE_UID, uid)
                .putStringArrayListExtra(EXTRA_PACKAGES, ArrayList(packages))
    }
}
