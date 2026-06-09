<?php
/**
 * HMAC-SHA256 Signature Configuration
 * 
 * Copy this file to backend-config.php (it already exists in this location)
 * and set your HMAC secret key to match the key generated on the Android app.
 * 
 * $hmac_signing_enabled: Set to true to enable signature verification on incoming webhooks.
 * $hmac_secret_key: The Base64-encoded 256-bit HMAC key (must match Android's key).
 * $hmac_log_verifications: If true, logs verification results to backend/log.txt.
 */

$hmac_signing_enabled = false;    // Set to true to enable HMAC verification
$hmac_secret_key = "";            // Paste the Base64 HMAC key from Android Settings
$hmac_log_verifications = true;   // Log "SIGNATURE_VERIFIED" / "SIGNATURE_VERIFICATION_FAILED"
?>