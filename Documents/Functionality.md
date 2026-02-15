# Application Functionality Documentation

UI Layout:
This section explains the UI of this application
## 1. Layout Split
- **Description**: The layout is split with 2 tabs at the top.One tab is the live SMS Stream.Other tab is the Settings Tab that contains various settings.

## 2. Tab: Live SMS Stream
- **Description**: It contains a master switch to toggle the application on/off. It also has a button below it to clear Live Stream. (This corresponds to the older Clear Logs button). Below this is a live stream SMS log searchbar. Below the searchbar is the actual live stream SMS log.

## 3. Tab: Settings (No hamburger menu or overflow menu button required)
- **Description**: This contains 4 rectangular rounded buttons one below the other for different types of settings. Each button shows a menu when pressed. This menu can be closed with the close X, reverting back to showing all these 4 buttons. These menus are as follows:

### 3.1 Menu: Push & USDD messaging
- **Description**: This contains the following options.
    a)View and change secret ID in a field. This is secret ID for server to app communication. The app does not accept IP requests unless this secret ID is provided.Change secret ID button when clicked changes the secret ID as noted above.
    b) View device ID. This is the device ID allocated to mobile from server. Server could be Google Firebase or any other MQTT server communicating with the mobile. This cannot be set from the app.
    c) USSD Test: This opens up  small dialog box that allows the USSD code to be filled in and sent for a short USSD test.
    d) USSD Permission: This opens up the settings for requesting USSD permission.
    e) Push USSD URL: The Push USSD  URL is where the app responds to (unencrypted) on getting a push request or USSD feedback.
    f) View and Change expired: This controls the validity window for timestamped requests (default 3600s).

### 3.2 Menu: SMS Webhook
- **Description**: This contains the following options.
    a) View and Edit Reciever URL: This is a URL field that shows reciever URL. Receiver URL allows to recieve the filtered and encrypted POST, when the right SMS is recieved. Clicking on this will allow editing the URL. Ok and cancel buttons to be provided when editing the URL or setting it for the first time.
    b) Enable, view, Add, Clear all and Edit Allowed Country Code(s) filter for the SMSes to be forwarded to Receiver URL.
    c) Enable, view, Add, Clear all and Edit Allowed Prefix(s) filter for the SMSes to be forwarded to Receiver URL.
    d) Enable, view, Add, Clear all and Edit Allowed Message Length(s) filter for the SMSes to be forwarded to Receiver URL.
    e) View , Generate , Set and Copy AES-GCM secret key. This contains a viewer to see the AES-GCM key, generate new key button to generate new key, set key button to set the key shown on the viewer and copy button to copy the key to clipboard.
    f) View ,Edit and Save Backup URL: This shows the unfiltered and unencrypted Backup URL used to keep a record of all recieved SMSes.

### 3.3 Menu: Unified Log
- **Description**: This contains the following options.
    a) View Unified Log: This unified log keeps a record of various actions done by the user in Settings. This includes setting push secret key or setting webhook reciever_URL AES-GCM secret key. Also includes setting URLs (Push USSD, Reciever and Backup URLs) and any errors noted whilst communicating with these URLs. In addtion, any filters set for the Reciever_URL are also noted here. Auto-deletion actions are also recorded here.
    b) Share Log: Share log button allows log to be shared via Email/ Whatsapp etc etc as per normal sharing methods on Android.

### 3.4 Menu: System Settings
- **Description**: This contains the following options.  
    a) Set as Default Messaging App: This button on certain Android versions allows settings this app as default messaging app. A note should be provided here that it may not work with latest Android version. In this case, default messaging app should be set from Mobile settings menu.
    b) Auto-Delete SMSes. This is the existing Maintenance:Batch and Purge option.This should have a checkbox for enabling auto-deletion, retention hours to save and start auto-deletion button. If auto-deletion is selected with checkbox and a positive integer value (from 1 to 3000) in retention hours, then auto-deletion button is avaialbe to be pressed. Once pressed and smses have been deleted that were recieved before the retention period, a message shall be shown to user. The checkbox shall be unticked and the retention hours field reset to have no value.
    c) Disable or Enable Battery Optimization (Not sure if this functionality works in the app)


Key Terms:
This section explains the key functional components found in the application's menu and user interface.

## 1. Change Secret
- **Definition**: A unique identifier acting as an authentication token or password for the application instance.
- **Allocation**:
  - Automatically generated using `UUID.randomUUID()` when the application is first launched (in `Aplikasi.java`).
  - Can be manually regenerated by the user via the "Change Secret" menu option.
- **Usage**:
  - Acts as a security token when the server sends commands (like "Send SMS") to the app. The server request must include this secret to be authorized.
  - Displayed on the main screen so the user can copy it to their server configuration.
- **Reset**:
  - Via Menu > "Change Secret". This invalidates the old secret immediately.

## 2. Device ID (Token)
- **Definition**: The Firebase Cloud Messaging (FCM) Registration Token.
- **Allocation**:
  - Generated by Google's Firebase services when the app registers for push notifications.
  - The app retrieves this token via `PushService` (implied, though code snippet context often shows token handling).
- **Usage**:
  - Uniquely identifies the specific Android device to Google's servers.
  - Required by your backend server to target this specific phone when sending push notifications (to trigger SMS sending).
  - Displayed on the main screen ("Your Device ID").
- **Reset**:
  - Refreshed automatically by the Firebase SDK if the token changes (e.g., app reinstall, data clear).
  - The user can "Pull to refresh" on the main screen to attempt to retrieve/display the latest token.

## 3. Change Expired
- **Definition**: A security timeout setting (in seconds) for validating timestamped server requests.
- **Allocation**:
  - Default value is **3600 seconds** (1 hour).
  - User can customize this value via Menu > "Change expired".
- **Usage**:
  - When the server sends a request (like "Send SMS") using an MD5 signature (which includes a timestamp), the app checks if the request's timestamp is within this "Expired" window relative to the device's current time.
  - If `Current Time - Request Time > Expired Value`, the request is rejected as too old (preventing replay attacks).
- **Reset**:
  - Manually set by the user through the menu dialog. Minimum value enforced is 5 seconds.

## Summary Table

| Feature | Generated By | Modifiable by User? | Purpose |
| :--- | :--- | :--- | :--- |
| **Secret** | App (UUID) | Yes (Regenerate) | Authenticate Server $\rightarrow$ App requests |
| **Device ID** | Firebase (Google) | No (Read-only) | Address/Target device for Push Notifications |
| **Expired** | App Default (3600s) | Yes (Edit Value) | Validation window for timestamped requests |
