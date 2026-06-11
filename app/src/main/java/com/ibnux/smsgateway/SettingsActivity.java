package com.ibnux.smsgateway;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.ibnux.smsgateway.Utils.Fungsi;
import com.ibnux.smsgateway.Utils.GatewayLogger;
import com.ibnux.smsgateway.Utils.SecurityUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class SettingsActivity extends AppCompatActivity {

    private TextView tvSharedKey;
    private Button btnGenerateKey, btnUseKey, btnCopyKey;
    private EditText etBackupUrl, etRetentionHours;
    private Button btnSaveBackupUrl, btnStartAutoDelete, btnViewLog, btnShareLog;
    private CheckBox cbAutoDelete;
    private LinearLayout layoutAutoDelete;
    private TextView tvDefaultAppWarning;

    private String currentGeneratedKey;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        sp = getSharedPreferences("pref", 0);

        tvSharedKey = findViewById(R.id.tvSharedKey);
        btnGenerateKey = findViewById(R.id.btnGenerateKey);
        btnUseKey = findViewById(R.id.btnUseKey);
        btnCopyKey = findViewById(R.id.btnCopyKey);
        etBackupUrl = findViewById(R.id.etBackupUrl);
        btnSaveBackupUrl = findViewById(R.id.btnSaveBackupUrl);
        cbAutoDelete = findViewById(R.id.cbAutoDelete);
        layoutAutoDelete = findViewById(R.id.layoutAutoDelete);
        etRetentionHours = findViewById(R.id.etRetentionHours);
        btnStartAutoDelete = findViewById(R.id.btnStartAutoDelete);
        tvDefaultAppWarning = findViewById(R.id.tvDefaultAppWarning);
        btnViewLog = findViewById(R.id.btnViewLog);
        btnShareLog = findViewById(R.id.btnShareLog);

        setupSecuritySection();
        setupBackupSection();
        setupMaintenanceSection();
        setupLoggingSection();
    }

    private void setupSecuritySection() {
        String savedKey = SecurityUtil.getSharedKey(this);
        if (savedKey != null) {
            tvSharedKey.setText(savedKey);
            currentGeneratedKey = savedKey;
        }

        btnGenerateKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentGeneratedKey = SecurityUtil.generateNewKey();
                tvSharedKey.setText(currentGeneratedKey);
                Toast.makeText(SettingsActivity.this, "New Key Generated (Not Saved Yet)", Toast.LENGTH_SHORT).show();
            }
        });

        btnUseKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentGeneratedKey != null) {
                    SecurityUtil.saveKey(SettingsActivity.this, currentGeneratedKey);
                    Toast.makeText(SettingsActivity.this, "Key Activated and Saved", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(SettingsActivity.this, "Generate a key first", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        btnCopyKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentGeneratedKey != null) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Shared Key", currentGeneratedKey);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(SettingsActivity.this, "Copied to Clipboard", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void setupBackupSection() {
        String savedBackupUrl = sp.getString("backup_url", "");
        etBackupUrl.setText(savedBackupUrl);

        btnSaveBackupUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String url = etBackupUrl.getText().toString();
                sp.edit().putString("backup_url", url).apply();
                Toast.makeText(SettingsActivity.this, "Backup URL Saved", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupMaintenanceSection() {
        cbAutoDelete.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                layoutAutoDelete.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                checkDefaultAppStatus();
            }
        });

        etRetentionHours.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateRetentionInput();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnStartAutoDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performAutoDeletion();
            }
        });
    }
    
    private void checkDefaultAppStatus() {
        if (!Fungsi.isDefaultSmsApp(this)) {
            tvDefaultAppWarning.setVisibility(View.VISIBLE);
            btnStartAutoDelete.setEnabled(false);
        } else {
            tvDefaultAppWarning.setVisibility(View.GONE);
            validateRetentionInput();
        }
    }

    private void validateRetentionInput() {
        if (!Fungsi.isDefaultSmsApp(this)) {
            btnStartAutoDelete.setEnabled(false);
            return;
        }

        try {
            String input = etRetentionHours.getText().toString();
            int hours = Integer.parseInt(input);
            if (hours > 0 && hours <= 3000) {
                btnStartAutoDelete.setEnabled(true);
            } else {
                btnStartAutoDelete.setEnabled(false);
            }
        } catch (NumberFormatException e) {
            btnStartAutoDelete.setEnabled(false);
        }
    }

    private void performAutoDeletion() {
        try {
            int hours = Integer.parseInt(etRetentionHours.getText().toString());
            long cutoffTime = System.currentTimeMillis() - (hours * 3600000L);
            
            int deletedCount = getContentResolver().delete(
                    Uri.parse("content://sms"),
                    "date < ? AND type = 1",
                    new String[]{String.valueOf(cutoffTime)}
            );
            
            GatewayLogger.log(this, "SYSTEM", "Auto-Deletion complete. Deleted " + deletedCount + " messages.");
            Toast.makeText(this, "Deleted " + deletedCount + " messages.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error deleting messages: " + e.getMessage(), Toast.LENGTH_LONG).show();
            GatewayLogger.log(this, "ERROR", "Auto-Deletion failed: " + e.getMessage());
        }
    }

    private void setupLoggingSection() {
        btnViewLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLogDialog();
            }
        });

        btnShareLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareLogFile();
            }
        });
    }

    private void showLogDialog() {
        File logFile = GatewayLogger.getLogFile(this);
        StringBuilder logContent = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new FileReader(logFile));
            String line;
            while ((line = br.readLine()) != null) {
                logContent.append(line).append("\n");
            }
            br.close();
        } catch (IOException e) {
            logContent.append("Error reading log file.");
        }

        new AlertDialog.Builder(this)
                .setTitle("Gateway Log")
                .setMessage(logContent.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    private void shareLogFile() {
        File logFile = GatewayLogger.getLogFile(this);
        if (!logFile.exists()) {
            Toast.makeText(this, "Log file is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", logFile);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share Log"));
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        checkDefaultAppStatus();
    }
}
