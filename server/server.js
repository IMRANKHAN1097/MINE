/**
 * SKIMMS - Smart Kavach for Intelligent Mine Monitoring
 * Team HEXACREW, University of Calcutta, Dept of Applied Optics & Photonics (AOP)
 * 
 * Core Node.js 18+ Backend & Real-Time Telemetry Engine
 * Features:
 * - Dual-role access: Admin (fleet monitor) & Worker (personal helmet)
 * - Per-helmet device key validation (401 invalid, 403 unregistered)
 * - Cryptographic PIN hashing with Node.js crypto.scrypt
 * - Rate limiting (10 failed logins -> 10-minute lockout)
 * - AI Safety Box: Gas trend (60s), Man-Down (15s), Signal Loss (15s), 3-sample Debounce
 * - Real-time WebSocket broadcasting with role filtering
 * - Escalation timer (120s default) & Webhook dispatch
 * - Formula-safe CSV export
 */

const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

const PORT = process.env.PORT || 3000;
const ADMIN_PASS = process.env.ADMIN_PASS || 'HexacrewCalcutta2026!';
const JWT_SECRET = process.env.SECRET || 'kavach_secret_safety_key_2026';
const ESCALATE_S = parseInt(process.env.ESCALATE_S || '120', 10);
const WEBHOOK_URL = process.env.WEBHOOK_URL || null;

// Sensor Safety Limits (DGMS aligned)
const LIMITS = {
  MQ4_LIMIT: 2200,          // Methane raw ADC threshold
  MQ135_LIMIT: 2000,        // Air Quality raw ADC threshold
  IMPACT_ACCEL: 24.5,       // m/s^2 impact threshold
  SIGNAL_LOSS_MS: 15000,    // 15 seconds signal loss
  MAN_DOWN_STILL_MS: 15000, // 15 seconds immobility post-impact
  DEBOUNCE_SAMPLES: 3       // 3 consecutive samples (~1.5s)
};

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// ----------------------------------------------------
// In-Memory & Persistent State
// ----------------------------------------------------
const DATA_FILE = path.join(__dirname, 'store.json');

let store = {
  workers: [
    {
      esp32Id: 'ESP32_NODE_01',
      helmetNumber: 'HLM-01',
      workerName: 'Sukhendu Roy',
      deviceKey: 'kavach_devkey_9f81c201a4bc88',
      pinHash: hashPin('1234', 'salt_node01'),
      salt: 'salt_node01',
      registeredAt: Date.now() - 86400000,
      calibration: {
        mq4_r0: 10.0,
        mq4_rl: 20.0,
        mq4_limit_ppm: 1000.0,
        mq135_r0: 76.6,
        mq135_rl: 20.0,
        mq135_limit_ppm: 50.0
      }
    },
    {
      esp32Id: 'ESP32_NODE_02',
      helmetNumber: 'HLM-02',
      workerName: 'Arpan Ghosh',
      deviceKey: 'kavach_devkey_3b7e4110d9fa22',
      pinHash: hashPin('5678', 'salt_node02'),
      salt: 'salt_node02',
      registeredAt: Date.now() - 43200000,
      calibration: {
        mq4_r0: 11.2,
        mq4_rl: 20.0,
        mq4_limit_ppm: 1000.0,
        mq135_r0: 74.0,
        mq135_rl: 20.0,
        mq135_limit_ppm: 50.0
      }
    }
  ],
  alerts: []
};

// In-memory runtime telemetry state
const latestTelemetry = new Map();
const telemetryHistory = new Map(); // boardId -> Array of samples
const debounceCounters = new Map(); // boardId -> count
const impactEvents = new Map();     // boardId -> timestamp
const lastSeen = new Map();         // boardId -> timestamp
const currentInsights = new Map();  // boardId -> insight object

// Login Rate Limiter: IP/Account -> { failures: number, lockedUntil: timestamp }
const loginAttempts = new Map();

