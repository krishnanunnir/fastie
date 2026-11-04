package app.kino

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject

/** Media3 playback: resumes where you stopped, saves progress every 10 seconds, and syncs it to Nuvio. */
@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {
    private lateinit var player: ExoPlayer
    private lateinit var view: PlayerView
    private lateinit var data: DataSource.Factory
    private lateinit var item: Item
    private lateinit var videoId: String
    private var season: Int? = null
    private var episode: Int? = null
    private val tick = object : Runnable {
        override fun run() { save(push = false); view.postDelayed(this, 10_000) }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        Timing.mark("player created")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        item = Item.from(JSONObject(intent.getStringExtra("item")!!))
        videoId = intent.getStringExtra("id")!!
        season = intent.getIntExtra("season", -1).takeIf { it >= 0 }
        episode = intent.getIntExtra("episode", -1).takeIf { it >= 0 }

        // The device's own decoders first; FFmpeg only for audio they can't handle. Dolby Vision that the device can't
        // decode (profile 7 remuxes, or any DV on phones without a DV decoder) plays its HEVC base layer, as HDR10.
        val decoders = MediaCodecSelector { mime, secure, tunneling ->
            val own = MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
            if (mime == MimeTypes.VIDEO_DOLBY_VISION) own + MediaCodecSelector.DEFAULT.getDecoderInfos(MimeTypes.VIDEO_H265, secure, tunneling) else own
        }
        // Legacy decoding: subtitles from addons arrive unparsed (see LateSubtitles); the video's own come parsed either way.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildTextRenderers(context: android.content.Context, output: androidx.media3.exoplayer.text.TextOutput,
                                            looper: android.os.Looper, mode: Int, out: ArrayList<androidx.media3.exoplayer.Renderer>) {
                out += androidx.media3.exoplayer.text.TextRenderer(output, looper).apply { experimentalSetLegacyDecodingEnabled(true) }
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setMediaCodecSelector(decoders).setEnableDecoderFallback(true)
        // Audio in the phone's languages, then English, rather than whichever track the file flags as default.
        // Forced subtitles follow the audio language, so this also stops foreign forced subtitles showing.
        val tracks = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setPreferredAudioLanguages(*languages.distinct().toTypedArray()))
        }
        // Start after half a second of video instead of 2.5s; debrid links are fast enough that the rest fills while it
        // plays. After a stall it waits for 3s, so a slow link doesn't stutter.
        val buffer = DefaultLoadControl.Builder().setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, DefaultLoadControl.DEFAULT_MAX_BUFFER_MS, 500, 3_000).build()
        // Counts what arrives, so the loading screen can show the download speed.
        val http = DefaultHttpDataSource.Factory().setTransferListener(object : TransferListener {
            override fun onTransferInitializing(source: DataSource, spec: DataSpec, network: Boolean) {}
            override fun onTransferStart(source: DataSource, spec: DataSpec, network: Boolean) {}
            override fun onBytesTransferred(source: DataSource, spec: DataSpec, network: Boolean, bytes: Int) { received.addAndGet(bytes.toLong()) }
            override fun onTransferEnd(source: DataSource, spec: DataSpec, network: Boolean) {}
        })
        data = DefaultDataSource.Factory(this, http)
        player = ExoPlayer.Builder(this, renderers).setTrackSelector(tracks).setLoadControl(buffer)
            .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build()
        view = PlayerView(this).apply {
            this.player = this@PlayerActivity.player
            setShowSubtitleButton(true)
            controllerAutoShow = false // controls on a tap, not over the first seconds
            setShowNextButton(false)
            setShowPreviousButton(false)
            setBackgroundColor(android.graphics.Color.BLACK)
            // The scrubber takes the theme's primary, like every other progress indicator in Kino.
            findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.apply {
                setPlayedColor(C.accent); setScrubberColor(C.accent); setBufferedColor(C.alpha(C.onSurface, 0.4f))
            }
            // A white spinner whenever playback stalls to fill its buffer.
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            findViewById<android.widget.ProgressBar>(androidx.media3.ui.R.id.exo_buffering)?.indeterminateTintList =
                android.content.res.ColorStateList.valueOf(C.onSurface)
        }
        setContentView(FrameLayout(this).apply {
            addView(view, FrameLayout.LayoutParams(-1, -1))
            addView(cover(), FrameLayout.LayoutParams(-1, -1))
        })

        // Whatever isn't ready yet happens here, behind the poster: a cached torrent gets its link from debrid, and with
        // nothing chosen (an episode or Continue Watching) the best stream is found first.
        val url = intent.getStringExtra("url")
        val hash = intent.getStringExtra("hash")
        val subs = intent.getStringExtra("subs") ?: "[]"
        val debrid = Debrid.connected(this)
        val type = intent.getStringExtra("type")!!
        subtitlesAhead = Net.fan.submit(java.util.concurrent.Callable { Addons.subtitles(this, type, videoId).also { Timing.mark("subtitles: ${it.size}") } })
        when {
            url != null -> start(url, subs)
            hash != null && debrid != null -> prepare("Getting the video from ${debrid.label}…", {
                debrid.resolve(hash, intent.getStringExtra("filename"), intent.getIntExtra("fileIdx", -1).takeIf { it >= 0 }) to subs
            })
            else -> prepare("Finding the best stream…", {
                val found = findStreams(this, type, videoId, season != null)
                val best = found.streams.firstOrNull() ?: error(found.emptyMessage())
                waiting = if (best.hash != null) "Getting the video from ${debrid!!.label}…" else "Connecting…"
                (best.url ?: debrid!!.resolve(best.hash!!, best.filename, best.fileIdx)) to subsJson(best)
            })
        }
        view.post(progress)
    }

    // Your languages, the phone's then English: audio prefers them, and subtitles come in them.
    private val languages by lazy {
        val config = resources.configuration
        @Suppress("DEPRECATION")
        val preferred = if (android.os.Build.VERSION.SDK_INT >= 24) {
            config.locales.let { l -> List(l.size()) { l[it].language } }
        } else listOf(config.locale.language)
        (preferred + "en").distinct()
    }

    // Subtitles from every subtitle addon, asked for as the player opens so they're usually in by the time the link is.
    private var subtitlesAhead: java.util.concurrent.Future<List<Subtitle>>? = null

    // What the cover says while there's no link yet; null once the player has one.
    @Volatile private var waiting: String? = null

    /** Runs [work] off the main thread for a link and its subtitles, then plays it; on failure the cover says why. */
    private fun prepare(status: String, work: () -> Pair<String, String>) {
        waiting = status
        load(work) { r ->
            waiting = null
            Timing.mark("link ready")
            r.onSuccess { (link, subs) -> Store.saveLink(this, videoId, link, subs); start(link, subs) }
                .onFailure { failCover(it.message ?: "Couldn't play this one. Go back and try again.") }
        }
    }

    /**
     * Plays at once. Subtitles load beside the video: the addons get up to 2s more (they've usually answered while the
     * link was being found), and only the player's last step of getting ready waits for them.
     */
    private fun start(url: String, subsJson: String) {
        val fromStream = JSONArray(subsJson).let { a -> List(a.length()) { a.getJSONObject(it) } }
            .map { Subtitle(it.optString("lang"), it.getString("url"), "stream") }
        val ahead = subtitlesAhead
        begin(url, Net.fan.submit(java.util.concurrent.Callable {
            subtitleConfigs(fromStream + (runCatching { ahead?.get(2, java.util.concurrent.TimeUnit.SECONDS) }.getOrNull() ?: emptyList()))
        }))
    }

    private fun subtitleConfigs(offered: List<Subtitle>) = pickSubtitles(offered, languages).map { s ->
        val code = langCode(s.lang)
        val name = code?.let { java.util.Locale(it).getDisplayLanguage(java.util.Locale.getDefault()).replaceFirstChar(Char::uppercase) } ?: s.lang.ifBlank { "Subtitles" }
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(s.url))
            .setMimeType(when {
                s.url.contains(".vtt", true) -> MimeTypes.TEXT_VTT
                s.url.contains(".ass", true) || s.url.contains(".ssa", true) -> MimeTypes.TEXT_SSA
                else -> MimeTypes.APPLICATION_SUBRIP
            })
            .setLanguage(code ?: s.lang.ifBlank { null })
            .setLabel(if (s.source == "stream") name else "$name · ${s.source}")
            .build()
    }

    private fun begin(url: String, subs: java.util.concurrent.Future<List<MediaItem.SubtitleConfiguration>>) {
        Timing.mark("prepare")
        player.setMediaSource(MergingMediaSource(DefaultMediaSourceFactory(data).createMediaSource(MediaItem.fromUri(url)), LateSubtitles(subs, data)))
        // Subtitles switch on by themselves only when the audio isn't in one of your languages (a foreign film);
        // otherwise they wait in the CC menu.
        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val audio = tracks.groups.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && it.isSelected } ?: return
                player.removeListener(this)
                val spoken = langCode((0 until audio.length).firstOrNull { audio.isTrackSelected(it) }?.let { audio.getTrackFormat(it).language })
                if (spoken != null && spoken !in languages) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setPreferredTextLanguages(*languages.toTypedArray()).build()
            }
        })
        // Resume at the keyframe nearest your spot rather than the exact frame: the player can start drawing at once
        // instead of first downloading and decoding everything from the keyframe before it.
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        val resumeAt = Store.progressFor(this, videoId)?.takeIf { !it.finished && it.position > 0 }?.position
        // Seek parameters only apply once the file's index is known, so the resume seek waits for that.
        if (resumeAt != null) player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (timeline.isEmpty || !player.isCurrentMediaItemSeekable) return
                player.removeListener(this)
                player.seekTo(resumeAt)
            }
        })
        player.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() { Timing.mark("FIRST FRAME"); hideCover() }

            override fun onPlayerError(error: PlaybackException) {
                hideCover()
                Store.saveLink(this@PlayerActivity, videoId, null) // don't send Resume back to a broken link
                view.setCustomErrorMessage("This stream didn't play. Go back and pick another one.")
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) { save(push = true, ended = true); finish() }
            }
        })
        player.prepare()
        player.playWhenReady = true
        view.postDelayed(tick, 10_000)
    }

    /** The cover stays, but says what went wrong; Back returns to the streams. */
    private fun failCover(message: String) {
        coverBar.visibility = View.GONE
        coverStatus.text = message
        coverStatus.setTextColor(C.onSurface)
        coverView = null // stops the progress updates overwriting the message
    }

    private var coverView: View? = null
    private lateinit var coverBar: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var coverStatus: android.widget.TextView
    private val received = java.util.concurrent.atomic.AtomicLong()

    /**
     * Until the first frame draws: the backdrop, dimmed and fading in from the left, with the poster and then the title's
     * logo (or name), the episode, where it resumes, a red line and what the player is doing. Laid out for landscape,
     * which is how the player always runs.
     */
    private fun cover() = FrameLayout(this).apply {
        setBackgroundColor(C.ground)
        addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.5f
            Images.load(this, item.backgroundOrHub, resources.displayMetrics.widthPixels)
        }, FrameLayout.LayoutParams(-1, -1))
        addView(View(context).apply {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(C.alpha(C.ground, 0.97f), C.alpha(C.ground, 0.85f), C.alpha(C.ground, 0.35f)))
        }, FrameLayout.LayoutParams(-1, -1))

        val short = minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val ph = (short * 0.56f).toInt()
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(C.container, dp(Shape.M).toFloat())
            outlineProvider = MorphOutline(dp(Shape.M).toFloat()); clipToOutline = true
            elevation = dp(16).toFloat()
            Images.load(this, item.posterOrHub, ph * 2 / 3)
        }, lp(ph * 2 / 3, ph))

        val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val title = text(T.DISPLAY, lines = 2, s = item.name)
        val logo = ImageView(context).apply {
            adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_START
            maxHeight = dp(if (isTv) 96 else 72); maxWidth = dp(if (isTv) 420 else 300); visibility = View.GONE
        }
        val logoUrl = item.logo ?: if (item.id.startsWith("tt")) "https://images.metahub.space/logo/medium/${item.id}/img" else null
        Images.load(logo, logoUrl, dp(300), alpha = true) { ok -> logo.visibility = if (ok) View.VISIBLE else View.GONE; title.visibility = if (ok) View.GONE else View.VISIBLE }
        info.addView(logo, lp(-2, -2))
        info.addView(title, lp(-2, -2))
        val resume = Store.progressFor(this@PlayerActivity, videoId)?.takeIf { !it.finished && it.position > 0 }?.position
        val line = listOfNotNull(
            season?.let { "Season $it, Episode $episode" },
            resume?.let { "Resuming at ${clock(it)}" },
        ).joinToString("  ·  ")
        if (line.isNotEmpty()) info.addView(text(T.META, C.alpha(C.onSurface, 0.75f), s = line), lp(-2, -2).apply { topMargin = dp(10) })
        coverBar = progressLine().apply { isIndeterminate = true }
        info.addView(coverBar, lp(dp(if (isTv) 360 else 260), -2).apply { topMargin = dp(24) })
        coverStatus = text(T.META, C.variant, s = "Connecting…")
        info.addView(coverStatus, lp(-2, -2).apply { topMargin = dp(10) })
        row.addView(info, lp(-2, -2).apply { marginStart = dp(if (isTv) 48 else 32) })
        addView(row, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL or Gravity.START).apply { marginStart = dp(if (isTv) 96 else 56) })
        coverView = this
    }

    /** 2770000 → "46:10", or "1:02:05" past the hour. */
    private fun clock(ms: Long): String {
        val t = ms / 1000
        return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
    }

    /**
     * Four times a second while the cover shows: the download speed, and once video is buffered past where playback
     * starts, how much of the half second it needs. Before that (reading the file's index, reaching the right spot) the line
     * stays indeterminate, since there's nothing honest to measure.
     */
    private val progress = object : Runnable {
        private var lastBytes = 0L
        private var lastAt = android.os.SystemClock.elapsedRealtime()
        private var speed = 0.0

        override fun run() {
            if (coverView == null) return
            val now = android.os.SystemClock.elapsedRealtime()
            val bytes = received.get()
            if (now > lastAt) speed = speed * 0.6 + (bytes - lastBytes) * 1000.0 / (now - lastAt) * 0.4
            lastBytes = bytes; lastAt = now
            val ahead = (player.bufferedPosition - player.currentPosition).coerceAtLeast(0)
            val fraction = if (player.playbackState == Player.STATE_BUFFERING && ahead > 0) (ahead / 500f).coerceAtMost(1f) else 0f
            if (fraction > 0) coverBar.setProgressCompat((fraction * 1000).toInt(), true)
            val rate = if (speed >= 1e6) "%.1f MB/s".format(speed / 1e6) else "%.0f KB/s".format(speed / 1e3)
            coverStatus.text = when {
                waiting != null -> waiting!!
                bytes == 0L -> "Connecting…"
                fraction > 0 -> "Buffering ${(fraction * 100).toInt()}%  ·  $rate"
                else -> "Loading  ·  $rate"
            }
            view.postDelayed(this, 250)
        }
    }

    private fun hideCover() {
        coverView?.animate()?.alpha(0f)?.setDuration(250)?.withEndAction { coverView?.visibility = View.GONE; coverView = null }?.start()
    }

    /** With the controls hidden, left and right seek straight away, as on every TV player. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && !view.isControllerFullyVisible) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { player.seekBack(); view.showController(); return true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { player.seekForward(); view.showController(); return true }
            }
        }
        return super.dispatchKeyEvent(event) || view.dispatchKeyEvent(event)
    }

    private fun save(push: Boolean, ended: Boolean = false) {
        val duration = player.duration.takeIf { it > 0 } ?: return
        val p = Progress(item.id, if (season != null) "series" else item.type, videoId, season, episode,
            if (ended) duration else player.currentPosition, duration, System.currentTimeMillis(), item.name)
        Store.saveProgress(this, p)
        if (push) Nuvio.later(this) { Nuvio.pushProgress(it, p) }
    }

    override fun onPause() {
        super.onPause()
        save(push = true)
        player.pause()
    }

    override fun onDestroy() {
        view.removeCallbacks(tick)
        view.removeCallbacks(progress)
        player.release()
        super.onDestroy()
    }
}
