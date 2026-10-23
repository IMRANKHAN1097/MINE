// SKIMMS Client Application
let currentRole = 'worker'; // 'worker' or 'admin'
let activeWorkerId = 'ESP32_NODE_01';
let socket = null;
let leafletMap = null;
const mapMarkers = new Map();
const mapTrails = new Map();
const mq4Sparklines = new Map();

// Register Service Worker for PWA
if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js').catch(console.error);
}

// Connect WebSocket
function initWebSocket() {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  socket = new WebSocket(`${protocol}//${window.location.host}`);

  socket.onopen = () => {
    console.log('[SKIMMS] WebSocket connected');
    if (currentRole === 'admin') {
      socket.send(JSON.stringify({ type: 'AUTH_ADMIN' }));
    } else {
      socket.send(JSON.stringify({ type: 'AUTH_WORKER', workerId: activeWorkerId }));
    }
  };

  socket.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      if (data.type === 'TELEMETRY_UPDATE') {
        handleTelemetryUpdate(data);
      } else if (data.type === 'ALERT_UPDATE') {
        handleAlertUpdate(data.alert);
      } else if (data.type === 'WORKER_REMOVED') {
        handleWorkerRemoved(data.workerId);
      }
    } catch (e) {
      console.error(e);
    }
  };

  socket.onclose = () => {
    setTimeout(initWebSocket, 2000);
  };
}

// Setup Leaflet Map
function initMap() {
  const mapElem = document.getElementById('map');
  if (!mapElem || leafletMap) return;

  leafletMap = L.map('map').setView([22.5726, 88.3639], 16);
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '© OpenStreetMap • SKIMMS Mine Radar'
  }).addTo(leafletMap);
}

// Fetch Initial Fleet Snapshot
async function loadFleet() {
  try {
    const res = await fetch('/api/fleet');
    const data = await res.json();
    renderFleetCards(data.workers);
    renderAdminAlerts(data.alerts);
  } catch (e) {
    console.error(e);
  }
}

function handleTelemetryUpdate(data) {
  // If Worker View
  if (data.workerId === activeWorkerId) {
    updateWorkerUI(data.telemetry, data.insight);
  }

  // If Admin View
  updateAdminWorkerCard(data);
  updateMapMarker(data);
}

function updateWorkerUI(telemetry, insight) {
  const statusColors = {
    SAFE: '#00E676',
    CAUTION: '#FFD600',
    DANGER: '#FF6D00',
    CRITICAL: '#FF1744'
  };

  const block = document.getElementById('statusBlock');
  block.className = `status-block ${insight.status}`;
  const label = document.getElementById('statusLabel');
  label.innerText = insight.status;
  label.style.color = statusColors[insight.status];

  const badge = document.getElementById('riskScoreBadge');
  badge.innerText = `RISK ${insight.riskScore}%`;
  badge.style.color = statusColors[insight.status];

  // Sensors
  const s = telemetry.sensors || {};
  document.getElementById('valMq4').innerText = s.mq4 || 0;
  document.getElementById('valMq135').innerText = s.mq135 || 0;
  document.getElementById('valAccel').innerText = s.accel ? s.accel.toFixed(1) : '9.8';
  document.getElementById('valIr').innerText = s.ir || 0;

  if (telemetry.battery) {
    document.getElementById('valBattery').innerText = `Battery: ${telemetry.battery}%`;
  }

  if (telemetry.gps && telemetry.gps.valid) {
    document.getElementById('valGps').innerText = `${telemetry.gps.lat.toFixed(4)}°N, ${telemetry.gps.lng.toFixed(4)}°E`;
  }

  // AI Reasons
  const list = document.getElementById('aiReasonsList');
  list.innerHTML = (insight.reasons || []).map(r => `<li>${escapeHtml(r)}</li>`).join('');

  // Haptic feedback / vibrate on DANGER or CRITICAL (Requirement 6)
  if (insight.status === 'DANGER' || insight.status === 'CRITICAL') {
    if ('vibrate' in navigator) {
      navigator.vibrate([300, 100, 300]);
    }
  }
}

