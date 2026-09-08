# Vixz Cross-Device Sync Server

Lightweight, self-hosted synchronization server for Vixz (Windows Desktop & Android app).
Synchronizes your viewed videos, watch progress, favorites, and algorithm preferences so you never watch a video twice across PC and Phone.

---

## 🚀 Quick Start on Your VPS

### Option A: Direct Python (Zero Dependencies)
Runs on any Linux/Windows VPS with Python 3.8+:

```bash
# Clone or copy server files to your VPS
git clone https://github.com/Viguru24/YouTube.git
cd YouTube/server

# Run directly on port 8088 (optional: specify an auth token)
python3 sync_server.py --port 8088 --token "your-secret-token"
```

### Option B: Docker / Docker Compose (Recommended)
```bash
cd server
docker compose up -d
```

### Option C: Systemd Background Service (Linux VPS)
Create `/etc/systemd/system/vixz-sync.service`:
```ini
[Unit]
Description=Vixz Sync Server
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/vixz/server
ExecStart=/usr/bin/python3 /opt/vixz/server/sync_server.py --port 8088 --token "your-secret-token"
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```
Enable and start:
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now vixz-sync
```

---

## 📱 Connecting Your Devices

In both the **PC Desktop App** and **Phone Android App**:
1. Open **Settings**.
2. Scroll to **VPS Cross-Device Sync**.
3. Enter your VPS address (e.g. `http://your-vps-ip:8088` or `https://sync.yourdomain.com`).
4. Enter your secret token (if configured).
5. Click/Tap **Test Connection & Sync**.
