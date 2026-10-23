package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberSafety
import com.example.ui.theme.CautionYellow
import com.example.ui.theme.CriticalRed
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.SafeGreen
import com.example.ui.theme.TacticalCyan

@Composable
fun DocScreen(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = "Docs",
                    tint = TacticalCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "PROJECT ARCHITECTURE & COMPLIANCE",
                        color = TacticalCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "B.Tech Guide: SKIMMS System",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Section 1: Project Context
        item {
            DocSectionCard(
                title = "1. Team HEXACREW & System Context",
                accentColor = AmberSafety
            ) {
                Text(
                    text = "• Institution: University of Calcutta, Department of Applied Optics and Photonics (AOP)\n" +
                            "• Project: Smart Kavach for Intelligent Mine Monitoring (SKIMMS)\n" +
                            "• Target: Underground coal/metal miners equipped with smart safety helmets feeding real-time telemetry to the Control Room laptop/app and personal mobile PWA.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // Section 2: Hardware Pinout
        item {
            DocSectionCard(
                title = "2. Helmet Hardware & FreeRTOS Architecture",
                accentColor = TacticalCyan
            ) {
                Text(
                    text = "ESP32 dual-core architecture is critical to avoid safety blocking:\n\n" +
                            "• CORE 1 (High Priority Real-Time Safety Loop):\n" +
                            "  - MQ-4 Methane on GPIO34 (ADC input)\n" +
                            "  - MQ-135 Hazardous Gases on GPIO35 (ADC input)\n" +
                            "  - MPU6050 (0x68) & MAX30105 (0x57) on I2C (SDA GPIO21, SCL GPIO22)\n" +
                            "  - NEO-6M GPS on UART2 (TX GPIO16, RX GPIO17, 9600 baud)\n" +
                            "  - Green LED (GPIO26), Yellow LED (GPIO27), Red LED (GPIO25)\n" +
                            "  - Piezo Buzzer (GPIO13): toggles 10s (sensor missing), 5s (health hazard), 2s (gas hazard)\n\n" +
                            "• CORE 0 (Network Communication Task):\n" +
                            "  - WiFi and HTTPClient ring buffer of 20 telemetry packets\n" +
                            "  - Sends packets every 500 ms with x-device-key\n" +
                            "  - If WiFi disconnects, local Core 1 alarms continue functioning without delay!",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        }

        // Section 3: AI Safety Rules
        item {
            DocSectionCard(
                title = "3. AI Safety Box Explainable Logic",
                accentColor = SafeGreen
            ) {
                Text(
                    text = "• Gas Trend Slope: Linear regression on rolling MQ-4 window. If dGas/dt projects threshold breach (2200) within 60s, raises early warning.\n" +
                            "• Man-Down: Impact (accel ≥ 24.5 m/s²) followed by continuous immobility (1G ± 1.0) for 15s triggers CRITICAL.\n" +
                            "• 3-Sample Debounce: Hazard must persist across 3 consecutive packets (~1.5s) to elevate to DANGER; shows CAUTION (Confirming) meanwhile.\n" +
                            "• Dual Hazard: Gas + Trauma triggers immediate CRITICAL without debounce.\n" +
                            "• Signal Loss: No packets for 15s triggers DANGER.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // Section 4: DGMS & Intrinsic Safety Notice
        item {
            DocSectionCard(
                title = "4. DGMS & Intrinsic Safety Notice (Crucial)",
                accentColor = CriticalRed
            ) {
                Text(
                    text = "⚠️ PROTOTYPE SAFETY NOTICE:\n\n" +
                            "1. Explosive Atmospheres: Standard ESP32 dev boards and bare lithium batteries are NOT explosion-proof. In Indian underground coal mines, equipment must be certified Intrinsically Safe (IS) or Flameproof by DGMS (Directorate General of Mines Safety) and CIMFR under IECEx/ATEX Zone 0/1.\n\n" +
                            "2. Sensor Calibration: MQ sensors rely on a tin dioxide (SnO2) heating element requiring 24-48 hours burn-in. Raw ADC readings drift with ambient humidity/temperature and need calibration curves against certified multi-gas testers.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun DocSectionCard(
    title: String,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
