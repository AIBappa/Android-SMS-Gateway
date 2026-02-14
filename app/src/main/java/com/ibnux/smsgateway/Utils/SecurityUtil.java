package com.ibnux.smsgateway.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SecurityUtil {
    private static final String TAG = "SecurityUtil";
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

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
    
    public static void saveKey(Context context, String key) {
        SharedPreferences sp = context.getSharedPreferences("pref", 0);
        sp.edit().putString("shared_secret_key", key).apply();
        GatewayLogger.log(context, "SECURITY", "NEW_KEY_SET: Key " + key + " was activated.");
    }
    
    public static String getSharedKey(Context context) {
         SharedPreferences sp = context.getSharedPreferences("pref", 0);
         return sp.getString("shared_secret_key", null);
    }
}
