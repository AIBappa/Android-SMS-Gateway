Updates from AIBappa

Prompt 1
For this repo, the input is an SMS and the output is a web POST to a URL that is specified in settings. I want to install some filters in the settings. 1) Allowed country codes. Only those SMSese from allowed country codes should be sent to the URL . On clicking allowed country codes in settings, user can see allowed ones on UI page, plus be able to search for country codes and add them accordingly. 2) Allowed SMS message prefix. User should be able to set an allowed SMS message prefix or prefixes. Only those SMSes with allowed prefixes should be sent to the URL . User should be able to type in the prefix using keys and can add in one or more prefixes to the list. This will allow only those SMSes in which the SMS message begins with that appropriate prefix(s) to be sent to the URL 3) Allowed SMS Message Lengths. The user should be able to select the allowed SMS lengths (numbers). Only those messages fit a certain length or lengths as specified in settings will be sent in the URL 4) It shall be individually possible via settings to set/reset these 3 filters.

Prompt 2 (Yet to be given)
The receiver_URL POST shall be encoded with AES-GCM. It shall be possible to set the shared secret key via settings.

Prompt 3 (Yet to be given)
I want another backup_URL in addition to the existing menu_set_url(I will also call this receiver_URL). The current filters for allowed country code(s), allowed prefix(s) and allowed length(s) will not be applicable for this backup URL. It should send all SMSes that were received to backup URL. Create a new backupsendservice.java for this in the layanan folder.

Updates to prompt 2 + 3 + 6 (logging)
Role: Systems Architect

Objective: Implement a Bifurcated Data Engine with AES-GCM Encryption and Secure Key Management.

1. Secure Key Management UI (Inside Settings):

Field: "Shared Secret Key" (Non-editable).

Button 1: "Generate New Key": Create a 256-bit key (Base64). Generating new key does not impact the log.

Button 2: "Use This Key": * Activate the key and save to SharedPreferences.

Log Event: Write to sms.gateway.log: [SECURITY] NEW_KEY_SET: Key [Base64] was activated.

Button 3: "Copy to Clipboard": Copies the active key.

2. The Dispatcher (The Splitter):

Modify the SmsListener to split outbound data into two parallel, independent streams.

Stream A (Primary): Apply filters (Country Code, Prefix, Length). If passed, encrypt the JSON payload using AES-GCM before POSTing to the receiver_URL.

Use AES/GCM/NoPadding with a 128-bit or 256-bit key (derived from the "Shared Secret Key" in settings).

For every message, generate a random 12-byte IV.

The Envelope: To make decryption easy for the receiver, encode the output as a single Base64 string in this order: Base64(Byte_Array_of_IV + Byte_Array_of_Ciphertext + Byte_Array_of_Tag)

Send this Base64 string in a JSON field named "payload".



Stream B (Backup): No filters. Send raw, unencrypted JSON to the backup_URL.
Implement BackupSendService.java in the layanan folder.
It must handle standard JSON POSTs to local 192.168.x.x addresses.
Manifest must allow android:usesCleartextTraffic="true" or include a Network Security Config for the local IP range

