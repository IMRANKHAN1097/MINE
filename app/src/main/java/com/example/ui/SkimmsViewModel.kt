package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SkimmsRepository
import com.example.model.AiInsight
import com.example.model.AlertItem
import com.example.model.GpsData
import com.example.model.SafetyStatus
import com.example.model.SensorData
import com.example.model.StatusData
import com.example.model.TelemetryData
import com.example.model.WorkerProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ScreenMode(val title: String) {
    ADMIN("Control Room"),
    WORKER("Personal Helmet"),
    SIMULATOR("Test & Simulation"),
    DOCS("System & DGMS Guide")
}

enum class SimScenario(val label: String, val description: String) {
    SAFE_BASELINE("Safe Baseline", "Normal methane (380 ADC), air (260 ADC), 1G gravity"),
    METHANE_SPIKE("Methane Spike", "MQ-4 jumps to 2450 ADC (limit: 2200)"),
    AIR_TOXICITY("Air Toxicity Spike", "MQ-135 reaches 2150 ADC (limit: 2000)"),
    RISING_GAS_TREND("Rising Gas Trend", "Fast gas surge (+25/s) triggering 60s early warning"),
    FALL_IMPACT("Fall & Immobility", "Impact 28.5 m/s² followed by 15s stillness (Man-down CRITICAL)"),
    DUAL_HAZARD("Dual Hazard Emergency", "Gas toxicity + impact together triggers immediate CRITICAL"),
    SENSOR_FAILURE("Sensor Failure", "MPU6050 and MQ-135 offline (missing: 2) -> CAUTION"),
    WEAK_CONTACT("Weak Optical Contact", "MAX30105 IR 12,000 (loose helmet strap warning)"),
    SIGNAL_LOSS("Signal Loss", "No packet received for 16 seconds -> DANGER"),
    DEBOUNCE_PROGRESS("3-Sample Debounce", "Step-by-step confirmation before raising to DANGER")
}

