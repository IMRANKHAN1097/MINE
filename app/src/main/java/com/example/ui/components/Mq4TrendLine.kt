package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safety.AiSafetyEngine
import com.example.ui.theme.AmberSafety
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.DarkSurfaceVariant

@Composable
fun Mq4TrendLine(
    history: List<Int>,
    currentValue: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag("mq4_trend_card")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MQ-4 Methane Trend",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "• Real-time (last 30 samples)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$currentValue ADC",
                    color = if (currentValue >= AiSafetyEngine.MQ4_LIMIT) CriticalRed else AmberSafety,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .testTag("mq4_trend_canvas")
            ) {
                val w = size.width
                val h = size.height

                if (history.isEmpty()) return@Canvas

                val minVal = 200f
                val maxVal = 2800f
                val range = maxVal - minVal

                // Draw Danger Threshold (2200 ADC)
                val dangerY = h - ((AiSafetyEngine.MQ4_LIMIT - minVal) / range) * h
                drawLine(
                    color = CriticalRed.copy(alpha = 0.7f),
                    start = Offset(0f, dangerY),
                    end = Offset(w, dangerY),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                // Safe threshold (1000 ADC)
                val safeY = h - ((1000 - minVal) / range) * h
                drawLine(
                    color = Color.Gray.copy(alpha = 0.25f),
                    start = Offset(0f, safeY),
                    end = Offset(w, safeY),
                    strokeWidth = 1f
                )

                val points = mutableListOf<Offset>()
                val stepX = if (history.size > 1) w / (history.size - 1) else w

                history.forEachIndexed { i, v ->
                    val clamped = v.toFloat().coerceIn(minVal, maxVal)
                    val y = h - ((clamped - minVal) / range) * h
                    val x = i * stepX
                    points.add(Offset(x, y))
                }

                if (points.size > 1) {
                    val linePath = Path()
                    val fillPath = Path()

                    linePath.moveTo(points.first().x, points.first().y)
                    fillPath.moveTo(points.first().x, h)
                    fillPath.lineTo(points.first().x, points.first().y)

                    for (i in 1 until points.size) {
                        val prev = points[i - 1]
                        val curr = points[i]
                        val midX = (prev.x + curr.x) / 2f
                        linePath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                        fillPath.cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                    }

                    fillPath.lineTo(points.last().x, h)
                    fillPath.close()

                    // Fill gradient
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            listOf(
                                AmberSafety.copy(alpha = 0.25f),
                                Color.Transparent
                            )
                        )
                    )

                    // Stroke line
                    drawPath(
                        path = linePath,
                        color = if (currentValue >= AiSafetyEngine.MQ4_LIMIT) CriticalRed else AmberSafety,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Last point dot
                    val lastPt = points.last()
                    drawCircle(
                        color = if (currentValue >= AiSafetyEngine.MQ4_LIMIT) CriticalRed else AmberSafety,
                        radius = 4.dp.toPx(),
                        center = lastPt
                    )
                }
            }
        }
    }
}
