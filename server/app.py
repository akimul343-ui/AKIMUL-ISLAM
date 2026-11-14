"""
SentinelGuard Master-Slave Anti-Theft Flask Backend Server (Render Free Tier Ready)

Endpoints:
  GET  /health
  GET  /api/devices?fleet_id=<FLEET_ID>
  POST /api/command     (Lock/Unlock with custom contact_info like @Akimul0 or phone number)
  GET  /api/poll/<device_id>  (Returns lock_state, is_locked, contact_info, lock_message, and commands)
  POST /api/location
  GET  /api/status/<device_id>
  GET  /
"""

import hashlib
import os
from datetime import datetime, timezone
from flask import Flask, jsonify, render_template_string, request

app = Flask(__name__)

# In-memory multi-device store (use PostgreSQL/SQLite for persistent disk storage across restarts)
DEVICES = {}
COMMAND_QUEUE = {}

VALID_COMMANDS = {"LOCK", "UNLOCK", "LOCATE", "SIREN", "STOP_SIREN", "GUARD_ON", "GUARD_OFF"}


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def hash_pin(pin: str) -> str:
    return hashlib.sha256(pin.strip().encode("utf-8")).hexdigest()


def ensure_device(
    device_id: str,
    pin: str,
    device_name: str = "Android Device",
    device_alias: str = "",
    fleet_id: str = "FAMILY-FLEET",
) -> tuple[dict, bool]:
    key = device_id.strip().upper()
    pin_digest = hash_pin(pin)
    clean_fleet = (fleet_id or "FAMILY-FLEET").strip().upper()
    existing = DEVICES.get(key)

    if existing is None:
        DEVICES[key] = {
            "device_id": key,
            "device_name": device_name,
            "device_alias": device_alias or device_name,
            "fleet_id": clean_fleet,
            "pin_hash": pin_digest,
            "guard_armed": False,
            "is_locked": False,
            "lock_state": "UNLOCKED",
            "contact_info": "",
            "lock_message": "This device is lost or protected by SentinelGuard. Please contact the owner immediately.",
            "is_device_owner": False,
            "battery_percent": -1,
            "last_seen": utc_now(),
            "last_location": None,
        }
        COMMAND_QUEUE.setdefault(key, [])
        return DEVICES[key], True

    if existing["pin_hash"] != pin_digest:
        return existing, False

    existing["last_seen"] = utc_now()
    if device_name:
        existing["device_name"] = device_name
    if device_alias:
        existing["device_alias"] = device_alias
    if fleet_id:
        existing["fleet_id"] = clean_fleet
    return existing, True


@app.get("/health")
def health():
    return jsonify({
        "status": "ok",
        "service": "SentinelGuard-MasterSlave-Flask",
        "registered_devices": len(DEVICES),
        "timestamp": utc_now(),
    })


@app.get("/api/devices")
def list_fleet_devices():
    fleet_filter = request.args.get("fleet_id", "").strip().upper()
    result = []
    for dev in DEVICES.values():
        if fleet_filter and dev.get("fleet_id", "") != fleet_filter:
            continue
        result.append({
            "device_id": dev["device_id"],
            "device_name": dev["device_name"],
            "device_alias": dev.get("device_alias", dev["device_name"]),
            "fleet_id": dev.get("fleet_id", "FAMILY-FLEET"),
            "guard_armed": dev.get("guard_armed", False),
            "is_locked": dev.get("is_locked", False),
            "lock_state": dev.get("lock_state", "UNLOCKED"),
            "contact_info": dev.get("contact_info", ""),
            "lock_message": dev.get("lock_message", ""),
            "is_device_owner": dev.get("is_device_owner", False),
            "battery_percent": dev.get("battery_percent", -1),
            "last_seen": dev.get("last_seen", ""),
            "pending_commands": len(COMMAND_QUEUE.get(dev["device_id"], [])),
            "last_location": dev.get("last_location"),
        })
    result.sort(key=lambda item: item["last_seen"], reverse=True)
    return jsonify({"fleet_id": fleet_filter or "ALL", "devices": result}), 200


@app.post("/api/location")
def receive_location():
    data = request.get_json(silent=True) or {}
    device_id = str(data.get("device_id", "")).strip().upper()
    pin = str(data.get("pin", "")).strip()
    latitude = data.get("latitude")
    longitude = data.get("longitude")

    if not device_id or not pin or latitude is None or longitude is None:
        return jsonify({"error": "device_id, pin, latitude, and longitude are required."}), 400

    device, authorized = ensure_device(
        device_id=device_id,
        pin=pin,
        device_name=str(data.get("device_name", "Android Device")),
        device_alias=str(data.get("device_alias", "")),
        fleet_id=str(data.get("fleet_id", "FAMILY-FLEET")),
    )
    if not authorized:
        return jsonify({"error": "Invalid Secret PIN."}), 403

    battery = int(data.get("battery_percent", -1))
    location_payload = {
        "latitude": float(latitude),
        "longitude": float(longitude),
        "accuracy_meters": float(data.get("accuracy_meters", 0.0)),
        "battery_percent": battery,
        "provider": str(data.get("provider", "fused")),
        "maps_url": f"https://maps.google.com/?q={latitude},{longitude}",
        "reported_at": utc_now(),
    }
    device["last_location"] = location_payload
    if battery >= 0:
        device["battery_percent"] = battery
    device["last_seen"] = utc_now()

    return jsonify({"status": "updated", "device_id": device_id, "location": location_payload}), 200


