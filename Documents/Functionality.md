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
*   **AES-GCM Encryption**: Optional encryption for the Primary Stream.
    *   If Enabled: Sends `{ "payload": "ENCRYPTED_DATA", "is_encrypted": true }`.
    *   If Disabled: Sends raw JSON `{ ..., "is_encrypted": false }`.
*   **Key Management**: Built-in Key Generator (256-bit Base64) with View/Copy/Save capabilities.

### 3. Bifurcated Data Streams
The application implements a bifurcated data engine to handle SMS forwarding:

*   **Stream A (Primary)**:
    *   **Target**: `Receiver URL`.
    *   **Purpose**: Secure, filtered transaction processing.
    *   **Features**: Supports AES Encryption, Filters (Country/Prefix/Length).
    *   **Logging**: Failures log to System Log; Successes log to Live Stream (RAM).

*   **Stream B (Backup)**:
    *   **Target**: `Backup URL`.
    *   **Purpose**: Raw data archival / Catch-all.
    *   **Features**: Sends raw, unencrypted JSON. Bypasses all filters.
    *   **Logging**: Failures log to System Log; Successes log to Live Stream (RAM).

### 4. Logging System
The application maintains three distinct log types, accessible via the UI:

#### A. Live Stream (Main Page)
*   **Storage**: **Volatile RAM-only** (`LiveLogBuffer`). Cleared on app restart.
*   **Content**: Real-time view of **ALL** transactions (SMS Received, SMS Sent, HTTP Success/Fail, USSD actions).
*   **Limits**: Self-truncating based on "Live Stream Max Entries" user setting (Default: 100).
*   **UI**: Searchable list with "Clear Log" button.

#### B. System Log (Settings -> Unified & System Logs)
*   **Storage**: **Persistent Text File** (`sms.gateway.log`).
*   **Content**: **Failures Only** (HTTP Errors, Timeouts, Send Failures) and critical system events (Heartbeats). Successful transactions are NOT logged here to save space and reduce noise.
*   **UI**:
    *   Paginated view (50 lines per page, newest first).
    *   "Share File" button to export the full log file.
    *   "Clear File" button to wipe the log.

#### C. Audit Log (Settings -> Unified & System Logs)
*   **Storage**: **Persistent Database** (`ActionLog` entity via ObjectBox).
*   **Content**: User actions and configuration changes (e.g., "Changed Secret ID", "Generated New Key", "Auto-Delete Run", "Updated Receiver URL").
*   **UI**:
    *   Paginated view (50 entries per page).
    *   "Share Page" button.
    *   "Clear DB" button.

## Maintenance Tools
*   **Auto-Delete**: Deletes old SMS messages from the device inbox based on "Retention Hours". Can be run manually via "Start Auto-Delete".
*   **Inbox Capacity**: Checks and displays the current number of messages in the inbox.
*   **Battery Optimization**: Shortcut to disable battery optimization for reliable background operation.

## UI Structure

### Tab 1: Live SMS Stream
*   Master Toggle (Gateway On/Off).
*   Clear Log Button.
*   Search Bar.
*   Live Log List (RAM-based).

### Tab 2: Settings
*   **Push & USSD Messaging**: Secret ID, Device ID, USSD Test/Permissions, Push URL.
*   **SMS Webhook**:
    *   **Security & Encryption**: Enable Encryption toggle, Key Management (Generate/View/Copy/Save).
    *   **Dispatcher & Gatekeeper**: Network Timeout, Primary/Backup URL Toggles & Inputs.
    *   **Filters**: Country Code, Prefix, Length.
*   **Unified & System Logs**:
    *   **Audit Log**: View user actions (Paginated), Share, Clear.
    *   **System Log**: View failures (Paginated), Share File, Clear File.
*   **System Settings**: Default App check, Inbox Maintenance (Auto-Delete, Capacity), Battery Optimization.
