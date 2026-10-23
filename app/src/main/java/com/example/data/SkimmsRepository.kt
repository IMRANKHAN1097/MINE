package com.example.data

import com.example.model.AiInsight
import com.example.model.AlertItem
import com.example.model.GpsData
import com.example.model.SafetyStatus
import com.example.model.SensorData
import com.example.model.StatusData
import com.example.model.TelemetryData
import com.example.model.WorkerProfile
import com.example.safety.AiSafetyEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SkimmsRepository {

    private val safetyEngine = AiSafetyEngine()
    private val random = SecureRandom()

    // Registered workers
    private val _workers = MutableStateFlow<List<WorkerProfile>>(
        listOf(
            WorkerProfile(
                esp32Id = "ESP32_NODE_01",
                helmetNumber = "HLM-01",
                workerName = "Sukhendu Roy",
                deviceKey = "kavach_key_9f81c201a4bc88",
                pin = "1234"
            ),
            WorkerProfile(
                esp32Id = "ESP32_NODE_02",
                helmetNumber = "HLM-02",
                workerName = "Arpan Ghosh",
                deviceKey = "kavach_key_3b7e4110d9fa22",
                pin = "5678"
            )
        )
    )
    val workers: StateFlow<List<WorkerProfile>> = _workers.asStateFlow()

    // Latest telemetry per board_id
    private val _latestTelemetry = MutableStateFlow<Map<String, TelemetryData>>(
        mapOf(
            "ESP32_NODE_01" to TelemetryData(
                board_id = "ESP32_NODE_01",
                battery = 88,
                gps = GpsData(valid = true, lat = 22.5726, lng = 88.3639),
                sensors = SensorData(mq4 = 380, mq135 = 261, accel = 9.8, ir = 49107),
                status = StatusData(),
                ts = System.currentTimeMillis()
            ),
            "ESP32_NODE_02" to TelemetryData(
                board_id = "ESP32_NODE_02",
                battery = 76,
                gps = GpsData(valid = true, lat = 22.5735, lng = 88.3650),
                sensors = SensorData(mq4 = 420, mq135 = 310, accel = 9.7, ir = 47500),
                status = StatusData(),
                ts = System.currentTimeMillis()
            )
        )
    )
    val latestTelemetry: StateFlow<Map<String, TelemetryData>> = _latestTelemetry.asStateFlow()

    // AI Insights per board_id
    private val _insights = MutableStateFlow<Map<String, AiInsight>>(emptyMap())
    val insights: StateFlow<Map<String, AiInsight>> = _insights.asStateFlow()

    // Sensor History for MQ-4 Sparkline (last 30 samples per worker)
    private val _mq4History = MutableStateFlow<Map<String, List<Int>>>(
        mapOf(
            "ESP32_NODE_01" to listOf(350, 360, 355, 370, 375, 380),
            "ESP32_NODE_02" to listOf(400, 410, 405, 415, 420, 420)
        )
    )
    val mq4History: StateFlow<Map<String, List<Int>>> = _mq4History.asStateFlow()

    // GPS Breadcrumb Trails per board_id
    private val _gpsTrails = MutableStateFlow<Map<String, List<GpsData>>>(
        mapOf(
            "ESP32_NODE_01" to listOf(
                GpsData(valid = true, lat = 22.5720, lng = 88.3630),
                GpsData(valid = true, lat = 22.5726, lng = 88.3639)
            ),
            "ESP32_NODE_02" to listOf(
                GpsData(valid = true, lat = 22.5730, lng = 88.3645),
                GpsData(valid = true, lat = 22.5735, lng = 88.3650)
            )
        )
    )
    val gpsTrails: StateFlow<Map<String, List<GpsData>>> = _gpsTrails.asStateFlow()

    // Alerts Feed
    private val _alerts = MutableStateFlow<List<AlertItem>>(
        listOf(
            AlertItem(
                id = "alert-init-01",
                workerId = "ESP32_NODE_01",
                helmetNumber = "HLM-01",
                workerName = "Sukhendu Roy",
                level = SafetyStatus.SAFE,
                reason = "Telemetry link established. Baseline normal.",
                timestamp = System.currentTimeMillis() - 300_000L,
                acknowledged = true,
                acknowledgedAt = System.currentTimeMillis() - 290_000L,
                acknowledgedBy = "Admin Auto-Check"
            )
        )
    )
    val alerts: StateFlow<List<AlertItem>> = _alerts.asStateFlow()

    init {
        // Initial AI evaluation for registered devices
        recomputeAllInsights()
    }

    private fun recomputeAllInsights() {
        val updated = mutableMapOf<String, AiInsight>()
        _latestTelemetry.value.forEach { (boardId, telem) ->
            updated[boardId] = safetyEngine.evaluate(telem)
        }
        _insights.value = updated
    }

    fun generateDeviceKey(): String {
        val bytes = ByteArray(12)
        random.nextBytes(bytes)
        return "kavach_" + bytes.joinToString("") { "%02x".format(it) }
    }

    fun registerWorker(esp32Id: String, helmetNumber: String, name: String, pin: String): Result<WorkerProfile> {
        val cleanEsp = esp32Id.trim().uppercase()
        val cleanHlm = helmetNumber.trim().uppercase()
        val cleanName = name.trim()

        if (cleanEsp.isEmpty() || cleanHlm.isEmpty() || cleanName.isEmpty() || pin.length < 4) {
            return Result.failure(IllegalArgumentException("All fields required. PIN must be at least 4 digits."))
        }

        val existing = _workers.value
        if (existing.any { it.esp32Id.equals(cleanEsp, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("ESP32 ID '$cleanEsp' already registered."))
        }
        if (existing.any { it.helmetNumber.equals(cleanHlm, ignoreCase = true) }) {
            return Result.failure(IllegalArgumentException("Helmet Number '$cleanHlm' already assigned."))
        }

        val newProfile = WorkerProfile(
            esp32Id = cleanEsp,
            helmetNumber = cleanHlm,
            workerName = cleanName,
            deviceKey = generateDeviceKey(),
            pin = pin
        )
        _workers.value = _workers.value + newProfile
        return Result.success(newProfile)
    }

    fun rotateDeviceKey(esp32Id: String): String? {
        val newKey = generateDeviceKey()
        var found = false
        _workers.value = _workers.value.map { worker ->
            if (worker.esp32Id.equals(esp32Id, ignoreCase = true)) {
                found = true
                worker.copy(deviceKey = newKey)
            } else {
                worker
            }
        }
        return if (found) newKey else null
    }

    fun removeWorker(esp32Id: String): Boolean {
        val initialSize = _workers.value.size
        _workers.value = _workers.value.filterNot { it.esp32Id.equals(esp32Id, ignoreCase = true) }
        val removed = _workers.value.size < initialSize

        if (removed) {
            // Remove telemetry & map marker
            _latestTelemetry.value = _latestTelemetry.value.filterKeys { !it.equals(esp32Id, ignoreCase = true) }
            _insights.value = _insights.value.filterKeys { !it.equals(esp32Id, ignoreCase = true) }
            _gpsTrails.value = _gpsTrails.value.filterKeys { !it.equals(esp32Id, ignoreCase = true) }
            _mq4History.value = _mq4History.value.filterKeys { !it.equals(esp32Id, ignoreCase = true) }
            safetyEngine.resetBoard(esp32Id)
        }
        return removed
    }

    fun ingestTelemetry(telemetry: TelemetryData): AiInsight {
        val boardId = telemetry.board_id
        val prevInsight = _insights.value[boardId]

        // Update telemetry
        val currentMap = _latestTelemetry.value.toMutableMap()
        currentMap[boardId] = telemetry
        _latestTelemetry.value = currentMap

        // Update MQ4 History
        val curHistory = _mq4History.value[boardId] ?: emptyList()
        val nextHistory = (curHistory + telemetry.sensors.mq4).takeLast(30)
        _mq4History.value = _mq4History.value + (boardId to nextHistory)

        // Update GPS Trail
        if (telemetry.gps.valid) {
            val curTrail = _gpsTrails.value[boardId] ?: emptyList()
            val nextTrail = (curTrail + telemetry.gps).takeLast(25)
            _gpsTrails.value = _gpsTrails.value + (boardId to nextTrail)
        }

        // Run AI Safety Engine
        val insight = safetyEngine.evaluate(telemetry)
        val insightMap = _insights.value.toMutableMap()
        insightMap[boardId] = insight
        _insights.value = insightMap

        // Check if alert level escalated
        val worker = _workers.value.find { it.esp32Id.equals(boardId, ignoreCase = true) }
        val workerName = worker?.workerName ?: "Worker ($boardId)"
        val helmetNum = worker?.helmetNumber ?: "HLM-?"

        val prevLevel = prevInsight?.status ?: SafetyStatus.SAFE
        if (insight.status > prevLevel || (insight.status >= SafetyStatus.DANGER && prevInsight == null)) {
            // Create Alert
            val alert = AlertItem(
                id = UUID.randomUUID().toString(),
                workerId = boardId,
                helmetNumber = helmetNum,
                workerName = workerName,
                level = insight.status,
                reason = insight.reasons.firstOrNull() ?: "Alert raised: ${insight.status.label}",
                timestamp = System.currentTimeMillis()
            )
            _alerts.value = listOf(alert) + _alerts.value
        }

        return insight
    }

    fun triggerSos(boardId: String) {
        val worker = _workers.value.find { it.esp32Id.equals(boardId, ignoreCase = true) }
        val workerName = worker?.workerName ?: "Worker ($boardId)"
        val helmetNum = worker?.helmetNumber ?: "HLM-?"

        val sosAlert = AlertItem(
            id = UUID.randomUUID().toString(),
            workerId = boardId,
            helmetNumber = helmetNum,
            workerName = workerName,
            level = SafetyStatus.CRITICAL,
            reason = "EMERGENCY SOS: Manually activated from worker helmet interface",
            timestamp = System.currentTimeMillis()
        )
        _alerts.value = listOf(sosAlert) + _alerts.value

        // Force critical insight
        val curInsight = _insights.value[boardId] ?: AiInsight()
        val forcedInsight = curInsight.copy(
            riskScore = 100,
            status = SafetyStatus.CRITICAL,
            reasons = listOf("MANUAL SOS TRIGGERED BY WORKER") + curInsight.reasons
        )
        _insights.value = _insights.value + (boardId to forcedInsight)
    }

    fun acknowledgeAlert(alertId: String, adminName: String = "Admin Control Room"): Boolean {
        var found = false
        _alerts.value = _alerts.value.map { alert ->
            if (alert.id == alertId && !alert.acknowledged) {
                found = true
                alert.copy(
                    acknowledged = true,
                    acknowledgedAt = System.currentTimeMillis(),
                    acknowledgedBy = adminName
                )
            } else {
                alert
            }
        }
        return found
    }

    fun checkAndEscalateAlerts(escalateSeconds: Long = 120L) {
        val now = System.currentTimeMillis()
        val thresholdMs = escalateSeconds * 1000L
        _alerts.value = _alerts.value.map { alert ->
            if (!alert.acknowledged && !alert.escalated &&
                (alert.level == SafetyStatus.DANGER || alert.level == SafetyStatus.CRITICAL) &&
                (now - alert.timestamp > thresholdMs)
            ) {
                alert.copy(escalated = true)
            } else {
                alert
            }
        }
    }

    // CSV Export with formula injection neutralization (=, +, -, @ prepended with ')
    fun exportAlertsCsv(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val sb = StringBuilder()
        sb.append("Alert_ID,Timestamp_UTC,Worker_ID,Helmet_No,Worker_Name,Level,Reason,Acknowledged,Acknowledged_At,Acknowledged_By,Escalated\n")

        for (alert in _alerts.value) {
            val sanitize = { text: String ->
                var s = text.replace("\"", "\"\"")
                if (s.startsWith("=") || s.startsWith("+") || s.startsWith("-") || s.startsWith("@")) {
                    s = "'$s"
                }
                "\"$s\""
            }

            val timeStr = sdf.format(Date(alert.timestamp))
            val ackTimeStr = alert.acknowledgedAt?.let { sdf.format(Date(it)) } ?: "N/A"

            sb.append("${sanitize(alert.id)},")
            sb.append("${sanitize(timeStr)},")
            sb.append("${sanitize(alert.workerId)},")
            sb.append("${sanitize(alert.helmetNumber)},")
            sb.append("${sanitize(alert.workerName)},")
            sb.append("${sanitize(alert.level.label)},")
            sb.append("${sanitize(alert.reason)},")
            sb.append("${alert.acknowledged},")
            sb.append("${sanitize(ackTimeStr)},")
            sb.append("${sanitize(alert.acknowledgedBy ?: "N/A")},")
            sb.append("${alert.escalated}\n")
        }
        return sb.toString()
    }
}
