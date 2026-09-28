package top.yogiczy.mytv.tv.ui.screens.monitor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import java.util.Locale

@Composable
fun MonitorPerformance(
    modifier: Modifier = Modifier,
    state: MonitorPerformanceState = rememberMonitorPerformanceState(),
) {
    val gpuFrameTime = state.gpuFrameTimeMs?.let { "%.2f ms".format(Locale.US, it) } ?: "N/A"

    Column(
        modifier = modifier
            .widthIn(min = 240.dp, max = 340.dp)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                MaterialTheme.shapes.medium,
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "性能监控",
            style = MaterialTheme.typography.titleSmall,
        )
        MonitorPerformanceText("FPS: ${state.fps}  UI掉帧: ${state.droppedFrames}")
        MonitorPerformanceText("CPU: ${"%.1f".format(Locale.US, state.cpuUsage)}%")
        MonitorPerformanceText(
            "应用内存: ${"%.1f".format(Locale.US, state.appMemoryMb)} MB (PSS)"
        )
        MonitorPerformanceText(
            "系统内存: ${formatMemory(state.systemUsedMemoryMb)} / " +
                formatMemory(state.systemTotalMemoryMb)
        )
        MonitorPerformanceText("下行网速: ${formatNetworkSpeed(state.networkBytesPerSecond)}")
        MonitorPerformanceText("GPU: ${state.gpuRenderer}", maxLines = 2)
        MonitorPerformanceText("GPU占用: N/A（系统未开放）")
        MonitorPerformanceText("GPU帧耗时: $gpuFrameTime")
    }
}

@Composable
private fun MonitorPerformanceText(
    text: String,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun formatMemory(memoryMb: Long): String =
    if (memoryMb >= 1024L) {
        "%.2f GB".format(Locale.US, memoryMb / 1024f)
    } else {
        "$memoryMb MB"
    }

private fun formatNetworkSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= MB_BYTES ->
        "%.2f MB/s".format(Locale.US, bytesPerSecond / MB_BYTES.toFloat())

    bytesPerSecond >= KB_BYTES ->
        "%.1f KB/s".format(Locale.US, bytesPerSecond / KB_BYTES.toFloat())

    else -> "$bytesPerSecond B/s"
}

private const val KB_BYTES = 1024L
private const val MB_BYTES = 1024L * 1024L

@Preview
@Composable
private fun MonitorPerformancePreview() {
    MyTVTheme {
        MonitorPerformance(
            state = MonitorPerformanceState(
                fps = 60,
                droppedFrames = 1,
                cpuUsage = 18.4f,
                appMemoryMb = 286.5f,
                systemUsedMemoryMb = 5_940,
                systemTotalMemoryMb = 12_288,
                networkBytesPerSecond = 1_572_864,
                gpuRenderer = "Mali-G715",
                gpuFrameTimeMs = 6.24f,
            )
        )
    }
}
