# Android SMS Gateway

Recreated from the original [anjlab/android-sms-gateway](https://github.com/anjlab/android-sms-gateway).  
Forked and enhanced from [ibnux/Android-SMS-Gateway](https://github.com/ibnux/Android-SMS-Gateway).  
Now uses Firebase to turn an Android device into an SMS/USSD gateway.

## How it works

Sending flow

1. You POST a send request to your server (see backend examples).
2. Server forwards the request to Firebase Cloud Messaging (FCM).
3. The app receives the FCM payload and sends SMS or initiates USSD.
4. The app posts sent/delivery status back to your server.

Receiving flow

1. The app receives incoming SMS and (optionally) filters/encrypts it.
2. It forwards messages to your configured server endpoint(s).

Streams

- Primary Receiver (Stream A): filtered and optionally AES-GCM encrypted payloads.
- Backup Receiver (Stream B): raw copy of every received SMS (no filters, no encryption).

## How to use

- Download APK from the releases page or build from source.
- The app requires a Firebase project; add your Android app to Firebase and place `google-services.json` in `app/` before building.
- Configure your server URL(s) and add the server API key where needed (see `backend/index.php`).
- The app exposes a "Your Secret" and a Device ID (FCM token) in `app/src/main/java/com/ibnux/smsgateway/Aplikasi.java` which the server expects for authenticated send requests.

## App Overview

The app provides two tabs:

### 1. Live Stream
Real-time view of SMS/USSD activity as it happens on the device.

### 2. Settings
Four configuration menus:

- **Push & USSD Messaging** — Manage your Secret ID, Device ID (FCM token), USSD test/permissions, push/USSD endpoints, request expiry, and WebSocket tunnel settings (alternative to Firebase push).
- **SMS Webhook** — Configure primary and backup receiver URLs, AES-GCM encryption, HMAC-SHA256 webhook signing, network timeout, and SMS filters (country codes, message prefix, message length).
- **Unified & System Logs** — Browse paginated audit logs of user actions and system failure/error logs; share or clear logs.
- **System Settings** — Set as default SMS app, manage inbox auto-delete/retention, configure live stream max entries and POST logging, disable battery optimization.

## API Documentation

- See the API spec at [Documents/API_Documentation.yaml](Documents/API_Documentation.yaml).

## Features

- Send SMS and initiate USSD via FCM push from server to device.
- Forward incoming SMS to server (Primary and Backup streams).
- Optional AES-GCM encryption for Primary stream.
- Optional HMAC-SHA256 webhook signing.
- Sent and Delivered status callbacks to server.
- Basic multi-SIM support (behavior depends on device/vendor).
- Retries for failed outgoing SMS (configurable in app).
- WebSocket tunnel as an alternative to Firebase push.

## USSD

USSD support requires accessibility permission to read and close USSD dialogs. Behavior varies by device and vendor; some phones may not be able to automatically close the USSD dialog.

## Building / Deploying

1. Create a Firebase project and add an Android app to obtain `google-services.json`.
2. Put `google-services.json` into the `app/` directory.
3. Edit `backend/index.php` to include your server key (if using the supplied backend) and adjust endpoints.
4. Build the app with Gradle (Android Studio recommended).

ObjectBox

When building you may see ObjectBox generated code errors — run a build once so ObjectBox can generate the model classes (see https://docs.objectbox.io/getting-started#generate-objectbox-code).

## Backend

The `backend/` folder contains a simple PHP example (`backend/index.php`) used to accept send requests and forward them to FCM. Customize it for your infrastructure and secure it appropriately.

## MQTT Version

An alternate MQTT-based implementation exists: https://github.com/ibnux/Android-SMS-Gateway-MQTT/

---

## License

Apache License 2.0 — see the `LICENSE` file included with the project.