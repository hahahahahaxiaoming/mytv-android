package top.yogiczy.mytv.ui.screens.leanback.video

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.video.player.LeanbackMedia3VideoPlayer
import top.yogiczy.mytv.ui.screens.leanback.video.player.LeanbackVideoPlayer

/**
 * 播放器状态
 */
@Stable
class LeanbackVideoPlayerState(
    private val instance: LeanbackVideoPlayer,
    private val defaultAspectRatioProvider: () -> Float? = { null },
) {
    /** 视频宽高比 */
    var aspectRatio by mutableFloatStateOf(16f / 9f)

    /** 错误 */
    var error by mutableStateOf<String?>(null)

    /** 元数据 */
    var metadata by mutableStateOf(LeanbackVideoPlayer.Metadata())

    internal class WebPlayback(val url: String)

    internal var webPlayback by mutableStateOf<WebPlayback?>(null)
        private set

    fun prepare(url: String) {
        error = null
        if (url.startsWith(Constants.WEB_IPTV_URL_PREFIX)) {
            instance.pause()
            metadata = LeanbackVideoPlayer.Metadata()
            aspectRatio = defaultAspectRatioProvider() ?: 16f / 9f
            webPlayback = WebPlayback(url.removePrefix(Constants.WEB_IPTV_URL_PREFIX))
        } else {
            webPlayback = null
            instance.prepare(url)
        }
    }

    fun play() {
        if (webPlayback == null) instance.play()
    }

    internal fun onWebReady(playback: WebPlayback, width: Int, height: Int) {
        if (webPlayback !== playback || error != null) return
        aspectRatio = defaultAspectRatioProvider() ?: width.toFloat() / height
        metadata = metadata.copy(videoWidth = width, videoHeight = height)
        onReadyListeners.forEach { it() }
    }

    internal fun onWebError(playback: WebPlayback, message: String) {
        if (webPlayback !== playback || error != null) return
        error = message
        onErrorListeners.forEach { it() }
    }

    fun pause() {
        instance.pause()
    }

    fun setVideoSurfaceView(surfaceView: SurfaceView) {
        instance.setVideoSurfaceView(surfaceView)
    }

    private val onReadyListeners = mutableListOf<() -> Unit>()
    private val onErrorListeners = mutableListOf<() -> Unit>()
    private val onCutoffListeners = mutableListOf<() -> Unit>()

    fun onReady(listener: () -> Unit) {
        onReadyListeners.add(listener)
    }

    fun onError(listener: () -> Unit) {
        onErrorListeners.add(listener)
    }

    fun onCutoff(listener: () -> Unit) {
        onCutoffListeners.add(listener)
    }

    fun initialize() {
        instance.initialize()
        instance.onResolution { width, height ->
            if (webPlayback != null) return@onResolution
            val defaultAspectRatio = defaultAspectRatioProvider()

            if (defaultAspectRatio == null) {
                if (width > 0 && height > 0) aspectRatio = width.toFloat() / height
            } else {
                aspectRatio = defaultAspectRatio
            }
        }
        instance.onError { ex ->
            if (webPlayback != null) return@onError
            error = if (ex != null) "${ex.errorCodeName}(${ex.errorCode})"
            else null

            if (error != null) onErrorListeners.forEach { it.invoke() }

        }
        instance.onReady { if (webPlayback == null) onReadyListeners.forEach { it.invoke() } }
        instance.onBuffering { if (webPlayback == null && it) error = null }
        instance.onPrepared { }
        instance.onMetadata { if (webPlayback == null) metadata = it }
        instance.onCutoff { if (webPlayback == null) onCutoffListeners.forEach { it.invoke() } }
    }

    fun release() {
        onReadyListeners.clear()
        onErrorListeners.clear()
        instance.release()
    }
}

@Composable
fun rememberLeanbackVideoPlayerState(
    defaultAspectRatioProvider: () -> Float? = { null },
): LeanbackVideoPlayerState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val state = remember {
        LeanbackVideoPlayerState(
            LeanbackMedia3VideoPlayer(context, coroutineScope),
            defaultAspectRatioProvider = defaultAspectRatioProvider,
        )
    }

    DisposableEffect(Unit) {
        state.initialize()

        onDispose {
            state.release()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                state.play()
            } else if (event == Lifecycle.Event.ON_STOP) {
                state.pause()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return state
}