// ----------------------------------------------------
// Security Helpers: Scrypt PIN Hashing & Keys
// ----------------------------------------------------
function hashPin(pin, salt) {
  return crypto.scryptSync(pin, salt, 32).toString('hex');
}

function verifyPin(pin, salt, expectedHash) {
  const actual = hashPin(pin, salt);
  return crypto.timingSafeEqual(Buffer.from(actual, 'hex'), Buffer.from(expectedHash, 'hex'));
}

function generateDeviceKey() {
  return 'kavach_' + crypto.randomBytes(12).toString('hex');
}

function checkRateLimit(key) {
  const record = loginAttempts.get(key);
  if (!record) return { allowed: true };
  if (record.lockedUntil && Date.now() < record.lockedUntil) {
    const remainMin = Math.ceil((record.lockedUntil - Date.now()) / 60000);
    return { allowed: false, message: `Account locked due to 10 failed logins. Try again in ${remainMin} minutes.` };
  }
  return { allowed: true };
}

function recordLoginFailure(key) {
  const record = loginAttempts.get(key) || { failures: 0, lockedUntil: 0 };
  record.failures += 1;
  if (record.failures >= 10) {
    record.lockedUntil = Date.now() + 10 * 60 * 1000; // 10 minutes lock
  }
  loginAttempts.set(key, record);
}

function resetLoginFailures(key) {
  loginAttempts.delete(key);
}

