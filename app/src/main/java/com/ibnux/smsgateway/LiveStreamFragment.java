package com.ibnux.smsgateway;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.ibnux.smsgateway.Utils.Fungsi;
import com.ibnux.smsgateway.data.LogAdapter;
import com.ibnux.smsgateway.data.LogLine;
import com.ibnux.smsgateway.data.PaginationListener;
import com.ibnux.smsgateway.layanan.BackgroundService;

public class LiveStreamFragment extends Fragment {

    private RecyclerView recyclerview;
    private LogAdapter adapter;
    private SwipeRefreshLayout swipe;
    private EditText editTextSearch;
    private Switch switchMaster;
    private Button btnClearLog;
    private boolean serviceActive = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_live_stream, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerview = view.findViewById(R.id.recyclerview);
        editTextSearch = view.findViewById(R.id.editTextSearch);
        swipe = view.findViewById(R.id.swipe);
        switchMaster = view.findViewById(R.id.switchMaster);
        btnClearLog = view.findViewById(R.id.btnClearLog);

        setupViews();
        setupServiceCheck();
    }

    private void setupViews() {
        // Master Switch Logic
        SharedPreferences sp = requireContext().getSharedPreferences("pref", 0);
        switchMaster.setChecked(sp.getBoolean("gateway_on", true));
        
        switchMaster.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                requireContext().getSharedPreferences("pref", 0).edit().putBoolean("gateway_on", isChecked).apply();
                if (!isChecked) {
                    Intent intent = new Intent("BackgroundService");
                    intent.putExtra("kill", true);
                    LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent);
                    Toast.makeText(getContext(), "Gateway OFF", Toast.LENGTH_LONG).show();
                } else {
                    checkServices();
                    Toast.makeText(getContext(), "Gateway ON", Toast.LENGTH_LONG).show();
                }
            }
        });

        // Clear Log Button
        btnClearLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ObjectBox.get().boxFor(LogLine.class).removeAll();
                adapter.reload();
            }
        });

        // Swipe Refresh
        swipe.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                adapter.getNewData();
                swipe.setRefreshing(false);
            }
        });

        // RecyclerView Setup
        recyclerview.setHasFixedSize(true);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerview.setLayoutManager(layoutManager);
        adapter = new LogAdapter();
        recyclerview.setAdapter(adapter);
        adapter.reload();

        recyclerview.addOnScrollListener(new PaginationListener(layoutManager) {
            @Override
            protected void loadMoreItems() {
                recyclerview.post(new Runnable() {
                    @Override
                    public void run() {
                        adapter.nextData();
                    }
                });
            }
        });

        // Search
        editTextSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    recyclerview.post(new Runnable() {
                        @Override
                        public void run() {
                            adapter.search(editTextSearch.getText().toString());
                            editTextSearch.clearFocus();
                        }
                    });
                    return true;
                }
                return false;
            }
        });
    }

    public void checkServices() {
        Fungsi.log("checkServices");
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(new Intent("BackgroundService"));
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                Fungsi.log("checkServices " + serviceActive);
                if (!serviceActive && isAdded()) {
                    requireContext().startService(new Intent(requireContext(), BackgroundService.class));
                }
            }
        }, 3000);
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiver, new IntentFilter("MainActivity"));
        if (requireContext().getSharedPreferences("pref", 0).getBoolean("gateway_on", true))
            checkServices();
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiver);
        super.onPause();
    }

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.hasExtra("newMessage"))
                recyclerview.post(new Runnable() {
                    @Override
                    public void run() {
                        adapter.getNewData();
                    }
                });
            else if (intent.hasExtra("kill") && intent.getBooleanExtra("kill", false)) {
                serviceActive = false;
            } else
                serviceActive = true;
        }
    };
}
