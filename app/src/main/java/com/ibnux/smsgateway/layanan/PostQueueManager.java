package com.ibnux.smsgateway.layanan;

import android.content.Context;
import com.ibnux.smsgateway.Utils.GatewayLogger;
import com.ibnux.smsgateway.Utils.SecurityUtil;
import com.ibnux.smsgateway.data.LiveLogBuffer;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.ibnux.smsgateway.layanan.PushService.tellMainActivity;

public class PostQueueManager {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Legacy method for backward compatibility
    public static void enqueue(Context context, String url, String payload, String contentType, boolean logSuccess, int timeout) {
        enqueue(context, url, payload, contentType, logSuccess, timeout, null, null);
    }

    // New method with bearer token and HMAC key support
    public static void enqueue(Context context, String url, String payload, String contentType, boolean logSuccess, int timeout, String bearerToken, String hmacKey) {
        final String finalBearerToken = bearerToken;
        final String finalHmacKey = hmacKey;
        executor.execute(() -> {
            postData(context, url, payload, contentType, logSuccess, timeout, finalBearerToken, finalHmacKey);
        });
    }

    private static void postData(Context context, String targetUrl, String payload, String contentType, boolean logSuccess, int timeout) {
        postData(context, targetUrl, payload, contentType, logSuccess, timeout, null, null);
    }

    private static void postData(Context context, String targetUrl, String payload, String contentType, boolean logSuccess, int timeout, String bearerToken, String hmacKey) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(targetUrl);
            conn = (HttpURLConnection) url.openConnection();
            
            // Apply Timeout Setting (clamp to minimum 1 second)
            int safeTimeout = Math.max(1, timeout);
            int timeoutMs = safeTimeout * 1000;
            conn.setReadTimeout(timeoutMs);
            conn.setConnectTimeout(timeoutMs);
            
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            if (contentType != null) {
                conn.setRequestProperty("Content-Type", contentType);
            }

            // Bearer Token Authentication
            if (bearerToken != null && !bearerToken.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
            }

            // HMAC-SHA256 Signing (per-stream)
            if (hmacKey != null && !hmacKey.isEmpty()) {
                String signature = SecurityUtil.signPayload(payload, hmacKey);
                if (signature != null) {
                    conn.setRequestProperty("X-signature", signature);
                    GatewayLogger.log(context, "HMAC", "HMAC_SIGN_SUCCESS: Signature added to " + targetUrl);
                } else {
                    GatewayLogger.log(context, "HMAC", "HMAC_SIGN_FAILED: Could not generate signature for " + targetUrl);
                    return;
                }
            }

            try (OutputStream os = conn.getOutputStream();
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                writer.write(payload);
                writer.flush();
            }
            
            int responseCode = conn.getResponseCode();

            if (responseCode >= 200 && responseCode < 300) {
                LiveLogBuffer.updateLatestStatus("ACK [" + responseCode + "]");
                tellMainActivity();
                GatewayLogger.log(context, "POST_SUCCESS", "URL: " + targetUrl + " | Code: " + responseCode);
                if (logSuccess) {
                    PushService.writeLog("SMS: POST : " + targetUrl + " : Code " + responseCode, context);
                }
            } else {
                LiveLogBuffer.updateLatestStatus("FAIL [" + responseCode + "]");
                tellMainActivity();
                GatewayLogger.log(context, "POST_FAIL", "HTTP " + responseCode + " | URL: " + targetUrl);
                PushService.writeLog("SMS: POST FAILED : " + targetUrl + " : HTTP " + responseCode, context);
            }
        } catch (Exception e) {
            LiveLogBuffer.updateLatestStatus("ERR");
            tellMainActivity();
            GatewayLogger.log(context, "POST_ERROR", e.getMessage() + " | URL: " + targetUrl);
            PushService.writeLog("SMS: POST FAILED : " + targetUrl + " : " + e.getMessage(), context);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
