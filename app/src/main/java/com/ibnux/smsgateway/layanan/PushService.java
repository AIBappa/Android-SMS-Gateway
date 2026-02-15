package com.ibnux.smsgateway.layanan;

/**
 * Created by Ibnu Maksum 2020
 */

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SmsManager;
import android.text.TextUtils;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.ibnux.smsgateway.Aplikasi;
import com.ibnux.smsgateway.ObjectBox;
import com.ibnux.smsgateway.Utils.Fungsi;
import com.ibnux.smsgateway.Utils.SimUtil;
import com.ibnux.smsgateway.data.LogLine;
import com.ibnux.smsgateway.data.LogLine_;
import com.ibnux.smsgateway.data.UssdData;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import io.objectbox.Box;

public class PushService extends Service {
    private String TAG = "SMSin";
    private static Box<LogLine> logBox;
    private final static String simSlotName[] = {
            "extra_asus_dial_use_dualsim",
            "com.android.phone.extra.slot",
            "slot",
            "simslot",
            "sim_slot",
            "subscription",
            "Subscription",
            "phone",
            "com.android.phone.DialingMode",
            "simSlot",
            "slot_id",
            "simId",
            "simnum",
            "phone_type",
            "slotId",
            "slotIdx"
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static Context context;

    public static List<UssdData> ussdDataList = new ArrayList<>();
    public static boolean isRun = false;
    public static UssdData current;
    public static long lastUssd = 0L;

    BroadcastReceiver deliveredReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            String msg = null;
            switch (getResultCode()) {
                case Activity.RESULT_OK:
                    msg = "success";
                    break;
                case Activity.RESULT_CANCELED:
                    msg = "failed";
                    break;
            }
            if (msg != null) {
                writeLog("DELIVERED: " + msg + " : " + arg1.getStringExtra("number"), arg0);
                SmsListener.sendPOST(
                        getSharedPreferences("pref", 0).getString("urlPost", null),
                        arg1.getStringExtra("number"),
                        msg,
                        "delivered",
                        arg0,
                        String.valueOf(System.currentTimeMillis()));
            }
        }
    };

    BroadcastReceiver sentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent arg1) {
            String msg = null;
            switch (getResultCode()) {
                case Activity.RESULT_OK:
                    msg = "success";
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    msg = "Generic failure";
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    msg = "No service";
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    msg = "Null PDU";
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    msg = "Radio off";
                    break;
            }

            // RETRY AFTER 10 SECOND IF FAILED UNTIL 3 TIMES
            if(msg!=null && !msg.equals("success")){
                int retry = arg1.getIntExtra("retry",0);
                if(retry<3){
                    PushService.writeLog("SENT FAILED: " + msg, context);
                    PushService.writeLog("RETRY SEND SMS in 10s #" + (retry+1), context);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            String number = arg1.getStringExtra("number");
                            int simID = arg1.getIntExtra("simID",0);
                            String centerNum = arg1.getStringExtra("centerNum");
                            String smsText = arg1.getStringExtra("smsText");
                            int retry = arg1.getIntExtra("retry",0);
                            retry++;
                            SimUtil.sendSMS(context,simID,number,centerNum,smsText,retry);
                        }
                    }, 10000);

                    return;
                }
            }

            if (msg != null) {
                writeLog("SENT: " + msg + " : " + arg1.getStringExtra("number"), arg0);
                SmsListener.sendPOST(getSharedPreferences("pref", 0).getString("urlPost", null),
                        arg1.getStringExtra("number"), msg, "sent", arg0, String.valueOf(System.currentTimeMillis()));
            }
        }
    };

    @Override
    public void onCreate() {
        context = this;
        registerReceiver(sentReceiver, new IntentFilter(Fungsi.SENT));
        registerReceiver(deliveredReceiver, new IntentFilter(Fungsi.DELIVERED));
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(sentReceiver);
        unregisterReceiver(deliveredReceiver);
        super.onDestroy();
    }