function updateMapMarker(data) {
  if (!leafletMap || !data.telemetry || !data.telemetry.gps || !data.telemetry.gps.valid) return;
  const lat = data.telemetry.gps.lat;
  const lng = data.telemetry.gps.lng;

  const statusColor = data.insight.status === 'CRITICAL' ? '#FF1744' :
                      data.insight.status === 'DANGER' ? '#FF6D00' :
                      data.insight.status === 'CAUTION' ? '#FFD600' : '#00E676';

  let marker = mapMarkers.get(data.workerId);
  if (!marker) {
    marker = L.circleMarker([lat, lng], {
      color: statusColor,
      fillColor: statusColor,
      fillOpacity: 0.8,
      radius: 9
    }).addTo(leafletMap);
    marker.bindPopup(`<b>${data.workerName}</b><br>${data.helmetNumber}<br>Status: ${data.insight.status}`);
    mapMarkers.set(data.workerId, marker);
  } else {
    marker.setLatLng([lat, lng]);
    marker.setStyle({ color: statusColor, fillColor: statusColor });
    marker.setPopupContent(`<b>${data.workerName}</b><br>${data.helmetNumber}<br>Status: ${data.insight.status}`);
  }
}

function handleWorkerRemoved(workerId) {
  // Remove card from admin
  const card = document.getElementById(`card-${workerId}`);
  if (card) card.remove();

  // Remove map marker
  const marker = mapMarkers.get(workerId);
  if (marker && leafletMap) {
    leafletMap.removeLayer(marker);
    mapMarkers.delete(workerId);
  }
}

function renderFleetCards(workers) {
  const container = document.getElementById('workerFleetCards');
  container.innerHTML = workers.map(w => {
    const status = w.insight.status || 'SAFE';
    const s = w.telemetry?.sensors || { mq4: 0, mq135: 0, accel: 9.8, ir: 0 };
    return `
      <div id="card-${w.esp32Id}" class="worker-card ${status}">
        <div style="display:flex; justify-content:space-between; align-items:center;">
          <div>
            <b>${escapeHtml(w.workerName)}</b>
            <div style="font-size:0.75rem; color:var(--text-dim); font-family:monospace;">${w.helmetNumber} • ${w.esp32Id}</div>
          </div>
          <span style="font-size:0.8rem; font-weight:bold; color:var(--${status.toLowerCase()});">${status}</span>
        </div>
        <div style="display:flex; justify-content:space-between; font-family:monospace; font-size:0.8rem; background:rgba(0,0,0,0.3); padding:6px; border-radius:6px;">
          <span>MQ4: ${s.mq4}</span>
          <span>MQ135: ${s.mq135}</span>
          <span>Acc: ${s.accel.toFixed(1)}</span>
        </div>
        <canvas id="spark-${w.esp32Id}" class="sparkline-canvas"></canvas>
        <div style="display:flex; justify-content:flex-end; gap:6px;">
          <button onclick="openCalibModal('${w.esp32Id}')" class="action-btn" style="color:var(--primary); font-size:0.75rem;">Calibrate</button>
          <button onclick="rotateKey('${w.esp32Id}')" class="action-btn" style="font-size:0.75rem;">Rotate Key</button>
          <button onclick="deleteWorker('${w.esp32Id}')" class="action-btn" style="color:var(--critical); font-size:0.75rem;">Delete</button>
        </div>
      </div>
    `;
  }).join('');
}

function updateAdminWorkerCard(data) {
  const card = document.getElementById(`card-${data.workerId}`);
  if (!card) {
    loadFleet();
    return;
  }
  card.className = `worker-card ${data.insight.status}`;
  // Draw sparkline
  const canvas = document.getElementById(`spark-${data.workerId}`);
  if (canvas) {
    let history = mq4Sparklines.get(data.workerId) || [];
    history.push(data.telemetry?.sensors?.mq4 || 0);
    if (history.length > 25) history.shift();
    mq4Sparklines.set(data.workerId, history);
    drawSparkline(canvas, history);
  }
}

