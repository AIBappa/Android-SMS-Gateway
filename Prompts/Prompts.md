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



