package com.ibnux.smsgateway.layanan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.ibnux.smsgateway.Utils.GatewayLogger;

public class HeartbeatReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        GatewayLogger.logHeartbeat(context);
    }
}
