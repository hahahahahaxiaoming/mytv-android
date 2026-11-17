package top.yogiczy.mytv.ui.screens.leanback.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
internal fun WebVideoLoading(progress: Float) {
    val barShape = RoundedCornerShape(3.dp)
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("正在切换频道…", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Text(
                "${progress.toInt()}%",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
            )
            Box(
                Modifier.width(280.dp).height(6.dp)
                    .clip(barShape).background(Color.White.copy(alpha = 0.2f)),
            ) {
                Box(
                    Modifier.fillMaxWidth(progress / 100f).height(6.dp)
                        .clip(barShape).background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
