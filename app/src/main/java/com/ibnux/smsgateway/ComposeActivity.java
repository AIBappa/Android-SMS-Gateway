package com.ibnux.smsgateway;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class ComposeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Redirect to MainActivity or handle the "Compose" intent
        // For now, just forward to MainActivity to keep it simple as per requirement "Mandatory component"
        Intent intent = new Intent(this, MainActivity.class);
        if (getIntent().getData() != null) {
            intent.setData(getIntent().getData());
        }
        startActivity(intent);
        finish();
    }
}
