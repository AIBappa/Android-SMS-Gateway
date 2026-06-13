package com.ibnux.smsgateway;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.FileProvider;

import com.ibnux.smsgateway.BuildConfig;
import com.ibnux.smsgateway.Utils.Fungsi;
import com.ibnux.smsgateway.Utils.GatewayLogger;
import com.ibnux.smsgateway.Utils.SecurityUtil;
import com.ibnux.smsgateway.data.ActionLog;
import com.ibnux.smsgateway.data.ActionLog_;
import com.ibnux.smsgateway.layanan.PushService;
import com.ibnux.smsgateway.layanan.UssdService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import io.objectbox.Box;

public class SettingsFragment extends Fragment {

    private LinearLayout layoutMainSettings;
    private LinearLayout layoutSubMenu;
    private LinearLayout containerSubMenuContent;
    private TextView tvSubMenuTitle;
    private SharedPreferences sp;
    private Box<ActionLog> actionLogBox;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        sp = requireContext().getSharedPreferences("pref", 0);
        actionLogBox = ObjectBox.get().boxFor(ActionLog.class);

        layoutMainSettings = view.findViewById(R.id.layoutMainSettings);
        layoutSubMenu = view.findViewById(R.id.layoutSubMenu);
        containerSubMenuContent = view.findViewById(R.id.containerSubMenuContent);
        tvSubMenuTitle = view.findViewById(R.id.tvSubMenuTitle);
        
        view.findViewById(R.id.btnCloseSubMenu).setOnClickListener(v -> closeSubMenu());

