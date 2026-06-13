package com.ibnux.smsgateway.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.security.SecureRandom;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SecurityUtil {
    private static final String TAG = "SecurityUtil";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    // --- AES-GCM Encryption Methods ---

    public static String generateNewKey() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] key = new byte[32]; // 256 bit
        secureRandom.nextBytes(key);
        return Base64.encodeToString(key, Base64.NO_WRAP);
    }

    public static String encrypt(String plainText, String base64Key) throws Exception {
        byte[] keyBytes = Base64.decode(base64Key, Base64.NO_WRAP);
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");

        byte[] iv = new byte[IV_LENGTH_BYTE];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        byte[] cipherText = cipher.doFinal(plainText.getBytes("UTF-8"));

        // Format: Base64(IV + Ciphertext + Tag)
        // Note: Java's GCM implementation appends the tag to the ciphertext automatically.
        // So we just need to concatenate IV + CipherText (which includes Tag)
        
        byte[] finalMessage = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, finalMessage, 0, iv.length);
        System.arraycopy(cipherText, 0, finalMessage, iv.length, cipherText.length);

        return Base64.encodeToString(finalMessage, Base64.NO_WRAP);
    }
    
    private static SharedPreferences getEncryptedPrefs(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    "pref_encrypted",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to create EncryptedSharedPreferences: " + e.getMessage(), e);
            // Fallback to plain SharedPreferences to avoid crashes
            return context.getSharedPreferences("pref", 0);
        }
    }

    public static void saveKey(Context context, String key) {
        SharedPreferences sp = getEncryptedPrefs(context);
        sp.edit().putString("shared_secret_key", key).apply();
        GatewayLogger.log(context, "SECURITY", "NEW_KEY_SET: A new AES key was activated.");
    }
    
    public static String getSharedKey(Context context) {
         SharedPreferences sp = getEncryptedPrefs(context);
         return sp.getString("shared_secret_key", null);
    }

    // --- HMAC-SHA256 Signing Methods (Stream A) ---

    public static String generateHmacKey() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] key = new byte[32]; // 256 bit
        secureRandom.nextBytes(key);
        return Base64.encodeToString(key, Base64.NO_WRAP);
    }

    public static String getHmacKey(Context context) {
        SharedPreferences sp = getEncryptedPrefs(context);
        return sp.getString("hmac_secret_key", null);
    }

    public static void saveHmacKey(Context context, String key) {
        SharedPreferences sp = getEncryptedPrefs(context);
        sp.edit().putString("hmac_secret_key", key).apply();
        GatewayLogger.log(context, "HMAC", "HMAC_KEY_SAVED: New HMAC key saved for Stream A.");
    }

    public static String signPayload(String payload, String hmacKey) {
        try {
            byte[] keyBytes = Base64.decode(hmacKey, Base64.NO_WRAP);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] signature = mac.doFinal(payload.getBytes("UTF-8"));
            return Base64.encodeToString(signature, Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "HMAC signing failed: " + e.getMessage(), e);
            return null;
        }
    }

    // --- Bearer Token Methods (Stream A) ---

    public static String generateBearerToken() {
        return UUID.randomUUID().toString() + "." + UUID.randomUUID().toString();
    }

    public static String getBearerToken(Context context) {
        SharedPreferences sp = getEncryptedPrefs(context);
        return sp.getString("bearer_token_a", null);
    }

    public static void saveBearerToken(Context context, String token) {
        SharedPreferences sp = getEncryptedPrefs(context);
        sp.edit().putString("bearer_token_a", token).apply();
        GatewayLogger.log(context, "SECURITY", "BEARER_TOKEN_SAVED: New Bearer token saved for Stream A.");
    }

    // --- Bearer Token Methods (Stream B) ---

    public static String getBearerTokenStreamB(Context context) {
        SharedPreferences sp = getEncryptedPrefs(context);
        return sp.getString("bearer_token_b", null);
    }

    public static void saveBearerTokenStreamB(Context context, String token) {
        SharedPreferences sp = getEncryptedPrefs(context);
        sp.edit().putString("bearer_token_b", token).apply();
        GatewayLogger.log(context, "SECURITY", "BEARER_TOKEN_SAVED: New Bearer token saved for Stream B.");
    }
}
