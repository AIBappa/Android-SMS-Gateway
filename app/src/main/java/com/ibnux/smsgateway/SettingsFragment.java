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

        // a) Receiver URL
        addEditableField("Receiver URL", sp.getString("urlPost", ""), value -> {
            sp.edit().putString("urlPost", value).commit();
            logAction("Updated Receiver URL", value);
        });

        // b, c, d) Filters
        addButton("Filter: Country Codes", v -> showCountryFilterDialog());
        addButton("Filter: Message Prefix", v -> showFilterDialog("Allowed SMS Prefixes", "filter_prefix_enabled", "filter_prefix_list", false));
        addButton("Filter: Message Length", v -> showFilterDialog("Allowed Message Lengths", "filter_length_enabled", "filter_length_list", true));

        // e) AES-GCM Key
        String currentKey = SecurityUtil.getSharedKey(ctx);
        addReadOnlyField("AES-GCM Key", currentKey != null ? currentKey : "Not Set");
        addButton("Generate New AES Key", v -> {
            String newKey = SecurityUtil.generateNewKey();
            SecurityUtil.saveKey(ctx, newKey);
            logAction("Generated AES Key", "New key generated and saved");
            setupSmsWebhookMenu(); // Refresh
        });
        addButton("Copy AES Key", v -> {
            if(currentKey != null) {
                ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("AES Key", currentKey);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show();
            }
        });

        // f) Backup URL
        addEditableField("Backup URL", sp.getString("backup_url", ""), value -> {
            sp.edit().putString("backup_url", value).commit();
            logAction("Updated Backup URL", value);
        });
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

        // b) Auto-Delete
        // Logic migrated from SettingsActivity
        LinearLayout autoDelLayout = new LinearLayout(ctx);
        autoDelLayout.setOrientation(LinearLayout.VERTICAL);
        autoDelLayout.setPadding(10, 20, 10, 20);

        CheckBox cbAuto = new CheckBox(ctx);
        cbAuto.setText("Enable Auto-Delete");
        
        EditText etHours = new EditText(ctx);
        etHours.setHint("Retention Hours (1-3000)");
        etHours.setInputType(InputType.TYPE_CLASS_NUMBER);
        
        Button btnRun = new Button(ctx);
        btnRun.setText("Run Auto-Delete Now");
        btnRun.setEnabled(false);

        autoDelLayout.addView(cbAuto);
        autoDelLayout.addView(etHours);
        autoDelLayout.addView(btnRun);
        containerSubMenuContent.addView(autoDelLayout);

        // Logic
        btnRun.setOnClickListener(v -> {
            try {
                int hours = Integer.parseInt(etHours.getText().toString());
                long cutoffTime = System.currentTimeMillis() - (hours * 3600000L);
                int deletedCount = ctx.getContentResolver().delete(
                        Uri.parse("content://sms"),
                        "date < ? AND type = 1",
                        new String[]{String.valueOf(cutoffTime)}
                );
                logAction("Auto-Delete Run", "Deleted " + deletedCount + " messages > " + hours + "h old");
                Toast.makeText(ctx, "Deleted " + deletedCount + " messages", Toast.LENGTH_LONG).show();
                cbAuto.setChecked(false);
                etHours.setText("");
            } catch (Exception e) {
                Toast.makeText(ctx, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

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
}
