# EnFait (Android alpha)

Version francaise: `README.md`

## Table of Contents

- [Current status (alpha)](#current-status-alpha)
- [What this alpha does not do yet](#what-this-alpha-does-not-do-yet)
- [Architecture (summary)](#architecture-summary)
- [Build APK (debug)](#build-apk-debug)
- [Online build (GitHub Actions)](#online-build-github-actions)
- [Install (testing)](#install-testing)
- [Quick test (2 Android phones)](#quick-test-2-android-phones)
- [HTTP relay (optional)](#http-relay-optional)
- [Android permissions (alpha)](#android-permissions-alpha)
- [Message channels and statuses](#message-channels-and-statuses)
- [Bluetooth and BLE behavior](#bluetooth-and-ble-behavior)
- [Known issues / common messages](#known-issues--common-messages)
- [Security (alpha state)](#security-alpha-state)
- [Recommended roadmap (next steps)](#recommended-roadmap-next-steps)
- [Useful files](#useful-files)

Android alpha prototype of a decentralized messaging app, without a central trusted server.

The project targets a "Signal-like" UX on Android, with local peer-to-peer transports, optional relay, and an architecture prepared for real E2E encryption (X3DH / Double Ratchet).

## Current status (alpha)

This alpha is usable for real testing on 2 Android phones.

Available features:
- modern Compose chat UI
- local identity (device-generated key pair)
- editable/persistent local alias
- identity QR generation + contact QR scan/import
- imported contacts list + target selection
- text messages + attachment metadata (name/type/size)
- encrypted local message storage (AES/GCM via Android Keystore)
- delivery/read receipts
- Android notifications (foreground/background)
- hybrid transport:
  - LAN Wi-Fi (UDP) for local exchange
  - classic Bluetooth (RFCOMM) for local exchange
  - optional HTTP relay (alpha store-and-forward)
- improved Bluetooth discovery:
  - BLE discovery filtered by EnFait UUID (to reduce TVs/headsets/etc.)
  - non-mobile device filtering
- channel indicator on messages/receipts (`Wi-Fi`, `BT`, `relay`, `Tor`, `local`)

## What this alpha does not do yet

- no real Signal-like E2E encryption (X3DH / Double Ratchet)
  - encryption abstraction exists, current engine is placeholder
- no groups
- no multi-device sync
- no real binary file transfer (attachment metadata only)
- no native Internet P2P transport (HTTP relay only, optional)
- no Play Store release/signing pipeline

## Architecture (summary)

### Identity
- local key + fingerprint
- QR code for identity/contact exchange

### Crypto
- encrypted local storage (Keystore + AES/GCM)
- E2E abstraction (`E2ECipherEngine`) ready to be replaced

### Transport
- `LanUdpMeshTransport` (local Wi-Fi)
- `BluetoothMeshTransport` (RFCOMM) + BLE for discovery/filtering
- `RelayHttpMeshTransport` (optional)
- `HybridMeshTransport` aggregator

### Persistence
- local messages and contacts
- alpha hardening already added:
  - field validation
  - size caps
  - retention/rotation
  - local JSON backup fallback

## Build APK (debug)

This repo does not include `gradlew` yet, so use Android Studio or a locally installed `gradle`.

```bash
gradle assembleDebug
```

Expected APK:
- `app/build/outputs/apk/debug/app-debug.apk`

## Online build (GitHub Actions)

Workflow:
- `.github/workflows/android-debug-apk.yml`

Triggers:
- push on `main` or `alpha`
- pull requests

Output:
- downloadable `app-debug-apk` artifact in GitHub Actions

## Install (testing)

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Quick test (2 Android phones)

### LAN chat over Wi-Fi
1. Install the app on both phones
2. Connect both phones to the same Wi-Fi
3. Open the app on both devices
4. Check LAN peer discovery
5. Select a peer and send a message

### Bluetooth chat
1. Grant Bluetooth permissions (scan/connect/advertise)
2. Switch to `Bluetooth` mode in `Settings > Offline transport`
3. Check visible peers are mobile EnFait devices (BLE/filtering)
4. Send a message

Notes:
- BLE discovery is used for filtering (EnFait UUID)
- actual message exchange still uses classic Bluetooth (RFCOMM) in this alpha

## HTTP relay (optional)

Included test relay:
- `relay/minirelay.py`

### Start relay (without auth)
```bash
python3 relay/minirelay.py
```

### Start relay (with HMAC auth)
```bash
AUFAIT_RELAY_SHARED_SECRET=mysecret python3 relay/minirelay.py
```

### App side (relay secret)
In the app:
- `Settings > Relay / Tor`
- field `Secret relay (HMAC)`
- `Save`

If empty:
- client HMAC auth is disabled

If set:
- relay `push/pull` requests are signed (HMAC-SHA256)

### Enable relay in code (alpha)
In `app/src/main/java/com/aufait/alpha/data/AlphaChatContainer.kt`:
- set `enabled = true`
- configure `relayUrl`
  - `10.0.2.2` for Android emulator
  - your PC LAN IP for real phones

## Android permissions (alpha)

Used permissions:
- `INTERNET`
- `POST_NOTIFICATIONS`
- `CAMERA`
- `BLUETOOTH_CONNECT`
- `BLUETOOTH_SCAN`
- `BLUETOOTH_ADVERTISE`
- `ACCESS_FINE_LOCATION` (Android <= 30 compatibility)

UX behavior:
- Bluetooth permission is requested on demand (only when user selects a Bluetooth-capable mode)
- camera permission is requested only for QR scanning

## Message channels and statuses

The UI shows channel info discreetly:
- inbound message: receive channel (`Wi-Fi`, `BT`, `relay`, `Tor`, `local`)
- outbound message: receipt return channel (`delivered` / `read`) when available

Alpha reliability improvements included:
- multi-channel message/receipt deduplication
- merged receipt metadata (time + channel)
- more monotonic statuses (less UI regression)

## Bluetooth and BLE behavior

### Why BLE discovery
To avoid showing unrelated devices such as:
- TVs
- headsets / speakers
- other peripherals

### What is filtered
- BLE scan on EnFait UUID
- non-mobile Bluetooth classes filtered out
- `EnFait-` prefix used as an additional filter for classic discovery

### Devices already paired with the phone
They remain paired at Android system level, but:
- they are not shown in EnFait unless they match EnFait discovery/filter criteria

## Known issues / common messages

### BLE advertise error 1
Likely cause:
- BLE advertising payload too large (`ADVERTISE_FAILED_DATA_TOO_LARGE`)

Fix already applied:
- BLE advertising reduced to EnFait UUID only (no `serviceData`)

### BT error read failed read ret minus 1
Usually a normal Bluetooth socket closure.

Fix already applied:
- benign socket closure errors (EOF / reset / broken pipe) are filtered and should not remain as persistent red errors.

## Security (alpha state)

Already implemented:
- encrypted local storage
- validation / size caps for QR, contacts, attachment metadata
- HTTP relay:
  - optional HMAC request signing
  - anti-replay (timestamp + nonce)
  - basic rate limiting
  - size / queue caps

Main security debt:
- no real message E2E encryption yet (X3DH / Double Ratchet)

## Recommended roadmap (next steps)

1. Real E2E encryption (X3DH then Double Ratchet)
2. Relay URL configurable from UI (not hardcoded in `AlphaChatContainer`)
3. Real binary attachment transfer (images, then documents)
4. BLE discovery + stronger EnFait handshake
5. More robust Internet transport (persistent store-and-forward relay)

## Useful files

- `app/src/main/java/com/aufait/alpha/data/AlphaChatContainer.kt`
- `app/src/main/java/com/aufait/alpha/data/ChatService.kt`
- `app/src/main/java/com/aufait/alpha/data/LanUdpMeshTransport.kt`
- `app/src/main/java/com/aufait/alpha/data/BluetoothMeshTransport.kt`
- `app/src/main/java/com/aufait/alpha/data/RelayHttpMeshTransport.kt`
- `relay/minirelay.py`
- `.github/workflows/android-debug-apk.yml`
