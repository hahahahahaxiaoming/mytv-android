package top.yogiczy.mytv.tv.ui.screens.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.tv.ui.material.Visible
import top.yogiczy.mytv.tv.ui.screens.webview.components.WebViewPlaceholder

/** WebView 加载超时（毫秒），超时仍未出画面则切下一条线路 */
private const val LOAD_TIMEOUT_MS = 15000L

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    modifier: Modifier = Modifier,
    urlProvider: () -> String = { "${ChannelUtil.HYBRID_WEB_VIEW_URL_PREFIX}https://tv.cctv.com/live/index.shtml" },
    onVideoResolutionChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
    onError: () -> Unit = {},
) {
    val url = urlProvider().replace(ChannelUtil.HYBRID_WEB_VIEW_URL_PREFIX, "")
    var placeholderVisible by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    // 进度条直接显示页面真实进度（视频出画面前封顶 99%）；视频出画面后快速补到 100% 再撤屏
    var displayProgress by remember { mutableFloatStateOf(0f) }
    // 视频是否已真正出画面（JS onVideoReady）
    var videoReady by remember { mutableStateOf(false) }
    var loadToken by remember { mutableIntStateOf(0) }
    val lastLoadedUrl = remember { arrayOfNulls<String>(1) }
    val errorFired = remember { booleanArrayOf(false) }

    // 网页全屏（customView）状态
    val customViewState = remember { arrayOfNulls<View>(1) }
    val customViewCallbackState = remember { arrayOfNulls<WebChromeClient.CustomViewCallback>(1) }

    fun fireError() {
        if (errorFired[0]) return
        errorFired[0] = true
        placeholderVisible = false
        onError()
    }

    // 超时守护：每次新加载开始后计时，到时仍未出画面则切线路
    LaunchedEffect(loadToken) {
        if (loadToken == 0) return@LaunchedEffect
        kotlinx.coroutines.delay(LOAD_TIMEOUT_MS)
        if (placeholderVisible) fireError()
    }

    // 进度条：直接显示页面真实进度（封顶 99%）；视频出画面后快速冲线到 100% 再撤屏
    LaunchedEffect(loadToken) {
        if (loadToken == 0) return@LaunchedEffect
        while (placeholderVisible) {
            if (!videoReady) {
                val target = progress.coerceAtMost(99).toFloat()
                if (displayProgress < target) displayProgress = target
            } else {
                displayProgress = (displayProgress + 4f).coerceAtMost(100f)
                if (displayProgress >= 100f) {
                    placeholderVisible = false
                    break
                }
            }
            kotlinx.coroutines.delay(16)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            factory = { ctx ->
                // 根容器：WebView + 全屏 customView 叠加
                val root = FrameLayout(ctx)
                val web = MyWebView(ctx)
                root.addView(
                    web,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                )

                val mainHandler = Handler(Looper.getMainLooper())

                web.webViewClient = MyClient(
                    onPageStarted = {
                        progress = 0
                        displayProgress = 0f
                        videoReady = false
                        errorFired[0] = false
                        placeholderVisible = true
                        loadToken += 1
                    },
                    onMainFrameError = { fireError() },
                )
                web.webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                        progress = newProgress
                    }

                    // 兜底：若网页自身触发全屏，原生接管全屏视图
                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        if (customViewState[0] != null) {
                            callback?.onCustomViewHidden()
                            return
                        }
                        customViewState[0] = view
                        customViewCallbackState[0] = callback
                        root.addView(
                            view,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                        )
                    }

                    override fun onHideCustomView() {
                        customViewState[0]?.let { root.removeView(it) }
                        customViewState[0] = null
                        customViewCallbackState[0]?.onCustomViewHidden()
                        customViewCallbackState[0] = null
                    }
                }

                web.setBackgroundColor(Color.Black.toArgb())

                web.settings.javaScriptEnabled = true
                web.settings.useWideViewPort = true
                web.settings.loadWithOverviewMode = true
                web.settings.domStorageEnabled = true
                web.settings.databaseEnabled = true
                web.settings.loadsImagesAutomatically = true
                web.settings.blockNetworkImage = false
                web.settings.userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36 Edg/126.0.0.0"
                web.settings.cacheMode = WebSettings.LOAD_DEFAULT
                web.settings.javaScriptCanOpenWindowsAutomatically = true
                web.settings.setSupportZoom(false)
                web.settings.displayZoomControls = false
                web.settings.builtInZoomControls = false
                web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                web.settings.mediaPlaybackRequiresUserGesture = false

                web.isHorizontalScrollBarEnabled = false
                web.isVerticalScrollBarEnabled = false
                web.isClickable = false
                web.isFocusable = false
                web.isFocusableInTouchMode = false

                web.addJavascriptInterface(
                    MyWebViewInterface(
                        onVideoResolutionChanged = { w, h ->
                            // JS 接口在绑定线程回调，Compose 状态写入必须切主线程
                            mainHandler.post { onVideoResolutionChanged(w, h) }
                        },
                        videoReadyCallback = { mainHandler.post { videoReady = true } },
                    ), "Android"
                )

                root
            },
            update = { root ->
                val web = root.getChildAt(0) as WebView
                // 仅当 URL 变化时才加载，避免进度变化触发重组而反复刷新页面
                if (lastLoadedUrl[0] != url) {
                    lastLoadedUrl[0] = url
                    // 换台瞬间立即黑屏 + 重置进度，防止旧频道网页残留透出
                    placeholderVisible = true
                    progress = 0
                    displayProgress = 0f
                    videoReady = false
                    web.stopLoading()
                    web.loadUrl(url)
                }
            },
        )

        Visible({ placeholderVisible }) {
            WebViewPlaceholder(progressProvider = { displayProgress })
        }
    }
}

