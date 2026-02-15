package com.ibnux.smsgateway.layanan;

import static com.ibnux.smsgateway.layanan.PushService.writeLog;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import com.ibnux.smsgateway.Utils.GatewayLogger;
import com.ibnux.smsgateway.Utils.SecurityUtil;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import javax.net.ssl.HttpsURLConnection;

public class SmsListener extends BroadcastReceiver {

    SharedPreferences sp;
    @Override
    public void onReceive(Context context, Intent intent) {
        if(sp==null)sp = context.getSharedPreferences("pref",0);
        
        if (Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) {
            for (SmsMessage smsMessage : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                String messageFrom = smsMessage.getOriginatingAddress();
                String messageBody = smsMessage.getMessageBody();
                String messageTimestamp = smsMessage.getTimestampMillis()+"";
                Log.i("SMS From", messageFrom);
                Log.i("SMS Body", messageBody);
                writeLog("SMS: RECEIVED : " + messageFrom + " " + messageBody,context);
                
                // Construct Base JSON Payload
                JSONObject jsonPayload = new JSONObject();
                try {
                    jsonPayload.put("from", messageFrom);
                    jsonPayload.put("message", messageBody);
                    jsonPayload.put("type", "received");
                    jsonPayload.put("timestamp", messageTimestamp);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                int timeout = sp.getInt("network_timeout", 15);
                boolean logSuccess = sp.getBoolean("log_post_success", false);

                // Stream B (Backup) - Gatekeeper & Dispatcher
                if (sp.getBoolean("enable_stream_b", false)) {
                    String backupUrl = sp.getString("backup_url", null);
                    if (backupUrl != null && !backupUrl.isEmpty() && backupUrl.startsWith("http")) {
                         // Backup sends raw JSON, unencrypted
                         PostQueueManager.enqueue(context, backupUrl, jsonPayload.toString(), "application/json", logSuccess, timeout);
                    }
                }

                // Filters
                boolean passedFilters = true;
                
                // Filter Country Code
                if(sp.getBoolean("filter_country_enabled", false)){
                    boolean match = false;
                    String countryList = sp.getString("filter_country_list", "");
                    if(!countryList.isEmpty()){
                        String[] codes = countryList.split(",");
                        for(String code : codes){
                            if(!code.trim().isEmpty() && messageFrom.startsWith(code.trim())){
                                match = true;
                                break;
                            }
                        }
                    }
                    if(!match) passedFilters = false;
                }
                
                // Filter Prefix
                if(passedFilters && sp.getBoolean("filter_prefix_enabled", false)){
                    boolean match = false;
                    String prefixList = sp.getString("filter_prefix_list", "");
                    if(!prefixList.isEmpty()){
                        String[] prefixes = prefixList.split(",");
                        for(String prefix : prefixes){
                            if(!prefix.trim().isEmpty() && messageBody.startsWith(prefix.trim())){
                                match = true;
                                break;
                            }
                        }
                    }
                    if(!match) passedFilters = false;
                }
                
                // Filter Length
                if(passedFilters && sp.getBoolean("filter_length_enabled", false)){
                    boolean match = false;
                    String lengthList = sp.getString("filter_length_list", "");
                    if(!lengthList.isEmpty()){
                        String[] lengths = lengthList.split(",");
                        for(String len : lengths){
                            try{
                                if(!len.trim().isEmpty() && messageBody.length() == Integer.parseInt(len.trim())){
                                    match = true;
                                    break;
                                }
                            }catch(Exception e){}
                        }
                    }
                    if(!match) passedFilters = false;
                }

                // Stream A (Primary) - Gatekeeper & Dispatcher
                if(sp.getBoolean("gateway_on",true)) {
                    if(passedFilters) {
                        if (sp.getBoolean("enable_stream_a", true)) {
                            String url = sp.getString("urlPost", null);
                            if(url!=null) {
                                // Delegate to sendPOST which handles Encryption logic
                                sendPOST(url, messageFrom, messageBody, "received", context, messageTimestamp);
                            }
                        }
                    }
                    else
                        writeLog("SMS: FILTERED : " + messageFrom + " " + messageBody,context);
                } else {
                    writeLog("GATEWAY OFF: SMS NOT POSTED TO SERVER", context);
                }
            }
        }
    }

    public static void sendPOST(String urlPost,String from, String msg,String tipe, Context context, String msgTimestamp){
        if(urlPost==null) return;
        if(from == null) from = "";
        if(!urlPost.startsWith("http")) return;
        
        SharedPreferences sp = context.getSharedPreferences("pref", 0);
        int timeout = sp.getInt("network_timeout", 15);
        boolean logSuccess = sp.getBoolean("log_post_success", false);
        boolean encrypt = sp.getBoolean("enable_encryption", false);
        
        try {
            JSONObject json = new JSONObject();
            json.put("from", from);
            json.put("message", msg);
            json.put("type", tipe);
            json.put("timestamp", msgTimestamp);
            
            String finalPayload;
            
            if (encrypt) {
                String key = SecurityUtil.getSharedKey(context);
                if (key != null) {
                    JSONObject wrapper = new JSONObject();
                    wrapper.put("payload", SecurityUtil.encrypt(json.toString(), key));
                    wrapper.put("is_encrypted", true);
                    finalPayload = wrapper.toString();
                } else {
                    // Fallback if key missing but encryption enabled
                    json.put("is_encrypted", false);
                    finalPayload = json.toString();
                }
            } else {
                json.put("is_encrypted", false);
                finalPayload = json.toString();
            }
            
            PostQueueManager.enqueue(context, urlPost, finalPayload, "application/json", logSuccess, timeout);
            
        } catch (Exception e) {
            e.printStackTrace();
            writeLog("SMS: POST ERROR : " + e.getMessage(), context);
        }
    }
}

