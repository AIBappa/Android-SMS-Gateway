package com.ibnux.smsgateway.Utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GatewayLogger {
    private static final String LOG_FILENAME = "sms.gateway.log";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    public static void log(final Context context, final String tag, final String message) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                writeLog(context, tag, message);
            }
        });
    }

    private static synchronized void writeLog(Context context, String tag, String message) {
        File logFile = new File(context.getFilesDir(), LOG_FILENAME);
        String timestamp = dateFormat.format(new Date());
        String entry = String.format("[%s] %s: %s\n", timestamp, tag, message);

        try {
            FileWriter writer = new FileWriter(logFile, true);
            writer.append(entry);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            Log.e("GatewayLogger", "Error writing to log file", e);
        }
    }

    public static File getLogFile(Context context) {
        return new File(context.getFilesDir(), LOG_FILENAME);
    }
    
    // Heartbeat method - should be called periodically
    public static void logHeartbeat(Context context) {
        log(context, "SYSTEM", "Heartbeat: Logger Active");
    }
}
