# Application Functionality & Architecture

## Overview
This Android SMS Gateway application acts as a bridge between SMS/USSD networks and HTTP webhooks. It allows incoming SMS to be forwarded to a server, and the server to send SMS/USSD via the device.

## Core Components

### 1. SMS Dispatcher & Gatekeeper
*   **Sequential Queue**: Uses a `SingleThreadExecutor` (via `PostQueueManager`) to process HTTP POST requests sequentially (one-by-one) to prevent server overload or race conditions.
*   **Gatekeeper**:
    *   **Master Switch**: Global On/Off toggle on the Live Stream tab.
    *   **Stream Toggles**: Individual enable/disable switches for Primary and Backup URL streams in Settings.
    *   **Filters**: SMS can be filtered by Country Code, Prefix, or Length before processing (Primary Stream only).
*   **Timeout**: User-configurable network timeout (seconds) applied to all HTTP requests.

### 2. Security & Encryption

#### A. AES-GCM Encryption (Payload)
*   **Algorithm**: AES/GCM/NoPadding, 256-bit key.
*   **Format**: Base64(IV + CipherText + Tag), where IV is 12 bytes.
*   Optional encryption for the Primary Stream.
    *   If Enabled: Sends `{ "payload": "ENCRYPTED_DATA", "is_encrypted": true }`.
    *   If Disabled: Sends raw JSON `{ ..., "is_encrypted": false }`.
*   **Key Management**: Built-in Key Generator (256-bit Base64) with View/Copy/Save capabilities. Keys are stored in `EncryptedSharedPreferences`.

#### B. HMAC-SHA256 Signing (Webhook Signature)
*   **Algorithm**: HMAC-SHA256 using a 256-bit key.
*   **Header**: `X-signature: <Base64-encoded HMAC-SHA256 digest of the raw request body>`
*   Optional signing applied to **all** HTTP POST requests (both Primary and Backup streams) when toggled on in Settings.
*   **Key Management**: Separate HMAC 256-bit Base64 key with Generate/Copy/Save capabilities. Keys stored in `EncryptedSharedPreferences`.
*   **Behavior**: If signing is enabled but the HMAC key is missing or signing fails, the POST is aborted (not sent).

### 3. Bifurcated Data Streams
The application implements a bifurcated data engine to handle SMS forwarding:

> **⚠️ Important:** The app uses the Primary Receiver URL and Backup URL **exactly as configured** in Settings — it does **NOT** automatically append any path suffix. For example, if you set the Backup URL to `https://x.com`, the app will POST directly to `https://x.com` (not to `https://x.com/api/sms/backup`). The paths shown in the API documentation (e.g., `/api/sms/receive`, `/api/sms/backup`) are **suggested server endpoint paths** that you should include in your full URL when configuring the app.

*   **Stream A (Primary)**:
    *   **Target**: `Receiver URL`.
    *   **Purpose**: Secure, filtered transaction processing.
    *   **Features**:
        *   Supports AES-GCM Encryption
        *   Supports HMAC-SHA256 Signing
        *   Supports Filters (Country Code, Message Prefix, Message Length)
    *   **Logging**: Failures log to System Log; Successes log to Live Stream (RAM) (when "Log Successful POSTs" is enabled).

*   **Stream B (Backup)**:
    *   **Target**: `Backup URL`.
    *   **Purpose**: Raw data archival / Catch-all.
    *   **Features**:
        *   Sends raw, unencrypted JSON.
        *   Bypasses all filters.
        *   Supports HMAC-SHA256 Signing (shared with Stream A).
    *   **Logging**: Failures log to System Log (via `BackupSendService`); Successes are intentionally not logged.

### 4. Logging System
The application maintains three distinct log types, accessible via the UI:

#### A. Live Stream (Main Page)
*   **Storage**: **Volatile RAM-only** (`LiveLogBuffer`). Cleared on app restart.
*   **Content**: Real-time view of transactions (SMS Received, SMS Sent, HTTP Success/Fail, USSD actions, Gateway status).
*   **Limits**: Self-truncating based on "Live Stream Max Entries" user setting (Default: 100, Minimum: 10).
*   **UI**: Searchable list with "Clear Log" button.