// ----------------------------------------------------
// AI Safety Box Engine
// ----------------------------------------------------
function evaluateTelemetry(telemetry) {
  const boardId = telemetry.board_id;
  const now = Date.now();
  lastSeen.set(boardId, now);

  const sensors = telemetry.sensors || {};
  const status = telemetry.status || {};
  const mq4 = sensors.mq4 || 0;
  const mq135 = sensors.mq135 || 0;
  const accel = typeof sensors.accel === 'number' ? sensors.accel : 9.8;
  const ir = sensors.ir || 45000;

  const reasons = [];

  // 1. Gas Rolling History & 60s Slope Prediction
  let history = telemetryHistory.get(boardId) || [];
  history.push({ mq4, time: now });
  if (history.length > 15) history.shift();
  telemetryHistory.set(boardId, history);

  let trendWarning = null;
  if (history.length >= 4) {
    const first = history[0];
    const last = history[history.length - 1];
    const dt = (last.time - first.time) / 1000.0;
    if (dt > 1.0) {
      const slope = (last.mq4 - first.mq4) / dt; // units per sec
      if (slope > 8.0 && mq4 < LIMITS.MQ4_LIMIT) {
        const secToLimit = (LIMITS.MQ4_LIMIT - mq4) / slope;
        if (secToLimit > 0 && secToLimit <= 60) {
          trendWarning = `Methane rising rapidly (+${slope.toFixed(1)}/s). Threshold in ${Math.round(secToLimit)}s!`;
          reasons.push(trendWarning);
        }
      }
    }
  }

  // 2. Risk Score (0 to 100)
  const gasRatio = Math.max(mq4 / LIMITS.MQ4_LIMIT, mq135 / LIMITS.MQ135_LIMIT);
  const impactRatio = Math.min(accel / LIMITS.IMPACT_ACCEL, 1.5);
  let riskScore = Math.min(100, Math.round(gasRatio * 55 + impactRatio * 35));

  // Optical Contact check (MAX30105 IR 1000..20000 = weak contact)
  const isWeakContact = ir >= 1000 && ir <= 20000;
  if (isWeakContact) {
    reasons.push(`MAX30105 IR (${ir}): Weak skin contact probe`);
    riskScore = Math.max(riskScore, 35);
  }

  // 3. Fall & Man-down Detection (Impact >= 24.5 m/s^2 followed by 15s immobility)
  let manDownActive = false;
  if (accel >= LIMITS.IMPACT_ACCEL) {
    impactEvents.set(boardId, now);
    reasons.push(`High impact acceleration: ${accel.toFixed(1)} m/s²`);
  }

  const lastImpact = impactEvents.get(boardId);
  if (lastImpact) {
    const stillDuration = now - lastImpact;
    const isStill = Math.abs(accel - 9.8) < 1.2 && accel >= 8.8 && accel <= 10.8;
    if (isStill) {
      if (stillDuration >= LIMITS.MAN_DOWN_STILL_MS) {
        manDownActive = true;
        reasons.push(`MAN-DOWN CONFIRMED: Fall impact followed by ${Math.round(stillDuration / 1000)}s stillness`);
      } else {
        reasons.push(`Man-down monitoring: Immobility ${Math.round(stillDuration / 1000)}s / 15s`);
      }
    } else if (Math.abs(accel - 9.8) >= 3.0) {
      impactEvents.delete(boardId); // miner moved
    }
  }

  // 4. Raw Hazard flags from helmet local logic
  const rawEnvHazard = status.env_hazard || mq4 >= LIMITS.MQ4_LIMIT || mq135 >= LIMITS.MQ135_LIMIT;
  const rawHealthHazard = status.health_hazard || accel >= LIMITS.IMPACT_ACCEL || isWeakContact;

  // 5. Debounce: 3 consecutive samples before raising to DANGER
  const isHazardous = rawEnvHazard || rawHealthHazard;
  const curCount = debounceCounters.get(boardId) || 0;
  const newCount = isHazardous ? curCount + 1 : 0;
  debounceCounters.set(boardId, newCount);

  let debounceMsg = null;
  const debounced = newCount >= LIMITS.DEBOUNCE_SAMPLES;
  if (isHazardous && !debounced) {
    debounceMsg = `Confirming reading (${newCount}/${LIMITS.DEBOUNCE_SAMPLES})`;
    reasons.push(debounceMsg);
  }

  // 6. Sensor Failure
  const sensorFail = (status.missing && status.missing > 0) || !status.mpu_ok || !status.max_ok || !status.mq4_ok || !status.mq135_ok;
  if (sensorFail) {
    reasons.push(`Hardware diagnostic: ${status.missing || 1} sensor(s) offline`);
  }

  // 7. Status Resolution
  let finalStatus = 'SAFE';
  if (rawEnvHazard && rawHealthHazard) {
    reasons.push('DUAL HAZARD: Simultaneous gas toxicity and physical impact');
    finalStatus = 'CRITICAL';
  } else if (manDownActive) {
    finalStatus = 'CRITICAL';
  } else if (rawEnvHazard && debounced) {
    finalStatus = 'DANGER';
  } else if (rawHealthHazard && debounced) {
    finalStatus = 'DANGER';
  } else if (isHazardous && !debounced) {
    finalStatus = 'CAUTION';
  } else if (trendWarning) {
    finalStatus = 'CAUTION';
  } else if (sensorFail || isWeakContact) {
    finalStatus = 'CAUTION';
  }

  if (finalStatus === 'CRITICAL') riskScore = Math.max(riskScore, 95);
  else if (finalStatus === 'DANGER') riskScore = Math.max(riskScore, 75);
  else if (finalStatus === 'CAUTION') riskScore = Math.max(riskScore, 40);

  if (reasons.length === 0) reasons.push('All mine parameters nominal');

  return {
    status: finalStatus,
    riskScore,
    trendWarning,
    reasons,
    debounceMsg,
    manDownActive
  };
}

// ----------------------------------------------------
// Alert & Automated Signal Transfer / Webhook
// ----------------------------------------------------
async function triggerAlert(worker, level, reason) {
  const alert = {
    id: 'alt_' + crypto.randomBytes(6).toString('hex'),
    workerId: worker.esp32Id,
    helmetNumber: worker.helmetNumber,
    workerName: worker.workerName,
    level,
    reason,
    timestamp: Date.now(),
    acknowledged: false,
    acknowledgedAt: null,
    acknowledgedBy: null,
    escalated: false
  };

  store.alerts.unshift(alert);

  // Broadcast via WebSocket to Admin & Worker
  broadcastAlert(alert);

  // Forward to Webhook (Telegram / SMS / WhatsApp)
  await dispatchWebhook(alert, false);

  return alert;
}