Settings UI: Add a field for "Backup URL" (e.g., http://192.168.x.x:port).


3. Maintenance: The "Batch-and-Purge"
Settings UI: * Add a checkbox: "Enable Auto-Deletion".

If checked, reveal an input field: "Retention Hours" (Integer) and "Start Auto-Deletion" button.

The Logic:

If "Start Auto-Deletion" is triggered (and validated), calculate the cutoff: System.currentTimeMillis() - (RetentionHours * 3,600,000).

Delete all SMS in the inbox where the message date is older than the cutoff.

Validation for Start Auto-Deletion: Button press only allowed if "Retention hours" > 0 and an Integer. If 0 is entered in this field, button should be greyed out. Only if integer value greater than 0 is entered (max value 3000), then Auto-Deletion button will be available.

Note: Deletion of SMSes is only allowed if app is Default Messaging App. If This is not default messaging app, a message should be given to that effect to the user. User should then be instructed to setup the app as Default Messaging App via Settings. Note that to setup this app as Default App via the "Make Default Messaging App" button, it must include Manifest stubs for MmsReceiver, ComposeSmsActivity, and HeadlessSmsSendService to satisfy Android Default App requirements

4. The Logger Utility (The Foundation):

Create a thread-safe GatewayLogger writing to sms.gateway.log.

Fail-Only Monitoring:

Log [INTERRUPT] PRIMARY_FAIL: HTTP [Code] for Path A.

Log [INTERRUPT] BACKUP_FAIL: HTTP [Code] for Path B.

Heartbeat: Log [SYSTEM] Heartbeat: Logger Active every 24 hours.

Also the logs from the shared key settings are captured here.

In addition, the logs from auto-deleting old SMSes should also be stored here.

Fail-Only Logging: Do not log successful posts. Log only if a POST to the Primary or Backup URL fails (capture the HTTP error code or Timeout exception).

Audit Logging: Log when a Purge activity completes, stating how many messages were deleted.

Settings impact: A view log and share log buttons must be provided in Logging section. View log should show the log. Share log should allow log to be shared using other apps (standard Android - email, whatsapp etc etc). Implement a FileProvider to securely share sms.gateway.log with external apps. To "Share" the log via WhatsApp/Email, the file sms.gateway.log must be stored in the app's Internal Private Storage.














Prompt 4 (Yet to be given)
Android manifest for this app should request all SMS send, receive, create, update and delete permissions. This app should request permissions to become the default SMS messaging app.

Updated Prompt 4 (Given before prompt 2, out of sequence.)

Prompt 5 (Yet to be given)
The settings in this app should have an option to auto-delete SMSes. Within settings, there should be a checkbox for auto-deleting received SMS that will select this service. If auto-deletion is selected, 1 further setting should be shown to the user. This setting must be filled for auto-deletion service to be enabled. This setting is for how far back in time shall auto-deletion work. E.g. if I dont want recently recieved SMSes to be deleteted, i can set this at 48 hours, so only those SMSes that are older than 48 hours from current time can be deleted. 

Review with Google Gemini for prompts
. Dual-Path Data Routing (Integrated Prompts 2 & 3)
Modify the SMS reception logic to split into two distinct paths:

Path A (Receiver/Primary URL): Continue using the Country Code, Prefix, and Length filters.

New Security Requirement: The POST payload for this path must be encrypted using AES-GCM.

Settings: Add a field to set/save a "Shared Secret Key" for this encryption.

Path B (Backup URL): Create a new BackupSendService.java in the layanan folder.

Logic: This path ignores all filters. Every received SMS must be sent to this URL.



2. Permissions & Default Handler (Prompt 4)
Manifest Update: Add SEND_SMS, RECEIVE_SMS, READ_SMS, WRITE_SMS, and INTERNET.

Default App Status: To enable the deletion of SMS, the app must be the Default SMS Handler.

Implement a system request dialog (ACTION_CHANGE_DEFAULT) when the user initializes the service.

Mandatory Stubs: Create the required MmsReceiver and ComposeSmsActivity (can be empty stubs) to satisfy Android's requirements for a Default SMS App.

 (Prompt 5)



Updates required (15/02/2025)
1) Encryption enabled/disabled toggle- If encryption enabled via toggle switch AES-GCM key will be used, else data will be passed without encryption. YAML file will need an update.
2) All URLs have a timeout, if immediate response is not recieved, they will wait until timeout period to send the next message in sequence.
3) All URLs need to have an enable functionality, this is a toggle to enable this functionality.
4) Sequential dispatcher and burst protection as below.
5) Live SMS stream should be completely in memory and not be written to mobile. The maximum shown transactions on live SMS stream should be dictated by a non-zero integer value in system settings menu.
6) Menus should have border boxes for functionality that is grouped together. This is a feature only for good-looking purposes.
7) With the auto-deletion feature also provide a button to list the number of existing inbox SMS messages. Results of this button press should be logged.


Sequential Dispatcher & Burst Protection (Add to Section 2)
Sequential Processing Requirement:
To prevent network congestion, memory exhaustion, and out-of-order delivery during high-volume SMS bursts, the Dispatcher must implement a Producer-Consumer pattern:

The Queue: Use a single-threaded background worker (e.g., SingleThreadExecutor or a serialized IntentService). Incoming SMS events must be queued and processed strictly one at a time in the order they are received.

Blocking Handshake: The dispatcher must wait for the current POST request (to both Primary and Backup streams) to either complete or hit a timeout before picking up the next message from the queue.

Memory Safety: The queue must exist only in RAM as a list of volatile objects. Do not persist the queue to a database. Once a message is successfully dispatched or logged as a failure, it should be removed from memory.

UI Synchronization: As the worker processes the queue, update the existing Live Feed UI to show current progress (e.g., "Processing 4 of 100"). Use runOnUiThread to ensure the UI remains responsive and does not "jitter" during high-frequency bursts.

Systems Architect's Final Review
By adding this, you change the behavior of the app under stress:

Without this: If 100 SMS arrive, the phone tries to open 200 sockets (100 Primary + 100 Backup) at once. This is the #1 cause of "App Not Responding" (ANR) crashes on older Android 7.1.1 hardware.