//    private void sendSMSorUSSD(String to, String message){
//        sendSMSorUSSD(to,message,0);
//    }


    private void sendSMSorUSSD(String to, String message, int simNumber, String messageId) {
        if (simNumber > 2) simNumber = 2;
        if (to.startsWith("*")) {
            if (to.trim().endsWith("#")) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    Fungsi.log("CALL_PHONE not granted");
                    return;
                }
                Fungsi.log("USSD to " + to + " sim " + simNumber);
                writeLog(messageId + " QUEUE USSD: " + to + " SIM " + simNumber, this);

                queueUssd(to,(simNumber == 0) ? 1 : simNumber);
            } else {
                Fungsi.log("not end with #");
                writeLog("USSD not end with # : " + to, this);

            }
        } else {
            Fungsi.log("send SMS " + to);
            writeLog(messageId + " SEND SMS: " + to + " SIM " + simNumber + "\n" + message, this);

            if (simNumber > 0) {
                SmsManager smsManager = SmsManager.getDefault();
                ArrayList<String> messageList = smsManager.divideMessage(message);
                boolean sukses = true;
                if (messageList.size() > 1) {
                    sukses = SimUtil.sendMultipartTextSMS(this, simNumber - 1, to, null, messageList);
                } else {
                    sukses = SimUtil.sendSMS(this, simNumber - 1, to, null, message, 0);
                }
            } else {
                Fungsi.sendSMS(to, message, this);
            }
        }
    }

    public static void queueUssd(String to, int simNumber){
        Fungsi.log("queueUssd "+to+" "+simNumber);
        UssdData data = new UssdData();
        data.to = to;
        data.sim = simNumber;
        ussdDataList.add(data);
        if(!isRun){
            runUssd();
        }
    }

    public static void runUssd(){
        Fungsi.log("runUssd");
        isRun = true;
        if(current!=null){
            Fungsi.log("current!=null");
            ussdDataList.remove(0);
            current = null;
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    runUssd();
                }
            }, 10000);
            return;
        }
        if(ussdDataList.size()>0) {
            lastUssd = System.currentTimeMillis();
            current = ussdDataList.get(0);
            sendUSSD(current.to, current.sim);
            checkTimeout();
        }else{
            Fungsi.log("runUssd Finished");
            isRun = false;
        }
    }

    public static boolean isCheck = false;
    private static Runnable runnable = () -> {
        Fungsi.log("check is Timeout");
        isCheck = false;
        if(isRun){
            long sisa = (System.currentTimeMillis()-lastUssd)/1000L;

            Fungsi.log("check is Timeout sisa "+sisa);
            if(sisa>=60){
                runUssd();
            }else{
                checkTimeout();
            }
        }
    };
    public static void checkTimeout(){
        Fungsi.log("checkTimeout");
        if(isCheck)return;
        isCheck = true;
        Fungsi.log("checkTimeout 5 second");
        new Handler(Looper.getMainLooper()).postDelayed(runnable, 5000);
    }

    public static void tellMainActivity(){
        Intent i = new Intent("MainActivity");
        i.putExtra("newMessage", "newMessage");
        if(context==null) context = Aplikasi.app;
        LocalBroadcastManager.getInstance(context).sendBroadcast(i);
    }

    public static void sendUSSD(String to, int simNumber) {
        Fungsi.log("sendUSSD");
        if (simNumber == 0) {
            Fungsi.log("send ussd " + Fungsi.ussdToCallableUri(to));
            writeLog("CALLING USSD: " + to, context);
            Intent i = new Intent("android.intent.action.CALL", Fungsi.ussdToCallableUri(to));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        } else {
            Fungsi.log("USSD to " + to + " sim " + simNumber);
            writeLog("CALLING USSD: " + to + " SIM " + simNumber, context);
            Intent intent = new Intent(Intent.ACTION_CALL, Fungsi.ussdToCallableUri(to));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("com.android.phone.force.slot", true);
            intent.putExtra("Cdma_Supp", true);
            //Add all slots here, according to device.. (different device require different key so put all together)
            for (String s : simSlotName)
                intent.putExtra(s, simNumber - 1); //0 or 1 according to sim.......
            //works only for API >= 21
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
                    List<PhoneAccountHandle> phoneAccountHandleList = telecomManager.getCallCapablePhoneAccounts();
                    intent.putExtra("android.telecom.extra.PHONE_ACCOUNT_HANDLE", phoneAccountHandleList.get(simNumber - 1));
                } catch (Exception e) {
                    e.printStackTrace();
                    //writeLog("No Sim card? at slot " + simNumber+"\n\n"+e.getMessage(), this);
                }
            }
            context.startActivity(intent);
        }

    }

    static public void writeLog(String message, Context cx){
        if(logBox==null){
            logBox = ObjectBox.get().boxFor(LogLine.class);
        }
        
        // Self-truncating logic
        try {
            SharedPreferences sp = cx.getSharedPreferences("pref", 0);
            int maxEntries = sp.getInt("live_stream_max", 100);
            long count = logBox.count();
            if (count >= maxEntries) {
                long excess = count - maxEntries + 1;
                if (excess > 0) {
                     List<LogLine> oldLogs = logBox.query().order(LogLine_.time).build().find(0, excess);
                     logBox.remove(oldLogs);
                }
            }
        } catch (Exception e) {
            // Ignore errors in truncation
        }

        LogLine ll = new LogLine();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());
        ll.time = cal.getTimeInMillis();
        ll.date = cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DAY_OF_MONTH) + " " +
                cal.get(Calendar.HOUR_OF_DAY) + ":" + cal.get(Calendar.MINUTE) + ":" + cal.get(Calendar.SECOND);
        ll.message = message;
        logBox.put(ll);
        tellMainActivity();
    }

}