async function dispatchWebhook(alert, isEscalation) {
  if (!WEBHOOK_URL) return;
  const payload = {
    event: isEscalation ? 'ALERT_ESCALATED' : 'ALERT_TRIGGERED',
    system: 'SKIMMS_CU_AOP',
    alert_id: alert.id,
    helmet: alert.helmetNumber,
    worker: alert.workerName,
    level: alert.level,
    reason: alert.reason,
    timestamp: new Date(alert.timestamp).toISOString()
  };

  try {
    const fetch = globalThis.fetch || require('node-fetch');
    await fetch(WEBHOOK_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });
  } catch (err) {
    console.error('[SKIMMS Webhook Error]', err.message);
  }
}

// Check alert escalation every 5 seconds
setInterval(() => {
  const now = Date.now();
  const thresholdMs = ESCALATE_S * 1000;

  for (const alert of store.alerts) {
    if (!alert.acknowledged && !alert.escalated && (alert.level === 'DANGER' || alert.level === 'CRITICAL')) {
      if (now - alert.timestamp > thresholdMs) {
        alert.escalated = true;
        console.warn(`[SKIMMS ESCALATION] Alert ${alert.id} escalated after ${ESCALATE_S}s!`);
        broadcastAlert(alert);
        dispatchWebhook(alert, true);
      }
    }
  }

  // Check signal loss (>15s)
  for (const worker of store.workers) {
    const last = lastSeen.get(worker.esp32Id);
    if (last && now - last > LIMITS.SIGNAL_LOSS_MS) {
      const cur = currentInsights.get(worker.esp32Id);
      if (!cur || cur.status !== 'DANGER') {
        const insight = {
          status: 'DANGER',
          riskScore: 80,
          reasons: [`SIGNAL LOST: No telemetry for ${Math.round((now - last) / 1000)}s (>15s threshold)`]
        };
        currentInsights.set(worker.esp32Id, insight);
        triggerAlert(worker, 'DANGER', insight.reasons[0]);
      }
    }
  }
}, 4000);

// ----------------------------------------------------
// REST API Endpoints
// ----------------------------------------------------

// 1. Telemetry Ingestion (Requirement 3 & 4)
app.post('/api/telemetry', (req, res) => {
  const deviceKey = req.headers['x-device-key'];
  const telemetry = req.body;
  const boardId = telemetry.board_id;

  if (!boardId) {
    return res.status(400).json({ error: 'Missing board_id in telemetry payload' });
  }

  const worker = store.workers.find(w => w.esp32Id === boardId);
  if (!worker) {
    // Requirement 4: Unregistered ID returns 403
    return res.status(403).json({ error: 'Unregistered ESP32 Board ID' });
  }

  if (!deviceKey || deviceKey !== worker.deviceKey) {
    // Requirement 4: Wrong key returns 401
    return res.status(401).json({ error: 'Invalid x-device-key authorization header' });
  }

  // Inject server timestamp if missing
  if (!telemetry.ts) telemetry.ts = Date.now();

  // Evaluate AI Safety
  const prevInsight = currentInsights.get(boardId);
  const insight = evaluateTelemetry(telemetry);
  currentInsights.set(boardId, insight);
  latestTelemetry.set(boardId, telemetry);

  // Trigger alert if level increased
  const prevLevel = prevInsight ? prevInsight.status : 'SAFE';
  const levelOrder = { SAFE: 0, CAUTION: 1, DANGER: 2, CRITICAL: 3 };
  if (levelOrder[insight.status] > levelOrder[prevLevel]) {
    triggerAlert(worker, insight.status, insight.reasons[0]);
  }

  // Broadcast to WebSockets
  broadcastTelemetry(worker, telemetry, insight);

  res.status(200).json({ status: 'ok', evaluated_level: insight.status, risk_score: insight.riskScore });
});

