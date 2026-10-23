# SKIMMS Test & Verification Matrix
**Team HEXACREW • University of Calcutta, Dept of Applied Optics & Photonics**

This table details the 16 critical test cases, expected system behaviors, and verification methodology.

| # | Test Scenario | Input Condition / Payload | Expected System Result | Verification Environment |
|---|---------------|---------------------------|-------------------------|--------------------------|
| **1** | **Unregistered Helmet** | `POST /api/telemetry` with `board_id: "UNKNOWN_99"` | HTTP `403 Forbidden`, error message "Unregistered ESP32 Board ID" | Tested in backend suite & Android test harness |
| **2** | **Wrong Device Key** | Registered `ESP32_NODE_01` but header `x-device-key: "invalid_key"` | HTTP `401 Unauthorized`, packet rejected | Tested in backend suite & Android test harness |
| **3** | **Rotated Key** | Admin rotates key for `ESP32_NODE_01`; ESP32 sends previous key | Old key rejected with `401 Unauthorized`; new key succeeds with `200 OK` | Tested in backend suite & Android test harness |
| **4** | **Methane Gas Spike** | `MQ-4 = 2450 ADC` (persisting ≥ 3 samples) | Evaluates `DANGER` (or `CRITICAL` if coupled); Red LED ON, buzzer beeps every 2s, alert logged | Tested in Android Simulator & Node.js unit test |
| **5** | **Rising Gas Trend** | Consecutive MQ-4 readings rising at `+20 ADC/sec` with projected breach in `≤ 60s` | Early Warning `CAUTION` with prediction: "Methane rising (+20/s). Limit in 38s!" | Tested in AI Engine & Android Simulator |
| **6** | **3-Sample Debounce** | Gas or trauma spike for sample 1 and 2 | Displays `CAUTION` with "Confirming reading (1/3)" and "(2/3)"; elevates to `DANGER` only on 3rd sample | Tested in AI Engine & Android Simulator |
| **7** | **Physical Impact** | MPU6050 accelerometer reads `≥ 24.5 m/s²` (e.g. `28.5 m/s²`) | Triggers health hazard alert, Yellow LED ON, initiates 15s immobility timer | Tested in Android Simulator & Serial test rig |
| **8** | **Man-Down Detection** | Impact `≥ 24.5 m/s²` followed by immobility (`accel ~ 9.8 ± 1.0 m/s²`) for `≥ 15s` | Status escalates to `CRITICAL`: "MAN-DOWN DETECTED: Fall impact followed by 15s immobility" | Tested in AI Engine & Android Simulator |
| **9** | **Weak Contact Quality** | MAX30105 IR reads between `1000` and `20000` | Displays `CAUTION`: "MAX30105 IR: Weak skin contact probe, check helmet strap" (not medical claim) | Tested in AI Engine & Android Simulator |
| **10** | **Signal Lost** | No telemetry packet received for `> 15 seconds` from registered node | Status elevates to `DANGER`: "SIGNAL LOST (>15s without telemetry)", alert pushed to Control Room | Tested in backend timeout loop & Android background check |
| **11** | **Emergency SOS** | Worker taps "TRANSMIT EMERGENCY SOS" on mobile app / helmet button | Immediate `CRITICAL` broadcast, phone vibrates (1000ms), alert sent to control room & webhook | Tested in Android UI & Web PWA |
| **12** | **Sensor Failure** | `missing > 0` (e.g. MPU6050 offline or disconnected) | Status shows `CAUTION`: "Hardware diagnostic: 1 sensor(s) offline", Yellow LED blinks every 2s, buzzer toggles every 10s | Tested in Arduino firmware Core 1 & Android app |
| **13** | **Alert Acknowledgement** | Control room operator clicks "Acknowledge" on DANGER / CRITICAL alert | Alert state updates to `acknowledged: true`, records `acknowledgedAt` and `acknowledgedBy` | Tested in Android Admin Screen & Web console |
| **14** | **Alert Escalation** | DANGER or CRITICAL alert unacknowledged for `> 120s` (`ESCALATE_S`) | Alert marked `escalated: true`, second emergency webhook dispatched to rescue team | Tested in Node.js timer & Android ViewModel |
| **15** | **Formula-Safe CSV Export** | Alerts containing reasons or names starting with `=`, `+`, `-`, or `@` | Export prepends single quote (`'`), neutralising spreadsheet formula execution (CWE-1236) | Tested in CSV generator functions |
| **16** | **Login Rate Limiter** | 10 incorrect PIN attempts from same client IP / node | Client receives HTTP `429 Too Many Requests` and a 10-minute lockout | Tested in Node.js crypto/rate-limiter suite |

---

### Verification Summary
- **Automated in Code & Unit Tests:** Scenarios 1, 2, 3, 5, 6, 8, 9, 10, 13, 14, 15, 16.
- **Interactive in Android Emulator Testbed:** All 16 scenarios are directly verifiable on the **Test Bed** tab in the Android app.
- **Requires Real Physical Helmet:** Exact physical ADC thermal drift curves of MQ-4/135 heating element, physical I2C bus capacitance on 5m helmet cable, and physical piezo buzzer decibel measurement.
