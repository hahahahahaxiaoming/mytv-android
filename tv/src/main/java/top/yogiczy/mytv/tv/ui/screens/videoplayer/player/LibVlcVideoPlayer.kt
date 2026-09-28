package top.yogiczy.mytv.tv.ui.screens.videoplayer.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import android.view.View
import kotlinx.coroutines.CoroutineScope
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout
import top.yogiczy.mytv.tv.ui.utils.Configs

/** 使用 libVLC 播放，作为 Media3 兼容性不佳设备的备用播放器。 */
class LibVlcVideoPlayer(
    context: Context,
    coroutineScope: CoroutineScope,
    private val hardwareDecode: Boolean,
) : VideoPlayer(coroutineScope), IVLCVout.OnNewVideoLayoutListener {
    private val appContext = context.applicationContext
    private val libVlc by lazy {
        LibVLC(
            appContext,
            arrayListOf(
                "--http-user-agent=${Configs.videoPlayerUserAgent}",
                "--network-caching=1500",
                "--live-caching=1500",
            ),
        )
    }
    private val mediaPlayer by lazy { MediaPlayer(libVlc) }
    private var videoSurfaceView: SurfaceView? = null
    private var initialized = false
    private var mediaDuration = 0L

    private val surfaceLayoutChangeListener =
        View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            updateWindowSize(right - left, bottom - top)
        }

    private val eventListener = object : MediaPlayer.EventListener {
        override fun onEvent(event: MediaPlayer.Event) {
            when (event.type) {
                MediaPlayer.Event.Buffering -> {
                    triggerError(null)
                    triggerBuffering(event.buffering < 100f)
                }

                MediaPlayer.Event.Playing -> {
                    triggerBuffering(false)
                    triggerReady()
                    triggerIsPlayingChanged(true)
                    updateMetadata()
                }

                MediaPlayer.Event.Paused,
                MediaPlayer.Event.Stopped,
                MediaPlayer.Event.EndReached -> triggerIsPlayingChanged(false)

                MediaPlayer.Event.EncounteredError -> {
                    triggerIsPlayingChanged(false)
                    triggerError(PlaybackException.VLC_PLAYBACK_ERROR)
                }

                MediaPlayer.Event.TimeChanged -> {
                    triggerCurrentPosition(
                        if (mediaDuration > 0L) event.timeChanged else System.currentTimeMillis()
                    )
                }

                MediaPlayer.Event.LengthChanged -> {
                    mediaDuration = event.lengthChanged
                    triggerDuration(mediaDuration)
                }

                MediaPlayer.Event.ESAdded,
                MediaPlayer.Event.ESSelected,
                MediaPlayer.Event.Vout -> updateMetadata()
            }
        }
    }

    override fun initialize() {
        super.initialize()
        mediaPlayer.setEventListener(eventListener)
        initialized = true
        attachVideoOutput()
    }

    override fun prepare(url: String) {
        mediaDuration = 0L
        val media = Media(libVlc, Uri.parse(url))
        // 与 VLC for Android 的自动硬件加速一致：启用硬解，但允许不兼容设备回退。
        media.setHWDecoderEnabled(hardwareDecode, false)
        media.addOption(":network-caching=1500")
        media.addOption(":live-caching=1500")
        mediaPlayer.setMedia(media)
        media.release()
        triggerPrepared()
        mediaPlayer.play()
    }

    override fun play() {
        mediaPlayer.play()
    }

    override fun pause() {
        mediaPlayer.pause()
    }

    override fun seekTo(position: Long) {
        mediaPlayer.setTime(position)
    }

    override fun stop() {
        mediaPlayer.stop()
        mediaDuration = 0L
        super.stop()
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        if (videoSurfaceView === surfaceView) return
        videoSurfaceView?.removeOnLayoutChangeListener(surfaceLayoutChangeListener)
        detachVideoOutput()
        videoSurfaceView = surfaceView
        surfaceView.addOnLayoutChangeListener(surfaceLayoutChangeListener)
        attachVideoOutput()
    }

    private fun attachVideoOutput() {
        if (!initialized) return
        val surfaceView = videoSurfaceView ?: return
        val vlcVout = mediaPlayer.vlcVout
        if (vlcVout.areViewsAttached()) return
        vlcVout.setVideoView(surfaceView)
        vlcVout.attachViews(this)
        updateWindowSize(surfaceView.width, surfaceView.height)
    }

    private fun detachVideoOutput() {
        if (!initialized) return
        val vlcVout = mediaPlayer.vlcVout
        if (vlcVout.areViewsAttached()) vlcVout.detachViews()
    }

    private fun updateWindowSize(width: Int, height: Int) {
        if (!initialized || width <= 0 || height <= 0) return
        val vlcVout = mediaPlayer.vlcVout
        if (vlcVout.areViewsAttached()) vlcVout.setWindowSize(width, height)
    }

    override fun onNewVideoLayout(
        vlcVout: IVLCVout,
        width: Int,
        height: Int,
        visibleWidth: Int,
        visibleHeight: Int,
        sarNum: Int,
        sarDen: Int,
    ) {
        if (visibleWidth <= 0 || visibleHeight <= 0) return
        videoSurfaceView?.let { updateWindowSize(it.width, it.height) }
        val displayWidth =
            if (sarNum > 0 && sarDen > 0) visibleWidth * sarNum / sarDen else visibleWidth
        triggerResolution(displayWidth, visibleHeight)
    }

    private fun updateMetadata() {
        val media = mediaPlayer.media ?: return
        try {
            var updated = metadata.copy(
                videoDecoder = if (hardwareDecode) "libVLC硬件解码" else "libVLC软件解码",
                audioDecoder = "libVLC自动解码",
            )
            for (index in 0 until media.trackCount) {
                when (val track = media.getTrack(index)) {
                    is IMedia.VideoTrack -> updated = updated.copy(
                        videoMimeType = track.codec.orEmpty(),
                        videoWidth = track.width,
                        videoHeight = track.height,
                        videoFrameRate = if (track.frameRateDen > 0) {
                            track.frameRateNum.toFloat() / track.frameRateDen
                        } else {
                            0f
                        },
                        videoBitrate = track.bitrate,
                    )

                    is IMedia.AudioTrack -> updated = updated.copy(
                        audioMimeType = track.codec.orEmpty(),
                        audioChannels = track.channels,
                        audioSampleRate = track.rate,
                    )
                }
            }
            metadata = updated
            triggerMetadata(updated)
        } finally {
            media.release()
        }
    }

    override fun release() {
        videoSurfaceView?.removeOnLayoutChangeListener(surfaceLayoutChangeListener)
        detachVideoOutput()
        initialized = false
        mediaPlayer.setEventListener(null)
        mediaPlayer.release()
        libVlc.release()
        super.release()
    }
}
