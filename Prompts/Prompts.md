Updates from AIBappa

Prompt 1
For this repo, the input is an SMS and the output is a web POST to a URL that is specified in settings. I want to install some filters in the settings. 1) Allowed country codes. Only those SMSese from allowed country codes should be sent to the URL . On clicking allowed country codes in settings, user can see allowed ones on UI page, plus be able to search for country codes and add them accordingly. 2) Allowed SMS message prefix. User should be able to set an allowed SMS message prefix or prefixes. Only those SMSes with allowed prefixes should be sent to the URL . User should be able to type in the prefix using keys and can add in one or more prefixes to the list. This will allow only those SMSes in which the SMS message begins with that appropriate prefix(s) to be sent to the URL 3) Allowed SMS Message Lengths. The user should be able to select the allowed SMS lengths (numbers). Only those messages fit a certain length or lengths as specified in settings will be sent in the URL 4) It shall be individually possible via settings to set/reset these 3 filters.

Prompt 2 (Yet to be given)
The receiver_URL POST shall be encoded with AES-GCM. It shall be possible to set the shared secret key via settings.

Prompt 3 (Yet to be given)
I want another backup_URL in addition to the existing menu_set_url(I will also call this receiver_URL). The current filters for allowed country code(s), allowed prefix(s) and allowed length(s) will not be applicable for this backup URL. It should send all SMSes that were received to backup URL. Create a new backupsendservice.java for this in the layanan folder.

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

Settings: Add a field for "Backup URL" (e.g., http://192.168.x.x:port).

2. Permissions & Default Handler (Prompt 4)
Manifest Update: Add SEND_SMS, RECEIVE_SMS, READ_SMS, WRITE_SMS, and INTERNET.

Default App Status: To enable the deletion of SMS, the app must be the Default SMS Handler.

Implement a system request dialog (ACTION_CHANGE_DEFAULT) when the user initializes the service.

Mandatory Stubs: Create the required MmsReceiver and ComposeSmsActivity (can be empty stubs) to satisfy Android's requirements for a Default SMS App.

3. Maintenance: The "Batch-and-Purge" (Prompt 5)
Settings UI: * Add a checkbox: "Enable Auto-Deletion".

If checked, reveal an input field: "Retention Hours" (Integer).

The Logic:

If "Start Auto-Deletion" is triggered (and validated), calculate the cutoff: System.currentTimeMillis() - (RetentionHours * 3,600,000).

Delete all SMS in the inbox where the message date is older than the cutoff.

4. Unified Error Logging (The "Systems" Layer)
Create a simple log file vlifecycle_gateway.log.

Fail-Only Logging: Do not log successful posts. Log only if a POST to the Primary or Backup URL fails (capture the HTTP error code or Timeout exception).

Audit Logging: Log when a Purge activity completes, stating how many messages were deleted.