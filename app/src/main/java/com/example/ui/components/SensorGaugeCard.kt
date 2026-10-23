package com.example.ui.components

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
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.SensorData
import com.example.safety.AiSafetyEngine
import com.example.ui.theme.AmberSafety
import com.example.ui.theme.CautionYellow
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.TacticalCyan

@Composable
fun SensorTile(
    label: String,
    subLabel: String,
    valueStr: String,
    unit: String,
    progress: Float,
    isWarning: Boolean,
    isDanger: Boolean,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    val effectiveColor = when {
        isDanger -> CriticalRed
        isWarning -> CautionYellow
        else -> color
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isDanger) 1.5.dp else 1.dp,
                color = if (isDanger) CriticalRed else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(12.dp)
            .testTag("sensor_tile_${label.lowercase().replace(" ", "_")}")
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
                            .size(28.dp)
                            .background(effectiveColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = effectiveColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = subLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }

                if (isDanger) {
                    Box(
                        modifier = Modifier
                            .background(CriticalRed.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LIMIT!",
                            color = CriticalRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = valueStr,
                        color = effectiveColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = effectiveColor,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

@Composable
fun SensorGrid(
    sensors: SensorData,
    modifier: Modifier = Modifier
) {
    val mq4Progress = sensors.mq4 / 3000f
    val mq135Progress = sensors.mq135 / 3000f
    val accelProgress = (sensors.accel / 35.0).toFloat()
    val irProgress = (sensors.ir / 60000f)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SensorTile(
                label = "MQ-4 Methane",
                subLabel = "${String.format("%.0f", sensors.mq4_ppm)} PPM • Lim: 2200",
                valueStr = "${sensors.mq4}",
                unit = "ADC",
                progress = mq4Progress,
                isWarning = sensors.mq4 in 1400..2199,
                isDanger = sensors.mq4 >= AiSafetyEngine.MQ4_LIMIT,
                icon = Icons.Default.LocalGasStation,
                color = AmberSafety,
                modifier = Modifier.weight(1f)
            )

            SensorTile(
                label = "MQ-135 Air",
                subLabel = "${String.format("%.1f", sensors.mq135_ppm)} PPM • Lim: 2000",
                valueStr = "${sensors.mq135}",
                unit = "ADC",
                progress = mq135Progress,
                isWarning = sensors.mq135 in 1200..1999,
                isDanger = sensors.mq135 >= AiSafetyEngine.MQ135_LIMIT,
                icon = Icons.Default.Air,
                color = TacticalCyan,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SensorTile(
                label = "MPU6050 Accel",
                subLabel = "Impact: ≥24.5 m/s²",
                valueStr = String.format("%.1f", sensors.accel),
                unit = "m/s²",
                progress = accelProgress,
                isWarning = sensors.accel in 18.0..24.4,
                isDanger = sensors.accel >= AiSafetyEngine.IMPACT_ACCEL_LIMIT,
                icon = Icons.Default.Sensors,
                color = SafeGreen,
                modifier = Modifier.weight(1f)
            )

            val isWeakIr = sensors.ir in 1000..20000
            SensorTile(
                label = "MAX30105 IR",
                subLabel = "Skin contact probe",
                valueStr = "${sensors.ir}",
                unit = "raw",
                progress = irProgress,
                isWarning = isWeakIr,
                isDanger = sensors.ir < 1000,
                icon = Icons.Default.Favorite,
                color = if (isWeakIr) CautionYellow else SafeGreen,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
