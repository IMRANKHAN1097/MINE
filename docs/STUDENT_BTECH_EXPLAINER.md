# SKIMMS: Comprehensive B.Tech Engineering & Safety Guide
**Team HEXACREW • University of Calcutta, Dept of Applied Optics & Photonics (AOP)**

---

## 1. Project Overview & Motivation

In underground mining operations (such as Eastern Coalfields or Bharat Coking Coal in India), subterranean workers face life-threatening hazards:
1. **Firedamp & Methane Inflow ($CH_4$):** Colorless, odorless gas that forms an explosive atmosphere between 5% and 15% concentration in air.
2. **Toxic Blackdamp / Afterdamp:** Carbon Monoxide ($CO$), Nitrogen Oxides ($NO_x$), and Oxygen depletion.
3. **Rockbursts, Roof Collapses & Falls:** Traumatic impacts and worker incapacitation ("Man-Down").
4. **Communication Blindspots:** Underground shafts block conventional cellular and GPS signals.

**SKIMMS ("Smart Kavach for Intelligent Mine Monitoring")** addresses this challenge with a two-tier safety architecture:
- **Tier 1 (Embedded Helmet Kavach):** Immediate on-board fail-safe actuation on Core 1 (LEDs and Piezo Buzzer) that functions **100% offline** even if all mine network infrastructure is destroyed.
- **Tier 2 (Control Room & Mobile PWA):** Real-time telemetry ingestion, AI Safety Box explainable inference, incident escalation, and dispatch.

---

## 2. Embedded FreeRTOS Architecture: Why Two Cores?

The ESP32 microcontroller features two 32-bit Xtensa LX6 CPU cores:
- **CORE 1 (Application / High-Priority Safety Loop):**
  - Pin assignments: MQ-4 (GPIO34), MQ-135 (GPIO35), I2C SDA/SCL (GPIO21/22), LEDs (GPIO26, 27, 25), Buzzer (GPIO13).
  - Runs with zero network dependencies. Sensor sampling, debounce evaluation, LED pattern shifts, and buzzer tone cadences happen deterministically every 500 ms.
  - **Crucial Safety Rule:** Core 1 must NEVER make blocking network calls (`WiFi.begin()`, `http.POST()`). If it did, a lost WiFi signal would freeze the helmet's emergency buzzer while the miner is walking into a methane pocket!

- **CORE 0 (Protocol / Network Task):**
  - Manages the TCP/IP stack, WiFi reconnect logic, and HTTP transmission.
  - Receives serialized JSON packets from Core 1 through a thread-safe FreeRTOS Queue (`telemetryQueue`) configured for **20 packets**.
  - If network is disrupted, Core 0 buffers packets. When the buffer is full, it drops the oldest packet, ensuring that whenever connection resumes, the most recent helmet telemetry is delivered first.

---

## 3. Directorate General of Mines Safety (DGMS) & Intrinsic Safety (IS)

### Is a prototype ESP32 safe for underground coal mines?
**NO.** Standard commercial development boards, exposed copper traces, open jumper wires, and standard Li-Ion pouch batteries can generate micro-sparks or surface heat above the auto-ignition temperature of methane ($537^\circ\text{C}$).

### What is required for underground certification?
Under Indian Mining Standards (DGMS Circulars) and international standards (IEC 60079-11 / ATEX):
1. **Intrinsic Safety (Ex 'ia' / 'ib'):** Circuit voltages and currents must be strictly limited using zener diode barriers and current-limiting resistors so that no spark or thermal effect can ignite a mixture of methane and air.
2. **Flameproof Enclosures (Ex 'd'):** Any non-intrinsically safe electronics must be encased in an explosion-proof housing capable of withstanding internal gas explosions without igniting the surrounding mine atmosphere.
3. **Approval Authority:** Indian coal mines require testing and statutory certification from CIMFR (Central Institute of Mining and Fuel Research, Dhanbad) and DGMS approval before deployment beyond surface or pilot demonstration pits.

---

## 4. Sensor Physics & Real-World Calibration

