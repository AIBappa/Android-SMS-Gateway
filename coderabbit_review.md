Issue list from coderabbit_review
In `@app/src/main/java/com/ibnux/smsgateway/Utils/SecurityUtil.java` around lines
57 - 59, The saveKey method (and the similar methods at lines 82-84 that store
AES/HMAC keys) currently persist raw Base64 key strings into ordinary
SharedPreferences; instead, change persistence to keystore-backed storage by
either (A) generating or importing the AES/HMAC secrets into the Android
Keystore and storing only references/aliases, or (B) wrapping the raw keys with
a KeyStore-backed RSA/AES wrapping key and then storing the wrapped bytes (or
use EncryptedSharedPreferences) before writing to prefs; update saveKey and the
corresponding loadKey methods to perform wrapping/unwrapping via AndroidKeyStore
APIs (or switch to EncryptedSharedPreferences) and ensure you use unique aliases
(e.g., "transport_aes_key" / "transport_hmac_key") and handle KeyStore
initialization and error paths.

In `@app/src/main/java/com/ibnux/smsgateway/layanan/PostQueueManager.java` around
lines 59 - 64, In PostQueueManager, the OutputStream/BufferedWriter pair used to
write the request body can leak if write/flush throws; update the code that
calls conn.getOutputStream(), creates new BufferedWriter(new
OutputStreamWriter(...)) and writes payload to use try-with-resources so both
the OutputStream and Writer are closed automatically (prefer using
StandardCharsets.UTF_8 for the writer), remove the manual
writer.close()/os.close(), and ensure the write/flush happens inside the try
block so cleanup is guaranteed even on exception.

In `@app/src/main/java/com/ibnux/smsgateway/layanan/PostQueueManager.java` around
lines 30 - 33, PostQueueManager applies timeouts directly, so a timeout value of
0 results in infinite read/connect timeouts and can block the single-thread
executor; change the timeout calculation before calling conn.setReadTimeout and
conn.setConnectTimeout by clamping the input timeout to a positive value (e.g.
int safeTimeout = Math.max(1, timeout); int timeoutMs = safeTimeout * 1000;) or
use a defined DEFAULT_TIMEOUT_SECONDS, then use timeoutMs in
conn.setReadTimeout(timeoutMs) and conn.setConnectTimeout(timeoutMs) to prevent
infinite blocking.

In `@app/src/main/java/com/ibnux/smsgateway/layanan/PostQueueManager.java`:
- Around line 43-56: When HMAC signing is enabled in PostQueueManager, the code
currently only logs failures but continues to send the request; change the flow
so that if SecurityUtil.getHmacKey(context) returns null/empty or
SecurityUtil.signPayload(payload, hmacKey) returns null, the method aborts
(return/throw) before posting to preserve the integrity guarantee. Locate the
HMAC block around the hmacEnabled check in PostQueueManager and add an early
exit (e.g., return false or throw a checked/appropriate exception) immediately
after the log lines for "HMAC_KEY_MISSING" and "HMAC_SIGN_FAILED" so the request
is not sent when signing cannot be applied.

In `@app/src/main/java/com/ibnux/smsgateway/layanan/WebSocketService.java`:
- Around line 56-70: The onStartCommand currently calls connect() on every start
intent which can create duplicate sockets; modify onStartCommand to check the
current connection state before calling connect(): use the service's existing
state flags (isRunning, shouldReconnect) and/or the WebSocket instance status
(e.g., webSocket != null && webSocket.isOpen()) to skip calling connect() when a
socket is already active, only allow disconnect() flow to proceed when the
disconnect extra is true, and ensure connect() is only invoked when not already
connected and shouldReconnect is true.
- Around line 153-159: The message-secret check currently permits messages when
the stored secret is empty; change the logic in WebSocketService's message
handler so an empty configured secret is treated as unauthorized: retrieve
msgSecret and storedSecret as before but replace the if condition with one that
rejects when storedSecret.isEmpty() OR when msgSecret does not equal
storedSecret (e.g., if (storedSecret.isEmpty() ||
!msgSecret.equals(storedSecret)) { PushService.writeLog("WEBSOCKET: Invalid or
missing secret - ignored", WebSocketService.this); return; }), thereby dropping
all commands until a non-empty secret is configured (optionally close the socket
elsewhere if desired).
- Around line 280-291: The start/stop helpers (WebSocketService.start and
WebSocketService.stop) call context.startService(), which can cause
IllegalStateException on Android O+ when called from the background; update both
helpers to use Context.startForegroundService(intent) instead (or add a guard
that validates the provided Context is foreground-capable before calling
startService) so the service's startForeground() in onStartCommand() is safe;
locate WebSocketService.start, WebSocketService.stop and related
onStartCommand() to implement the change and ensure the "disconnect" extra
behavior is preserved.

