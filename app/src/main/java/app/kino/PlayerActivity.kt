package app.kino

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
        // The device's own decoders first; FFmpeg only for audio they can't handle.
        val renderers = DefaultRenderersFactory(this).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        player = ExoPlayer.Builder(this, renderers).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).build()
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
        }
        setContentView(FrameLayout(this).apply { addView(view, FrameLayout.LayoutParams(-1, -1)) })

        player.setMediaItem(MediaItem.Builder().setUri(intent.getStringExtra("url")).setSubtitleConfigurations(subs).build())
        Store.progressFor(this, videoId)?.takeIf { !it.finished && it.position > 0 }?.let { player.seekTo(it.position) }
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
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
