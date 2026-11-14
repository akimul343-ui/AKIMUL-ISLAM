# SentinelGuard — Multi-Device Android Anti-Theft MDM & Render Flask Server

SentinelGuard is a free, open-source **Multi-Device Anti-Theft Management System** featuring a **Master Admin Console**, **Target Client Background Guard**, **Render Python Flask Fleet Server**, **Per-Device Unique SMS Triggers**, **Device Owner Uninstall Protection**, **Accessibility Lockdown**, and **Stealth App Icon Toggle**.

## Architecture

1. **Master Admin Console (`Remote` Tab / Flask Web Dashboard)**
   - Connects to your Render Flask server (`GET /api/devices?fleet_id=<FLEET_ID>`) to list all family/target devices with live battery %, Lock State (`LOCKED`/`UNLOCKED`), Armed status, Device Owner badge, and Google Maps coordinates.
   - Allows enrolling multiple target devices locally (by Device ID / IMEI, Nickname, Phone Number, and PIN) and sending per-device **LOCK** (with custom Owner Contact Info like `@Akimul0` and custom lock message), `UNLOCK`, `LOCATE`, `SIREN`, `GUARD_ON`, or `STOP_SIREN` via Flask (`POST /api/command`) or offline SMS.

2. **Target Client Mode**
   - Polls `/api/poll/<device_id>` in the background via `RemoteGuardService` (persisted across reboots via `BootCompletedReceiver`).
   - When the server or SMS command triggers `LOCK`, immediately launches a non-dismissible full-screen **`LockOverlayActivity`** displaying the owner's custom `contact_info` (e.g., Telegram `@Akimul0` or phone number) and lock message, blocking Back navigation and using `startLockTask()` when provisioned as Device Owner.
   - `BootCompletedReceiver` automatically restores background polling and re-engages `LockOverlayActivity` after reboot if `is_device_locked` is active locally or on the Flask server.
   - Responds to unique per-device offline SMS triggers:
     - `#LOCK_<DEVICE_ID>#` (e.g. `#LOCK_SG-8492A1#`)
     - `<DEVICE_ID>#<CMD>#<PIN>` (e.g. `SG-8492A1#LOCATE#2468`)
     - Custom instant lock keyword (`#LOCK_PHONE#`) or `<PREFIX>#<CMD>#<PIN>`
   - Blocks Power Menu, Phone Settings, Notification Shade / Quick Settings, and app-switching out of `LockOverlayActivity` via `PowerMenuGuardService` (`AccessibilityService`).
   - Supports Stealth Launcher Icon toggling (`PackageManager.COMPONENT_ENABLED_STATE_DISABLED` on `.LauncherAlias`) and ADB Device Owner uninstall locking (`dpm.setUninstallBlocked`).

3. **Flask Backend (`server/`) & GitHub Actions**
   - `server/app.py`, `server/requirements.txt`, `server/Procfile`, and `server/render.yaml` ready for Render deployment.
   - `.github/workflows/build-apk.yml` automatically builds the release/debug APK on push.
