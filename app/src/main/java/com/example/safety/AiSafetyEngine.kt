package com.example.safety

import com.example.model.AiInsight
import com.example.model.SafetyStatus
import com.example.model.TelemetryData
import kotlin.math.max
import kotlin.math.min

class AiSafetyEngine {

    companion object {
        const val MQ4_LIMIT = 2200
        const val MQ135_LIMIT = 2000
        const val IMPACT_ACCEL_LIMIT = 24.5 // m/s^2
        const val SIGNAL_LOSS_THRESHOLD_MS = 15_000L
        const val MAN_DOWN_STILL_MS = 15_000L
        const val DEBOUNCE_SAMPLES_REQUIRED = 3
    }

    private data class SampleRecord(val mq4: Int, val timeMs: Long)

    // History per board_id
    private val gasHistory = mutableMapOf<String, ArrayDeque<SampleRecord>>()
    private val debounceCounter = mutableMapOf<String, Int>()
    private val impactTimestamp = mutableMapOf<String, Long>()
    private val lastSeenTimestamp = mutableMapOf<String, Long>()

    fun resetBoard(boardId: String) {
        gasHistory.remove(boardId)
        debounceCounter.remove(boardId)
        impactTimestamp.remove(boardId)
        lastSeenTimestamp.remove(boardId)
    }

