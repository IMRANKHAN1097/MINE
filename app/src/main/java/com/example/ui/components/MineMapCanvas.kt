package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AiInsight
import com.example.model.GpsData
import com.example.model.SafetyStatus
import com.example.model.TelemetryData
import com.example.model.WorkerProfile
import com.example.ui.theme.CautionYellow
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.DangerOrange
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.Obsidian
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.TacticalCyan

@Composable
fun MineMapCanvas(
    workers: List<WorkerProfile>,
    telemetryMap: Map<String, TelemetryData>,
    insightsMap: Map<String, AiInsight>,
    trailsMap: Map<String, List<GpsData>>,
    selectedWorkerId: String?,
    onSelectWorker: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(14.dp)
            .testTag("mine_map_card")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Map",
                    tint = TacticalCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Subterranean Mine Sector & GPS Map",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${workers.size} Helmets Active",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Map canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Obsidian)
                    .border(1.dp, Color(0xFF1E2838), RoundedCornerShape(8.dp))
            ) {
                Canvas(
                    modifier = Modifier
                        .matchParentSize()
                        .testTag("mine_map_canvas")
                ) {
                    val w = size.width
                    val h = size.height

                    // Draw Mine Gallery Grid
                    val gridColor = Color(0xFF152233)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)

                    // Vertical gallery grid lines
                    for (x in 0..6) {
                        val posX = w * (x / 6f)
                        drawLine(
                            color = gridColor,
                            start = Offset(posX, 0f),
                            end = Offset(posX, h),
                            strokeWidth = 1f,
                            pathEffect = dashEffect
                        )
                    }

                    // Horizontal tunnel levels
                    for (y in 0..5) {
                        val posY = h * (y / 5f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, posY),
                            end = Offset(w, posY),
                            strokeWidth = 1f,
                            pathEffect = dashEffect
                        )
                    }

                    // Main Drift Tunnel axis
                    val tunnelPath = Path().apply {
                        moveTo(w * 0.1f, h * 0.85f)
                        lineTo(w * 0.45f, h * 0.5f)
                        lineTo(w * 0.85f, h * 0.2f)
                    }
                    drawPath(
                        path = tunnelPath,
                        color = Color(0xFF26394E),
                        style = Stroke(width = 8.dp.toPx())
                    )

                    // Stope gallery branch
                    val stopeBranch = Path().apply {
                        moveTo(w * 0.45f, h * 0.5f)
                        lineTo(w * 0.65f, h * 0.75f)
                    }
                    drawPath(
                        path = stopeBranch,
                        color = Color(0xFF1E2C3D),
                        style = Stroke(width = 5.dp.toPx())
                    )

                    // Map Worker GPS coordinates to screen
                    // Center reference: Lat 22.573, Lng 88.364 with delta ~0.003
                    val baseLat = 22.5710
                    val baseLng = 88.3620
                    val spanLat = 0.0050
                    val spanLng = 0.0050

                    workers.forEachIndexed { idx, worker ->
                        val telemetry = telemetryMap[worker.esp32Id]
                        val gps = telemetry?.gps ?: GpsData()
                        val insight = insightsMap[worker.esp32Id]
                        val status = insight?.status ?: SafetyStatus.SAFE
                        val statusColor = when (status) {
                            SafetyStatus.SAFE -> SafeGreen
                            SafetyStatus.CAUTION -> CautionYellow
                            SafetyStatus.DANGER -> DangerOrange
                            SafetyStatus.CRITICAL -> CriticalRed
                        }

                        // Compute screen pos with fallback offset per worker index
                        val normX = if (gps.valid) {
                            ((gps.lng - baseLng) / spanLng).toFloat().coerceIn(0.15f, 0.85f)
                        } else {
                            0.3f + idx * 0.3f
                        }
                        val normY = if (gps.valid) {
                            (1f - ((gps.lat - baseLat) / spanLat).toFloat()).coerceIn(0.15f, 0.85f)
                        } else {
                            0.4f + idx * 0.25f
                        }

                        val center = Offset(normX * w, normY * h)

                        // Draw GPS breadcrumb trail
                        val trail = trailsMap[worker.esp32Id] ?: emptyList()
                        if (trail.size > 1) {
                            val trailPath = Path()
                            trail.forEachIndexed { tIdx, pt ->
                                val ptX = ((pt.lng - baseLng) / spanLng).toFloat().coerceIn(0.1f, 0.9f) * w
                                val ptY = (1f - ((pt.lat - baseLat) / spanLat).toFloat()).coerceIn(0.1f, 0.9f) * h
                                if (tIdx == 0) trailPath.moveTo(ptX, ptY) else trailPath.lineTo(ptX, ptY)
                                drawCircle(
                                    color = statusColor.copy(alpha = 0.4f),
                                    radius = 2.dp.toPx(),
                                    center = Offset(ptX, ptY)
                                )
                            }
                            drawPath(
                                path = trailPath,
                                color = statusColor.copy(alpha = 0.3f),
                                style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashEffect)
                            )
                        }

                        // Hazard aura circle
                        drawCircle(
                            color = statusColor.copy(alpha = 0.25f),
                            radius = 16.dp.toPx(),
                            center = center
                        )

                        // Marker outline
                        drawCircle(
                            color = statusColor,
                            radius = 7.dp.toPx(),
                            center = center
                        )

                        // Center core
                        drawCircle(
                            color = Color.White,
                            radius = 3.dp.toPx(),
                            center = center
                        )
                    }
                }

                // Sector labels on map
                Text(
                    text = "SECTOR A • PORTAL INCLINE",
                    color = Color(0xFF4A6572),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp)
                )

                Text(
                    text = "SECTOR C • DEEP STOPE",
                    color = Color(0xFF4A6572),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick worker marker legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                workers.forEach { worker ->
                    val insight = insightsMap[worker.esp32Id]
                    val status = insight?.status ?: SafetyStatus.SAFE
                    val statusColor = when (status) {
                        SafetyStatus.SAFE -> SafeGreen
                        SafetyStatus.CAUTION -> CautionYellow
                        SafetyStatus.DANGER -> DangerOrange
                        SafetyStatus.CRITICAL -> CriticalRed
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onSelectWorker(worker.esp32Id) }
                            .padding(end = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${worker.helmetNumber} (${worker.workerName.take(8)})",
                            color = if (selectedWorkerId == worker.esp32Id) TacticalCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = if (selectedWorkerId == worker.esp32Id) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