function drawSparkline(canvas, data) {
  const ctx = canvas.getContext('2d');
  const w = canvas.width = canvas.offsetWidth;
  const h = canvas.height = canvas.offsetHeight;
  ctx.clearRect(0, 0, w, h);

  if (data.length < 2) return;
  const min = 200, max = 2500;
  const step = w / (data.length - 1);

  ctx.beginPath();
  ctx.strokeStyle = '#FFB300';
  ctx.lineWidth = 2;

  data.forEach((val, i) => {
    const clamped = Math.max(min, Math.min(max, val));
    const y = h - ((clamped - min) / (max - min)) * h;
    const x = i * step;
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  });
  ctx.stroke();
}

function handleAlertUpdate(alert) {
  // Add to Admin feed
  const feed = document.getElementById('adminAlertFeed');
  if (feed) {
    const item = document.createElement('div');
    item.className = 'sensor-card';
    item.style.borderColor = alert.level === 'CRITICAL' ? 'var(--critical)' : 'var(--border)';
    item.innerHTML = `
      <div style="display:flex; justify-content:space-between; align-items:center;">
        <b>${alert.level} • ${escapeHtml(alert.workerName)} (${alert.helmetNumber})</b>
        <span style="font-size:0.75rem; color:var(--text-dim);">${new Date(alert.timestamp).toLocaleTimeString()}</span>
      </div>
      <p style="font-size:0.85rem; margin:6px 0;">${escapeHtml(alert.reason)}</p>
      ${(!alert.acknowledged && (alert.level === 'DANGER' || alert.level === 'CRITICAL')) ? 
        `<button class="ack-btn" onclick="acknowledgeAlert('${alert.id}')">Acknowledge</button>` : 
        (alert.acknowledged ? `<span style="color:var(--safe); font-size:0.75rem;">ACK</span>` : '')}
      ${alert.escalated ? `<span class="badge-escalated">ESCALATED (>120s)</span>` : ''}
    `;
    feed.prepend(item);
  }

  // Add to Worker feed if it's for current worker
  if (alert.workerId === activeWorkerId) {
    const wContainer = document.getElementById('workerAlertsContainer');
    if (wContainer) {
      const wItem = document.createElement('div');
      wItem.className = 'sensor-card';
      wItem.innerHTML = `<span style="color:var(--critical); font-weight:bold;">${alert.level}:</span> ${escapeHtml(alert.reason)}`;
      wContainer.prepend(wItem);
    }
  }
}

function renderAdminAlerts(alerts) {
  const feed = document.getElementById('adminAlertFeed');
  feed.innerHTML = '';
  (alerts || []).forEach(handleAlertUpdate);
}

// Global actions
window.acknowledgeAlert = async function(id) {
  await fetch(`/api/alerts/${id}/ack`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ adminName: 'Admin Web Console' })
  });
};

window.rotateKey = async function(esp32Id) {
  const res = await fetch('/api/admin/rotate-key', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ esp32Id })
  });
  const data = await res.json();
  document.getElementById('keyDisplayBox').innerText = data.newDeviceKey;
  document.getElementById('keyModal').classList.add('open');
};

window.deleteWorker = async function(esp32Id) {
  if (confirm(`Remove worker ${esp32Id}? Map marker and telemetry will be purged immediately.`)) {
    await fetch(`/api/admin/workers/${esp32Id}`, { method: 'DELETE' });
  }
};