    fun evaluate(telemetry: TelemetryData, nowMs: Long = System.currentTimeMillis()): AiInsight {
        val boardId = telemetry.board_id
        lastSeenTimestamp[boardId] = nowMs

        val sensors = telemetry.sensors
        val status = telemetry.status
        val reasons = mutableListOf<String>()

        // 1. Gas history & slope prediction
        val history = gasHistory.getOrPut(boardId) { ArrayDeque() }
        history.addLast(SampleRecord(sensors.mq4, nowMs))
        if (history.size > 12) {
            history.removeFirst()
        }

        var trendWarning: String? = null
        if (history.size >= 4) {
            val first = history.first()
            val last = history.last()
            val dtSec = (last.timeMs - first.timeMs) / 1000.0
            if (dtSec > 1.0) {
                val dMq4 = last.mq4 - first.mq4
                val slope = dMq4 / dtSec // units per second
                if (slope > 8.0 && sensors.mq4 < MQ4_LIMIT) {
                    val secToLimit = (MQ4_LIMIT - sensors.mq4) / slope
                    if (secToLimit in 1.0..60.0) {
                        trendWarning = "Gas trend: Methane rising at +${String.format("%.1f", slope)}/s. Limit in ${secToLimit.toInt()}s"
                        reasons.add(trendWarning)
                    }
                }
            }
        }

        // 2. Risk Score calculation (0 - 100)
        val mq4Ratio = sensors.mq4.toDouble() / MQ4_LIMIT
        val mq135Ratio = sensors.mq135.toDouble() / MQ135_LIMIT
        val gasRatio = max(mq4Ratio, mq135Ratio)

        val accelDelta = kotlin.math.abs(sensors.accel - 9.8)
        val impactRatio = (sensors.accel / IMPACT_ACCEL_LIMIT).coerceIn(0.0, 1.5)

        var calculatedRisk = (gasRatio * 55.0 + impactRatio * 35.0).toInt().coerceIn(0, 100)

        // Contact quality check (MAX30105 IR 1000..20000 = weak skin contact)
        val isWeakContact = sensors.ir in 1000..20000
        if (isWeakContact) {
            reasons.add("MAX30105 IR (${sensors.ir}): Weak optical contact detected")
            calculatedRisk = max(calculatedRisk, 35)
        }

        // 3. Impact & Man-Down Logic (Impact >= 24.5 m/s2 followed by 15s immobility)
        var manDownDetected = false
        if (sensors.accel >= IMPACT_ACCEL_LIMIT) {
            impactTimestamp[boardId] = nowMs
            reasons.add("High acceleration impact: ${String.format("%.1f", sensors.accel)} m/s²")
        }

        val lastImpact = impactTimestamp[boardId]
        if (lastImpact != null) {
            val stillDuration = nowMs - lastImpact
            val isStill = accelDelta < 1.2 && sensors.accel in 8.8..10.8
            if (isStill) {
                if (stillDuration >= MAN_DOWN_STILL_MS) {
                    manDownDetected = true
                    reasons.add("MAN-DOWN DETECTED: Fall impact followed by ${stillDuration / 1000}s of immobility")
                } else {
                    reasons.add("Man-down monitoring: Immobility ${(stillDuration / 1000)}s / 15s after impact")
                }
            } else if (accelDelta >= 3.0) {
                // Movement resumed, clear impact
                impactTimestamp.remove(boardId)
            }
        }

        // 4. Raw Hazard flags from helmet local logic
        val rawEnvHazard = status.env_hazard || sensors.mq4 >= MQ4_LIMIT || sensors.mq135 >= MQ135_LIMIT
        val rawHealthHazard = status.health_hazard || sensors.accel >= IMPACT_ACCEL_LIMIT || isWeakContact

        // 5. Debounce logic: hazard must persist for 3 consecutive samples (~1.5s)
        val isHazardous = rawEnvHazard || rawHealthHazard
        val currentCount = debounceCounter.getOrDefault(boardId, 0)
        val newCount = if (isHazardous) currentCount + 1 else 0
        debounceCounter[boardId] = newCount

        var debounceProgress: String? = null
        val hazardDebounced = newCount >= DEBOUNCE_SAMPLES_REQUIRED
        if (isHazardous && !hazardDebounced) {
            debounceProgress = "Confirming reading ($newCount/$DEBOUNCE_SAMPLES_REQUIRED)"
            reasons.add(debounceProgress)
        }

        // 6. Sensor missing / failure check
        val sensorFailure = status.missing > 0 || !status.mpu_ok || !status.max_ok || !status.mq4_ok || !status.mq135_ok
        if (sensorFailure) {
            reasons.add("Hardware diagnostic: ${status.missing} sensor(s) offline")
        }

        // 7. Status Resolution
        val finalStatus = when {
            // Dual hazard: gas + health together = CRITICAL immediately
            rawEnvHazard && rawHealthHazard -> {
                reasons.add("DUAL HAZARD: Simultaneous gas toxicity and physical trauma")
                SafetyStatus.CRITICAL
            }
            manDownDetected -> {
                SafetyStatus.CRITICAL
            }
            rawEnvHazard && hazardDebounced -> {
                reasons.add("Gas hazard confirmed: MQ-4=${sensors.mq4}, MQ-135=${sensors.mq135}")
                SafetyStatus.DANGER
            }
            rawHealthHazard && hazardDebounced -> {
                reasons.add("Health hazard confirmed: Accel=${String.format("%.1f", sensors.accel)} m/s²")
                SafetyStatus.DANGER
            }
            isHazardous && !hazardDebounced -> {
                // Showing CAUTION while debouncing
                SafetyStatus.CAUTION
            }
            trendWarning != null -> {
                SafetyStatus.CAUTION
            }
            sensorFailure || isWeakContact -> {
                SafetyStatus.CAUTION
            }
            else -> {
                SafetyStatus.SAFE
            }
        }

        // Adjust risk score according to status
        val finalRisk = when (finalStatus) {
            SafetyStatus.CRITICAL -> max(calculatedRisk, 95)
            SafetyStatus.DANGER -> max(calculatedRisk, 75)
            SafetyStatus.CAUTION -> max(calculatedRisk, 40)
            SafetyStatus.SAFE -> calculatedRisk.coerceAtMost(35)
        }

        if (reasons.isEmpty()) {
            reasons.add("All mine parameters within DGMS normal safety limits")
        }

        return AiInsight(
            riskScore = finalRisk,
            status = finalStatus,
            trendPrediction = trendWarning,
            reasons = reasons,
            debounceState = debounceProgress,
            manDownActive = manDownDetected,
            signalLost = false
        )
    }

    fun checkSignalLoss(boardId: String, nowMs: Long = System.currentTimeMillis()): AiInsight? {
        val lastSeen = lastSeenTimestamp[boardId] ?: return null
        if (nowMs - lastSeen > SIGNAL_LOSS_THRESHOLD_MS) {
            val seconds = (nowMs - lastSeen) / 1000
            return AiInsight(
                riskScore = 80,
                status = SafetyStatus.DANGER,
                trendPrediction = null,
                reasons = listOf("SIGNAL LOST: No telemetry received for ${seconds}s (>15s threshold)"),
                debounceState = null,
                manDownActive = false,
                signalLost = true
            )
        }
        return null
    }
}
