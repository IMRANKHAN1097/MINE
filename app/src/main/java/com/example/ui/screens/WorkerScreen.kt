package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Sos
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AiInsight
import com.example.model.SafetyStatus
import com.example.model.TelemetryData
import com.example.ui.SkimmsViewModel
import com.example.ui.components.AlertFeedList
import com.example.ui.components.SensorGrid
import com.example.ui.components.StatusBanner
import com.example.ui.theme.AmberSafety
import com.example.ui.theme.CautionYellow
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.DangerOrange
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.TacticalCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkerScreen(
    viewModel: SkimmsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val workers by viewModel.workers.collectAsState()
    val selectedWorkerId by viewModel.selectedWorkerId.collectAsState()
    val isAuthenticated by viewModel.isWorkerAuthenticated.collectAsState()
    val telemetryMap by viewModel.latestTelemetry.collectAsState()
    val insightsMap by viewModel.insights.collectAsState()
    val allAlerts by viewModel.alerts.collectAsState()
    val loginError by viewModel.loginError.collectAsState()

    val currentWorker = workers.find { it.esp32Id.equals(selectedWorkerId, ignoreCase = true) }
        ?: workers.firstOrNull()

    val telemetry = currentWorker?.let { telemetryMap[it.esp32Id] } ?: TelemetryData(board_id = "UNKNOWN")
    val insight = currentWorker?.let { insightsMap[it.esp32Id] } ?: AiInsight()

    // Filter alerts belonging only to this worker (Requirement 4 & 6)
    val workerAlerts = allAlerts.filter { it.workerId.equals(currentWorker?.esp32Id, ignoreCase = true) }

    if (!isAuthenticated) {
        // Worker Login View
        WorkerLoginCard(
            viewModel = viewModel,
            currentWorkerId = selectedWorkerId,
            errorMessage = loginError,
            modifier = modifier
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Worker Switcher / Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "WORKER KAVACH MONITOR",
                        color = TacticalCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = currentWorker?.workerName ?: "Miner",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Node & Battery badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = "Battery",
                                tint = if (telemetry.battery < 20) CriticalRed else SafeGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${telemetry.battery}%",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Big Status Block (Requirement 6: SAFE, CAUTION, DANGER, CRITICAL)
        item {
            StatusBanner(
                status = insight.status,
                riskScore = insight.riskScore,
                helmetNumber = currentWorker?.helmetNumber ?: "HLM-01",
                workerName = currentWorker?.workerName ?: "Miner",
                isLarge = true
            )
        }

        // AI Safety Box (Requirement 6 & 7: Explainable rules, reasons, debounce, gas trend prediction)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, TacticalCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
                    .testTag("ai_safety_box")
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "AI Safety Box",
                            tint = TacticalCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Safety Box Intelligence",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Rule Engine v2.5",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    insight.reasons.forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            val dotColor = when {
                                reason.contains("CRITICAL", ignoreCase = true) || reason.contains("MAN-DOWN", ignoreCase = true) -> CriticalRed
                                reason.contains("hazard", ignoreCase = true) || reason.contains("rising", ignoreCase = true) -> DangerOrange
                                reason.contains("Confirming", ignoreCase = true) || reason.contains("Weak", ignoreCase = true) -> CautionYellow
                                else -> SafeGreen
                            }
                            Box(
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .size(6.dp)
                                    .background(dotColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = reason,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    if (insight.trendPrediction != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DangerOrange.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, DangerOrange.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "⚠️ Predictive Alert: ${insight.trendPrediction}",
                                color = DangerOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Live Sensor Tiles (Requirement 6: MQ-4, MQ-135, Accel, IR)
        item {
            Text(
                text = "Live Helmet Telemetry",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            SensorGrid(sensors = telemetry.sensors)
        }

        // GPS Location & Helmet Details
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "GPS",
                        tint = AmberSafety,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "NEO GPS Coordinates",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (telemetry.gps.valid) {
                                "${String.format("%.4f", telemetry.gps.lat)}°N, ${String.format("%.4f", telemetry.gps.lng)}°E (Fix OK)"
                            } else {
                                "Underground gallery (Subterranean mesh node)"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = currentWorker?.esp32Id ?: "",
                            color = TacticalCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Core 0 FreeRTOS",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Prominent Emergency SOS Button (Requirement 6)
        item {
            Button(
                onClick = { viewModel.triggerWorkerSos(context) },
                colors = ButtonDefaults.buttonColors(containerColor = CriticalRed),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("worker_sos_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Sos,
                    contentDescription = "SOS",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "TRANSMIT EMERGENCY SOS",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }
        }

        // Own Alert History (Requirement 6)
        item {
            Text(
                text = "My Helmet Alert Log",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            AlertFeedList(alerts = workerAlerts, onAcknowledge = { viewModel.acknowledgeAlert(it) })
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun WorkerLoginCard(
    viewModel: SkimmsViewModel,
    currentWorkerId: String,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val workers by viewModel.workers.collectAsState()
    var selectedId by remember { mutableStateOf(currentWorkerId) }
    var pin by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AmberSafety.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(AmberSafety.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Login",
                        tint = AmberSafety,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Miner Helmet Login",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Enter your ESP32 node ID and 4-digit safety PIN to access helmet telemetry.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = selectedId,
                    onValueChange = { selectedId = it },
                    label = { Text("ESP32 Node ID (e.g. ESP32_NODE_01)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("worker_login_id_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("Worker Access PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("worker_login_pin_input")
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        color = CriticalRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = { viewModel.loginWorker(selectedId, pin) },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberSafety),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("worker_login_submit_btn")
                ) {
                    Text("Authenticate & Connect Helmet", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
