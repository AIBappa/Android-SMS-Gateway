package com.ibnux.smsgateway.layanan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Mandatory component for Default SMS App, but we might not handle MMS yet.
    }
}
