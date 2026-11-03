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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import org.json.JSONArray
import org.json.JSONObject

/** Media3 playback: resumes where you stopped, saves progress every 10 seconds, and syncs it to Nuvio. */
@OptIn(UnstableApi::class)
class PlayerActivity : Activity() {
    private lateinit var player: ExoPlayer
    private lateinit var view: PlayerView
    private lateinit var item: Item
    private lateinit var videoId: String
    private var season: Int? = null
    private var episode: Int? = null
    private val tick = object : Runnable {
        override fun run() { save(push = false); view.postDelayed(this, 10_000) }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        item = Item.from(JSONObject(intent.getStringExtra("item")!!))
        videoId = intent.getStringExtra("id")!!
        season = intent.getIntExtra("season", -1).takeIf { it >= 0 }
        episode = intent.getIntExtra("episode", -1).takeIf { it >= 0 }

        val subs = JSONArray(intent.getStringExtra("subs") ?: "[]").let { a -> List(a.length()) { a.getJSONObject(it) } }.map { s ->
            val url = s.getString("url")
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                .setMimeType(if (url.contains(".vtt", true)) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
                .setLanguage(s.optString("lang").ifBlank { null })
                .build()
        }
        // The device's own decoders first; FFmpeg only for audio they can't handle. Dolby Vision that the device can't
        // decode (profile 7 remuxes, or any DV on phones without a DV decoder) plays its HEVC base layer, as HDR10.
        val decoders = MediaCodecSelector { mime, secure, tunneling ->
            val own = MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling)
            if (mime == MimeTypes.VIDEO_DOLBY_VISION) own + MediaCodecSelector.DEFAULT.getDecoderInfos(MimeTypes.VIDEO_H265, secure, tunneling) else own
        }
        val renderers = DefaultRenderersFactory(this).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setMediaCodecSelector(decoders).setEnableDecoderFallback(true)
        // Audio in the phone's languages, then English, rather than whichever track the file flags as default.
        // Forced subtitles follow the audio language, so this also stops foreign forced subtitles showing.
        val languages = resources.configuration.locales.let { l -> List(l.size()) { l[it].language } } + "en"
        val tracks = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setPreferredAudioLanguages(*languages.distinct().toTypedArray()))
        }
        // Start after 1.5s of video instead of 2.5s; debrid links are fast enough that the rest fills while it plays.
        val buffer = DefaultLoadControl.Builder().setBufferDurationsMs(
            DefaultLoadControl.DEFAULT_MIN_BUFFER_MS, DefaultLoadControl.DEFAULT_MAX_BUFFER_MS, 1_500, 3_000).build()
        player = ExoPlayer.Builder(this, renderers).setTrackSelector(tracks).setLoadControl(buffer)
            .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build()
        view = PlayerView(this).apply {
            this.player = this@PlayerActivity.player
            setShowSubtitleButton(true)
            setShowNextButton(false)
            setShowPreviousButton(false)
            setBackgroundColor(android.graphics.Color.BLACK)
            // The scrubber takes the theme's primary, like every other progress indicator in Kino.
            findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)?.apply {
                setPlayedColor(C.amber); setScrubberColor(C.amber); setBufferedColor(C.alpha(C.onSurface, 0.4f))
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

        player.setMediaItem(MediaItem.Builder().setUri(intent.getStringExtra("url")).setSubtitleConfigurations(subs).build())
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
            override fun onRenderedFirstFrame() { hideCover() }

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

    private var coverView: View? = null

    /**
     * Until the first frame draws: the title's backdrop, dimmed, with its logo (or name) over a spinner,
     * so the wait reads as loading rather than a black screen.
     */
    private fun cover() = FrameLayout(this).apply {
        setBackgroundColor(C.ground)
        val w = resources.displayMetrics.widthPixels
        addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.35f
            Images.load(this, item.backgroundOrHub, w)
        }, FrameLayout.LayoutParams(-1, -1))
        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val title = text(T.TITLE, s = item.name)
        val logo = ImageView(context).apply { adjustViewBounds = true; maxHeight = dp(80); maxWidth = dp(280); visibility = View.GONE }
        val logoUrl = item.logo ?: if (item.id.startsWith("tt")) "https://images.metahub.space/logo/medium/${item.id}/img" else null
        Images.load(logo, logoUrl, dp(280), alpha = true) { ok -> logo.visibility = if (ok) View.VISIBLE else View.GONE; title.visibility = if (ok) View.GONE else View.VISIBLE }
        col.addView(logo, lp(-2, -2))
        col.addView(title, lp(-2, -2))
        if (season != null) col.addView(text(T.META, C.variant, s = "Season $season, Episode $episode").apply { setPadding(0, dp(8), 0, 0) }, lp(-2, -2))
        col.addView(CircularProgressIndicator(context).apply {
            isIndeterminate = true; setIndicatorColor(C.onSurface); trackColor = C.alpha(C.onSurface, 0.15f)
            indicatorSize = dp(36); trackThickness = dp(3)
        }, lp(-2, -2).apply { topMargin = dp(28) })
        addView(col, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        coverView = this
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
        player.release()
        super.onDestroy()
    }
}
