package top.yogiczy.mytv.tv.ui.screens.monitor.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.roundToInt

data class MonitorFpsState(
    val current: Int = 0,
    val droppedFrames: Int = 0,
    val history: ImmutableList<Int> = List(30) { 0 }.toImmutableList(),
)

@Composable
fun rememberMonitorFpsState(): MonitorFpsState {
    var state by remember { mutableStateOf(MonitorFpsState()) }
    val refreshRate = LocalView.current.display?.refreshRate?.coerceAtLeast(1f) ?: 60f

    var fpsCount by remember { mutableIntStateOf(0) }
    var droppedFrames by remember { mutableIntStateOf(0) }
    var lastFrameTime by remember { mutableLongStateOf(0L) }
    var lastUpdate by remember { mutableLongStateOf(0L) }

    LaunchedEffect(refreshRate) {
        val expectedFrameDuration = 1_000_000_000.0 / refreshRate

        while (true) {
            withFrameNanos { frameTime ->
                if (lastUpdate == 0L) {
                    lastUpdate = frameTime
                    lastFrameTime = frameTime
                    return@withFrameNanos
                }

                fpsCount++
                val frameDuration = frameTime - lastFrameTime
                if (frameDuration > expectedFrameDuration * 1.5) {
                    droppedFrames +=
                        (frameDuration / expectedFrameDuration).roundToInt().coerceAtLeast(1) - 1
                }
                lastFrameTime = frameTime

                val elapsed = frameTime - lastUpdate
                if (elapsed >= 1_000_000_000L) {
                    val fps = (fpsCount * 1_000_000_000.0 / elapsed).roundToInt()
                    state = state.copy(
                        current = fps,
                        droppedFrames = droppedFrames,
                        history = (state.history.takeLast(30) + fps).toImmutableList(),
                    )
                    lastUpdate = frameTime
                    fpsCount = 0
                    droppedFrames = 0
                }
            }
        }
    }

    return state
}
