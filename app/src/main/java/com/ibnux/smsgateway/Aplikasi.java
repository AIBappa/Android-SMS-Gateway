package com.ibnux.smsgateway;

/**
 * Created by Ibnu Maksum 2020
 */

import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.ibnux.smsgateway.layanan.HeartbeatReceiver;
import com.ibnux.smsgateway.Utils.GatewayLogger;

import java.util.UUID;

public class Aplikasi extends Application {

    public static Application app;
    public static String secret;
    private SharedPreferences sp;

    @Override
    public void onCreate() {
        super.onCreate();
        this.app = this;
        ObjectBox.init(this);
        sp = getSharedPreferences("pref",0);
        secret = sp.getString("secret",null);
        if(secret==null){
            secret = UUID.randomUUID().toString();
            sp.edit().putString("secret", secret).apply();
        }
        
        scheduleHeartbeat();
    }
    
    private void scheduleHeartbeat() {
        Intent intent = new Intent(this, HeartbeatReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
            GatewayLogger.log(this, "SYSTEM", "Heartbeat scheduled");
        }
    }

}