class MyClient(
    private val onPageStarted: () -> Unit,
    private val onMainFrameError: () -> Unit,
) : WebViewClient() {
    // override fun shouldInterceptRequest(
    //     view: WebView?,
    //     request: WebResourceRequest?
    // ): WebResourceResponse? {
    //     if (request?.url.toString().endsWith(".css"))
    //         return WebResourceResponse("text/css", "UTF-8", null)
    //     return null
    // }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        onPageStarted()
        super.onPageStarted(view, url, favicon)
    }

    @Deprecated("Deprecated in Java")
    override fun onReceivedError(
        view: WebView?,
        errorCode: Int,
        description: String?,
        failingUrl: String?,
    ) {
        @Suppress("DEPRECATION")
        super.onReceivedError(view, errorCode, description, failingUrl)
        // 旧版系统无 request 信息，主文档错误才兜底
        if (failingUrl != null && view?.url == failingUrl) onMainFrameError()
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: android.webkit.WebResourceError?,
    ) {
        super.onReceivedError(view, request, error)
        // 仅主框架错误才兜底，忽略图片/css 等子资源失败
        if (request?.isForMainFrame == true) onMainFrameError()
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.proceed() // 直播页证书异常时继续，避免白屏
    }

    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        view.evaluateJavascript(
            """
            ;(async () => {
                function delay(ms) {
                    return new Promise(resolve => setTimeout(resolve, ms));
                }
                // 静默日志；需要排查时把 dlog 改为 console.log
                const dlog = function() {};

                try {
                dlog('js-start');

                // 1) 等 video 标签被播放器创建
                let videoEl = null
                while(true) {
                  videoEl = document.querySelector('video')
                  if(videoEl) break
                  await delay(200)
                }
                dlog('video-found');

                videoEl.volume = 1
                videoEl.autoplay = true

                // 2) 把 video 移到 body 顶层：脱离带 transform/filter 的祖先，
                //    否则 fixed 定位会相对祖先而非屏幕（这是全屏失效的根因）
                function applyLayout() {
                  if(videoEl.parentElement !== document.body) {
                    document.body.appendChild(videoEl)
                  }
                  videoEl.style.cssText =
                    'position:fixed!important;' +
                    'left:0!important;top:0!important;' +
                    'width:100vw!important;height:100vh!important;' +
                    'z-index:2147483647!important;' +
                    'background:#000!important;' +
                    'object-fit:contain!important;' +
                    'max-width:none!important;max-height:none!important;' +
                    'margin:0!important;padding:0!important;';
                  document.documentElement.style.setProperty('overflow', 'hidden', 'important');
                  if(document.body) document.body.style.background = '#000';
                  // 隐藏 body 其它所有内容（导航/推荐/广告/网页缓冲提示）
                  document.querySelectorAll('body > *').forEach(el => {
                    if(el !== videoEl) el.style.display = 'none'
                  });
                }
                applyLayout();
                dlog('video-styled');

                // 3) playing 出画面 -> 进度条冲线 100% 后撤黑屏（同时上报分辨率）
                let notified = false
                function notifyReady() {
                  if(notified) return
                  if(videoEl.videoWidth <= 0 || videoEl.videoHeight <= 0) return
                  notified = true
                  applyLayout();
                  dlog('fullscreen-ready');
                  Android.onVideoReady()
                  try {
                    Android.changeVideoResolution(videoEl.videoWidth, videoEl.videoHeight)
                  } catch(e) { dlog('res-err ' + e.message) }
                }
                videoEl.addEventListener('playing', () => {
                  dlog('playing vw=' + videoEl.videoWidth)
                  if(videoEl.videoWidth > 0) notifyReady()
                });
                videoEl.addEventListener('canplay', () => {
                  if(videoEl.videoWidth > 0) notifyReady()
                });
                videoEl.addEventListener('loadeddata', () => {
                  if(videoEl.videoWidth > 0) notifyReady()
                });

                // 4) 轮询兜底：反复确保布局（防框架移动/重置），有画面即撤
                let tries = 0
                while(true) {
                  await delay(300)
                  tries++
                  // 播放器框架若重建了 video，重新获取
                  if(!videoEl.isConnected) {
                    const nv = document.querySelector('video')
                    if(nv) videoEl = nv
                  }
                  applyLayout()
                  if(videoEl.videoWidth > 0 && videoEl.readyState >= 2) notifyReady()
                  if(notified) break
                  if(tries > 45) { dlog('giveup'); break }
                }
                } catch(e) {
                  dlog('js-err ' + e.message);
                }
            })()
        """.trimIndent()
        ) { }
    }
}

class MyWebView(context: Context) : WebView(context) {
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        return false
    }
}

class MyWebViewInterface(
    private val onVideoResolutionChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
    private val videoReadyCallback: () -> Unit = {},
) {
    @JavascriptInterface
    fun changeVideoResolution(width: Int, height: Int) {
        onVideoResolutionChanged(width, height)
    }

    @JavascriptInterface
    fun onVideoReady() {
        videoReadyCallback()
    }
}