// 2. Worker Login (Requirement 4 & 10: PIN check & Rate Limit)
app.post('/api/worker/login', (req, res) => {
  const { esp32Id, pin } = req.body;
  const clientIp = req.ip || req.connection.remoteAddress;
  const rateKey = `${clientIp}_${esp32Id}`;

  const rateCheck = checkRateLimit(rateKey);
  if (!rateCheck.allowed) {
    return res.status(429).json({ error: rateCheck.message });
  }

  const worker = store.workers.find(w => w.esp32Id === esp32Id);
  if (!worker) {
    recordLoginFailure(rateKey);
    return res.status(404).json({ error: 'Helmet ESP32 ID not found' });
  }

  const valid = verifyPin(pin, worker.salt, worker.pinHash);
  if (!valid) {
    recordLoginFailure(rateKey);
    return res.status(401).json({ error: 'Invalid access PIN' });
  }

  resetLoginFailures(rateKey);
  res.json({
    token: 'wk_' + crypto.randomBytes(16).toString('hex'),
    worker: {
      esp32Id: worker.esp32Id,
      helmetNumber: worker.helmetNumber,
      workerName: worker.workerName
    }
  });
});

// 3. Admin Login (Requirement 10)
app.post('/api/admin/login', (req, res) => {
  const { password } = req.body;
  if (password !== ADMIN_PASS) {
    return res.status(401).json({ error: 'Invalid Admin Credentials' });
  }
  res.json({ token: 'adm_' + crypto.randomBytes(16).toString('hex') });
});

// 4. Admin: Register Worker (Requirement 4)
app.post('/api/admin/workers', (req, res) => {
  const { esp32Id, helmetNumber, workerName, pin } = req.body;
  if (!esp32Id || !helmetNumber || !workerName || !pin) {
    return res.status(400).json({ error: 'All worker fields are required' });
  }

  const cleanEsp = esp32Id.trim().toUpperCase();
  const cleanHlm = helmetNumber.trim().toUpperCase();

  if (store.workers.some(w => w.esp32Id === cleanEsp)) {
    return res.status(409).json({ error: `ESP32 ID ${cleanEsp} is already registered` });
  }
  if (store.workers.some(w => w.helmetNumber === cleanHlm)) {
    return res.status(409).json({ error: `Helmet Number ${cleanHlm} is already assigned` });
  }

  const salt = crypto.randomBytes(8).toString('hex');
  const deviceKey = generateDeviceKey();
  const newWorker = {
    esp32Id: cleanEsp,
    helmetNumber: cleanHlm,
    workerName: workerName.trim(),
    deviceKey,
    pinHash: hashPin(pin, salt),
    salt,
    registeredAt: Date.now()
  };

  store.workers.push(newWorker);
  res.status(201).json({
    message: 'Worker registered successfully',
    worker: {
      esp32Id: newWorker.esp32Id,
      helmetNumber: newWorker.helmetNumber,
      workerName: newWorker.workerName,
      deviceKey: newWorker.deviceKey
    }
  });
});

// 5. Admin: Rotate Device Key (Requirement 4 & 5)
app.post('/api/admin/rotate-key', (req, res) => {
  const { esp32Id } = req.body;
  const worker = store.workers.find(w => w.esp32Id === esp32Id);
  if (!worker) return res.status(404).json({ error: 'Worker not found' });

  worker.deviceKey = generateDeviceKey();
  res.json({ message: 'Key rotated', esp32Id: worker.esp32Id, newDeviceKey: worker.deviceKey });
});

