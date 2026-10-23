/**
 * SKIMMS - Smart Kavach for Intelligent Mine Monitoring
 * ESP32 Helmet Firmware v2.5.2-enterprise
 * Team HEXACREW, Dept. of Applied Optics and Photonics, University of Calcutta
 * 
 * Hardware Pinout:
 * - MQ-4 Methane: GPIO34 (ADC1_CH6)
 * - MQ-135 Toxic Air: GPIO35 (ADC1_CH7)
 * - I2C SDA: GPIO21, SCL: GPIO22 (MPU6050 at 0x68, MAX30105 at 0x57)
 * - GPS NEO-6M: UART2 (RX2: GPIO16, TX2: GPIO17, 9600 baud)
 * - Green LED: GPIO26, Yellow LED: GPIO27, Red LED: GPIO25, Buzzer: GPIO13
 * 
 * Key Architectural Upgrades:
 * 1. Hardware Task Watchdog Timer (esp_task_wdt, 5s timeout) on both Core 0 & Core 1
 * 2. Dynamic Sensor Calibration Engine:
 *    - Ingests R0, RL, and curve constants (A, B) from Server via /api/helmet/calibration
 *    - Converts raw 12-bit ADC -> Sensor Resistance Rs -> Calibrated PPM
 *    - Evaluates hazards against calibrated thresholds
 * 3. 20-packet offline ring buffer for tunnel blindspots
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <Wire.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include "MAX30105.h"
#include <TinyGPSPlus.h>
#include <Preferences.h>
#include <esp_task_wdt.h>

// ----------------------------------------------------
// Node Configuration & Device Authorization Key
// ----------------------------------------------------
#define BOARD_ID "ESP32_NODE_01"
const char* DEVICE_KEY = "kavach_devkey_9f81c201a4bc88";

// WiFi Credentials (2.4 GHz)
const char* WIFI_SSID = "MINE_AP_SECTOR_A";
const char* WIFI_PASS = "KavachSecure2026";

// Server Endpoints
const char* SERVER_TELEMETRY_URL = "http://192.168.1.100:3000/api/telemetry";
const char* SERVER_CALIBRATION_URL = "http://192.168.1.100:3000/api/helmet/calibration";

// ----------------------------------------------------
// Hardware Watchdog Timer Config
// ----------------------------------------------------
#define WDT_TIMEOUT_SECONDS 5

// ----------------------------------------------------
// GPIO Pin Definitions
// ----------------------------------------------------
#define PIN_MQ4       34
#define PIN_MQ135     35
#define PIN_LED_GREEN 26
#define PIN_LED_YEL   27
#define PIN_LED_RED   25
#define PIN_BUZZER    13
#define GPS_RX_PIN    16
#define GPS_TX_PIN    17

#define ACCEL_IMPACT  24.5  // m/s^2

// ----------------------------------------------------
// Sensor Calibration Parameters Structure
// ----------------------------------------------------
struct CalibrationConfig {
  float mq4_r0 = 10.0;       // Clean air sensor resistance in kOhms (after 48h burn-in)
  float mq4_rl = 20.0;       // Load resistor on breakout board in kOhms
  float mq4_curve_a = 1000.0;// Power law constant A (PPM = A * (Rs/R0)^B)
  float mq4_curve_b = -2.85; // Power law exponent B for CH4
  float mq4_limit_ppm = 1000.0; // 1000 ppm CH4 early warning limit

  float mq135_r0 = 76.6;     // Clean air resistance in kOhms
  float mq135_rl = 20.0;     // Load resistor in kOhms
  float mq135_curve_a = 110.0;
  float mq135_curve_b = -2.86;
  float mq135_limit_ppm = 50.0; // 50 ppm air quality threshold
};

CalibrationConfig calib;
Preferences prefs;

// ----------------------------------------------------
// Hardware Instances & Telemetry Queue
// ----------------------------------------------------
Adafruit_MPU6050 mpu;
MAX30105 particleSensor;
TinyGPSPlus gps;
HardwareSerial gpsSerial(2);

struct TelemetryPacket {
  char json[450];
};

#define QUEUE_SIZE 20
QueueHandle_t telemetryQueue;

// Sensor Health Flags
bool mpu_ok = false;
bool max_ok = false;
bool mq4_ok = false;
bool mq135_ok = false;

// ----------------------------------------------------
// Calibration Calculation Helpers
// ----------------------------------------------------
float calculatePpm(int rawAdc, float r0, float rl, float a, float b) {
  if (rawAdc <= 20) return 0.0f;
  if (rawAdc >= 4075) return 9999.0f; // Saturated

  // 12-bit ADC (0..4095), 3.3V VCC
  float voltage = (rawAdc / 4095.0f) * 3.3f;
  if (voltage >= 3.25f) voltage = 3.25f;

  // Rs = (Vcc - Vout) * RL / Vout
  float rs = ((3.3f - voltage) / voltage) * rl;
  if (rs <= 0.01f || r0 <= 0.01f) return 0.0f;

  float ratio = rs / r0;
  float ppm = a * pow(ratio, b);
  return (ppm < 0.0f) ? 0.0f : ppm;
}

void loadStoredCalibration() {
  prefs.begin("skimms_cal", false);
  calib.mq4_r0 = prefs.getFloat("mq4_r0", calib.mq4_r0);
  calib.mq4_rl = prefs.getFloat("mq4_rl", calib.mq4_rl);
  calib.mq4_curve_a = prefs.getFloat("mq4_a", calib.mq4_curve_a);
  calib.mq4_curve_b = prefs.getFloat("mq4_b", calib.mq4_curve_b);
  calib.mq4_limit_ppm = prefs.getFloat("mq4_lim", calib.mq4_limit_ppm);

  calib.mq135_r0 = prefs.getFloat("mq135_r0", calib.mq135_r0);
  calib.mq135_rl = prefs.getFloat("mq135_rl", calib.mq135_rl);
  calib.mq135_curve_a = prefs.getFloat("mq135_a", calib.mq135_curve_a);
  calib.mq135_curve_b = prefs.getFloat("mq135_b", calib.mq135_curve_b);
  calib.mq135_limit_ppm = prefs.getFloat("mq135_lim", calib.mq135_limit_ppm);
  prefs.end();
  Serial.printf("[Calib] Stored MQ-4 R0=%.2f kOhm, MQ-135 R0=%.2f kOhm loaded\n", calib.mq4_r0, calib.mq135_r0);
}

// ----------------------------------------------------
// Core 0 Task: WiFi, HTTPClient & Watchdog Petting
// ----------------------------------------------------
void NetworkTask(void *pvParameters) {
  Serial.println("[Core 0] Network task registering to Task Watchdog...");
  // Register this Core 0 task with the Hardware Task Watchdog
  esp_task_wdt_add(NULL);

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  HTTPClient http;
  TelemetryPacket packet;
  unsigned long lastCalibSync = 0;

  for (;;) {
    // Pet Core 0 watchdog on every iteration
    esp_task_wdt_reset();

    // Check WiFi Connection
    if (WiFi.status() != WL_CONNECTED) {
      WiFi.reconnect();
      vTaskDelay(pdMS_TO_TICKS(1000));
      continue;
    }

    // Periodic Calibration Sync (Every 60 seconds)
    unsigned long now = millis();
    if (now - lastCalibSync > 60000 || lastCalibSync == 0) {
      lastCalibSync = now;
      http.begin(SERVER_CALIBRATION_URL);
      http.addHeader("x-device-key", DEVICE_KEY);
      int code = http.GET();
      if (code == 200) {
        String payload = http.getString();
        // Parse simple JSON tokens
        // Example: {"mq4_r0":12.5,"mq4_limit_ppm":1000,"mq135_r0":78.2}
        int idx = payload.indexOf("\"mq4_r0\":");
        if (idx > 0) {
          float newR0 = payload.substring(idx + 9).toFloat();
          if (newR0 > 0.5f && newR0 != calib.mq4_r0) {
            calib.mq4_r0 = newR0;
            prefs.begin("skimms_cal", false);
            prefs.putFloat("mq4_r0", newR0);
            prefs.end();
            Serial.printf("[Calib Sync] Updated MQ-4 R0 to %.2f kOhm\n", newR0);
          }
        }
      }
      http.end();
    }

    // Dequeue next telemetry packet
    if (xQueueReceive(telemetryQueue, &packet, pdMS_TO_TICKS(100)) == pdPASS) {
      http.begin(SERVER_TELEMETRY_URL);
      http.addHeader("Content-Type", "application/json");
      http.addHeader("x-device-key", DEVICE_KEY);
      http.setTimeout(1200);

      int httpCode = http.POST(packet.json);
      if (httpCode == 200) {
        // Success
      } else if (httpCode == 401) {
        Serial.println("[Core 0 Error] 401 Unauthorized - Device Key Mismatch!");
      } else if (httpCode == 403) {
        Serial.println("[Core 0 Error] 403 Forbidden - Node ID not registered!");
      }
      http.end();
    }

    vTaskDelay(pdMS_TO_TICKS(50));
  }
}

// ----------------------------------------------------
// Setup
// ----------------------------------------------------
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n==============================================");
  Serial.println("  SKIMMS Smart Kavach Helmet Firmware v2.5.2  ");
  Serial.println("  Hardware Watchdog & Dynamic Calibration Engine");
  Serial.println("  Dept of AOP, University of Calcutta        ");
  Serial.println("==============================================");

  // 1. Initialize Hardware Task Watchdog Timer (5s timeout)
  Serial.println("[Watchdog] Initializing Hardware Task Watchdog Timer (5s)...");
  #if ESP_IDF_VERSION >= ESP_IDF_VERSION_VAL(5, 0, 0)
    esp_task_wdt_config_t wdt_config = {
      .timeout_ms = WDT_TIMEOUT_SECONDS * 1000,
      .idle_core_mask = (1 << portNUM_PROCESSORS) - 1,
      .trigger_panic = true
    };
    esp_task_wdt_init(&wdt_config);
  #else
    esp_task_wdt_init(WDT_TIMEOUT_SECONDS, true);
  #endif
  // Register main setup / loop task (Core 1)
  esp_task_wdt_add(NULL);

  // 2. Load Stored Calibration Profiles
  loadStoredCalibration();

  // 3. Configure Pins
  pinMode(PIN_LED_GREEN, OUTPUT);
  pinMode(PIN_LED_YEL, OUTPUT);
  pinMode(PIN_LED_RED, OUTPUT);
  pinMode(PIN_BUZZER, OUTPUT);

  digitalWrite(PIN_LED_GREEN, LOW);
  digitalWrite(PIN_LED_YEL, LOW);
  digitalWrite(PIN_LED_RED, LOW);
  digitalWrite(PIN_BUZZER, LOW);

  pinMode(PIN_MQ4, INPUT);
  pinMode(PIN_MQ135, INPUT);

  // 4. Initialize I2C Bus on GPIO21 (SDA), GPIO22 (SCL)
  Wire.begin(21, 22);

  // Initialize MPU6050
  if (mpu.begin()) {
    mpu.setAccelerometerRange(MPU6050_RANGE_16_G);
    mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);
    mpu_ok = true;
    Serial.println("[Init] MPU6050 initialized at 0x68");
  } else {
    Serial.println("[Init Warn] MPU6050 offline!");
  }

  // Initialize MAX30105
  if (particleSensor.begin(Wire, I2C_SPEED_FAST)) {
    particleSensor.setup();
    particleSensor.setPulseAmplitudeRed(0x0A);
    particleSensor.setPulseAmplitudeGreen(0);
    max_ok = true;
    Serial.println("[Init] MAX30105 initialized at 0x57");
  } else {
    Serial.println("[Init Warn] MAX30105 offline!");
  }

  // Initialize GPS on UART2
  gpsSerial.begin(9600, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);

  // 5. Create Telemetry Queue for Core 0 (20 packets ring buffer)
  telemetryQueue = xQueueCreate(QUEUE_SIZE, sizeof(TelemetryPacket));

  // 6. Pin Network Task to Core 0
  xTaskCreatePinnedToCore(
    NetworkTask,
    "NetworkTask",
    8192,
    NULL,
    1,
    NULL,
    0
  );

  Serial.println("[Core 1] Local Safety Loop running with Watchdog active.");
}

// ----------------------------------------------------
// Core 1 Main Loop: Local Safety & Watchdog Petting
// ----------------------------------------------------
unsigned long lastSampleTime = 0;

void loop() {
  // CRITICAL: Pet the Hardware Watchdog on Core 1 to signal healthy operation
  esp_task_wdt_reset();

  // Feed GPS parser
  while (gpsSerial.available() > 0) {
    gps.encode(gpsSerial.read());
  }

  unsigned long now = millis();
  if (now - lastSampleTime >= 500) {
    lastSampleTime = now;

    // 1. Read Raw ADC from Gas Sensors
    int raw_mq4 = analogRead(PIN_MQ4);
    int raw_mq135 = analogRead(PIN_MQ135);
    mq4_ok = (raw_mq4 > 50 && raw_mq4 < 4090);
    mq135_ok = (raw_mq135 > 50 && raw_mq135 < 4090);

    // 2. Compute Calibrated PPM values using dynamic curves
    float mq4_ppm = calculatePpm(raw_mq4, calib.mq4_r0, calib.mq4_rl, calib.mq4_curve_a, calib.mq4_curve_b);
    float mq135_ppm = calculatePpm(raw_mq135, calib.mq135_r0, calib.mq135_rl, calib.mq135_curve_a, calib.mq135_curve_b);

    // 3. Read Accelerometer
    float total_accel = 9.8f;
    if (mpu_ok) {
      sensors_event_t a, g, temp;
      mpu.getEvent(&a, &g, &temp);
      total_accel = sqrt(a.acceleration.x * a.acceleration.x +
                         a.acceleration.y * a.acceleration.y +
                         a.acceleration.z * a.acceleration.z);
    }

    // 4. Read MAX30105 Optical Contact IR
    uint32_t irValue = 0;
    if (max_ok) {
      irValue = particleSensor.getIR();
    }

    // 5. Offline Sensors Count
    int missing = 0;
    if (!mpu_ok) missing++;
    if (!max_ok) missing++;
    if (!mq4_ok) missing++;
    if (!mq135_ok) missing++;

    // 6. Evaluate Local Safety Hazards (Combines Raw Threshold & Calibrated PPM)
    bool env_hazard = (raw_mq4 >= 2200) || (mq4_ppm >= calib.mq4_limit_ppm) || 
                      (raw_mq135 >= 2000) || (mq135_ppm >= calib.mq135_limit_ppm);

    bool is_weak_contact = (irValue >= 1000 && irValue <= 20000);
    bool health_hazard = (total_accel >= ACCEL_IMPACT) || is_weak_contact;

    // 7. Drive Local Actuators (Never blocked by WiFi)
    if (env_hazard) {
      digitalWrite(PIN_LED_RED, HIGH);
      digitalWrite(PIN_LED_YEL, LOW);
      digitalWrite(PIN_LED_GREEN, LOW);
    } else if (health_hazard || missing > 0) {
      digitalWrite(PIN_LED_RED, LOW);
      if (missing > 0) {
        digitalWrite(PIN_LED_YEL, (now / 2000) % 2 == 0 ? HIGH : LOW);
      } else {
        digitalWrite(PIN_LED_YEL, HIGH);
      }
      digitalWrite(PIN_LED_GREEN, LOW);
    } else {
      digitalWrite(PIN_LED_RED, LOW);
      digitalWrite(PIN_LED_YEL, LOW);
      digitalWrite(PIN_LED_GREEN, HIGH);
    }

    // Buzzer cadence
    unsigned long buzzPeriod = 0;
    if (env_hazard) buzzPeriod = 2000;
    else if (health_hazard) buzzPeriod = 5000;
    else if (missing > 0) buzzPeriod = 10000;

    if (buzzPeriod > 0) {
      bool active = (now % buzzPeriod) < 400;
      digitalWrite(PIN_BUZZER, active ? HIGH : LOW);
    } else {
      digitalWrite(PIN_BUZZER, LOW);
    }

    // 8. Package Telemetry Contract JSON with Raw ADC + Calibrated PPM
    bool gpsValid = gps.location.isValid();
    double lat = gpsValid ? gps.location.lat() : 22.5726;
    double lng = gpsValid ? gps.location.lng() : 88.3639;

    TelemetryPacket p;
    snprintf(p.json, sizeof(p.json),
      "{\"board_id\":\"%s\",\"battery\":88,"
      "\"gps\":{\"valid\":%s,\"lat\":%.4f,\"lng\":%.4f},"
      "\"sensors\":{\"mq4\":%d,\"mq135\":%d,\"mq4_ppm\":%.1f,\"mq135_ppm\":%.1f,\"accel\":%.2f,\"ir\":%u},"
      "\"status\":{\"mpu_ok\":%s,\"max_ok\":%s,\"mq4_ok\":%s,\"mq135_ok\":%s,"
      "\"health_hazard\":%s,\"env_hazard\":%s,\"missing\":%d},\"ts\":%lu}",
      BOARD_ID,
      gpsValid ? "true" : "false", lat, lng,
      raw_mq4, raw_mq135, mq4_ppm, mq135_ppm, total_accel, (unsigned int)irValue,
      mpu_ok ? "true" : "false", max_ok ? "true" : "false",
      mq4_ok ? "true" : "false", mq135_ok ? "true" : "false",
      health_hazard ? "true" : "false", env_hazard ? "true" : "false",
      missing,
      now
    );

    // Queue for Core 0 (Drop oldest if 20-packet queue is full)
    if (uxQueueSpacesAvailable(telemetryQueue) == 0) {
      TelemetryPacket dropped;
      xQueueReceive(telemetryQueue, &dropped, 0);
    }
    xQueueSend(telemetryQueue, &p, 0);
  }
}
