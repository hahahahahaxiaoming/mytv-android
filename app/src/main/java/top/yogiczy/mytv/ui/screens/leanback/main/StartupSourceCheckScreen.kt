package top.yogiczy.mytv.ui.screens.leanback.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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

@Composable
fun LeanbackStartupSourceCheckScreen(
    checked: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val rawProgress = if (total == 0) 0f else checked.toFloat() / total
    val progress by animateFloatAsState(rawProgress.coerceIn(0f, 1f), tween(420), label = "sourceProgress")
    val transition = rememberInfiniteTransition(label = "sourcePulse")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulseScale",
    )
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulseGlow",
    )
    val current = if (total == 0) 0 else (checked + 1).coerceAtMost(total)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF17366B), Color(0xFF08152B), Color(0xFF02050D)),
                    radius = 1200f,
                )
            ),
    ) {
        NetworkBackground(Modifier.fillMaxSize().alpha(0.32f))

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(650.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(Color.White.copy(alpha = 0.065f))
                .border(1.dp, Color(0xFF63DFFF).copy(alpha = 0.24f), RoundedCornerShape(34.dp))
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(126.dp)
                        .scale(pulse)
                        .background(Color(0xFF25C7FF).copy(alpha = glow * 0.16f), CircleShape)
                        .border(1.dp, Color(0xFF66E6FF).copy(alpha = glow), CircleShape)
                )
                Box(
                    Modifier
                        .size(82.dp)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF2EDBFF), Color(0xFF725BFF))),
                            CircleShape,
                        )
                )
                Text("AI", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(30.dp))
            AnimatedContent(targetState = stageText(checked, total), label = "sourceStage") { stage ->
                Text(
                    text = stage,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (total == 0) "正在获取云端直播源…" else current.toString(),
                color = Color(0xFFA8BDD8),
                fontSize = 16.sp,
            )

            Spacer(Modifier.height(30.dp))
            Box(
                Modifier
                    .width(500.dp)
                    .height(9.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(9.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF4D75FF), Color(0xFF29DDFF), Color(0xFF8160FF))
                            )
                        )
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "云端探测  ·  智能优选  ·  自动回退",
                color = Color(0xFF7896BB),
                fontSize = 13.sp,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(44.dp))
        }
    }
}

private fun stageText(checked: Int, total: Int): String {
    if (total <= 0) return "正在接入AI大模型"
    return when {
        checked * 10 < total * 4 -> "正在接入AI大模型"
        checked * 10 < total * 7 -> "正在进行深度匹配"
        else -> "正在优化直播链路"
    }
}

@Composable
private fun NetworkBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val nodes = listOf(
            Offset(size.width * .10f, size.height * .18f),
            Offset(size.width * .27f, size.height * .34f),
            Offset(size.width * .13f, size.height * .72f),
            Offset(size.width * .82f, size.height * .16f),
            Offset(size.width * .70f, size.height * .38f),
            Offset(size.width * .90f, size.height * .69f),
            Offset(size.width * .67f, size.height * .84f),
        )
        val line = Color(0xFF50CFFF).copy(alpha = 0.28f)
        nodes.zipWithNext().forEach { (start, end) -> drawLine(line, start, end, 1.5f) }
        nodes.forEach {
            drawCircle(Color(0xFF75E9FF).copy(alpha = 0.75f), 4f, it)
            drawCircle(Color(0xFF3F8CFF).copy(alpha = 0.13f), 24f, it)
        }
    }
}