#### B. System Log (Settings -> Unified & System Logs)
*   **Storage**: **Persistent Text File** (`sms.gateway.log` at `context.getFilesDir()/logs/`).
*   **Content**: **Failures Only by default** (HTTP Errors, Timeouts, Send Failures) and critical system events (Heartbeats, HMAC errors, key changes). Successful transactions are NOT logged here to save space and reduce noise, unless "Log Successful POSTs" is enabled in Settings.
*   **UI**:
    *   Paginated view (50 lines per page, newest first).
    *   "Share File" button to export the full log file (via `FileProvider`).
    *   "Clear File" button to wipe the log.

#### C. Audit Log (Settings -> Unified & System Logs)
*   **Storage**: **Persistent Database** (`ActionLog` entity via ObjectBox).
*   **Content**: User actions and configuration changes (e.g., "Changed Secret ID", "Generated New Key", "Auto-Delete Run", "Updated Receiver URL", "Encryption Toggle", "HMAC Toggle").
*   **UI**:
    *   Paginated view (50 entries per page).
    *   "Share Page" button.
    *   "Clear DB" button.

### 5. Push & USSD Messaging (Server → Device)

#### A. Firebase Cloud Messaging (FCM) Push
*   The server sends SMS/USSD commands to the device via FCM.
*   **Send SMS**: Server posts to `backend/index.php` with `to` (phone number), `text` (message), `secret`, `deviceID` (FCM token).
*   **Initiate USSD**: Server posts with `to` set to a USSD code (e.g., `*888#`). The `text` field is ignored.
*   **Authentication**: `secret` parameter must match "Your Secret" generated by the app (UUID v4), displayed in Settings.

#### B. WebSocket Tunnel (Alternative to Firebase)
*   A persistent WebSocket connection can replace Firebase Push for receiving commands.
*   Configured via "WebSocket Server URL (wss://)" in Settings (Push & USSD Messaging).
*   Started/Stopped via a toggle switch in the same section.
*   When enabled, the server can send SMS/USSD commands through the WebSocket tunnel instead of FCM.

### 6. USSD Callback
*   USSD responses are captured via `UssdService` (Accessibility Service) and forwarded to the configured USSD URL.
*   **Fallback**: If USSD URL is empty, falls back to the Primary Receiver URL.
*   **Payload**: Sent as JSON via `SmsListener.sendPOST()` (application/json format).
*   **Type**: Always `"type": "ussd"`.

## Maintenance Tools
*   **Auto-Delete**: Deletes old SMS messages from the device inbox based on "Retention Hours". Can be run manually via "Start Auto-Delete".
*   **Inbox Capacity**: Checks and displays the current number of messages in the inbox.
*   **Battery Optimization**: Status-aware section showing whether battery optimization is disabled; provides shortcut to request disable or open system battery settings.

## UI Structure

### Tab 1: Live SMS Stream
*   Master Toggle (Gateway On/Off).
*   Clear Log Button.
*   Search Bar.
*   Live Log List (RAM-based, RecyclerView with pagination).
*   Swipe-to-refresh.

### Tab 2: Settings
*   **Push & USSD Messaging**: Secret ID, Device ID (FCM Token), USSD Test/Permissions, Push URL (FCM endpoint), Request Expiry, WebSocket Tunnel URL/Toggle.
*   **SMS Webhook**:
    *   **Security & Encryption**: Enable Encryption toggle (AES-GCM), Key Management (Generate/View/Copy/Save).
    *   **Webhook Signature (HMAC-SHA256)**: Enable HMAC Signing toggle, Key Management (Generate/Copy/Save).
    *   **Dispatcher & Gatekeeper**: Network Timeout, Primary URL Toggle & Input, Backup URL Toggle & Input.
    *   **Filters**: Country Code (multi-select dialog), Message Prefix, Message Length.
*   **Unified & System Logs**:
    *   **Audit Log**: View user actions (Paginated, 50/page), Share Page, Clear DB.
    *   **System Log**: View failures (Paginated, 50/page), Share File, Clear File.
*   **System Settings**: Default SMS App check, Inbox Maintenance (Auto-Delete hours, Start Auto-Delete, Check Capacity), Live Stream Max Entries, Log Successful POSTs toggle, Battery Optimization status.

## Endpoint Reference