@app.post("/api/command")
def queue_command():
    data = request.get_json(silent=True) or {}
    device_id = str(data.get("device_id", "")).strip().upper()
    pin = str(data.get("pin", "")).strip()
    command = str(data.get("command", "")).strip().upper()
    contact_info = str(data.get("contact_info", "")).strip()
    lock_message = str(data.get("lock_message", "")).strip()

    if not device_id or not pin or command not in VALID_COMMANDS:
        return jsonify({
            "error": f"device_id, pin, and valid command ({', '.join(sorted(VALID_COMMANDS))}) required."
        }), 400

    device = DEVICES.get(device_id)
    if device is None:
        return jsonify({"error": f"Device {device_id} not registered yet. Target must poll once first."}), 404
    if device["pin_hash"] != hash_pin(pin):
        return jsonify({"error": f"Invalid Secret PIN for {device_id}."}), 403

    if command == "LOCK":
        device["is_locked"] = True
        device["lock_state"] = "LOCKED"
        device["guard_armed"] = True
        if contact_info:
            device["contact_info"] = contact_info
        if lock_message:
            device["lock_message"] = lock_message
    elif command == "UNLOCK":
        device["is_locked"] = False
        device["lock_state"] = "UNLOCKED"

    cmd_entry = {
        "id": f"cmd_{int(datetime.now(timezone.utc).timestamp() * 1000)}",
        "command": command,
        "contact_info": device.get("contact_info", ""),
        "lock_message": device.get("lock_message", ""),
        "issued_by": str(data.get("issued_by", "master_admin")),
        "created_at": utc_now(),
    }
    COMMAND_QUEUE.setdefault(device_id, []).append(cmd_entry)

    return jsonify({
        "status": "queued",
        "device_id": device_id,
        "command": command,
        "lock_state": device["lock_state"],
        "contact_info": device.get("contact_info", ""),
        "command_id": cmd_entry["id"],
    }), 200


@app.get("/api/poll/<device_id>")
def poll_commands(device_id: str):
    pin = request.args.get("pin", "").strip()
    device_name = request.args.get("device_name", "Android Device").strip()
    device_alias = request.args.get("device_alias", "").strip()
    fleet_id = request.args.get("fleet_id", "FAMILY-FLEET").strip().upper()
    guard_armed = request.args.get("guard_armed", "false").lower() == "true"
    is_device_owner = request.args.get("is_device_owner", "false").lower() == "true"
    battery_raw = request.args.get("battery_percent", "-1").strip()

    if not pin:
        return jsonify({"error": "pin query parameter is required."}), 400

    key = device_id.strip().upper()
    device, authorized = ensure_device(
        device_id=key,
        pin=pin,
        device_name=device_name,
        device_alias=device_alias,
        fleet_id=fleet_id,
    )
    if not authorized:
        return jsonify({"error": "Invalid Secret PIN."}), 403

    device["guard_armed"] = guard_armed or device.get("is_locked", False)
    device["is_device_owner"] = is_device_owner
    try:
        bat = int(battery_raw)
        if bat >= 0:
            device["battery_percent"] = bat
    except ValueError:
        pass
    device["last_seen"] = utc_now()

    pending = COMMAND_QUEUE.get(key, [])
    COMMAND_QUEUE[key] = []

    return jsonify({
        "device_id": key,
        "is_locked": device.get("is_locked", False),
        "lock_state": device.get("lock_state", "UNLOCKED"),
        "contact_info": device.get("contact_info", ""),
        "lock_message": device.get("lock_message", ""),
        "commands": pending,
        "server_time": utc_now(),
    }), 200


@app.get("/api/status/<device_id>")
def device_status(device_id: str):
    pin = request.args.get("pin", "").strip()
    key = device_id.strip().upper()
    device = DEVICES.get(key)
    if device is None:
        return jsonify({"error": "Device not found."}), 404
    if device["pin_hash"] != hash_pin(pin):
        return jsonify({"error": "Invalid Secret PIN."}), 403

    return jsonify({
        "device_id": device["device_id"],
        "device_name": device["device_name"],
        "device_alias": device.get("device_alias", device["device_name"]),
        "fleet_id": device.get("fleet_id", "FAMILY-FLEET"),
        "guard_armed": device["guard_armed"],
        "is_locked": device.get("is_locked", False),
        "lock_state": device.get("lock_state", "UNLOCKED"),
        "contact_info": device.get("contact_info", ""),
        "lock_message": device.get("lock_message", ""),
        "is_device_owner": device.get("is_device_owner", False),
        "battery_percent": device.get("battery_percent", -1),
        "last_seen": device["last_seen"],
        "pending_commands": len(COMMAND_QUEUE.get(key, [])),
        "last_location": device["last_location"],
    }), 200


