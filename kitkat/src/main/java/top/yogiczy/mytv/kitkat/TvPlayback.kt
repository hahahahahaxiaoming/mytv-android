package top.yogiczy.mytv.kitkat

import android.annotation.SuppressLint
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.video.PLAY_VIDEO_SCRIPT
import top.yogiczy.mytv.ui.screens.leanback.video.player.RtpDataSource

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class TvPlayback(private val screen: FrameLayout) {
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var web: WebView? = null
    private var attempt = 0

    fun stop() {
        attempt++
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        screen.removeAllViews()
        web?.stopLoading()
        web?.destroy()
        web = null
    }

    fun play(address: String, ready: () -> Unit, failed: () -> Unit) {
        stop()
        val current = attempt
        val timeout = Runnable { if (attempt == current) failed() }
        handler.postDelayed(timeout, Constants.VIDEO_PLAYER_LOAD_TIMEOUT)
        val onReady = {
            if (attempt == current) {
                handler.removeCallbacks(timeout)
                ready()
            }
        }
        val onError = { handler.post { if (attempt == current) failed() }; Unit }
        if (address.startsWith(Constants.WEB_IPTV_URL_PREFIX)) {
            openPage(address.removePrefix(Constants.WEB_IPTV_URL_PREFIX), onReady, onError)
        } else {
            val surface = SurfaceView(screen.context)
            screen.addView(surface)
            val http = DefaultHttpDataSource.Factory()
                .setUserAgent(Constants.VIDEO_PLAYER_USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
            val factory = DefaultMediaSourceFactory(http)
            player = ExoPlayer.Builder(screen.context).setMediaSourceFactory(factory).build().also { exo ->
                exo.setVideoSurfaceView(surface)
                var triedHls = false
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) onReady()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        if (!triedHls && address.startsWith("http", true) &&
                            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                        ) {
                            triedHls = true
                            exo.setMediaItem(MediaItem.Builder().setUri(address)
                                .setMimeType(MimeTypes.APPLICATION_M3U8).build())
                            exo.prepare()
                        } else onError()
                    }
                })
                val item = MediaItem.fromUri(address)
                if (address.startsWith("rtp://", true)) {
                    val factory = RtpDataSource.Factory(Constants.VIDEO_PLAYER_LOAD_TIMEOUT.toInt())
                    exo.setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(item))
                } else {
                    exo.setMediaItem(item)
                }
                exo.prepare()
                exo.playWhenReady = true
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openPage(address: String, ready: () -> Unit, failed: () -> Unit) {
        val view = WebView(screen.context)
        web = view
        view.setBackgroundColor(Color.BLACK)
        view.isFocusable = false
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false
            useWideViewPort = true
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        }
        view.webViewClient = object : WebViewClient() {
            @Deprecated("KitKat callback")
            override fun shouldOverrideUrlLoading(view: WebView, url: String) =
                Uri.parse(url).scheme !in listOf("http", "https")
            @Deprecated("KitKat callback")
            override fun onReceivedError(view: WebView, code: Int, description: String?, url: String?) = failed()
        }
        view.webChromeClient = object : WebChromeClient() {
            private var fullscreen: View? = null
            override fun onShowCustomView(custom: View, callback: CustomViewCallback) {
                if (web !== view || fullscreen != null) return callback.onCustomViewHidden()
                fullscreen = custom
                screen.addView(custom)
            }
            override fun onHideCustomView() {
                fullscreen?.let(screen::removeView)
                fullscreen = null
            }
        }
        screen.addView(view)
        view.loadUrl(address)
        fun probe() {
            if (web !== view) return
            view.evaluateJavascript(PLAY_VIDEO_SCRIPT) { dimensions ->
                if (web === view) {
                    if (dimensions?.startsWith("[") == true) ready()
                    else handler.postDelayed({ probe() }, 500)
                }
            }
        }
        probe()
    }
}