        view.findViewById(R.id.btnMenuPushUssd).setOnClickListener(v -> openSubMenu("Push & USSD Messaging", 1));
        view.findViewById(R.id.btnMenuSmsWebhook).setOnClickListener(v -> openSubMenu("SMS Webhook", 2));
        view.findViewById(R.id.btnMenuUnifiedLog).setOnClickListener(v -> openSubMenu("Unified & System Logs", 3));
        view.findViewById(R.id.btnMenuSystem).setOnClickListener(v -> openSubMenu("System Settings", 4));
    }

    private void openSubMenu(String title, int menuId) {
        tvSubMenuTitle.setText(title);
        layoutMainSettings.setVisibility(View.GONE);
        layoutSubMenu.setVisibility(View.VISIBLE);
        containerSubMenuContent.removeAllViews();
        
        switch (menuId) {
            case 1: setupPushUssdMenu(); break;
            case 2: setupSmsWebhookMenu(); break;
            case 3: setupUnifiedLogMenu(); break;
            case 4: setupSystemSettingsMenu(); break;
        }
    }

    private void closeSubMenu() {
        layoutSubMenu.setVisibility(View.GONE);
        layoutMainSettings.setVisibility(View.VISIBLE);
    }

    // --- 3.1 Push & USSD Messaging ---
    private void setupPushUssdMenu() {
        Context ctx = requireContext();

        // a) Secret ID
        addLockableField("Secret ID", sp.getString("secret", ""), value -> {
            sp.edit().putString("secret", value).commit();
            logAction("Changed Secret ID", "New Secret set manually");
        });
        addButton("Generate New Secret", v -> {
            showGenerateConfirmationDialog(ctx, "Secret", () -> {
                String newSecret = UUID.randomUUID().toString();
                sp.edit().putString("secret", newSecret).commit();
                logAction("Generated Secret", "New Secret generated");
                Toast.makeText(ctx, "New Secret Generated", Toast.LENGTH_SHORT).show();
                setupPushUssdMenu(); // Refresh UI
            });
        });

        // b) Device ID
        addReadOnlyField("Device ID (Token)", sp.getString("token", "Not available yet"));

        // c) USSD Test
        addButton("Test USSD", v -> showUssdTestDialog());

        // d) USSD Permission
        addButton("USSD Permission Settings", v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            logAction("USSD Permission", "Opened Accessibility Settings");
        });

        // e) Push USSD URL
        addLockableField("Push USSD URL", sp.getString("urlUssd", ""), value -> {
            sp.edit().putString("urlUssd", value).commit();
            logAction("Updated USSD URL", value);
        });
        
        // f) Change Expired
        addLockableField("Request Expiry (Seconds)", String.valueOf(sp.getInt("expired", 3600)), value -> {
            try {
                int exp = Integer.parseInt(value);
                if(exp < 5) exp = 5;
                sp.edit().putInt("expired", exp).commit();
                logAction("Updated Expiry", "New value: " + exp);
            } catch (Exception e) {}
        });

        // --- WebSocket Tunnel Section ---
        TextView wsInfo = new TextView(ctx);
        wsInfo.setTypeface(null, android.graphics.Typeface.BOLD);
        wsInfo.setTextSize(16);
        wsInfo.setText("WebSocket Tunnel (Replaces Firebase Push)");
        wsInfo.setPadding(0, 30, 0, 10);
        containerSubMenuContent.addView(wsInfo);

        addLockableField("WebSocket Server URL (wss://)", sp.getString("websocket_url", ""), value -> {
            sp.edit().putString("websocket_url", value).commit();
            logAction("Updated WebSocket URL", value);
        });

        // WebSocket Toggle
        final boolean wsConnected = com.ibnux.smsgateway.layanan.WebSocketService.isRunning;
        Switch swWebSocket = new Switch(ctx);
        swWebSocket.setText("Enable WebSocket Tunnel");
        swWebSocket.setChecked(wsConnected);
        swWebSocket.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                com.ibnux.smsgateway.layanan.WebSocketService.start(ctx);
                logAction("WebSocket Tunnel", "Started");
            } else {
                com.ibnux.smsgateway.layanan.WebSocketService.stop(ctx);
                logAction("WebSocket Tunnel", "Stopped");
            }
        });
        containerSubMenuContent.addView(swWebSocket);

        addReadOnlyField("Note", "Pushes your server's SMS/USSD commands to this device via a persistent WebSocket tunnel. No Firebase required.");
    }

    // --- 3.2 SMS Webhook ---
    private void setupSmsWebhookMenu() {
        Context ctx = requireContext();

        // --- Section: Dispatcher & Gatekeeper ---
        LinearLayout dispatchSection = createSection(ctx, "Dispatcher & Gatekeeper");

        // Timeout
        addLockableFieldToLayout(dispatchSection, "Network Timeout (Seconds)", String.valueOf(sp.getInt("network_timeout", 15)), value -> {
            try {
                int timeout = Integer.parseInt(value);
                if(timeout < 1) timeout = 1;
                sp.edit().putInt("network_timeout", timeout).commit();
                logAction("Updated Timeout", "New value: " + timeout);
            } catch (Exception e) {}
        });

        // Primary URL
        Switch swPrimary = new Switch(ctx);
        swPrimary.setText("Enable Primary URL");
        swPrimary.setChecked(sp.getBoolean("enable_stream_a", true));
        swPrimary.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean("enable_stream_a", isChecked).apply();
        });
        dispatchSection.addView(swPrimary);

        addLockableFieldToLayout(dispatchSection, "Receiver URL (Primary)", sp.getString("urlPost", ""), value -> {
            sp.edit().putString("urlPost", value).commit();
            logAction("Updated Receiver URL", value);
        });

        // Backup URL
        Switch swBackup = new Switch(ctx);
        swBackup.setText("Enable Backup URL");
        swBackup.setChecked(sp.getBoolean("enable_stream_b", false));
        swBackup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean("enable_stream_b", isChecked).apply();
        });
        dispatchSection.addView(swBackup);

        addLockableFieldToLayout(dispatchSection, "Backup URL", sp.getString("backup_url", ""), value -> {
            sp.edit().putString("backup_url", value).commit();
            logAction("Updated Backup URL", value);
        });

        containerSubMenuContent.addView(dispatchSection);

        // --- Stream A Section: Bearer Token ---
        LinearLayout streamABearerSection = createSection(ctx, "Stream A: Bearer Token");
        
        Switch swBearerA = new Switch(ctx);
        swBearerA.setText("Enable Bearer Token");
        swBearerA.setChecked(sp.getBoolean("enable_bearer_a", false));
        swBearerA.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean("enable_bearer_a", isChecked).apply();
            logAction("Bearer A Toggle", "Set to " + isChecked);
        });
        streamABearerSection.addView(swBearerA);

        String currentBearerA = SecurityUtil.getBearerToken(ctx);
        addLockableSecretFieldToLayout(streamABearerSection, "Bearer Token",
                currentBearerA != null ? currentBearerA : "",
                () -> {
                    String newToken = SecurityUtil.generateBearerToken();
                    SecurityUtil.saveBearerToken(ctx, newToken);
                    logAction("Updated Bearer Token A", "Generated and saved new token");
                    return newToken;
                },
                value -> {
                    SecurityUtil.saveBearerToken(ctx, value);
                    logAction("Updated Bearer Token A", "New token saved");
                },
                () -> SecurityUtil.getBearerToken(ctx),
                "Bearer Token",
                ctx);

        containerSubMenuContent.addView(streamABearerSection);

        // --- Stream A Section: HMAC-SHA256 Signing ---
        LinearLayout streamAHmacSection = createSection(ctx, "Stream A: HMAC-SHA256 Signing");
        
        Switch swHmac = new Switch(ctx);
        swHmac.setText("Enable HMAC Signing");
        swHmac.setChecked(sp.getBoolean("enable_hmac_signing", false));
        swHmac.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean("enable_hmac_signing", isChecked).apply();
            logAction("HMAC Toggle", "Set to " + isChecked);
        });
        streamAHmacSection.addView(swHmac);

        String currentHmacKey = SecurityUtil.getHmacKey(ctx);
        addLockableSecretFieldToLayout(streamAHmacSection, "HMAC Key (Base64)",
                currentHmacKey != null ? currentHmacKey : "",
                () -> {
                    String newKey = SecurityUtil.generateHmacKey();
                    SecurityUtil.saveHmacKey(ctx, newKey);
                    logAction("Updated HMAC Key", "Generated and saved new key");
                    return newKey;
                },
                value -> {
                    SecurityUtil.saveHmacKey(ctx, value);
                    logAction("Updated HMAC Key", "New key saved");
                },
                () -> SecurityUtil.getHmacKey(ctx),
                "HMAC Key",
                ctx);

        containerSubMenuContent.addView(streamAHmacSection);

        // --- Stream A Section: AES-GCM Encryption ---
        LinearLayout streamAAesSection = createSection(ctx, "Stream A: AES-GCM Encryption");
        
        Switch swEncryption = new Switch(ctx);
        swEncryption.setText("Enable Encryption");
        swEncryption.setChecked(sp.getBoolean("enable_encryption", false));
        swEncryption.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean("enable_encryption", isChecked).apply();
            logAction("Encryption Toggle", "Set to " + isChecked);
        });
        streamAAesSection.addView(swEncryption);

        String currentKey = SecurityUtil.getSharedKey(ctx);
        addLockableSecretFieldToLayout(streamAAesSection, "AES Key (Base64)",
                currentKey != null ? currentKey : "",
                () -> {
                    String newKey = SecurityUtil.generateNewKey();
                    SecurityUtil.saveKey(ctx, newKey);
                    logAction("Updated AES Key", "Generated and saved new key");
                    return newKey;
                },
                value -> {
                    SecurityUtil.saveKey(ctx, value);
                    logAction("Updated AES Key", "New key saved");
                },
                () -> SecurityUtil.getSharedKey(ctx),
                "AES Key",
                ctx);

        containerSubMenuContent.addView(streamAAesSection);

        // --- Stream B Section: Bearer Token ---
        LinearLayout streamBBearerSection = createSection(ctx, "Stream B: Bearer Token");
        
        Switch swBearerB = new Switch(ctx);
        swBearerB.setText("Enable Bearer Token");
        swBearerB.setChecked(sp.getBoolean("enable_bearer_b", false));
        swBearerB.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean("enable_bearer_b", isChecked).apply();
            logAction("Bearer B Toggle", "Set to " + isChecked);
        });
        streamBBearerSection.addView(swBearerB);

        String currentBearerB = SecurityUtil.getBearerTokenStreamB(ctx);
        addLockableSecretFieldToLayout(streamBBearerSection, "Bearer Token",
                currentBearerB != null ? currentBearerB : "",
                () -> {
                    String newToken = SecurityUtil.generateBearerToken();
                    SecurityUtil.saveBearerTokenStreamB(ctx, newToken);
                    logAction("Updated Bearer Token B", "Generated and saved new token");
                    return newToken;
                },
                value -> {
                    SecurityUtil.saveBearerTokenStreamB(ctx, value);
                    logAction("Updated Bearer Token B", "New token saved");
                },
                () -> SecurityUtil.getBearerTokenStreamB(ctx),
                "Bearer Token",
                ctx);

        containerSubMenuContent.addView(streamBBearerSection);

        // --- Filters Section ---
        LinearLayout filterSection = createSection(ctx, "Filters");

        // Checkbox to apply filters to Stream B
        CheckBox cbFilterStreamB = new CheckBox(ctx);
        cbFilterStreamB.setText("Also apply filters to Stream B (Backup URL)");
        cbFilterStreamB.setChecked(sp.getBoolean("filter_apply_to_stream_b", false));
        cbFilterStreamB.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean("filter_apply_to_stream_b", isChecked).apply();
        });
        filterSection.addView(cbFilterStreamB);

        addButtonToLayout(filterSection, "Filter: Country Codes", v -> showCountryFilterDialog());
        addButtonToLayout(filterSection, "Filter: Message Prefix", v -> showFilterDialog("Allowed SMS Prefixes", "filter_prefix_enabled", "filter_prefix_list", false));
        addButtonToLayout(filterSection, "Filter: Message Length", v -> showFilterDialog("Allowed Message Lengths", "filter_length_enabled", "filter_length_list", true));
        containerSubMenuContent.addView(filterSection);
    }

    // --- 3.3 Unified & System Logs ---
    private int auditOffset = 0;
    private int systemLogOffset = 0;
    private static final int PAGE_SIZE = 50;

    private void setupUnifiedLogMenu() {
        Context ctx = requireContext();
        
        // --- Section 1: Audit Log (User Actions) ---
        LinearLayout auditSection = createSection(ctx, "Audit Log (User Actions)");
        
        TextView logView = new TextView(ctx);
        TextView auditPageIndicator = new TextView(ctx);
        auditPageIndicator.setGravity(android.view.Gravity.CENTER);
        
        updateAuditLogView(logView, auditPageIndicator);
        logView.setPadding(10, 10, 10, 10);
        auditSection.addView(logView);
        auditSection.addView(auditPageIndicator);

        LinearLayout auditNavButtons = new LinearLayout(ctx);
        auditNavButtons.setOrientation(LinearLayout.HORIZONTAL);
        auditNavButtons.setGravity(android.view.Gravity.CENTER);
        
        Button btnPrevAudit = new Button(ctx);
        btnPrevAudit.setText("< Newer");
        btnPrevAudit.setOnClickListener(v -> {
            if (auditOffset >= PAGE_SIZE) {
                auditOffset -= PAGE_SIZE;
                updateAuditLogView(logView, auditPageIndicator);
            }
        });
        
        Button btnNextAudit = new Button(ctx);
        btnNextAudit.setText("Older >");
        btnNextAudit.setOnClickListener(v -> {
            // Check if there are more logs before advancing
            if (actionLogBox.count() > auditOffset + PAGE_SIZE) {
                auditOffset += PAGE_SIZE;
                updateAuditLogView(logView, auditPageIndicator);
            }
        });
        
        auditNavButtons.addView(btnPrevAudit);
        auditNavButtons.addView(btnNextAudit);
        auditSection.addView(auditNavButtons);

        LinearLayout auditActionButtons = new LinearLayout(ctx);
        auditActionButtons.setOrientation(LinearLayout.HORIZONTAL);
        
        Button btnShareAudit = new Button(ctx);
        btnShareAudit.setText("Share Page");
        btnShareAudit.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, logView.getText().toString());
            startActivity(Intent.createChooser(intent, "Share Audit Log"));
        });
        
        Button btnClearAudit = new Button(ctx);
        btnClearAudit.setText("Clear DB");
        btnClearAudit.setOnClickListener(v -> {
            actionLogBox.removeAll();
            auditOffset = 0;
            updateAuditLogView(logView, auditPageIndicator);
            Toast.makeText(ctx, "Audit Log Cleared", Toast.LENGTH_SHORT).show();
        });

        auditActionButtons.addView(btnShareAudit);
        auditActionButtons.addView(btnClearAudit);
        auditSection.addView(auditActionButtons);
        
        containerSubMenuContent.addView(auditSection);

        // --- Section 2: System Log (Failures/Errors) ---
        LinearLayout systemLogSection = createSection(ctx, "System Log (Failures Only)");
        
        TextView sysLogView = new TextView(ctx);
        TextView sysPageIndicator = new TextView(ctx);
        sysPageIndicator.setGravity(android.view.Gravity.CENTER);
        
        updateSystemLogView(ctx, sysLogView, sysPageIndicator);
        sysLogView.setPadding(10, 10, 10, 10);
        systemLogSection.addView(sysLogView);
        systemLogSection.addView(sysPageIndicator);

        LinearLayout sysNavButtons = new LinearLayout(ctx);
        sysNavButtons.setOrientation(LinearLayout.HORIZONTAL);
        sysNavButtons.setGravity(android.view.Gravity.CENTER);

        Button btnPrevSys = new Button(ctx);
        btnPrevSys.setText("< Newer");
        btnPrevSys.setOnClickListener(v -> {
            if (systemLogOffset >= PAGE_SIZE) {
                systemLogOffset -= PAGE_SIZE;
                updateSystemLogView(ctx, sysLogView, sysPageIndicator);
            }
        });
        
        Button btnNextSys = new Button(ctx);
        btnNextSys.setText("Older >");
        btnNextSys.setOnClickListener(v -> {
            systemLogOffset += PAGE_SIZE;
            updateSystemLogView(ctx, sysLogView, sysPageIndicator);
        });
        
        sysNavButtons.addView(btnPrevSys);
        sysNavButtons.addView(btnNextSys);
        systemLogSection.addView(sysNavButtons);

        LinearLayout sysActionButtons = new LinearLayout(ctx);
        sysActionButtons.setOrientation(LinearLayout.HORIZONTAL);

        Button btnShareSys = new Button(ctx);
        btnShareSys.setText("Share File");
        btnShareSys.setOnClickListener(v -> {
            try {
                File logFile = GatewayLogger.getLogFile(ctx);
                if(logFile.exists()) {
                    Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".provider", logFile);
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_STREAM, uri);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(intent, "Share System Log"));
                } else {
                    Toast.makeText(ctx, "Log file not found", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                 Toast.makeText(ctx, "Error sharing file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        
        Button btnClearSys = new Button(ctx);
        btnClearSys.setText("Clear File");
        btnClearSys.setOnClickListener(v -> {
             File logFile = GatewayLogger.getLogFile(ctx);
             if(logFile.exists()) {
                 logFile.delete();
                 systemLogOffset = 0;
                 updateSystemLogView(ctx, sysLogView, sysPageIndicator);
                 Toast.makeText(ctx, "System Log Cleared", Toast.LENGTH_SHORT).show();
             }
        });
        
        sysActionButtons.addView(btnShareSys);
        sysActionButtons.addView(btnClearSys);
        systemLogSection.addView(sysActionButtons);

        containerSubMenuContent.addView(systemLogSection);
    }

    private void updateAuditLogView(TextView tv, TextView indicator) {
        List<ActionLog> logs = actionLogBox.query().orderDesc(ActionLog_.time).build().find(auditOffset, PAGE_SIZE);
        StringBuilder sb = new StringBuilder();
        for(ActionLog log : logs) {
            sb.append("[").append(log.date).append("] ").append(log.action).append("\n");
            if(log.details != null && !log.details.isEmpty())
                sb.append("   ").append(log.details).append("\n");
            sb.append("\n");
        }
        if(logs.isEmpty()) sb.append(auditOffset == 0 ? "No actions recorded yet." : "End of logs.");
        tv.setText(sb.toString());
        
        long total = actionLogBox.count();
        long end = Math.min(auditOffset + PAGE_SIZE, total);
        indicator.setText("Showing " + (total > 0 ? auditOffset + 1 : 0) + "-" + end + " of " + total);
    }

    private void updateSystemLogView(Context ctx, TextView tv, TextView indicator) {
        File logFile = GatewayLogger.getLogFile(ctx);
        StringBuilder sb = new StringBuilder();
        int totalReadLines = 0;
        
        if(logFile.exists()) {
            try {
                // To paginate properly from "Newest", we read all lines into a list
                // For performance, we limit reading to the last 1000 lines if the file is huge
                // But simplified here: Read all, then sublist. 
                // A better approach for huge files would be RandomAccessFile, but complex to implement here.
                // Assuming log file is regularly cleared, simple list is fine.
                
                BufferedReader br = new BufferedReader(new FileReader(logFile));
                String line;
                LinkedList<String> allLines = new LinkedList<>();
                while ((line = br.readLine()) != null) {
                    allLines.add(line);
                }
                br.close();
                
                // Reverse to show newest first
                Collections.reverse(allLines);
                totalReadLines = allLines.size();
                
                int start = systemLogOffset;
                int end = Math.min(start + PAGE_SIZE, totalReadLines);
                
                if (start < totalReadLines) {
                    List<String> pageLines = allLines.subList(start, end);
                    for(String s : pageLines) sb.append(s).append("\n");
                } else {
                    sb.append(start == 0 ? "Log file empty." : "End of logs.");
                }
                
                indicator.setText("Showing " + (totalReadLines > 0 ? start + 1 : 0) + "-" + end + " of " + totalReadLines);

            } catch (IOException e) {
                sb.append("Error reading log file: ").append(e.getMessage());
            }
        } else {
            sb.append("Log file empty or not found.");
            indicator.setText("0-0 of 0");
        }
        tv.setText(sb.toString());
    }

    // --- 3.4 System Settings ---
    private void setupSystemSettingsMenu() {
        Context ctx = requireContext();

        // a) Default App
        boolean isDefault = Fungsi.isDefaultSmsApp(ctx);
        addReadOnlyField("Is Default SMS App?", isDefault ? "YES" : "NO");
        if(!isDefault) {
            addButton("Set as Default App", v -> Fungsi.requestDefaultSmsApp(ctx));
        }
        
        // --- Section 3: Maintenance & Storage ---
        LinearLayout maintSection = createSection(ctx, "Inbox Maintenance");

        // Info note about inbox scope
        final TextView tvNote = new TextView(ctx);
        tvNote.setText("Note: Inbox maintenance is only relevant for SMSes sent from this device. " +
                "Incoming SMSes from the SMS Webhook functionality are NOT stored on this device — " +
                "they are only forwarded to your configured webhook URL(s). " +
                "Use the Backup URL to ensure all incoming SMSes are independently stored.");
        tvNote.setPadding(10, 10, 10, 20);
        tvNote.setTextSize(13);
        tvNote.setTextColor(android.graphics.Color.parseColor("#666666"));
        maintSection.addView(tvNote);

        // Auto-Delete
        CheckBox cbAuto = new CheckBox(ctx);
        cbAuto.setText("Enable Auto-Delete");
        cbAuto.setChecked(sp.getBoolean("auto_delete_enabled", false));
        cbAuto.setOnCheckedChangeListener((v, isChecked) -> sp.edit().putBoolean("auto_delete_enabled", isChecked).apply());
        
        EditText etHours = new EditText(ctx);
        etHours.setHint("Retention Hours (1-3000)");
        etHours.setInputType(InputType.TYPE_CLASS_NUMBER);
        etHours.setText(sp.getString("auto_delete_hours", ""));
        
        Button btnSaveAuto = new Button(ctx);
        btnSaveAuto.setText("Save Settings");
        btnSaveAuto.setOnClickListener(v -> {
            sp.edit().putString("auto_delete_hours", etHours.getText().toString()).apply();
            Toast.makeText(ctx, "Settings Saved", Toast.LENGTH_SHORT).show();
        });
        
        Button btnRun = new Button(ctx);
        btnRun.setText("Start Auto-Delete");
        btnRun.setOnClickListener(v -> {
            try {
                String hStr = etHours.getText().toString();
                if(hStr.isEmpty()) hStr = sp.getString("auto_delete_hours", "24");
                
                int hours = Integer.parseInt(hStr);
                long cutoffTime = System.currentTimeMillis() - (hours * 3600000L);
                int deletedCount = ctx.getContentResolver().delete(
                        Uri.parse("content://sms"),
                        "date < ?",
                        new String[]{String.valueOf(cutoffTime)}
                );
                logAction("Auto-Delete Run", "Deleted " + deletedCount + " messages > " + hours + "h old");
                Toast.makeText(ctx, "Deleted " + deletedCount + " messages", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(ctx, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        maintSection.addView(cbAuto);
        maintSection.addView(etHours);
        maintSection.addView(btnSaveAuto);
        maintSection.addView(btnRun);

        // Inbox Capacity
        Button btnCapacity = new Button(ctx);
        btnCapacity.setText("Check Inbox Capacity");
        btnCapacity.setOnClickListener(v -> {
            try {
                android.database.Cursor c = ctx.getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);
                int count = 0;
                if(c != null) {
                    count = c.getCount();
                    c.close();
                }
                String msg = "Total Inbox: " + count + " messages";
                GatewayLogger.log(ctx, "AUDIT", msg);
                Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(ctx, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        maintSection.addView(btnCapacity);
        
        containerSubMenuContent.addView(maintSection);

        // --- Section 4: Logging & Live Stream ---
        LinearLayout loggingSection = createSection(ctx, "Logging & Stream");
        
        addEditableFieldToLayout(loggingSection, "Live Stream Max Entries", String.valueOf(sp.getInt("live_stream_max", 100)), value -> {
            try {
                int max = Integer.parseInt(value);
                if(max < 10) max = 10;
                sp.edit().putInt("live_stream_max", max).commit();
            } catch (Exception e) {}
        });
        
        Switch swLogSuccess = new Switch(ctx);
        swLogSuccess.setText("Log Successful POSTs");
        swLogSuccess.setChecked(sp.getBoolean("log_post_success", false));
        swLogSuccess.setOnCheckedChangeListener((v, isChecked) -> sp.edit().putBoolean("log_post_success", isChecked).apply());
        loggingSection.addView(swLogSuccess);
        
        containerSubMenuContent.addView(loggingSection);

        // c) Battery — Status-aware battery optimization section
        Context ctxBatt = requireContext();
        PowerManager pm = (PowerManager) ctxBatt.getSystemService(Context.POWER_SERVICE);
        boolean isIgnoring = pm != null && pm.isIgnoringBatteryOptimizations(ctxBatt.getPackageName());

        LinearLayout batterySection = new LinearLayout(ctxBatt);
        batterySection.setOrientation(LinearLayout.VERTICAL);
        batterySection.setPadding(0, 10, 0, 10);

        TextView batteryTitle = new TextView(ctxBatt);
        batteryTitle.setText("Battery Optimization");
        batteryTitle.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView batteryStatus = new TextView(ctxBatt);
        if (isIgnoring) {
            batteryStatus.setText("✓ Disabled (app can run in background)");
            batteryStatus.setTextColor(0xFF2E7D32); // green
        } else {
            batteryStatus.setText("✗ Active (app may be killed)");
            batteryStatus.setTextColor(0xFFC62828); // red
        }
        batteryStatus.setTextSize(14);

        batterySection.addView(batteryTitle);
        batterySection.addView(batteryStatus);

        // Always show a button to re-request or open system battery settings
        Button battBtn = new Button(ctxBatt);
        if (isIgnoring) {
            battBtn.setText("Open System Battery Settings");
        } else {
            battBtn.setText("Request Disable Battery Optimization");
        }
        battBtn.setOnClickListener(v -> {
            if (pm != null && !pm.isIgnoringBatteryOptimizations(ctxBatt.getPackageName())) {
                // First try the standard API
                try {
                    startActivity(new Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + BuildConfig.APPLICATION_ID)
                    ));
                } catch (Exception e) {
                    // Fallback: open system battery settings page
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    startActivity(intent);
                }
            } else {
                // Already exempted — open system battery settings for further tweaks
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
            }
        });
        batterySection.addView(battBtn);

        containerSubMenuContent.addView(batterySection);
    }

    // --- Helpers ---

    /**
     * Lockable field with Edit/Save toggle.
     * Locked state: Edit button visible, field greyed out.
     * Editing state: Save button visible, field enabled.
     */
    private void addLockableField(String label, String value, OnSaveListener listener) {
        Context ctx = requireContext();
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 10, 0, 10);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);

        EditText et = new EditText(ctx);
        et.setText(value);
        setFieldLocked(et, true);

        LinearLayout buttonRow = new LinearLayout(ctx);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnEdit = new Button(ctx);
        btnEdit.setText("\u270E");

        Button btnSave = new Button(ctx);
        btnSave.setText("Save");
        btnSave.setVisibility(View.GONE);

        btnEdit.setOnClickListener(v -> {
            setFieldLocked(et, false);
            btnEdit.setVisibility(View.GONE);
            btnSave.setVisibility(View.VISIBLE);
        });

        btnSave.setOnClickListener(v -> {
            listener.onSave(et.getText().toString());
            setFieldLocked(et, true);
            btnEdit.setVisibility(View.VISIBLE);
            btnSave.setVisibility(View.GONE);
            Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show();
        });

        buttonRow.addView(btnEdit);
        buttonRow.addView(btnSave);
        layout.addView(tv);
        layout.addView(et);
        layout.addView(buttonRow);
        containerSubMenuContent.addView(layout);
    }

    /**
     * Lockable field inside a section (LinearLayout parent).
     * Locked state: Edit button visible, field greyed out.
     * Editing state: Save button visible, field enabled.
     */
    private void addLockableFieldToLayout(LinearLayout parent, String label, String value, OnSaveListener listener) {
        Context ctx = requireContext();
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);

        EditText et = new EditText(ctx);
        et.setText(value);
        setFieldLocked(et, true);

        LinearLayout buttonRow = new LinearLayout(ctx);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnEdit = new Button(ctx);
        btnEdit.setText("\u270E");

        Button btnSave = new Button(ctx);
        btnSave.setText("Save");
        btnSave.setVisibility(View.GONE);

        btnEdit.setOnClickListener(v -> {
            setFieldLocked(et, false);
            btnEdit.setVisibility(View.GONE);
            btnSave.setVisibility(View.VISIBLE);
        });

        btnSave.setOnClickListener(v -> {
            listener.onSave(et.getText().toString());
            setFieldLocked(et, true);
            btnEdit.setVisibility(View.VISIBLE);
            btnSave.setVisibility(View.GONE);
            Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show();
        });

        buttonRow.addView(btnEdit);
        buttonRow.addView(btnSave);
        parent.addView(tv);
        parent.addView(et);
        parent.addView(buttonRow);
    }

    /**
     * Lockable secret field with Generate/Copy (in locked state) and Edit/Save toggle.
     * Locked state: Edit, Generate, Copy buttons visible; field greyed out.
     * Editing state: Save button visible; Edit, Generate, Copy hidden; field enabled.
     * Generate triggers a 4-digit confirmation dialog before generating.
     */
    private void addLockableSecretFieldToLayout(LinearLayout parent, String hint,
                                                String currentValue,
                                                final GenerateAction generateAction,
                                                final OnSaveListener saveAction,
                                                final CopyAction copyAction,
                                                String generateItemName,
                                                Context ctx) {
        EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setText(currentValue);
        setFieldLocked(et, true);

        LinearLayout buttonRow = new LinearLayout(ctx);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnEdit = new Button(ctx);
        btnEdit.setText("\u270E");

        Button btnSave = new Button(ctx);
        btnSave.setText("Save");
        btnSave.setVisibility(View.GONE);

        Button btnGenerate = new Button(ctx);
        btnGenerate.setText("Generate");

        Button btnCopy = new Button(ctx);
        btnCopy.setText("Copy");

        btnEdit.setOnClickListener(v -> {
            setFieldLocked(et, false);
            btnEdit.setVisibility(View.GONE);
            btnGenerate.setVisibility(View.GONE);
            btnCopy.setVisibility(View.GONE);
            btnSave.setVisibility(View.VISIBLE);
        });

        btnSave.setOnClickListener(v -> {
            String val = et.getText().toString();
            if(!val.isEmpty()) {
                saveAction.onSave(val);
                setFieldLocked(et, true);
                btnEdit.setVisibility(View.VISIBLE);
                btnGenerate.setVisibility(View.VISIBLE);
                btnCopy.setVisibility(View.VISIBLE);
                btnSave.setVisibility(View.GONE);
                Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show();
            }
        });

        btnGenerate.setOnClickListener(v -> {
            showGenerateConfirmationDialog(ctx, generateItemName, () -> {
                String newValue = generateAction.generate();
                et.setText(newValue != null ? newValue : "");
                // Keep locked state with Edit, Generate, Copy visible
                setFieldLocked(et, true);
                btnEdit.setVisibility(View.VISIBLE);
                btnGenerate.setVisibility(View.VISIBLE);
                btnCopy.setVisibility(View.VISIBLE);
                btnSave.setVisibility(View.GONE);
                Toast.makeText(ctx, generateItemName + " Generated & Saved", Toast.LENGTH_SHORT).show();
            });
        });

        btnCopy.setOnClickListener(v -> {
            String activeValue = copyAction.getCopyValue();
            if(activeValue != null) {
                ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText(hint, activeValue);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(ctx, "Copied to Clipboard", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ctx, "No Active " + generateItemName, Toast.LENGTH_SHORT).show();
            }
        });

        buttonRow.addView(btnEdit);
        buttonRow.addView(btnSave);
        buttonRow.addView(btnGenerate);
        buttonRow.addView(btnCopy);
        parent.addView(et);
        parent.addView(buttonRow);
    }

    /**
     * Show a confirmation dialog with a random 4-digit number.
     * User must type the exact number to confirm the action.
     */
    private void showGenerateConfirmationDialog(Context ctx, String itemName, Runnable onConfirm) {
        SecureRandom random = new SecureRandom();
        int code = 1000 + random.nextInt(9000); // 4-digit number
        final String codeStr = String.valueOf(code);

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle("Generate New " + itemName);
        builder.setMessage("Type the following 4-digit number to confirm:\n\n" + code);

        final EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Enter " + codeStr.length() + " digits");

        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        layout.addView(input);
        builder.setView(layout);

        builder.setPositiveButton("Confirm", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String entered = input.getText().toString().trim();
            if (entered.equals(codeStr)) {
                onConfirm.run();
                dialog.dismiss();
            } else {
                input.setError("Incorrect number");
                Toast.makeText(ctx, "Numbers don't match. Try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Enable or disable a field with greyed-out appearance when locked.
     */
    private void setFieldLocked(EditText et, boolean locked) {
        et.setEnabled(!locked);
        et.setAlpha(locked ? 0.5f : 1.0f);
    }

    private void addReadOnlyField(String label, String value) {
        Context ctx = requireContext();
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 10, 0, 10);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvVal = new TextView(ctx);
        tvVal.setText(value);
        tvVal.setTextSize(16);

        layout.addView(tv);
        layout.addView(tvVal);
        containerSubMenuContent.addView(layout);
    }

    private void addButton(String label, View.OnClickListener listener) {
        Button btn = new Button(requireContext());
        btn.setText(label);
        btn.setOnClickListener(listener);
        containerSubMenuContent.addView(btn);
    }

    private void showUssdTestDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("SEND USSD");
        final EditText input = new EditText(requireContext());
        input.setText("*888#");
        builder.setView(input);
        builder.setPositiveButton("Call", (dialog, which) -> {
            String ussd = input.getText().toString();
            if(PushService.context==null) PushService.context = Aplikasi.app;
            PushService.queueUssd(ussd, 1);
            logAction("USSD Test", "Sent: " + ussd);
        });
        builder.show();
    }
    
    private void showFilterDialog(String title, String prefEnabled, String prefList, boolean isNumeric) {
        Context ctx = requireContext();
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(title);

        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        // Checkbox
        final CheckBox cbEnable = new CheckBox(ctx);
        cbEnable.setText("Enable Filter");
        cbEnable.setChecked(sp.getBoolean(prefEnabled, false));

        // Input Field
        final EditText etValues = new EditText(ctx);
        etValues.setHint(isNumeric ? "Enter lengths (e.g. 5, 6)" : "Enter values (comma separated)");
        etValues.setText(sp.getString(prefList, ""));
        if (isNumeric) {
            etValues.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_CLASS_TEXT);
            etValues.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,"));
        }

        layout.addView(cbEnable);
        layout.addView(etValues);
        builder.setView(layout);

        // Buttons
        builder.setPositiveButton("Save", null); // Set null to override later
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();

        // Override Positive Button to handle validation
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            boolean enabled = cbEnable.isChecked();
            String values = etValues.getText().toString().trim();

            if (enabled && values.isEmpty()) {
                etValues.setError("Value required when enabled");
                Toast.makeText(ctx, "Please enter values or disable the filter.", Toast.LENGTH_LONG).show();
            } else {
                sp.edit()
                        .putBoolean(prefEnabled, enabled)
                        .putString(prefList, values)
                        .apply();

                logAction("Updated " + title, "Enabled: " + enabled + ", Values: " + values);
                Toast.makeText(ctx, title + " Saved", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });
    }

    private void showCountryFilterDialog() {
        Context ctx = requireContext();
        String[] countries = new String[COUNTRY_CODES.length];
        boolean[] checkedItems = new boolean[COUNTRY_CODES.length];
        String currentList = sp.getString("filter_country_list", "");

        java.util.Set<String> currentSet = new java.util.HashSet<>();
        if (!currentList.isEmpty()) {
            String[] parts = currentList.split(",");
            for (String part : parts) currentSet.add(part.trim());
        }

        for (int i = 0; i < COUNTRY_CODES.length; i++) {
            countries[i] = COUNTRY_CODES[i][0] + " (+" + COUNTRY_CODES[i][1] + ")";
            String code = COUNTRY_CODES[i][1];
            if (currentSet.contains("+" + code) || currentSet.contains("00" + code)) {
                checkedItems[i] = true;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle("Select Allowed Countries");
        builder.setMultiChoiceItems(countries, checkedItems, (dialog, which, isChecked) -> {
            checkedItems[which] = isChecked;
        });

        builder.setPositiveButton("Save", (dialog, which) -> {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (int i = 0; i < COUNTRY_CODES.length; i++) {
                if (checkedItems[i]) {
                    if (!first) sb.append(",");
                    sb.append("+").append(COUNTRY_CODES[i][1]).append(",");
                    sb.append("00").append(COUNTRY_CODES[i][1]);
                    first = false;
                }
            }
            String result = sb.toString();
            boolean enabled = !result.isEmpty();
            sp.edit()
                    .putBoolean("filter_country_enabled", enabled)
                    .putString("filter_country_list", result)
                    .apply();

            logAction("Updated Allowed Countries", "Enabled: " + enabled + ", List: " + result);
            Toast.makeText(ctx, "Allowed Countries Saved", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private static final String[][] COUNTRY_CODES = {
            {"Afghanistan", "93"},
            {"Albania", "355"},
            {"Algeria", "213"},
            {"Argentina", "54"},
            {"Australia", "61"},
            {"Austria", "43"},
            {"Bangladesh", "880"},
            {"Belgium", "32"},
            {"Brazil", "55"},
            {"Canada", "1"},
            {"China", "86"},
            {"Denmark", "45"},
            {"Egypt", "20"},
            {"Finland", "358"},
            {"France", "33"},
            {"Germany", "49"},
            {"Greece", "30"},
            {"Hong Kong", "852"},
            {"India", "91"},
            {"Indonesia", "62"},
            {"Iran", "98"},
            {"Iraq", "964"},
            {"Ireland", "353"},
            {"Israel", "972"},
            {"Italy", "39"},
            {"Japan", "81"},
            {"Malaysia", "60"},
            {"Mexico", "52"},
            {"Netherlands", "31"},
            {"New Zealand", "64"},
            {"Norway", "47"},
            {"Pakistan", "92"},
            {"Philippines", "63"},
            {"Poland", "48"},
            {"Portugal", "351"},
            {"Russia", "7"},
            {"Saudi Arabia", "966"},
            {"Singapore", "65"},
            {"South Africa", "27"},
            {"South Korea", "82"},
            {"Spain", "34"},
            {"Sweden", "46"},
            {"Switzerland", "41"},
            {"Taiwan", "886"},
            {"Thailand", "66"},
            {"Turkey", "90"},
            {"Ukraine", "380"},
            {"United Arab Emirates", "971"},
            {"United Kingdom", "44"},
            {"United States", "1"},
            {"Vietnam", "84"}
    };

    private void logAction(String action, String details) {
        ActionLog log = new ActionLog();
        log.action = action;
        log.details = details;
        log.time = System.currentTimeMillis();
        
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(log.time);
        log.date = cal.get(Calendar.YEAR) + "-" + (cal.get(Calendar.MONTH) + 1) + "-" + cal.get(Calendar.DAY_OF_MONTH) + " " +
                cal.get(Calendar.HOUR_OF_DAY) + ":" + cal.get(Calendar.MINUTE) + ":" + cal.get(Calendar.SECOND);
        
        actionLogBox.put(log);
    }

    interface OnSaveListener {
        void onSave(String value);
    }

    interface GenerateAction {
        String generate();
    }

    interface CopyAction {
        String getCopyValue();
    }

    // Helpers
    private LinearLayout createSection(Context ctx, String title) {
        LinearLayout section = new LinearLayout(ctx);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setBackgroundResource(R.drawable.border_background);
        section.setPadding(20, 20, 20, 20);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 20, 0, 20);
        section.setLayoutParams(params);

        if (title != null) {
            TextView tvTitle = new TextView(ctx);
            tvTitle.setText(title);
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTitle.setTextSize(18);
            tvTitle.setPadding(0, 0, 0, 20);
            section.addView(tvTitle);
        }
        return section;
    }
    
    // Always-editable field (preserved for fields like Live Stream Max Entries)
    private void addEditableFieldToLayout(LinearLayout parent, String label, String value, OnSaveListener listener) {
        Context ctx = requireContext();
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);

        EditText et = new EditText(ctx);
        et.setText(value);

        Button btn = new Button(ctx);
        btn.setText("Save " + label);
        btn.setOnClickListener(v -> {
            listener.onSave(et.getText().toString());
            Toast.makeText(ctx, "Saved", Toast.LENGTH_SHORT).show();
        });

        parent.addView(tv);
        parent.addView(et);
        parent.addView(btn);
    }
    
    private void addButtonToLayout(LinearLayout parent, String label, View.OnClickListener listener) {
        Button btn = new Button(requireContext());
        btn.setText(label);
        btn.setOnClickListener(listener);
        parent.addView(btn);
    }
}