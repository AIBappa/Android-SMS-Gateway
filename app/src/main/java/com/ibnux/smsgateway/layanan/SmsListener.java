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
        String url = sp.getString("urlPost",null);
        String backupUrl = sp.getString("backup_url", null);
        
        if (Telephony.Sms.Intents.SMS_DELIVER_ACTION.equals(intent.getAction())) {
            for (SmsMessage smsMessage : Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                String messageFrom = smsMessage.getOriginatingAddress();
                String messageBody = smsMessage.getMessageBody();
                String messageTimestamp = smsMessage.getTimestampMillis()+"";
                Log.i("SMS From", messageFrom);
                Log.i("SMS Body", messageBody);
                writeLog("SMS: RECEIVED : " + messageFrom + " " + messageBody,context);
                
                // Construct JSON Payload for Backup and Encrypted streams
                JSONObject jsonPayload = new JSONObject();
                try {
                    jsonPayload.put("from", messageFrom);
                    jsonPayload.put("message", messageBody);
                    jsonPayload.put("type", "received");
                    jsonPayload.put("timestamp", messageTimestamp);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                // Stream B (Backup): No filters. Send raw, unencrypted JSON to the backup_URL.
                if (backupUrl != null && !backupUrl.isEmpty()) {
                     BackupSendService.startBackupSend(context, backupUrl, jsonPayload.toString());
                }

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

                if(url!=null){
                    if(sp.getBoolean("gateway_on",true)) {
                        if(passedFilters) {
                            // Stream A (Primary)
                            String sharedKey = SecurityUtil.getSharedKey(context);
                            if (sharedKey != null) {
                                // Encrypt and Send
                                sendEncrypted(context, url, jsonPayload.toString(), sharedKey);
                            } else {
                                // Fallback to legacy or fail? 
                                // Requirement: "Stream A... encrypt... key (derived from 'Shared Secret Key')"
                                // If no key, we can't fulfill Stream A requirement.
                                // But to maintain backward compatibility, we might want to send legacy format.
                                // However, user requested "Implement a Bifurcated Data Engine".
                                // I will assume if key is missing, we use legacy sendPOST (form data).
                                sendPOST(url, messageFrom, messageBody,"received",context,messageTimestamp);
                            }
                        }
                        else
                            writeLog("SMS: FILTERED : " + messageFrom + " " + messageBody,context);
                    }else{
                        writeLog("GATEWAY OFF: SMS NOT POSTED TO SERVER", context);
                    }

                }else{
                    Log.i("SMS URL", "URL not SET");
                }
            }
        }
    }

    static class postDataTask extends AsyncTask<String, Void, String> {
        // Legacy or Encrypted Task
        // If args[2] is "JSON", send as JSON. Else form-data.
        
        private Context context;
        
        public postDataTask(Context context) {
            this.context = context;
        }

        protected String doInBackground(String... datas) {
            URL url;
            String response = "";
            String targetUrl = datas[0];
            String payload = datas[1];
            String contentType = datas.length > 2 ? datas[2] : "application/x-www-form-urlencoded";
            
            try {
                try {
                    url = new URL(targetUrl);

                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setReadTimeout(15000);
                    conn.setConnectTimeout(15000);
                    conn.setRequestMethod("POST");
                    conn.setDoInput(true);
                    conn.setDoOutput(true);
                    if (contentType != null) {
                        conn.setRequestProperty("Content-Type", contentType);
                    }

                    OutputStream os = conn.getOutputStream();
                    BufferedWriter writer = new BufferedWriter(
                            new OutputStreamWriter(os, "UTF-8"));
                    writer.write(payload);

                    writer.flush();
                    writer.close();
                    os.close();
                    int responseCode=conn.getResponseCode();

                    if (responseCode == HttpsURLConnection.HTTP_OK) {
                        String line;
                        BufferedReader br=new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        while ((line=br.readLine()) != null) {
                            response+=line;
                        }
                    }
                    else {
                        GatewayLogger.log(context, "INTERRUPT", "PRIMARY_FAIL: HTTP " + responseCode + " for Path A.");
                        response="HTTP Error: " + responseCode;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    GatewayLogger.log(context, "INTERRUPT", "PRIMARY_FAIL: " + e.getMessage() + " for Path A.");
                    return "SMS: POST FAILED : "+targetUrl+" : "+e.getMessage();
                }

                return "SMS: POST : "+targetUrl+" : "+response;
            }catch (Exception e){
                e.printStackTrace();
                GatewayLogger.log(context, "INTERRUPT", "PRIMARY_FAIL: " + e.getMessage() + " for Path A.");
                return "SMS: POST FAILED : "+targetUrl+" : "+e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String response) {
            writeLog(response, context);
        }
    }

    public static void sendEncrypted(Context context, String urlPost, String jsonBody, String sharedKey) {
        try {
            String encrypted = SecurityUtil.encrypt(jsonBody, sharedKey);
            JSONObject payload = new JSONObject();
            payload.put("payload", encrypted);
            
            new postDataTask(context).execute(urlPost, payload.toString(), "application/json");
        } catch (Exception e) {
            e.printStackTrace();
            GatewayLogger.log(context, "ERROR", "Encryption Failed: " + e.getMessage());
            writeLog("SMS: ENCRYPTION FAILED", context);
        }
    }


    public static void sendPOST(String urlPost,String from, String msg,String tipe, Context context, String msgTimestamp){
        if(urlPost==null) return;
        if(from.isEmpty()) return;
        if(!urlPost.startsWith("http")) return;
        try {
            new postDataTask(context).execute(urlPost,
                    "number="+URLEncoder.encode(from, "UTF-8")+
                            "&message="+URLEncoder.encode(msg, "UTF-8")+
                            "&type=" + URLEncoder.encode(tipe, "UTF-8") +
                            "&timestamp=" + URLEncoder.encode(msgTimestamp, "UTF-8")
            );
        }catch (Exception e){
            e.printStackTrace();
            writeLog("SMS: POST FAILED : "+urlPost+" : "+e.getMessage(),context);
        }
    }

}