// 6. Admin: Remove Worker (Requirement 4 & 5)
app.delete('/api/admin/workers/:esp32Id', (req, res) => {
  const id = req.params.esp32Id;
  const idx = store.workers.findIndex(w => w.esp32Id === id);
  if (idx === -1) return res.status(404).json({ error: 'Worker not found' });

  store.workers.splice(idx, 1);
  latestTelemetry.delete(id);
  currentInsights.delete(id);
  lastSeen.delete(id);

  broadcastWorkerRemoved(id);
  res.json({ message: `Worker ${id} removed` });
});

// 7. Acknowledge Alert (Requirement 5)
app.post('/api/alerts/:id/ack', (req, res) => {
  const alert = store.alerts.find(a => a.id === req.params.id);
  if (!alert) return res.status(404).json({ error: 'Alert not found' });

  alert.acknowledged = true;
  alert.acknowledgedAt = Date.now();
  alert.acknowledgedBy = req.body.adminName || 'Admin Control Room';

  broadcastAlert(alert);
  res.json({ message: 'Alert acknowledged', alert });
});

// 8. Emergency SOS Trigger (Requirement 6)
app.post('/api/worker/sos', (req, res) => {
  const { esp32Id } = req.body;
  const worker = store.workers.find(w => w.esp32Id === esp32Id);
  if (!worker) return res.status(404).json({ error: 'Worker not found' });

  triggerAlert(worker, 'CRITICAL', 'WORKER TRANSMITTED MANUAL EMERGENCY SOS');
  res.json({ status: 'SOS_BROADCAST' });
});

