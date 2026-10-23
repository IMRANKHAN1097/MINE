package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SafetyStatus
import com.example.ui.theme.CautionYellow
import com.example.ui.theme.CautionYellowBg
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.CriticalRedBg
import com.example.ui.theme.DangerOrange
import com.example.ui.theme.DangerOrangeBg
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.SafeGreenBg

@Composable
fun StatusBanner(
    status: SafetyStatus,
    riskScore: Int,
    helmetNumber: String,
    workerName: String,
    modifier: Modifier = Modifier,
    isLarge: Boolean = true
) {
    val statusColor = when (status) {
        SafetyStatus.SAFE -> SafeGreen
        SafetyStatus.CAUTION -> CautionYellow
        SafetyStatus.DANGER -> DangerOrange
        SafetyStatus.CRITICAL -> CriticalRed
    }

    val statusBg = when (status) {
        SafetyStatus.SAFE -> SafeGreenBg
        SafetyStatus.CAUTION -> CautionYellowBg
        SafetyStatus.DANGER -> DangerOrangeBg
        SafetyStatus.CRITICAL -> CriticalRedBg
    }

    val statusIcon = when (status) {
        SafetyStatus.SAFE -> Icons.Default.CheckCircle
        SafetyStatus.CAUTION -> Icons.Default.WarningAmber
        SafetyStatus.DANGER -> Icons.Default.Warning
        SafetyStatus.CRITICAL -> Icons.Default.Dangerous
    }

    // Pulse animation for DANGER and CRITICAL
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (status == SafetyStatus.CRITICAL) 400 else 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val effectiveBg = if (status >= SafetyStatus.DANGER) {
        statusBg.copy(alpha = statusBg.alpha * alphaAnim)
    } else {
        statusBg
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(effectiveBg, MaterialTheme.colorScheme.surfaceVariant)
                )
            )
            .border(
                width = if (status == SafetyStatus.CRITICAL) 2.dp else 1.dp,
                color = statusColor.copy(alpha = if (status >= SafetyStatus.DANGER) alphaAnim else 0.8f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(if (isLarge) 20.dp else 12.dp)
            .testTag("status_banner_${status.label.lowercase()}")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(if (isLarge) 44.dp else 32.dp)
                            .background(statusColor.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = status.label,
                            tint = statusColor,
                            modifier = Modifier.size(if (isLarge) 28.dp else 20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = status.label,
                            color = statusColor,
                            fontWeight = FontWeight.Black,
                            fontSize = if (isLarge) 26.sp else 18.sp,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "$workerName • $helmetNumber",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = if (isLarge) 13.sp else 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Risk meter badge
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "RISK $riskScore%",
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isLarge) 14.sp else 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            if (isLarge) {
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { (riskScore / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}
