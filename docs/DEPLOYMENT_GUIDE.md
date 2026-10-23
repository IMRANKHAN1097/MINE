# SKIMMS Deployment & Operations Guide
**Team HEXACREW • Dept of Applied Optics & Photonics, University of Calcutta**

This guide provides exact terminal commands and steps for all four deployment phases.

---

## Phase A: Local Laboratory / Laptop Test Rig

In this phase, the laptop acts as the Control Room Server and WiFi Access Point.

### 1. Requirements
- Node.js v18.0 or newer
- ESP32 connected to 2.4 GHz WiFi (ESP32 cannot connect to 5 GHz)
- Laptop and ESP32 on the exact same subnet

### 2. Run the Backend Server
```bash
cd server
npm install
export ADMIN_PASS="HexacrewCalcutta2026!"
export SECRET="kavach_secret_safety_key_2026"
export ESCALATE_S=120
node server.js
```

### 3. Configure Local Firewall
Allow incoming TCP connections on port 3000:
- **Ubuntu/Debian:**
  ```bash
  sudo ufw allow 3000/tcp
  ```
- **macOS / Windows:** Ensure port 3000 is open in inbound firewall rules.

### 4. Flash and Provision Helmet
1. Open your browser to `http://localhost:3000`. Switch to **Admin** view.
2. Click **+ Register Worker**, enter:
   - ESP32 ID: `ESP32_NODE_01`
   - Helmet No: `HLM-01`
   - Worker Name: `Sukhendu Roy`
   - PIN: `1234`
3. Copy the generated Device Key (e.g., `kavach_devkey_9f81c201a4bc88`).
4. In `firmware/skimms_esp32_firmware.ino`, set:
   ```cpp
   #define BOARD_ID "ESP32_NODE_01"
   const char* DEVICE_KEY = "kavach_devkey_9f81c201a4bc88";
   const char* WIFI_SSID = "YOUR_LAPTOP_HOTSPOT";
   const char* WIFI_PASS = "YOUR_HOTSPOT_PASSWORD";
   const char* SERVER_URL = "http://192.168.1.XX:3000/api/telemetry"; // your laptop local IP
   ```
5. Open Arduino IDE, select board `ESP32 Dev Module`, baud `115200`, and upload.
6. Open `http://<laptop-ip>:3000` on any mobile phone on the same WiFi.

---

## Phase B: Production Deployment on Ubuntu VPS

### 1. Provision Ubuntu 22.04 / 24.04 VPS
Connect via SSH:
```bash
ssh root@your-vps-ip
```

### 2. Configure Firewall (UFW)
Open only ports 22 (SSH), 80 (HTTP), and 443 (HTTPS):
```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

### 3. Install Node.js 20 LTS & PM2
```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs
sudo npm install -g pm2
```

### 4. Deploy Backend
```bash
git clone <your-skimms-repo> /opt/skimms
cd /opt/skimms/server
npm install --production

# Create system environment file
cat <<EOF > .env
PORT=3000
ADMIN_PASS=StrongProductionAdminSecret987!
SECRET=kavach_production_jwt_secret_654321
ESCALATE_S=120
WEBHOOK_URL=https://api.telegram.org/bot<TOKEN>/sendMessage
EOF

# Start service with PM2 auto-restart
pm2 start server.js --name skimms-server --env .env
pm2 save
pm2 startup
```

### 5. Install & Configure Caddy (Automatic SSL & WebSocket Proxy)
```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install caddy

# Edit Caddyfile
sudo nano /etc/caddy/Caddyfile
```
Paste configuration:
```caddy
skimms.yourdomain.com {
    reverse_proxy localhost:3000
}
```
Reload Caddy:
```bash
sudo systemctl reload caddy
```
Caddy will automatically fetch and renew Let's Encrypt TLS certificates on ports 80/443.

---

## Phase C: Firmware HTTPS with Root Certificate Verification

In production, prevent Man-in-the-Middle (MitM) attacks by checking the CA certificate instead of `setInsecure()`:

1. Obtain your domain's CA root certificate (e.g. ISRG Root X1 for Let's Encrypt).
2. In the ESP32 code, include `WiFiClientSecure`:
   ```cpp
   #include <WiFiClientSecure.h>

   const char* rootCACertificate = \
   "-----BEGIN CERTIFICATE-----\n" \
   "MIIFazCCA1OgAwIBAgIRAIIQz7DSQONZRGPgu2OCiwAwDQYJKoZIhvcNAQELBQAw\n" \
   "... (ISRG Root X1 Cert Pem) ...\n" \
   "-----END CERTIFICATE-----\n";

   WiFiClientSecure client;
   client.setCACert(rootCACertificate);
   ```

---

## Phase D: Telegram Bot Alerts & Gateway Integration

1. Message `@BotFather` on Telegram: `/newbot` -> Name: `SKIMMS_Safety_Bot`.
2. Save your API token (e.g. `789123456:AAFlk...`).
3. Create an emergency response group on Telegram, add the bot, and get the chat ID:
   ```bash
   curl https://api.telegram.org/bot<TOKEN>/getUpdates
   ```
4. Set the `WEBHOOK_URL` in `.env`:
   ```env
   WEBHOOK_URL=https://api.telegram.org/bot<TOKEN>/sendMessage?chat_id=<GROUP_ID>
   ```
5. Whenever a DANGER, CRITICAL, or ESCALATED incident occurs, the bot will automatically ping the rescue team with miner location, helmet ID, and sensor values.
