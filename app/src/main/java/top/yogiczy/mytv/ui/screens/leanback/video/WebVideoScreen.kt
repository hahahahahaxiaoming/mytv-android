package top.yogiczy.mytv.ui.screens.leanback.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import top.yogiczy.mytv.ui.utils.SP
import kotlin.coroutines.resume

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
internal fun WebVideoScreen(
    modifier: Modifier,
    playback: LeanbackVideoPlayerState.WebPlayback,
    state: LeanbackVideoPlayerState,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var webView by remember { mutableStateOf<WebView?>(null) }
    var ready by remember { mutableStateOf(false) }
    var page by remember { mutableIntStateOf(0) }
    var pageProgress by remember { mutableIntStateOf(0) }
    val progress = remember(page) { Animatable(0f) }
    val showProgress = progress.value < 100f && state.error == null

    LaunchedEffect(progress, pageProgress, ready, state.error) {
        if (state.error == null) {
            val target = if (ready) 100f else pageProgress.coerceAtMost(99).toFloat()
            if (target > progress.value) progress.animateTo(target, tween(220))
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val container = WebVideoView(context) { pageProgress = it }
                container.player.apply {
                    setBackgroundColor(Color.BLACK)
                    isFocusable = false
                    isClickable = false
                    isHorizontalScrollBarEnabled = false
                    isVerticalScrollBarEnabled = false
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    // 与 TV 2.2.6 一样请求桌面直播页，避免跳转到手机端应用入口。
                    settings.userAgentString = DESKTOP_USER_AGENT
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            container.coverPage(true)
                            ready = false
                            pageProgress = 0
                            page++
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) =
                            !isWebUrl(request.url)

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String) =
                            !isWebUrl(Uri.parse(url))

                        // API 21/22 的回调仅报告主页面加载错误。
                        @Suppress("DEPRECATION")
                        override fun onReceivedError(
                            view: WebView,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?,
                        ) {
                            state.onWebError(playback, "网页加载失败")
                        }
                    }
                    webView = this
                    loadUrl(playback.url)
                }
                container
            },
            onRelease = { it.release() },
            update = { it.coverPage(showProgress || state.error != null) },
        )
        if (showProgress) WebVideoLoading(progress.value)
    }

    DisposableEffect(webView, lifecycle) {
        val view = webView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> view?.onResume()
                Lifecycle.Event.ON_STOP -> {
                    view?.evaluateJavascript(
                        "var video = document.querySelector('video'); if (video) video.pause();",
                        null,
                    )
                    view?.onPause()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(webView, lifecycle, page) {
        val view = webView ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (state.webPlayback !== playback || state.error != null) return@repeatOnLifecycle
            val dimensions = withTimeoutOrNull(SP.videoPlayerLoadTimeout) {
                var frame = view.findVideoFrame()
                while (frame == null && state.webPlayback === playback && state.error == null) {
                    delay(300)
                    frame = view.findVideoFrame()
                }
                frame
            }
            if (dimensions == null) {
                state.onWebError(playback, "网页视频加载超时")
            } else if (!ready) {
                ready = true
                state.onWebReady(playback, dimensions.first, dimensions.second)
            }
        }
    }
}

private fun isWebUrl(uri: Uri) = uri.scheme == "http" || uri.scheme == "https"

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0"

@SuppressLint("ClickableViewAccessibility")
private class WebVideoView(
    context: Context,
    onProgressChanged: (Int) -> Unit,
) : FrameLayout(context) {
    val player = object : WebView(context) {
        override fun onTouchEvent(event: MotionEvent): Boolean = false
    }
    private val cover = View(context).apply { setBackgroundColor(Color.BLACK) }
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    init {
        addView(player, matchParent())
        addView(cover, matchParent())
        player.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                onProgressChanged(newProgress)
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                fullscreenView = view
                fullscreenCallback = callback
                addView(view, indexOfChild(cover), matchParent())
            }

            override fun onHideCustomView() {
                hideFullscreen()
            }
        }
    }

    fun coverPage(covered: Boolean) {
        cover.visibility = if (covered) View.VISIBLE else View.GONE
    }

    fun release() {
        hideFullscreen()
        player.stopLoading()
        removeView(player)
        player.destroy()
    }

    private fun hideFullscreen() {
        val view = fullscreenView ?: return
        val callback = fullscreenCallback
        fullscreenView = null
        fullscreenCallback = null
        removeView(view)
        callback?.onCustomViewHidden()
    }

    private fun matchParent() =
        LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
}

private suspend fun WebView.findVideoFrame(): Pair<Int, Int>? =
    suspendCancellableCoroutine { continuation ->
        evaluateJavascript(PLAY_VIDEO_SCRIPT) { result ->
            val dimensions = if (result != null && result.startsWith("[")) {
                val values = JSONArray(result)
                val width = values.optInt(0)
                val height = values.optInt(1)
                if (width > 0 && height > 0) width to height else null
            } else null
            continuation.resume(dimensions)
        }
    }

private val PLAY_VIDEO_SCRIPT = """
    (function () {
        var video = document.querySelector('video');
        if (!video || !document.body) return null;
        if (video.parentNode !== document.body) document.body.appendChild(video);
        if (!document.getElementById('mytv-video-layout')) {
            var style = document.createElement('style');
            style.id = 'mytv-video-layout';
            style.textContent = 'html,body{margin:0!important;overflow:hidden!important;background:#000!important;}' +
                'body > :not([data-mytv-video]){display:none!important;}';
            document.head.appendChild(style);
        }
        video.setAttribute('data-mytv-video', '');
        video.removeAttribute('controls');
        video.style.cssText = 'position:fixed!important;left:0!important;top:0!important;' +
            'width:100vw!important;height:100vh!important;max-width:none!important;' +
            'max-height:none!important;object-fit:contain!important;background:black!important;' +
            'z-index:2147483647!important;margin:0!important;padding:0!important;';
        video.autoplay = true;
        if (video.paused) {
            var play = video.play();
            if (play && play.catch) play.catch(function () {});
        }
        if (video.paused || video.readyState < 2 || !video.videoWidth) return null;
        return [video.videoWidth, video.videoHeight];
    })()
""".trimIndent()
