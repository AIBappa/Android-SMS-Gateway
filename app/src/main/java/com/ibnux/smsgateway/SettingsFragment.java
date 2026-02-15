package com.ibnux.smsgateway;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.FileProvider;

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
import java.util.Calendar;
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
        view.findViewById(R.id.btnMenuUnifiedLog).setOnClickListener(v -> openSubMenu("Unified Log", 3));
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
        addEditableField("Secret ID", sp.getString("secret", ""), value -> {
            sp.edit().putString("secret", value).commit();
            logAction("Changed Secret ID", "New Secret set manually");
        });
        addButton("Generate New Secret", v -> {
            String newSecret = UUID.randomUUID().toString();
            sp.edit().putString("secret", newSecret).commit();
            logAction("Generated Secret", "New Secret generated");
            Toast.makeText(ctx, "New Secret Generated", Toast.LENGTH_SHORT).show();
            setupPushUssdMenu(); // Refresh UI
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
        addEditableField("Push USSD URL", sp.getString("urlUssd", ""), value -> {
            sp.edit().putString("urlUssd", value).commit();
            logAction("Updated USSD URL", value);
        });
        
        // f) Change Expired
        addEditableField("Request Expiry (Seconds)", String.valueOf(sp.getInt("expired", 3600)), value -> {
            try {
                int exp = Integer.parseInt(value);
                if(exp < 5) exp = 5;
                sp.edit().putInt("expired", exp).commit();
                logAction("Updated Expiry", "New value: " + exp);
            } catch (Exception e) {}
        });
    }

    // --- 3.2 SMS Webhook ---
    private void setupSmsWebhookMenu() {
        Context ctx = requireContext();

        // --- Section 1: Security & Encryption ---
        LinearLayout securitySection = createSection(ctx, "Security & Encryption");
        
        // Toggle: Enable Encryption
        Switch swEncryption = new Switch(ctx);
        swEncryption.setText("Enable Encryption (AES-GCM)");
        swEncryption.setChecked(sp.getBoolean("enable_encryption", false));
        swEncryption.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean("enable_encryption", isChecked).apply();
            logAction("Encryption Toggle", "Set to " + isChecked);
        });
        securitySection.addView(swEncryption);

        // Key Management
        String currentKey = SecurityUtil.getSharedKey(ctx);
        EditText etKey = new EditText(ctx);
        etKey.setHint("AES Key (Base64)");
        etKey.setText(currentKey != null ? currentKey : "");
        securitySection.addView(etKey);

        LinearLayout keyButtons = new LinearLayout(ctx);
        keyButtons.setOrientation(LinearLayout.HORIZONTAL);
        
        Button btnGenKey = new Button(ctx);
        btnGenKey.setText("Generate New Key");
        btnGenKey.setOnClickListener(v -> {
            String newKey = SecurityUtil.generateNewKey();
            etKey.setText(newKey);
            Toast.makeText(ctx, "Key Generated (Not Saved Yet)", Toast.LENGTH_SHORT).show();
        });
        
        Button btnSaveKey = new Button(ctx);
        btnSaveKey.setText("Use This Key");
        btnSaveKey.setOnClickListener(v -> {
            String key = etKey.getText().toString();
            if(!key.isEmpty()) {
                SecurityUtil.saveKey(ctx, key);
                logAction("Updated AES Key", "New key saved");
                Toast.makeText(ctx, "Key Saved", Toast.LENGTH_SHORT).show();
            }
        });

        keyButtons.addView(btnGenKey);
        keyButtons.addView(btnSaveKey);
        securitySection.addView(keyButtons);

        // Clipboard
        Button btnCopyKey = new Button(ctx);
        btnCopyKey.setText("Copy Active Key");
        btnCopyKey.setOnClickListener(v -> {
            String activeKey = SecurityUtil.getSharedKey(ctx);
            if(activeKey != null) {
                ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("AES Key", activeKey);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(ctx, "Copied to Clipboard", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ctx, "No Active Key", Toast.LENGTH_SHORT).show();
            }
        });
        securitySection.addView(btnCopyKey);
        
        containerSubMenuContent.addView(securitySection);

        // --- Section 2: Sequential Dispatcher & Gatekeeper ---
        LinearLayout dispatchSection = createSection(ctx, "Dispatcher & Gatekeeper");

        // Timeout
        addEditableFieldToLayout(dispatchSection, "Network Timeout (Seconds)", String.valueOf(sp.getInt("network_timeout", 15)), value -> {
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

        addEditableFieldToLayout(dispatchSection, "Receiver URL (Primary)", sp.getString("urlPost", ""), value -> {
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

        addEditableFieldToLayout(dispatchSection, "Backup URL", sp.getString("backup_url", ""), value -> {
            sp.edit().putString("backup_url", value).commit();
            logAction("Updated Backup URL", value);
        });

        containerSubMenuContent.addView(dispatchSection);

        // Filters (Legacy)
        LinearLayout filterSection = createSection(ctx, "Filters");
        addButtonToLayout(filterSection, "Filter: Country Codes", v -> showCountryFilterDialog());
        addButtonToLayout(filterSection, "Filter: Message Prefix", v -> showFilterDialog("Allowed SMS Prefixes", "filter_prefix_enabled", "filter_prefix_list", false));
        addButtonToLayout(filterSection, "Filter: Message Length", v -> showFilterDialog("Allowed Message Lengths", "filter_length_enabled", "filter_length_list", true));
        containerSubMenuContent.addView(filterSection);
    }

    // --- 3.3 Unified Log ---
    private void setupUnifiedLogMenu() {
        Context ctx = requireContext();
        
        TextView logView = new TextView(ctx);
        List<ActionLog> logs = actionLogBox.query().orderDesc(ActionLog_.time).build().find(0, 50); // Last 50 actions
        StringBuilder sb = new StringBuilder();
        for(ActionLog log : logs) {
            sb.append("[").append(log.date).append("] ").append(log.action).append("\n");
            if(log.details != null && !log.details.isEmpty())
                sb.append("   ").append(log.details).append("\n");
            sb.append("\n");
        }
        if(logs.isEmpty()) sb.append("No actions recorded yet.");
        
        logView.setText(sb.toString());
        logView.setPadding(10, 10, 10, 10);
        containerSubMenuContent.addView(logView);

        addButton("Share Log", v -> {
            // Simple share implementation
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, sb.toString());
            startActivity(Intent.createChooser(intent, "Share Action Log"));
        });
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

        // c) Battery
        addButton("Disable Battery Optimization", v -> {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:"+BuildConfig.APPLICATION_ID)));
        });
    }

    // --- Helpers ---
    private void addEditableField(String label, String value, OnSaveListener listener) {
        Context ctx = requireContext();
        LinearLayout layout = new LinearLayout(ctx);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, 10, 0, 10);

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

        layout.addView(tv);
        layout.addView(et);
        layout.addView(btn);
        containerSubMenuContent.addView(layout);
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
