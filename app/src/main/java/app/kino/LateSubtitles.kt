package app.kino

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import java.util.concurrent.Future

/**
 * Subtitles that may still be on their way. Merged with the video, it lets the video connect, read its index and
 * buffer straight away; only the last step of getting ready waits for [subs], which have nearly always arrived by then.
 * With none, or once [subs] fails, it's an empty track list. Media3 can't add tracks to a period that's ready, so this
 * is as late as subtitles can join without restarting the video.
 */
@OptIn(UnstableApi::class)
class LateSubtitles(private val subs: Future<List<MediaItem.SubtitleConfiguration>>, private val data: DataSource.Factory) :
    CompositeMediaSource<Unit>() {
    private val periods = mutableListOf<Late>()
    private var inner: MediaSource? = null
    private var arrived = false
    private var preparing: Any? = null // which prepare a late answer belongs to

    override fun getMediaItem(): MediaItem = MediaItem.EMPTY

    override fun prepareSourceInternal(transfer: TransferListener?) {
        super.prepareSourceInternal(transfer)
        refreshSourceInfo(SinglePeriodTimeline(C.TIME_UNSET, true, false, false, null, mediaItem))
        val handler = Util.createHandlerForCurrentLooper()
        val token = Any().also { preparing = it }
        Net.fan.execute {
            val list = runCatching { subs.get() }.getOrDefault(emptyList())
            handler.post { if (preparing === token) arrive(list) }
        }
    }

    // On the playback thread. Subtitle sources report their timeline at once, so their periods can be made straight away.
    private fun arrive(list: List<MediaItem.SubtitleConfiguration>) {
        arrived = true
        inner = list.takeIf { it.isNotEmpty() }?.let { MergingMediaSource(*it.map(::source).toTypedArray()) }
        inner?.let { prepareChildSource(Unit, it) }
        periods.forEach { it.attach() }
    }

    // Ready without touching the network; each file loads afterwards. Needs the text renderer's legacy decoding.
    private fun source(s: MediaItem.SubtitleConfiguration): MediaSource = SingleSampleMediaSource.Factory(data).createMediaSource(s, C.TIME_UNSET)

    override fun onChildSourceInfoRefreshed(id: Unit?, source: MediaSource, timeline: androidx.media3.common.Timeline) {}

    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod =
        Late(id, allocator, startPositionUs).also { periods += it; if (arrived) it.attach() }

    override fun releasePeriod(period: MediaPeriod) {
        val p = period as Late
        periods -= p
        p.inner?.let { inner?.releasePeriod(it) }
    }

    override fun releaseSourceInternal() {
        preparing = null // a late answer has nothing to attach to
        arrived = false
        inner = null
        super.releaseSourceInternal()
    }

    /** Stands in for the subtitles' period: ready once they've arrived, then everything goes to theirs. */
    private inner class Late(val id: MediaSource.MediaPeriodId, val allocator: Allocator, var startUs: Long) : MediaPeriod, MediaPeriod.Callback {
        var inner: MediaPeriod? = null
        private var callback: MediaPeriod.Callback? = null
        private var attached = false

        fun attach() {
            if (attached) return
            attached = true
            inner = this@LateSubtitles.inner?.createPeriod(id, allocator, startUs)
            callback?.let { start(it) }
        }

        private fun start(cb: MediaPeriod.Callback) = inner?.prepare(this, startUs) ?: cb.onPrepared(this)

        override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) {
            this.callback = callback
            startUs = positionUs
            if (attached) start(callback)
        }

        override fun onPrepared(period: MediaPeriod) { callback?.onPrepared(this) }
        override fun onContinueLoadingRequested(source: MediaPeriod) { callback?.onContinueLoadingRequested(this) }
        override fun maybeThrowPrepareError() { inner?.maybeThrowPrepareError() }
        override fun getTrackGroups(): TrackGroupArray = inner?.trackGroups ?: TrackGroupArray.EMPTY
        override fun selectTracks(selections: Array<out ExoTrackSelection?>, mayRetainStreamFlags: BooleanArray,
                                  streams: Array<SampleStream?>, streamResetFlags: BooleanArray, positionUs: Long) =
            inner?.selectTracks(selections, mayRetainStreamFlags, streams, streamResetFlags, positionUs) ?: positionUs
        override fun discardBuffer(positionUs: Long, toKeyframe: Boolean) { inner?.discardBuffer(positionUs, toKeyframe) }
        override fun readDiscontinuity() = inner?.readDiscontinuity() ?: C.TIME_UNSET
        override fun seekToUs(positionUs: Long) = inner?.seekToUs(positionUs) ?: positionUs
        override fun getAdjustedSeekPositionUs(positionUs: Long, seekParameters: SeekParameters) =
            inner?.getAdjustedSeekPositionUs(positionUs, seekParameters) ?: positionUs
        override fun getBufferedPositionUs() = inner?.bufferedPositionUs ?: C.TIME_END_OF_SOURCE
        override fun getNextLoadPositionUs() = inner?.nextLoadPositionUs ?: C.TIME_END_OF_SOURCE
        override fun continueLoading(loadingInfo: LoadingInfo) = inner?.continueLoading(loadingInfo) ?: false
        override fun isLoading() = inner?.isLoading ?: false
        override fun reevaluateBuffer(positionUs: Long) { inner?.reevaluateBuffer(positionUs) }
    }
}
