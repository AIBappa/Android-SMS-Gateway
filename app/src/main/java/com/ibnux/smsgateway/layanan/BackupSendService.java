package com.ibnux.smsgateway.layanan;

import android.app.IntentService;
import android.content.Intent;
import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.ibnux.smsgateway.Utils.GatewayLogger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class BackupSendService extends IntentService {
    private static final String ACTION_SEND_BACKUP = "com.ibnux.smsgateway.layanan.action.SEND_BACKUP";
    private static final String EXTRA_URL = "com.ibnux.smsgateway.layanan.extra.URL";
    private static final String EXTRA_PAYLOAD = "com.ibnux.smsgateway.layanan.extra.PAYLOAD";

    public BackupSendService() {
        super("BackupSendService");
    }

    public static void startBackupSend(Context context, String url, String payload) {
        Intent intent = new Intent(context, BackupSendService.class);
        intent.setAction(ACTION_SEND_BACKUP);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_PAYLOAD, payload);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_SEND_BACKUP.equals(action)) {
                final String url = intent.getStringExtra(EXTRA_URL);
                final String payload = intent.getStringExtra(EXTRA_PAYLOAD);
                handleSendBackup(url, payload);
            }
        }
    }

    private void handleSendBackup(String urlStr, String payload) {
        if (urlStr == null || payload == null) return;
        
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setReadTimeout(15000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            OutputStream os = conn.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));
            writer.write(payload);
            writer.flush();
            writer.close();
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode >= 400) {
                 GatewayLogger.log(this, "INTERRUPT", "BACKUP_FAIL: HTTP " + responseCode + " for Path B.");
            } else {
                 // Success - do not log as per requirement "Fail-Only Logging: Do not log successful posts"
            }

        } catch (Exception e) {
            GatewayLogger.log(this, "INTERRUPT", "BACKUP_FAIL: " + e.getMessage() + " for Path B.");
        }
    }
}
