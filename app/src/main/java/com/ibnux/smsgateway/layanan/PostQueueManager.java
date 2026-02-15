package com.ibnux.smsgateway.layanan;

import android.content.Context;
import com.ibnux.smsgateway.Utils.GatewayLogger;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PostQueueManager {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static void enqueue(Context context, String url, String payload, String contentType, boolean logSuccess, int timeout) {
        executor.execute(() -> {
            postData(context, url, payload, contentType, logSuccess, timeout);
        });
    }

    private static void postData(Context context, String targetUrl, String payload, String contentType, boolean logSuccess, int timeout) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(targetUrl);
            conn = (HttpURLConnection) url.openConnection();
            
            // Apply Timeout Setting
            int timeoutMs = timeout * 1000;
            conn.setReadTimeout(timeoutMs);
            conn.setConnectTimeout(timeoutMs);
            
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            if (contentType != null) {
                conn.setRequestProperty("Content-Type", contentType);
            }

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            writer.write(payload);
            writer.flush();
            writer.close();
            os.close();
            
            int responseCode = conn.getResponseCode();

            if (responseCode >= 200 && responseCode < 300) {
                // Only log success if enabled
                if (logSuccess) {
                    GatewayLogger.log(context, "POST_SUCCESS", "URL: " + targetUrl + " | Code: " + responseCode);
                    PushService.writeLog("SMS: POST : " + targetUrl + " : Code " + responseCode, context);
                }
            } else {
                GatewayLogger.log(context, "POST_FAIL", "HTTP " + responseCode + " | URL: " + targetUrl);
                PushService.writeLog("SMS: POST FAILED : " + targetUrl + " : HTTP " + responseCode, context);
            }
        } catch (Exception e) {
            GatewayLogger.log(context, "POST_ERROR", e.getMessage() + " | URL: " + targetUrl);
            PushService.writeLog("SMS: POST FAILED : " + targetUrl + " : " + e.getMessage(), context);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