class SkimmsViewModel(
    private val repository: SkimmsRepository = SkimmsRepository()
) : ViewModel() {

    private val _screenMode = MutableStateFlow(ScreenMode.WORKER)
    val screenMode: StateFlow<ScreenMode> = _screenMode.asStateFlow()

    private val _selectedWorkerId = MutableStateFlow("ESP32_NODE_01")
    val selectedWorkerId: StateFlow<String> = _selectedWorkerId.asStateFlow()

    private val _isWorkerAuthenticated = MutableStateFlow(true)
    val isWorkerAuthenticated: StateFlow<Boolean> = _isWorkerAuthenticated.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    val workers: StateFlow<List<WorkerProfile>> = repository.workers
    val latestTelemetry: StateFlow<Map<String, TelemetryData>> = repository.latestTelemetry
    val insights: StateFlow<Map<String, AiInsight>> = repository.insights
    val mq4History: StateFlow<Map<String, List<Int>>> = repository.mq4History
    val gpsTrails: StateFlow<Map<String, List<GpsData>>> = repository.gpsTrails
    val alerts: StateFlow<List<AlertItem>> = repository.alerts

    private val _lastRotatedKey = MutableStateFlow<Pair<String, String>?>(null) // esp32Id, key
    val lastRotatedKey: StateFlow<Pair<String, String>?> = _lastRotatedKey.asStateFlow()

    private val _activeScenario = MutableStateFlow(SimScenario.SAFE_BASELINE)
    val activeScenario: StateFlow<SimScenario> = _activeScenario.asStateFlow()

    init {
        // Continuous background safety check: escalations and periodic signal loss detection
        viewModelScope.launch {
            while (true) {
                delay(3000L)
                repository.checkAndEscalateAlerts(escalateSeconds = 120L)
            }
        }
    }

    fun setScreenMode(mode: ScreenMode) {
        _screenMode.value = mode
    }

    fun selectWorker(esp32Id: String) {
        _selectedWorkerId.value = esp32Id
    }

    fun loginWorker(esp32Id: String, pin: String): Boolean {
        val worker = workers.value.find { it.esp32Id.equals(esp32Id, ignoreCase = true) }
        if (worker == null) {
            _loginError.value = "Unregistered Helmet Node ID"
            return false
        }
        if (worker.pin != pin) {
            _loginError.value = "Incorrect PIN code"
            return false
        }
        _selectedWorkerId.value = worker.esp32Id
        _isWorkerAuthenticated.value = true
        _loginError.value = null
        return true
    }

    fun logoutWorker() {
        _isWorkerAuthenticated.value = false
    }

    fun registerWorker(esp32Id: String, helmetNo: String, name: String, pin: String): String? {
        val res = repository.registerWorker(esp32Id, helmetNo, name, pin)
        return if (res.isSuccess) {
            val prof = res.getOrNull()
            if (prof != null) {
                _lastRotatedKey.value = Pair(prof.esp32Id, prof.deviceKey)
            }
            null
        } else {
            res.exceptionOrNull()?.message ?: "Registration error"
        }
    }

    fun rotateKey(esp32Id: String): String? {
        val newKey = repository.rotateDeviceKey(esp32Id)
        if (newKey != null) {
            _lastRotatedKey.value = Pair(esp32Id, newKey)
        }
        return newKey
    }

    fun dismissKeyDialog() {
        _lastRotatedKey.value = null
    }

    fun removeWorker(esp32Id: String): Boolean {
        val removed = repository.removeWorker(esp32Id)
        if (removed && _selectedWorkerId.value == esp32Id) {
            _selectedWorkerId.value = workers.value.firstOrNull()?.esp32Id ?: ""
        }
        return removed
    }

    fun acknowledgeAlert(alertId: String) {
        repository.acknowledgeAlert(alertId, adminName = "Admin Control Room")
    }

    fun triggerWorkerSos(context: Context? = null) {
        val currentId = _selectedWorkerId.value
        repository.triggerSos(currentId)
        vibrate(context, durationMs = 1200L)
    }

    fun vibrate(context: Context?, durationMs: Long = 500L) {
        if (context == null) return
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {
            // Graceful fallback if vibration unavailable
        }
    }

    fun exportCsv(context: Context) {
        val csvData = repository.exportAlertsCsv()
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, csvData)
            putExtra(Intent.EXTRA_SUBJECT, "SKIMMS_Mine_Safety_Alert_Log.csv")
            type = "text/csv"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Export SKIMMS Safety Audit Log")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    // Interactive Mine Simulator Engine
    fun applyScenario(scenario: SimScenario, context: Context? = null) {
        _activeScenario.value = scenario
        val boardId = _selectedWorkerId.value
        val now = System.currentTimeMillis()

        when (scenario) {
            SimScenario.SAFE_BASELINE -> {
                val data = TelemetryData(
                    board_id = boardId,
                    battery = 88,
                    gps = GpsData(valid = true, lat = 22.5726, lng = 88.3639),
                    sensors = SensorData(mq4 = 380, mq135 = 261, accel = 9.8, ir = 49107),
                    status = StatusData(),
                    ts = now
                )
                repository.ingestTelemetry(data)
            }
            SimScenario.METHANE_SPIKE -> {
                // Persistent 3 samples to overcome debounce
                viewModelScope.launch {
                    repeat(3) {
                        val data = TelemetryData(
                            board_id = boardId,
                            battery = 85,
                            gps = GpsData(valid = true, lat = 22.5728, lng = 88.3642),
                            sensors = SensorData(mq4 = 2450, mq135 = 480, accel = 9.8, ir = 48000),
                            status = StatusData(env_hazard = true),
                            ts = System.currentTimeMillis()
                        )
                        repository.ingestTelemetry(data)
                        delay(200L)
                    }
                    vibrate(context, 800L)
                }
            }
            SimScenario.AIR_TOXICITY -> {
                viewModelScope.launch {
                    repeat(3) {
                        val data = TelemetryData(
                            board_id = boardId,
                            battery = 82,
                            gps = GpsData(valid = true, lat = 22.5727, lng = 88.3640),
                            sensors = SensorData(mq4 = 520, mq135 = 2150, accel = 9.8, ir = 48000),
                            status = StatusData(env_hazard = true),
                            ts = System.currentTimeMillis()
                        )
                        repository.ingestTelemetry(data)
                        delay(200L)
                    }
                    vibrate(context, 800L)
                }
            }
            SimScenario.RISING_GAS_TREND -> {
                viewModelScope.launch {
                    var valMq4 = 1100
                    for (i in 0..5) {
                        valMq4 += 180
                        val data = TelemetryData(
                            board_id = boardId,
                            battery = 80,
                            gps = GpsData(valid = true, lat = 22.5726, lng = 88.3639),
                            sensors = SensorData(mq4 = valMq4, mq135 = 340, accel = 9.8, ir = 49000),
                            status = StatusData(),
                            ts = System.currentTimeMillis()
                        )
                        repository.ingestTelemetry(data)
                        delay(300L)
                    }
                }
            }
            SimScenario.FALL_IMPACT -> {
                viewModelScope.launch {
                    // Impact sample
                    val impactData = TelemetryData(
                        board_id = boardId,
                        battery = 79,
                        gps = GpsData(valid = true, lat = 22.5731, lng = 88.3644),
                        sensors = SensorData(mq4 = 400, mq135 = 280, accel = 28.5, ir = 42000),
                        status = StatusData(health_hazard = true),
                        ts = System.currentTimeMillis()
                    )
                    repository.ingestTelemetry(impactData)
                    vibrate(context, 1000L)
                }
            }
            SimScenario.DUAL_HAZARD -> {
                val data = TelemetryData(
                    board_id = boardId,
                    battery = 77,
                    gps = GpsData(valid = true, lat = 22.5733, lng = 88.3647),
                    sensors = SensorData(mq4 = 2380, mq135 = 2050, accel = 26.2, ir = 41000),
                    status = StatusData(health_hazard = true, env_hazard = true),
                    ts = now
                )
                repository.ingestTelemetry(data)
                vibrate(context, 1500L)
            }
            SimScenario.SENSOR_FAILURE -> {
                val data = TelemetryData(
                    board_id = boardId,
                    battery = 75,
                    gps = GpsData(valid = true, lat = 22.5726, lng = 88.3639),
                    sensors = SensorData(mq4 = 380, mq135 = 0, accel = 0.0, ir = 0),
                    status = StatusData(mpu_ok = false, mq135_ok = false, missing = 2),
                    ts = now
                )
                repository.ingestTelemetry(data)
            }
            SimScenario.WEAK_CONTACT -> {
                val data = TelemetryData(
                    board_id = boardId,
                    battery = 74,
                    gps = GpsData(valid = true, lat = 22.5726, lng = 88.3639),
                    sensors = SensorData(mq4 = 390, mq135 = 270, accel = 9.8, ir = 12400),
                    status = StatusData(health_hazard = true),
                    ts = now
                )
                repository.ingestTelemetry(data)
            }
            SimScenario.SIGNAL_LOSS -> {
                // Ingest older packet to simulate timeout
                val staleData = TelemetryData(
                    board_id = boardId,
                    battery = 70,
                    gps = GpsData(valid = true, lat = 22.5726, lng = 88.3639),
                    sensors = SensorData(mq4 = 380, mq135 = 260, accel = 9.8, ir = 48000),
                    status = StatusData(),
                    ts = now - 20_000L
                )
                repository.ingestTelemetry(staleData)
            }
            SimScenario.DEBOUNCE_PROGRESS -> {
                // 1 sample of hazard
                val data = TelemetryData(
                    board_id = boardId,
                    battery = 84,
                    gps = GpsData(valid = true, lat = 22.5726, lng = 88.3639),
                    sensors = SensorData(mq4 = 2280, mq135 = 310, accel = 9.8, ir = 49000),
                    status = StatusData(env_hazard = true),
                    ts = now
                )
                repository.ingestTelemetry(data)
            }
        }
    }
}
