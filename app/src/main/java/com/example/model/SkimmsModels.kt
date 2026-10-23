package com.example.model

enum class SafetyStatus(val label: String) {
    SAFE("SAFE"),
    CAUTION("CAUTION"),
    DANGER("DANGER"),
    CRITICAL("CRITICAL")
}

data class GpsData(
    val valid: Boolean = true,
    val lat: Double = 22.5726,
    val lng: Double = 88.3639
)

data class SensorData(
    val mq4: Int = 380,           // Methane (ADC raw, danger >= 2200)
    val mq135: Int = 261,         // Air Quality/Toxics (ADC raw, danger >= 2000)
    val mq4_ppm: Float = 120.5f,  // Calibrated Methane PPM
    val mq135_ppm: Float = 18.2f, // Calibrated Air Quality PPM
    val accel: Double = 9.8,      // m/s^2 (Impact threshold >= 24.5 m/s^2)
    val ir: Int = 49107           // MAX30105 IR contact intensity (1000..20000 = weak contact)
)

data class CalibrationData(
    val mq4_r0: Float = 10.0f,
    val mq4_rl: Float = 20.0f,
    val mq4_limit_ppm: Float = 1000.0f,
    val mq135_r0: Float = 76.6f,
    val mq135_rl: Float = 20.0f,
    val mq135_limit_ppm: Float = 50.0f
)

data class StatusData(
    val mpu_ok: Boolean = true,
    val max_ok: Boolean = true,
    val mq4_ok: Boolean = true,
    val mq135_ok: Boolean = true,
    val health_hazard: Boolean = false,
    val env_hazard: Boolean = false,
    val missing: Int = 0
)

data class TelemetryData(
    val board_id: String,
    val battery: Int = 88,
    val gps: GpsData = GpsData(),
    val sensors: SensorData = SensorData(),
    val status: StatusData = StatusData(),
    val ts: Long = System.currentTimeMillis()
)

data class WorkerProfile(
    val esp32Id: String,
    val helmetNumber: String,
    val workerName: String,
    val deviceKey: String,
    val pin: String,
    val registeredAt: Long = System.currentTimeMillis()
)

data class AlertItem(
    val id: String,
    val workerId: String,
    val helmetNumber: String,
    val workerName: String,
    val level: SafetyStatus,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis(),
    val acknowledged: Boolean = false,
    val acknowledgedAt: Long? = null,
    val acknowledgedBy: String? = null,
    val escalated: Boolean = false
)

data class AiInsight(
    val riskScore: Int = 5,
    val status: SafetyStatus = SafetyStatus.SAFE,
    val trendPrediction: String? = null,
    val reasons: List<String> = emptyList(),
    val debounceState: String? = null,
    val manDownActive: Boolean = false,
    val signalLost: Boolean = false
)