### Stream A (Primary) — POST to Receiver URL
```
POST /your-receiver-endpoint HTTP/1.1
Host: your-server.com
Content-Type: application/json

{
  "from": "+1234567890",
  "message": "Hello World",
  "type": "received",
  "timestamp": "1678886400000"
}
```
**With Encryption Enabled:**
```
POST /your-receiver-endpoint HTTP/1.1
Host: your-server.com
Content-Type: application/json

{
  "is_encrypted": true,
  "payload": "VGhpcyBpcyBhbiBleGFtcGxlIGVuY3J5cHRlZCBwYXlsb2Fk..."
}
```
**With HMAC-SHA256 Signing Enabled (Header added to all requests):**
```
X-signature: <Base64 HMAC-SHA256 digest of the request body>
```

### Stream B (Backup) — POST to Backup URL
```
POST /your-backup-endpoint HTTP/1.1
Host: your-server.com
Content-Type: application/json

{
  "from": "+1234567890",
  "message": "Hello World",
  "type": "received",
  "timestamp": "1678886400000"
}
```
*Note: Backup stream always sends raw/unencrypted JSON and bypasses all filters.*
> **⚠️ Important:** The Backup URL is used verbatim from Settings. If your server expects requests at `https://x.com/api/sms/backup`, you must enter the **complete URL** `https://x.com/api/sms/backup` in the Backup URL field — the app does not append any path automatically.

## Important URL Usage Note
The Primary Receiver URL and Backup URL fields in the app's Settings are used **exactly as entered** — no path, query parameter, or suffix is appended automatically. The endpoint paths listed below (e.g., `/api/sms/receive`, `/api/sms/backup`) are suggestions for your server implementation. When configuring the app, you must provide the **full URL** including any path segments.

For example, to use the suggested endpoints:
- **Primary URL**: `https://your-server.com/api/sms/receive`
- **Backup URL**: `https://your-server.com/api/sms/backup`

## Curl Test Examples

### 1. Primary Receiver (Stream A) — Unencrypted
```bash
curl -X POST https://your-server.com/api/sms/receive \
  -H "Content-Type: application/json" \
  -d '{
    "is_encrypted": false,
    "from": "+1234567890",
    "message": "Test SMS from curl",
    "type": "received",
    "timestamp": "1678886400000"
  }'
```

### 2. Primary Receiver (Stream A) — Encrypted
```bash
curl -X POST https://your-server.com/api/sms/receive \
  -H "Content-Type: application/json" \
  -d '{
    "is_encrypted": true,
    "payload": "BASE64_ENCODED_IV_CIPHERTEXT_TAG"
  }'
```

### 3. Primary Receiver (Stream A) — With HMAC-SHA256 Signature
```bash
# First, generate the HMAC signature of the request body:
# echo -n '<request-body>' | openssl dgst -sha256 -hmac '<base64-decoded-hmac-key>' -binary | base64 -w0

curl -X POST https://your-server.com/api/sms/receive \
  -H "Content-Type: application/json" \
  -H "X-signature: GENERATED_HMAC_SIGNATURE" \
  -d '{
    "is_encrypted": false,
    "from": "+1234567890",
    "message": "Test SMS with HMAC",
    "type": "received",
    "timestamp": "1678886400000"
  }'
```

### 4. Backup Receiver (Stream B)
```bash
curl -X POST https://your-server.com/api/sms/backup \
  -H "Content-Type: application/json" \
  -d '{
    "from": "+1234567890",
    "message": "Test SMS to backup stream",
    "type": "received",
    "timestamp": "1678886400000"
  }'
```

### 5. USSD Response Receiver
```bash
curl -X POST https://your-server.com/api/sms/ussd \
  -H "Content-Type: application/json" \
  -d '{
    "from": "ussd",
    "message": "Your balance is $10.00",
    "type": "ussd",
    "timestamp": "1678886400000"
  }'
```
*Note: The `from` field may include SIM info (e.g., `*888#1`).*

### 6. Send SMS / Initiate USSD (Server → Device via FCM)
```bash
curl -X POST https://your-server.com/api/sms/send \
  -d "to=+1234567890" \
  -d "text=Hello from server" \
  -d "secret=YOUR_SECRET_KEY" \
  -d "deviceID=YOUR_FCM_TOKEN" \
  -d "sim=1"
```
*Note: This uses `application/x-www-form-urlencoded` format, sent to your backend which forwards to FCM.*