### 1. MQ-4 ($CH_4$) and MQ-135 (Air Quality) Sensors
- **Mechanism:** Metal oxide semiconductor ($SnO_2$). In clean air, donor electrons in tin dioxide are captured by adsorbed oxygen, creating a high resistance barrier. In the presence of reducing gases (methane), oxygen is consumed and electrical conductivity increases ($R_s$ decreases).
- **The 48-Hour Burn-In Requirement:** When new or unpowered, $SnO_2$ absorbs ambient moisture and volatile organic compounds. The internal heater coil must run for **24 to 48 hours** to drive off contaminants before stable readings emerge.
- **Dynamic Calibration & Conversion from Raw ADC to PPM:**
  1. The ESP32 measures the 12-bit raw analog reading ($ADC \in [0, 4095]$):
     $$V_{out} = \left(\frac{ADC}{4095}\right) \times 3.3\text{ V}$$
  2. The instantaneous sensor surface resistance $R_s$ is calculated using the voltage divider with load resistor $R_L$ ($20\text{ k}\Omega$):
     $$R_s = \left(\frac{3.3\text{ V} - V_{out}}{V_{out}}\right) \times R_L$$
  3. Clean air baseline $R_0$ is measured in pure ambient air and saved via the Admin Web Interface.
  4. The gas concentration in parts-per-million (PPM) is calculated using the power-law logarithmic equation:
     $$\text{PPM} = A \times \left(\frac{R_s}{R_0}\right)^B$$
     For MQ-4 methane: $A = 1000.0, B = -2.85$.
     For MQ-135 air contaminants ($CO, NH_3$): $A = 110.0, B = -2.86$.
  5. **Admin Web Calibration Flow:**
     - The safety engineer enters the measured $R_0$ and site limits in the Admin web console.
     - The server stores the calibration profile per helmet node.
     - The ESP32 pulls `/api/helmet/calibration` using its `x-device-key` and commits the values to Non-Volatile Storage (`Preferences.h`).
     - Telemetry packets report both raw ADC and calibrated PPM (`mq4_ppm`, `mq135_ppm`).

---

## 5. Hardware Task Watchdog Timer (WDT) Implementation

In critical life-safety environments, firmware hangs caused by I2C bus lockups (e.g. static discharge on sensor cables) or RTOS deadlock must never leave a miner unprotected.

### Watchdog Architecture:
- **Module:** ESP-IDF Task Watchdog Timer (`esp_task_wdt.h`).
- **Timeout Period:** 5.0 seconds.
- **Multi-Task Enrollment:**
  - **Core 1 (Safety Task):** Registered in `setup()` via `esp_task_wdt_add(NULL)`. Petted every 500 ms in `loop()` with `esp_task_wdt_reset()`.
  - **Core 0 (Network Task):** Registered in `NetworkTask()` via `esp_task_wdt_add(NULL)`. Petted on every loop cycle.
- **Fail-Safe Behavior on Lockup:**
  If either the sensor acquisition loop on Core 1 freezes or the network socket on Core 0 hangs for $> 5$ seconds without petting:
  1. The hardware watchdog triggers a system reset interrupt.
  2. A panic report and register backtrace are logged to Serial at 115200 baud.
  3. The ESP32 performs a full hardware reboot in under 450 ms, re-initializing all GPIO pins, buzzer, and I2C sensors immediately back to the safe state.

### 2. MAX30105 Optical Contact Sensor
- In SKIMMS, the MAX30105 IR channel is used as an **optical contact quality probe**, verifying that the helmet suspension cradle is securely seated against the miner's head (IR return between 1,000 and 20,000 indicates weak or loose contact). It is not a clinical medical device.

---

## 5. Subterranean Underground Connectivity: Beyond WiFi

In real subterranean shafts, 2.4 GHz WiFi signals attenuate rapidly due to rock walls, coal absorption, and humidity (useful range is often $< 30$ meters).
In the production roadmap:
1. **LoRa (Long Range RF, 868 MHz / 433 MHz):** Sub-GHz frequencies penetrate tunnel bends and haulage roads significantly further (hundreds of meters).
2. **Subterranean Wireless Mesh (ESP-NOW / BLE Mesh):** Helmets act as relay nodes, hopping telemetry packet-by-packet along the drift until reaching a wired optical-fiber base station.
3. **Timestamp Integrity:** Every buffered packet includes a helmet hardware timestamp `ts` so that when stored packets arrive out-of-order, historical reconstruction accurately reflects the incident timeline.

---

## 6. Personal Data Protection & Privacy (DPDP Act 2023)

Worker location and biometric contact quality constitute personal health and location telemetry. Under the **Digital Personal Data Protection Act (DPDP Act, 2023)**:
- **Purpose Limitation:** Telemetry is gathered strictly for occupational safety and emergency mine rescue.
- **Role-Based Access Control:** Miners access only their personal helmet via worker PIN; supervisors/control room officers see fleet aggregation.
- **Audit Logging:** Every alert acknowledgement, key rotation, and data export is logged with operator identification.

---

## 7. Known Gaps & Production Roadmap

1. **Database Migration:** Replace the single-file JSON store with an embedded SQLite database or PostgreSQL with timescaledb.
2. **Hardware Watchdog:** Enable the ESP32 Hardware Watchdog Timer (`esp_task_wdt`) to automatically recover from any hardware lockup within 3 seconds.
3. **Over-The-Air (OTA) Updates:** Dual-partition flash updates via HTTPS so firmware can be patched without opening helmet enclosures.
4. **ML Anomaly Detection (Isolation Forest):** Run a lightweight edge model beside explainable rules to detect anomalous multi-sensor patterns before hard limits are crossed.
