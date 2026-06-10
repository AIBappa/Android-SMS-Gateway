package com.ibnux.smsgateway.layanan;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.ibnux.smsgateway.Aplikasi;
import com.ibnux.smsgateway.MainActivity;
import com.ibnux.smsgateway.R;
import com.ibnux.smsgateway.Utils.Fungsi;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class WebSocketService extends Service {

    private static final String TAG = "WebSocketService";
    private static final int NOTIFICATION_ID = 3;
    private static final String CHANNEL_ID = "WebSocket Tunnel";

    public static boolean isRunning = false;

    private OkHttpClient client;
    private WebSocket webSocket;
    private boolean shouldReconnect = true;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WebSocketService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Check if we should disconnect
        if (intent != null && intent.getBooleanExtra("disconnect", false)) {
            shouldReconnect = false;
            isRunning = false;
            disconnect();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        isRunning = true;
        startForegroundNotification();
        shouldReconnect = true;
        connect();

        return START_STICKY;
    }

    private void startForegroundNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT > 25) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "WebSocket Tunnel", NotificationManager.IMPORTANCE_LOW);
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            nm.createNotificationChannel(channel);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(
                Aplikasi.app, 0,
                new Intent(Aplikasi.app, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(Aplikasi.app, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("SMS Gateway")
                .setOngoing(true)
                .setContentText("WebSocket tunnel connected")
                .setAutoCancel(false)
                .setContentIntent(contentIntent)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }

    private void connect() {
        SharedPreferences sp = getSharedPreferences("pref", 0);
        String wsUrl = sp.getString("websocket_url", "");
        String secret = sp.getString("secret", "");

        if (wsUrl.isEmpty()) {
            PushService.writeLog("WEBSOCKET: No URL configured", this);
            stopSelf();
            return;
        }

        PushService.writeLog("WEBSOCKET: Connecting to " + wsUrl, this);

        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for long-lived connection
                .build();

        Request request = new Request.Builder()
                .url(wsUrl)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.d(TAG, "WebSocket connected");
                PushService.writeLog("WEBSOCKET: Connected", WebSocketService.this);

                // Send authentication handshake
                try {
                    JSONObject handshake = new JSONObject();
                    handshake.put("type", "auth");
                    handshake.put("secret", secret);
                    ws.send(handshake.toString());
                } catch (Exception e) {
                    Log.e(TAG, "Error sending auth", e);
                }

                // Update notification
                updateNotification("WebSocket tunnel connected");
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                Log.d(TAG, "WebSocket message: " + text);
                PushService.writeLog("WEBSOCKET: Received command", WebSocketService.this);

                try {
                    JSONObject msg = new JSONObject(text);

                    // Verify secret in each message for security
                    String msgSecret = msg.optString("secret", "");
                    String storedSecret = sp.getString("secret", "");
                    if (!storedSecret.isEmpty() && !msgSecret.equals(storedSecret)) {
                        PushService.writeLog("WEBSOCKET: Invalid secret - ignored", WebSocketService.this);
                        return;
                    }

                    String type = msg.optString("type", "");

                    if ("send_sms".equals(type) || "push".equals(type)) {
                        String to = msg.optString("to", "");
                        String message = msg.optString("message", "");
                        int sim = msg.optInt("sim", 0);

                        if (!to.isEmpty() && !message.isEmpty()) {
                            PushService.writeLog("WEBSOCKET: Sending SMS to " + to, WebSocketService.this);
                            // Reuse the existing send logic via SendSMSorUSSD
                            // We access PushService's static method through a local instance
                            Intent serviceIntent = new Intent(WebSocketService.this, PushService.class);
                            serviceIntent.putExtra("to", to);
                            serviceIntent.putExtra("message", message);
                            serviceIntent.putExtra("sim", sim);
                            // PushService doesn't accept intents for sending, so use Fungsi directly
                            if (to.startsWith("*")) {
                                PushService.queueUssd(to, (sim == 0) ? 1 : sim);
                            } else {
                                if (sim > 0) {
                                    com.ibnux.smsgateway.Utils.SimUtil.sendSMS(
                                            WebSocketService.this, sim - 1, to, null, message, 0);
                                } else {
                                    Fungsi.sendSMS(to, message, WebSocketService.this);
                                }
                            }
                        }
                    } else if ("ping".equals(type)) {
                        // Respond to keep-alive
                        JSONObject pong = new JSONObject();
                        pong.put("type", "pong");
                        ws.send(pong.toString());
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error processing message", e);
                    PushService.writeLog("WEBSOCKET: Error: " + e.getMessage(), WebSocketService.this);
                }
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                Log.d(TAG, "WebSocket closing: " + code + " " + reason);
                ws.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                Log.d(TAG, "WebSocket closed: " + code + " " + reason);
                PushService.writeLog("WEBSOCKET: Disconnected (" + code + ")", WebSocketService.this);
                updateNotification("WebSocket tunnel disconnected");
                scheduleReconnect();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.e(TAG, "WebSocket failure", t);
                PushService.writeLog("WEBSOCKET: Connection failed: " + t.getMessage(), WebSocketService.this);
                updateNotification("WebSocket tunnel disconnected");
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (!shouldReconnect) return;

        PushService.writeLog("WEBSOCKET: Reconnecting in 10 seconds...", this);

        new android.os.Handler(getMainLooper()).postDelayed(() -> {
            if (shouldReconnect) {
                connect();
            }
        }, 10000);
    }

    private void disconnect() {
        shouldReconnect = false;
        if (webSocket != null) {
            webSocket.close(1000, "User disconnected");
            webSocket = null;
        }
        if (client != null) {
            client.dispatcher().executorService().shutdown();
            client = null;
        }
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        PendingIntent contentIntent = PendingIntent.getActivity(
                Aplikasi.app, 0,
                new Intent(Aplikasi.app, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(Aplikasi.app, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("SMS Gateway")
                .setOngoing(true)
                .setContentText(text)
                .setAutoCancel(false)
                .setContentIntent(contentIntent)
                .build();

        nm.notify(NOTIFICATION_ID, notification);
    }

    @Override
    public void onDestroy() {
        disconnect();
        isRunning = false;
        super.onDestroy();
    }

    /**
     * Convenience method to start the WebSocket service.
     */
    public static void start(Context context) {
        Intent intent = new Intent(context, WebSocketService.class);
        context.startService(intent);
    }

    /**
     * Convenience method to stop the WebSocket service.
     */
    public static void stop(Context context) {
        Intent intent = new Intent(context, WebSocketService.class);
        intent.putExtra("disconnect", true);
        context.startService(intent);
    }
}