// 9. CSV Export with formula injection protection (Requirement 10)
app.get('/api/alerts/export', (req, res) => {
  let csv = 'Alert_ID,Timestamp_UTC,Worker_ID,Helmet_No,Worker_Name,Level,Reason,Acknowledged,Acknowledged_At,Acknowledged_By,Escalated\n';

  for (const alt of store.alerts) {
    const sanitize = (txt) => {
      let s = String(txt || '').replace(/"/g, '""');
      if (s.startsWith('=') || s.startsWith('+') || s.startsWith('-') || s.startsWith('@')) {
        s = "'" + s;
      }
      return `"${s}"`;
    };

    csv += [
      sanitize(alt.id),
      sanitize(new Date(alt.timestamp).toISOString()),
      sanitize(alt.workerId),
      sanitize(alt.helmetNumber),
      sanitize(alt.workerName),
      sanitize(alt.level),
      sanitize(alt.reason),
      alt.acknowledged,
      sanitize(alt.acknowledgedAt ? new Date(alt.acknowledgedAt).toISOString() : 'N/A'),
      sanitize(alt.acknowledgedBy || 'N/A'),
      alt.escalated
    ].join(',') + '\n';
  }

  res.setHeader('Content-Type', 'text/csv');
  res.setHeader('Content-Disposition', 'attachment; filename="SKIMMS_Mine_Alert_Audit.csv"');
  res.send(csv);
});

// 10. Fleet State snapshot
app.get('/api/fleet', (req, res) => {
  const fleet = store.workers.map(w => ({
    esp32Id: w.esp32Id,
    helmetNumber: w.helmetNumber,
    workerName: w.workerName,
    telemetry: latestTelemetry.get(w.esp32Id) || null,
    insight: currentInsights.get(w.esp32Id) || { status: 'SAFE', riskScore: 0, reasons: [] },
    lastSeen: lastSeen.get(w.esp32Id) || null
  }));
  res.json({ workers: fleet, alerts: store.alerts });
});

// 11. Helmet Calibration Pull (Called by ESP32 over WiFi with x-device-key)
app.get('/api/helmet/calibration', (req, res) => {
  const deviceKey = req.headers['x-device-key'];
  const worker = store.workers.find(w => w.deviceKey === deviceKey);
  if (!worker) {
    return res.status(401).json({ error: 'Invalid x-device-key' });
  }

  const defaultCal = {
    mq4_r0: 10.0,
    mq4_rl: 20.0,
    mq4_limit_ppm: 1000.0,
    mq135_r0: 76.6,
    mq135_rl: 20.0,
    mq135_limit_ppm: 50.0
  };

  res.json(worker.calibration || defaultCal);
});

// 12. Admin: Get Helmet Calibration Profile
app.get('/api/admin/calibration/:boardId', (req, res) => {
  const worker = store.workers.find(w => w.esp32Id === req.params.boardId);
  if (!worker) return res.status(404).json({ error: 'Worker not found' });
  res.json({ esp32Id: worker.esp32Id, calibration: worker.calibration });
});

// 13. Admin: Update Helmet Calibration Profile
app.post('/api/admin/calibration/:boardId', (req, res) => {
  const worker = store.workers.find(w => w.esp32Id === req.params.boardId);
  if (!worker) return res.status(404).json({ error: 'Worker not found' });

  const { mq4_r0, mq4_rl, mq4_limit_ppm, mq135_r0, mq135_rl, mq135_limit_ppm } = req.body;
  worker.calibration = {
    mq4_r0: parseFloat(mq4_r0) || 10.0,
    mq4_rl: parseFloat(mq4_rl) || 20.0,
    mq4_limit_ppm: parseFloat(mq4_limit_ppm) || 1000.0,
    mq135_r0: parseFloat(mq135_r0) || 76.6,
    mq135_rl: parseFloat(mq135_rl) || 20.0,
    mq135_limit_ppm: parseFloat(mq135_limit_ppm) || 50.0
  };

  res.json({ message: 'Calibration updated successfully', calibration: worker.calibration });
});

// ----------------------------------------------------
// WebSocket Real-Time Broadcasting (Requirement 8)
// ----------------------------------------------------
const clients = new Map(); // ws -> { role: 'admin'|'worker', workerId?: string }

wss.on('connection', (ws) => {
  clients.set(ws, { role: 'guest' });

  ws.on('message', (msg) => {
    try {
      const data = JSON.parse(msg);
      if (data.type === 'AUTH_ADMIN') {
        clients.set(ws, { role: 'admin' });
        ws.send(JSON.stringify({ type: 'AUTH_SUCCESS', role: 'admin' }));
      } else if (data.type === 'AUTH_WORKER') {
        clients.set(ws, { role: 'worker', workerId: data.workerId });
        ws.send(JSON.stringify({ type: 'AUTH_SUCCESS', role: 'worker', workerId: data.workerId }));
      }
    } catch (_) {}
  });

  ws.on('close', () => {
    clients.delete(ws);
  });
});

function broadcastTelemetry(worker, telemetry, insight) {
  const msg = JSON.stringify({
    type: 'TELEMETRY_UPDATE',
    workerId: worker.esp32Id,
    helmetNumber: worker.helmetNumber,
    workerName: worker.workerName,
    telemetry,
    insight
  });

  for (const [client, meta] of clients.entries()) {
    if (client.readyState === WebSocket.OPEN) {
      if (meta.role === 'admin' || (meta.role === 'worker' && meta.workerId === worker.esp32Id)) {
        client.send(msg);
      }
    }
  }
}

function broadcastAlert(alert) {
  const msg = JSON.stringify({ type: 'ALERT_UPDATE', alert });
  for (const [client, meta] of clients.entries()) {
    if (client.readyState === WebSocket.OPEN) {
      if (meta.role === 'admin' || (meta.role === 'worker' && meta.workerId === alert.workerId)) {
        client.send(msg);
      }
    }
  }
}

function broadcastWorkerRemoved(workerId) {
  const msg = JSON.stringify({ type: 'WORKER_REMOVED', workerId });
  for (const [client] of clients.entries()) {
    if (client.readyState === WebSocket.OPEN) {
      client.send(msg);
    }
  }
}

server.listen(PORT, () => {
  console.log(`[SKIMMS] Smart Kavach Server running on http://localhost:${PORT}`);
  console.log(`[SKIMMS] Admin Password: ${ADMIN_PASS}`);
  console.log(`[SKIMMS] Escalation Threshold: ${ESCALATE_S}s`);
});