function escapeHtml(str) {
  return String(str || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// Event Listeners
document.getElementById('btnRoleWorker').addEventListener('click', () => {
  currentRole = 'worker';
  document.getElementById('btnRoleWorker').classList.add('active');
  document.getElementById('btnRoleAdmin').classList.remove('active');
  document.getElementById('workerView').style.display = 'flex';
  document.getElementById('adminView').style.display = 'none';
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ type: 'AUTH_WORKER', workerId: activeWorkerId }));
  }
});

document.getElementById('btnRoleAdmin').addEventListener('click', () => {
  currentRole = 'admin';
  document.getElementById('btnRoleAdmin').classList.add('active');
  document.getElementById('btnRoleWorker').classList.remove('active');
  document.getElementById('adminView').style.display = 'flex';
  document.getElementById('workerView').style.display = 'none';
  initMap();
  loadFleet();
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify({ type: 'AUTH_ADMIN' }));
  }
});

document.getElementById('btnSos').addEventListener('click', async () => {
  await fetch('/api/worker/sos', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ esp32Id: activeWorkerId })
  });
  if ('vibrate' in navigator) navigator.vibrate(1000);
});

document.getElementById('btnExportCsv').addEventListener('click', () => {
  window.location.href = '/api/alerts/export';
});

// Modals
document.getElementById('btnOpenRegister').addEventListener('click', () => {
  document.getElementById('registerModal').classList.add('open');
});
document.getElementById('btnCancelReg').addEventListener('click', () => {
  document.getElementById('registerModal').classList.remove('open');
});
let currentCalibBoardId = '';

window.openCalibModal = async function(esp32Id) {
  currentCalibBoardId = esp32Id;
  document.getElementById('calibNodeId').innerText = esp32Id;
  
  try {
    const res = await fetch(`/api/admin/calibration/${esp32Id}`);
    if (res.ok) {
      const data = await res.json();
      const c = data.calibration || {};
      document.getElementById('calibMq4R0').value = c.mq4_r0 || 10.0;
      document.getElementById('calibMq4Lim').value = c.mq4_limit_ppm || 1000;
      document.getElementById('calibMq135R0').value = c.mq135_r0 || 76.6;
      document.getElementById('calibMq135Lim').value = c.mq135_limit_ppm || 50;
    }
  } catch (e) {
    console.error(e);
  }

  document.getElementById('calibModal').classList.add('open');
};

document.getElementById('btnCancelCalib').addEventListener('click', () => {
  document.getElementById('calibModal').classList.remove('open');
});

document.getElementById('btnSaveCalib').addEventListener('click', async () => {
  if (!currentCalibBoardId) return;

  const payload = {
    mq4_r0: parseFloat(document.getElementById('calibMq4R0').value),
    mq4_limit_ppm: parseFloat(document.getElementById('calibMq4Lim').value),
    mq135_r0: parseFloat(document.getElementById('calibMq135R0').value),
    mq135_limit_ppm: parseFloat(document.getElementById('calibMq135Lim').value)
  };

  const res = await fetch(`/api/admin/calibration/${currentCalibBoardId}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });

  if (res.ok) {
    document.getElementById('calibModal').classList.remove('open');
    alert(`Calibration successfully deployed for ${currentCalibBoardId}. The helmet will sync the new parameters.`);
  } else {
    alert('Failed to update calibration.');
  }
});

document.getElementById('btnSubmitReg').addEventListener('click', async () => {
  const esp32Id = document.getElementById('regEspId').value;
  const helmetNumber = document.getElementById('regHelmetNo').value;
  const workerName = document.getElementById('regWorkerName').value;
  const pin = document.getElementById('regPin').value;

  const res = await fetch('/api/admin/workers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ esp32Id, helmetNumber, workerName, pin })
  });

  const data = await res.json();
  if (res.ok) {
    document.getElementById('registerModal').classList.remove('open');
    document.getElementById('keyDisplayBox').innerText = data.worker.deviceKey;
    document.getElementById('keyModal').classList.add('open');
    loadFleet();
  } else {
    alert(data.error);
  }
});

// Initialize on start
initWebSocket();
