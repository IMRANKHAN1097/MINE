package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AiInsight
import com.example.model.SafetyStatus
import com.example.model.TelemetryData
import com.example.model.WorkerProfile
import com.example.ui.SkimmsViewModel
import com.example.ui.components.AlertFeedList
import com.example.ui.components.KeyRotationDialog
import com.example.ui.components.MineMapCanvas
import com.example.ui.components.Mq4TrendLine
import com.example.ui.components.RegisterWorkerDialog
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

@Composable
fun AdminScreen(
    viewModel: SkimmsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val workers by viewModel.workers.collectAsState()
    val telemetryMap by viewModel.latestTelemetry.collectAsState()
    val insightsMap by viewModel.insights.collectAsState()
    val mq4History by viewModel.mq4History.collectAsState()
    val gpsTrails by viewModel.gpsTrails.collectAsState()
    val alerts by viewModel.alerts.collectAsState()
    val lastRotatedKey by viewModel.lastRotatedKey.collectAsState()
    val selectedWorkerId by viewModel.selectedWorkerId.collectAsState()

    var showRegisterDialog by remember { mutableStateOf(false) }

    // Summary count calculation
    val safeCount = workers.count { (insightsMap[it.esp32Id]?.status ?: SafetyStatus.SAFE) == SafetyStatus.SAFE }
    val cautionCount = workers.count { (insightsMap[it.esp32Id]?.status ?: SafetyStatus.SAFE) == SafetyStatus.CAUTION }
    val dangerCount = workers.count { (insightsMap[it.esp32Id]?.status ?: SafetyStatus.SAFE) == SafetyStatus.DANGER }
    val criticalCount = workers.count { (insightsMap[it.esp32Id]?.status ?: SafetyStatus.SAFE) == SafetyStatus.CRITICAL }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Control Room Header & Actions
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MINE CONTROL ROOM (HEXACREW)",
                        color = AmberSafety,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Active Helmet Fleet",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showRegisterDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AmberSafety),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("admin_register_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Register",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.exportCsv(context) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("admin_export_csv_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "CSV",
                            tint = TacticalCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV", color = TacticalCyan, fontSize = 12.sp)
                    }
                }
            }
        }

        // Fleet Status Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FleetChip("Total", "${workers.size}", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                FleetChip("Safe", "$safeCount", SafeGreen, Modifier.weight(1f))
                FleetChip("Caution", "$cautionCount", CautionYellow, Modifier.weight(1f))
                FleetChip("Danger", "$dangerCount", DangerOrange, Modifier.weight(1f))
                FleetChip("Critical", "$criticalCount", CriticalRed, Modifier.weight(1f))
            }
        }

        // Subterranean Mine Sector & GPS Map (Requirement 5)
        item {
            MineMapCanvas(
                workers = workers,
                telemetryMap = telemetryMap,
                insightsMap = insightsMap,
                trailsMap = gpsTrails,
                selectedWorkerId = selectedWorkerId,
                onSelectWorker = { viewModel.selectWorker(it) }
            )
        }

        // Live Worker Helmet Cards (Requirement 5)
        item {
            Text(
                text = "Live Miner Telemetry Cards",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        items(workers, key = { it.esp32Id }) { worker ->
            val telem = telemetryMap[worker.esp32Id] ?: TelemetryData(board_id = worker.esp32Id)
            val insight = insightsMap[worker.esp32Id] ?: AiInsight()
            val history = mq4History[worker.esp32Id] ?: emptyList()

            AdminWorkerCard(
                worker = worker,
                telemetry = telem,
                insight = insight,
                mq4History = history,
                onRotateKey = { viewModel.rotateKey(worker.esp32Id) },
                onRemove = { viewModel.removeWorker(worker.esp32Id) },
                onSelect = { viewModel.selectWorker(worker.esp32Id) }
            )
        }

        // Incident Alert Feed (Requirement 5: Acknowledge & Escalation)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Control Room Incident Feed",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "${alerts.size} incidents logged",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            AlertFeedList(
                alerts = alerts,
                onAcknowledge = { viewModel.acknowledgeAlert(it) }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showRegisterDialog) {
        RegisterWorkerDialog(
            onDismiss = { showRegisterDialog = false },
            onRegister = { esp, hlm, name, pin ->
                viewModel.registerWorker(esp, hlm, name, pin)
            }
        )
    }

    val rotated = lastRotatedKey
    if (rotated != null) {
        val worker = workers.find { it.esp32Id.equals(rotated.first, ignoreCase = true) }
        KeyRotationDialog(
            esp32Id = rotated.first,
            helmetNumber = worker?.helmetNumber ?: "HLM-?",
            newDeviceKey = rotated.second,
            onDismiss = { viewModel.dismissKeyDialog() }
        )
    }
}

@Composable
fun FleetChip(
    label: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count,
                color = color,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun AdminWorkerCard(
    worker: WorkerProfile,
    telemetry: TelemetryData,
    insight: AiInsight,
    mq4History: List<Int>,
    onRotateKey: () -> Unit,
    onRemove: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (insight.status) {
        SafetyStatus.SAFE -> SafeGreen
        SafetyStatus.CAUTION -> CautionYellow
        SafetyStatus.DANGER -> DangerOrange
        SafetyStatus.CRITICAL -> CriticalRed
    }

    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(telemetry.ts))

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .clickable { onSelect() }
            .testTag("admin_worker_card_${worker.esp32Id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Name, Helmet, Status Badge, Battery
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = worker.workerName,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${worker.helmetNumber} • ${worker.esp32Id}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = insight.status.label,
                            color = statusColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${telemetry.battery}%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Sensors Quick Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                SensorMetric("MQ-4", "${telemetry.sensors.mq4}", telemetry.sensors.mq4 >= 2200)
                SensorMetric("MQ-135", "${telemetry.sensors.mq135}", telemetry.sensors.mq135 >= 2000)
                SensorMetric("Accel", String.format("%.1f", telemetry.sensors.accel), telemetry.sensors.accel >= 24.5)
                SensorMetric("IR", "${telemetry.sensors.ir}", telemetry.sensors.ir in 1000..20000)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // MQ-4 Trend Line (Requirement 5)
            Mq4TrendLine(
                history = mq4History,
                currentValue = telemetry.sensors.mq4
            )

            Spacer(modifier = Modifier.height(8.dp))

            // AI Reason & Last Seen
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AI: ${insight.reasons.firstOrNull() ?: "Normal"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Last: $timeStr",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Card Action Buttons: Rotate Key & Remove
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onRotateKey,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("rotate_key_btn_${worker.esp32Id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = "Rotate Key",
                        tint = TacticalCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rotate Key", color = TacticalCyan, fontSize = 10.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("remove_worker_btn_${worker.esp32Id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = CriticalRed.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SensorMetric(label: String, value: String, isAlert: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = if (isAlert) CriticalRed else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp
        )
    }
}