With this: The app remains calm. It handles SMS 1, sends it, then handles SMS 2. To the user, the Live Feed will look like a steady, professional ticker tape rather than a chaotic flash of data.

One Implementation Detail to Watch:
When the AI writes this, make sure it handles the Timeout correctly. If the Primary URL (Supabase) hangs for 30 seconds, you don't want the whole queue to stop forever. Ensure the prompt implies: "Each task in the queue must have a defined timeout (e.g., 10 seconds) so the worker can move to the next message if a server is unresponsive."

Section 2: The Sequential Dispatcher & Gatekeeper Logic
1. Gatekeeper Logic (Pre-Execution):

Before processing any incoming SMS, the app must check the following conditions. If any are true, the app should abort processing and do nothing:

The "Master Enable" switch on the app's front page is OFF.

Both the receiver_URL and backup_URL are empty/null in settings.

2. Sequential Queueing (The Pipeline):

Use a SingleThreadExecutor to manage a serialized background queue. This ensures messages are processed strictly one at a time in the order of receipt, even during high-volume bursts.

Blocking Handshake: The worker must wait for the current dispatch to finish before picking up the next task.

3. Configurable Network Timeout:

Add a setting field for "Network Timeout (Seconds)" (Integer, default 10).

Apply this timeout to both the Primary (Encrypted) and Backup (Raw) HTTP POST requests.

If a request hits this timeout, log the failure as [INTERRUPT] TIMEOUT_EXCEEDED in sms.gateway.log and move to the next message in the queue.

4. Memory Safety:

All queued messages must exist in RAM only. Once a message is processed (sent or timed out), it must be cleared from the queue to prevent memory leaks.

Systems-Level Audit
Battery Efficiency: By checking the "Master Switch" and URL fields first, you prevent the phone from waking up the CPU and radio unnecessarily. This is vital for maintaining battery health on an older Android 7.1.1 device.

Resiliency: The configurable timeout prevents the entire queue from being blocked by a single "hung" server connection.

UI Feedback: Your Live Feed should still show when the app is "Idle" or "Disabled," so you have visual confirmation of why nothing is being forwarded.

The "Onboarding" Test Case
When you test your ONBOARD:HASH handshake, you can now simulate a failure by setting the timeout to 1 second and seeing if the logger correctly captures the TIMEOUT_EXCEEDED event. This gives you a safe way to test your error-handling logic.



=====================================================================================
The Final Finalized Master Prompt (Integrated)
Section 1: Secure Key & Encryption UI in SMS Webhook Menu

Toggle: "Enable Encryption".

Keygen: "Generate New Key" (256-bit Base64) + "Use This Key" (Saves to Prefs & Logs). An issue is noted here - Generate New Key when pressed is creating an entire new section of key + reciever URL +Backup URL ie everything in the SMS Webhook Menu copy pasted below original. 

Clipboard: "Copy Active Key" button.

Visual: Group these inside a border box labeled "Security & Encryption".

Section 2: Sequential Dispatcher & Gatekeeper

Master Enable: Main UI toggle. If OFF, do nothing. This feature is already present on UI.

Toggles: Individual "Enable" switches for Primary and Backup URLs.

Timeout: User-configurable "Network Timeout" (Seconds) applied to all POSTs.

Sequential Queue: Producer-Consumer logic using a SingleThreadExecutor. Process bursts one-by-one.

Encryption Logic: If Toggle is ON, send AES-GCM envelope + "is_encrypted": true. If OFF, send raw JSON + "is_encrypted": false.

Section 3: Maintenance & Storage in System Settings Menu

Auto-Delete: "Retention Hours" (1-3000) + "Enable Auto-Deletion" checkbox + "Start" button.

Inbox Info: Button "Check Inbox Capacity" -> Returns total SMS count + logs result in sms.gateway.log.

Visual: Group these inside a border box labeled "Inbox Maintenance".

Section 4: Logging & Live Stream

Live Stream: Volatile RAM-only feed. This is already implemented on main page. If not implemented already need a max entries for this live feed . This is a User Setting (Integer) in System Settings Menu. It allows for Self-truncating behavior on the live stream.

System Log: sms.gateway.log (Fail-only and enabled/disabled for POSTs, Audit for Keys/Purge).


Stream A and Stream B functionality:
For Stream A - need Bearer token, HMAC and AES toggles, plus each should have generate, save, copy and edit buttons for all Bearer, HMAC and AES. For Stream B -need Bearer token toggle, and the generate, save, copy and edit buttons.  Also filters are currently applicable only for Stream A (country code, prefix and message length). There should also be a checkbox near the filters to make them applicable to Stream B.
Also is_encrypted field should be removed.