package top.yogiczy.mytv.tv.ui.screens.startup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.screens.settings.IptvSourceCheckProgress

@Composable
fun StartupSourceCheckScreen(
    progress: IptvSourceCheckProgress,
    modifier: Modifier = Modifier,
) {
    val progressValue = if (progress.total == 0) 0f else progress.checked.toFloat() / progress.total
    val animatedProgress by animateFloatAsState(progressValue, tween(450), label = "probeProgress")
    val pulseTransition = rememberInfiniteTransition(label = "startupPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "startupPulseScale",
    )
    val glow by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "startupGlow",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF142B52), Color(0xFF07101F), Color(0xFF030711)),
                    radius = 1100f,
                )
            ),
    ) {
        NetworkBackground(Modifier.fillMaxSize().alpha(0.28f))

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(620.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White.copy(alpha = 0.065f))
                .border(1.dp, Color(0xFF68D8FF).copy(alpha = 0.22f), RoundedCornerShape(32.dp))
                .animateContentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.04f), Color.Transparent)
                    )
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(44.dp))

            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(118.dp)
                        .scale(pulse)
                        .background(Color(0xFF23BFFF).copy(alpha = glow * 0.18f), CircleShape)
                        .border(1.dp, Color(0xFF63E6FF).copy(alpha = glow), CircleShape)
                )
                Box(
                    Modifier
                        .size(78.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF32D9FF), Color(0xFF6C63FF))),
                            CircleShape,
                        )
                )
                Text("TV", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(30.dp))

            AnimatedContent(targetState = stageText(progress), label = "probeStage") { text ->
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            if (progress.total > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = progress.checked.coerceAtMost(progress.total).toString(),
                    color = Color(0xFFA8BDD8),
                    fontSize = 16.sp,
                )
            }

            Spacer(Modifier.height(30.dp))
            Box(
                Modifier
                    .width(480.dp)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.09f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .height(8.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF4A7DFF), Color(0xFF28D9FF), Color(0xFF7C5CFF))
                            )
                        )
                )
            }

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(Color(0xFF51F6C1), CircleShape))
                Spacer(Modifier.width(8.dp))
                Text("快速探测 · 智能优选 · 自动回退", color = Color(0xFF7792B4), fontSize = 13.sp)
            }
            Spacer(Modifier.height(42.dp))
        }
    }
}

@Composable
private fun NetworkBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val nodes = listOf(
            Offset(size.width * .12f, size.height * .20f),
            Offset(size.width * .28f, size.height * .35f),
            Offset(size.width * .15f, size.height * .68f),
            Offset(size.width * .82f, size.height * .18f),
            Offset(size.width * .72f, size.height * .38f),
            Offset(size.width * .88f, size.height * .70f),
            Offset(size.width * .68f, size.height * .82f),
        )
        val lineColor = Color(0xFF50C9FF).copy(alpha = 0.25f)
        nodes.zipWithNext().forEach { (start, end) ->
            drawLine(lineColor, start, end, strokeWidth = 1.5f)
        }
        nodes.forEach {
            drawCircle(Color(0xFF78E6FF).copy(alpha = 0.7f), radius = 4f, center = it)
            drawCircle(Color(0xFF3F8CFF).copy(alpha = 0.12f), radius = 22f, center = it)
        }
    }
}

private fun stageText(progress: IptvSourceCheckProgress): String {
    if (progress.total == 0) return "正在接入AI大模型"
    return when {
        progress.checked * 10 < progress.total * 4 -> "正在接入AI大模型"
        progress.checked * 10 < progress.total * 7 -> "正在进行深度匹配"
        else -> "正在优化直播链路"
    }
}
