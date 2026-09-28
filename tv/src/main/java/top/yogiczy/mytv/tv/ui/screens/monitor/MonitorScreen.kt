package top.yogiczy.mytv.tv.ui.screens.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import top.yogiczy.mytv.tv.ui.material.Visible
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screens.monitor.components.MonitorFps
import top.yogiczy.mytv.tv.ui.screens.monitor.components.MonitorPerformance
import top.yogiczy.mytv.tv.ui.screens.videoplayer.components.VideoPlayerMetadata
import top.yogiczy.mytv.tv.ui.screens.videoplayer.player.VideoPlayer
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.tooling.PreviewWithLayoutGrids

@Composable
fun MonitorScreen(
    modifier: Modifier = Modifier,
    showFps: Boolean = false,
    showMetadata: Boolean = false,
    showPerformance: Boolean = false,
    metadataProvider: () -> VideoPlayer.Metadata = { VideoPlayer.Metadata() },
) {
    val childPadding = rememberChildPadding()

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = childPadding.start, top = childPadding.top),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Visible({ showMetadata }) {
                VideoPlayerMetadata(metadataProvider = metadataProvider)
            }
            Visible({ showPerformance }) {
                MonitorPerformance()
            }
            Visible({ showFps && !showPerformance }) {
                MonitorFps()
            }
        }
    }
}

@Preview(device = "id:Android TV (720p)")
@Composable
private fun MonitorScreenPreview() {
    MyTVTheme {
        PreviewWithLayoutGrids {
            MonitorScreen(
                showFps = true,
                showMetadata = true,
                showPerformance = true,
            )
        }
    }
}
