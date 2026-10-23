# SKIMMS Troubleshooting Guide
**Team HEXACREW • University of Calcutta, Dept of Applied Optics & Photonics**

This document covers common failure modes encountered during embedded flashing, network commissioning, and sensor integration.

---

## 1. Embedded Hardware & Sensor Failures

### Issue: Yellow LED blinks every 2 seconds, buzzer beeps every 10 seconds
- **Root Cause:** A sensor is marked offline (`missing > 0` in status).
- **Diagnosis:** Open Serial Monitor at 115200 baud during boot:
  - If `MPU6050 not responding`: Check SDA (GPIO21) and SCL (GPIO22) pull-up resistors (typically 4.7kΩ to 3.3V). Check address (default is `0x68`; if AD0 is HIGH, it is `0x69`).
  - If `MAX30105 not responding`: Check VIN (needs 3.3V to 5V depending on breakout) and I2C wiring (address `0x57`).
  - If `MQ-4` or `MQ-135` analog reading is 0 or 4095: Check ADC pin connections on GPIO34/35. Note: GPIO34 and GPIO35 are input-only pins with no internal pull-ups.

### Issue: MQ-4 or MQ-135 reading is erratic or constantly triggers DANGER
- **Root Cause:** MQ sensors have an internal tin dioxide ($SnO_2$) heating coil requiring 5V supply and a **24 to 48 hour burn-in period** before the chemical substrate stabilizes.
- **Fix:** Power the sensor continuously for 24 hours in clean ambient air before calibrating zero-point baseline ($R_0$).

### Issue: MAX30105 IR fluctuates or triggers "Weak Optical Contact"
- **Root Cause:** The MAX30105 optical window must have firm contact against the forehead or temple skin. Hair, sweat, or a loose strap drops the IR return below 20,000.
- **Fix:** Adjust helmet suspension cradle for skin contact; keep optical glass clean. Note that this is a contact quality indicator, not an FDA-certified medical diagnostic.

---

## 2. Network & Firmware Communication Failures

### Issue: Serial prints `[Core 0 Error] 401 Unauthorized`
- **Root Cause:** The `DEVICE_KEY` in `skimms_esp32_firmware.ino` does not match the key registered in the Admin Control Room database.
- **Fix:** In the Admin Control Room, look up `ESP32_NODE_01`, click **Rotate Key**, and copy the newly generated `kavach_devkey_...` into the firmware. Re-flash the ESP32.

### Issue: Serial prints `[Core 0 Error] 403 Forbidden`
- **Root Cause:** The `BOARD_ID` in firmware has not been registered by the Admin.
- **Fix:** In the Admin Control Room, click **+ Register Worker**, enter the exact board ID (e.g. `ESP32_NODE_01`), worker name, and PIN.

### Issue: ESP32 fails to connect to WiFi
- **Root Cause:** ESP32 hardware radio only supports **2.4 GHz IEEE 802.11b/g/n**. It cannot see or associate with 5 GHz only SSIDs.
- **Fix:** In dual-band routers or phone hotspots, select "2.4 GHz Band" or "Maximize Compatibility".

### Issue: LEDs freeze or buzzer halts when WiFi disconnects
- **Root Cause:** Network code was accidentally called on Core 1 instead of FreeRTOS Core 0.
- **Fix:** Ensure HTTP and WiFi tasks stay pinned to Core 0 (`xTaskCreatePinnedToCore(..., 0)`). Core 1 loop must remain purely local.

---

## 3. Server & Web PWA Issues

### Issue: PWA Worker View shows stale sensor values
- **Root Cause:** Service worker cached `/api/telemetry` or `/api/fleet`.
- **Fix:** Check `sw.js`. All `/api/*` and WebSocket endpoints must bypass cache (`network-only`).

### Issue: Worker login returns HTTP 429 Too Many Requests
- **Root Cause:** 10 consecutive incorrect PIN attempts triggered the brute-force security lock.
- **Fix:** Wait 10 minutes for lockout expiry, or restart server process to reset in-memory attempt table.
