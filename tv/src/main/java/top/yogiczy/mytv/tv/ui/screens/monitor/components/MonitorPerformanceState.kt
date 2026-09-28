package top.yogiczy.mytv.tv.ui.screens.monitor.components

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.net.TrafficStats
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

data class MonitorPerformanceState(
    val fps: Int = 0,
    val droppedFrames: Int = 0,
    val cpuUsage: Float = 0f,
    val appMemoryMb: Float = 0f,
    val systemUsedMemoryMb: Long = 0L,
    val systemTotalMemoryMb: Long = 0L,
    val networkBytesPerSecond: Long = 0L,
    val gpuRenderer: String = "检测中",
    val gpuFrameTimeMs: Float? = null,
)

private data class SystemPerformanceSample(
    val cpuTimeMs: Long,
    val elapsedTimeMs: Long,
    val appMemoryMb: Float,
    val systemUsedMemoryMb: Long,
    val systemTotalMemoryMb: Long,
    val receivedBytes: Long,
)

@Composable
fun rememberMonitorPerformanceState(): MonitorPerformanceState {
    val context = LocalContext.current
    val applicationContext = context.applicationContext
    val activity = remember(context) { context.findActivity() }
    val fpsState = rememberMonitorFpsState()
    val latestGpuDurationNs = remember { AtomicLong(-1L) }
    var state by remember { mutableStateOf(MonitorPerformanceState()) }

    DisposableEffect(activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || activity == null) {
            onDispose { }
        } else {
            val handlerThread = HandlerThread("performance-monitor").apply { start() }
            val listener = Window.OnFrameMetricsAvailableListener { _, frameMetrics, _ ->
                latestGpuDurationNs.set(frameMetrics.getMetric(FrameMetrics.GPU_DURATION))
            }

            activity.window.addOnFrameMetricsAvailableListener(
                listener,
                Handler(handlerThread.looper),
            )

            onDispose {
                activity.window.removeOnFrameMetricsAvailableListener(listener)
                handlerThread.quitSafely()
            }
        }
    }

    LaunchedEffect(applicationContext) {
        val gpuRenderer = withContext(Dispatchers.Default) { queryGpuRenderer() }
        var previousCpuTime = Process.getElapsedCpuTime()
        var previousElapsedTime = SystemClock.elapsedRealtime()
        var previousReceivedBytes = TrafficStats.getUidRxBytes(Process.myUid())

        while (true) {
            delay(1000)

            val sample = withContext(Dispatchers.Default) {
                collectSystemPerformanceSample(applicationContext)
            }
            val cpuDelta = sample.cpuTimeMs - previousCpuTime
            val elapsedDelta = sample.elapsedTimeMs - previousElapsedTime
            val processorCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val cpuUsage = if (elapsedDelta > 0) {
                (cpuDelta * 100f / elapsedDelta / processorCount).coerceIn(0f, 100f)
            } else {
                0f
            }
            val gpuDurationNs = latestGpuDurationNs.get()
            val networkBytesPerSecond =
                if (previousReceivedBytes >= 0L &&
                    sample.receivedBytes >= previousReceivedBytes &&
                    elapsedDelta > 0L
                ) {
                    (sample.receivedBytes - previousReceivedBytes) * 1000L / elapsedDelta
                } else {
                    0L
                }

            state = state.copy(
                cpuUsage = cpuUsage,
                appMemoryMb = sample.appMemoryMb,
                systemUsedMemoryMb = sample.systemUsedMemoryMb,
                systemTotalMemoryMb = sample.systemTotalMemoryMb,
                networkBytesPerSecond = networkBytesPerSecond,
                gpuRenderer = gpuRenderer,
                gpuFrameTimeMs = gpuDurationNs.takeIf { it >= 0L }?.div(1_000_000f),
            )

            previousCpuTime = sample.cpuTimeMs
            previousElapsedTime = sample.elapsedTimeMs
            previousReceivedBytes = sample.receivedBytes
        }
    }

    return state.copy(
        fps = fpsState.current,
        droppedFrames = fpsState.droppedFrames,
    )
}

private fun collectSystemPerformanceSample(context: Context): SystemPerformanceSample {
    val appMemoryInfo = Debug.MemoryInfo()
    Debug.getMemoryInfo(appMemoryInfo)

    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val systemMemoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(systemMemoryInfo)

    return SystemPerformanceSample(
        cpuTimeMs = Process.getElapsedCpuTime(),
        elapsedTimeMs = SystemClock.elapsedRealtime(),
        appMemoryMb = appMemoryInfo.totalPss / 1024f,
        systemUsedMemoryMb = (systemMemoryInfo.totalMem - systemMemoryInfo.availMem) / MB,
        systemTotalMemoryMb = systemMemoryInfo.totalMem / MB,
        receivedBytes = TrafficStats.getUidRxBytes(Process.myUid()),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun queryGpuRenderer(): String {
    val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    if (display == EGL14.EGL_NO_DISPLAY) return "N/A"

    val version = IntArray(2)
    if (!EGL14.eglInitialize(display, version, 0, version, 1)) return "N/A"

    val configAttributes = intArrayOf(
        EGL14.EGL_RENDERABLE_TYPE,
        EGL14.EGL_OPENGL_ES2_BIT,
        EGL14.EGL_SURFACE_TYPE,
        EGL14.EGL_PBUFFER_BIT,
        EGL14.EGL_RED_SIZE,
        8,
        EGL14.EGL_GREEN_SIZE,
        8,
        EGL14.EGL_BLUE_SIZE,
        8,
        EGL14.EGL_NONE,
    )
    val configs = arrayOfNulls<EGLConfig>(1)
    val configCount = IntArray(1)
    if (!EGL14.eglChooseConfig(
            display,
            configAttributes,
            0,
            configs,
            0,
            configs.size,
            configCount,
            0,
        ) || configCount[0] == 0
    ) {
        EGL14.eglTerminate(display)
        return "N/A"
    }

    val config = configs[0] ?: run {
        EGL14.eglTerminate(display)
        return "N/A"
    }
    val contextAttributes = intArrayOf(
        EGL14.EGL_CONTEXT_CLIENT_VERSION,
        2,
        EGL14.EGL_NONE,
    )
    val eglContext = EGL14.eglCreateContext(
        display,
        config,
        EGL14.EGL_NO_CONTEXT,
        contextAttributes,
        0,
    )
    if (eglContext == EGL14.EGL_NO_CONTEXT) {
        EGL14.eglTerminate(display)
        return "N/A"
    }

    val surfaceAttributes = intArrayOf(
        EGL14.EGL_WIDTH,
        1,
        EGL14.EGL_HEIGHT,
        1,
        EGL14.EGL_NONE,
    )
    val surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttributes, 0)
    if (surface == EGL14.EGL_NO_SURFACE) {
        EGL14.eglDestroyContext(display, eglContext)
        EGL14.eglTerminate(display)
        return "N/A"
    }

    return try {
        if (!EGL14.eglMakeCurrent(display, surface, surface, eglContext)) {
            "N/A"
        } else {
            GLES20.glGetString(GLES20.GL_RENDERER) ?: "N/A"
        }
    } finally {
        EGL14.eglMakeCurrent(
            display,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT,
        )
        EGL14.eglDestroySurface(display, surface)
        EGL14.eglDestroyContext(display, eglContext)
        EGL14.eglTerminate(display)
    }
}

private const val MB = 1024L * 1024L