@app.get("/")
def console():
    return render_template_string("""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>SentinelGuard Master-Slave MDM Console</title>
  <style>
    body{font-family:system-ui,-apple-system,sans-serif;background:#070c18;color:#e2e8f0;margin:0;padding:24px}
    .wrap{max-width:760px;margin:0 auto;display:grid;gap:16px}
    .card{background:#131d33;border:1px solid #28354f;border-radius:16px;padding:20px}
    h1{margin:0 0 8px;color:#00e5ff;font-size:1.4rem}
    label{display:block;margin:10px 0 4px;font-size:.82rem;color:#94a3b8}
    input{width:100%;box-sizing:border-box;padding:10px 12px;border-radius:10px;border:1px solid #334155;background:#0d1526;color:#f8fafc}
    .row{display:flex;gap:10px;flex-wrap:wrap;margin-top:12px}
    button{padding:10px 14px;border:none;border-radius:10px;font-weight:600;cursor:pointer;background:#00e5ff;color:#070c18}
    button.danger{background:#ef4444;color:#fff}
    button.ok{background:#10b981;color:#070c18}
    button.warn{background:#f59e0b;color:#111827}
    button.sec{background:#1b2845;color:#e2e8f0}
    .dev{border:1px solid #28354f;border-radius:12px;padding:12px;margin-top:10px;background:#0d1526}
    pre{background:#020617;padding:12px;border-radius:10px;overflow:auto;font-size:.8rem;border:1px solid #1e293b}
  </style>
</head>
<body>
  <div class="wrap">
    <div class="card">
      <h1>SentinelGuard Master-Slave MDM Console</h1>
      <label>Fleet ID</label>
      <div class="row">
        <input id="fleet" value="FAMILY-FLEET" style="flex:1" />
        <button class="sec" onclick="loadFleet()">Load Fleet</button>
      </div>
      <div id="fleetList"></div>
    </div>
    <div class="card">
      <label>Target Device ID</label>
      <input id="dev" placeholder="e.g. SG-8492A1" />
      <label>Target Secret PIN</label>
      <input id="pin" type="password" placeholder="Enter target device PIN" />
      <label>Owner Contact Info (Phone / Telegram e.g. @Akimul0)</label>
      <input id="contact" placeholder="@Akimul0 or +8801XXXXXXXXX" />
      <div class="row">
        <button class="danger" onclick="sendCmd('LOCK')">LOCK DEVICE</button>
        <button class="ok" onclick="sendCmd('UNLOCK')">UNLOCK</button>
        <button onclick="sendCmd('LOCATE')">Locate GPS</button>
        <button class="warn" onclick="sendCmd('SIREN')">Sound Siren</button>
        <button class="sec" onclick="checkStatus()">Check Status</button>
      </div>
      <label>Response</label>
      <pre id="out">Ready.</pre>
    </div>
  </div>
  <script>
    async function loadFleet(){
      const fleet=encodeURIComponent(document.getElementById('fleet').value.trim());
      const r=await fetch(`/api/devices?fleet_id=${fleet}`);
      const data=await r.json();
      const box=document.getElementById('fleetList');
      if(!data.devices || data.devices.length===0){box.innerHTML='<p style="color:#94a3b8">No devices in this fleet yet.</p>';return;}
      box.innerHTML=data.devices.map(d=>`<div class="dev">
        <b>${d.device_alias}</b> (<code>${d.device_id}</code>) • State: <b>${d.lock_state}</b> • Bat: ${d.battery_percent}%<br/>
        <small>Contact: ${d.contact_info || 'None'} • Last seen: ${d.last_seen}</small><br/>
        ${d.last_location?`<a style="color:#00e5ff" target="_blank" href="${d.last_location.maps_url}">Open Google Maps</a> `:''}
        <button class="sec" style="margin-top:6px" onclick="document.getElementById('dev').value='${d.device_id}'">Select</button>
      </div>`).join('');
    }
    async function sendCmd(command){
      const device_id=document.getElementById('dev').value.trim();
      const pin=document.getElementById('pin').value.trim();
      const contact_info=document.getElementById('contact').value.trim();
      const r=await fetch('/api/command',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({device_id,pin,command,contact_info})});
      document.getElementById('out').textContent=JSON.stringify(await r.json(),null,2);
    }
    async function checkStatus(){
      const dev=encodeURIComponent(document.getElementById('dev').value.trim());
      const pin=encodeURIComponent(document.getElementById('pin').value.trim());
      const r=await fetch(`/api/status/${dev}?pin=${pin}`);
      document.getElementById('out').textContent=JSON.stringify(await r.json(),null,2);
    }
  </script>
</body>
</html>""")


if __name__ == "__main__":
    port = int(os.getenv("PORT", "5000"))
    app.run(host="0.0.0.0", port=port)
