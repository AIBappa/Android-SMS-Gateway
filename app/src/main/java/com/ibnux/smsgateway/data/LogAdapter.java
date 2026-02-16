package com.ibnux.smsgateway.data;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ibnux.smsgateway.ObjectBox;
import com.ibnux.smsgateway.R;
import com.ibnux.smsgateway.Utils.Fungsi;

import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.MyViewHolder> {
    private List<LogLine> datas;
    long offset = 0, limit = 50;
    String search = "";
    long smallTime = System.currentTimeMillis(), bigTime = 0;

    public static class MyViewHolder extends RecyclerView.ViewHolder {
        TextView txtDate, txtMsg;

        public MyViewHolder(View v) {
            super(v);
            txtMsg = v.findViewById(R.id.txtMsg);
            txtDate = v.findViewById(R.id.txtDate);
        }
    }

    public LogAdapter() {
        Fungsi.log("Data: " + LiveLogBuffer.getLogs().size());
    }

    public void reload() {
        smallTime = System.currentTimeMillis();
        bigTime = 0;
        
        datas = LiveLogBuffer.search(search);
        
        for (int n = 0; n < getItemCount(); n++) {
            if (datas.get(n).time > bigTime) {
                bigTime = datas.get(n).time;
            }
            if (smallTime > datas.get(n).time) {
                smallTime = datas.get(n).time;
            }
        }
        Fungsi.log("reload " + datas.size() + " " + bigTime + " " + smallTime);
        notifyDataSetChanged();
    }

    public void search(String search) {
        this.search = search;
        reload();
    }

    public void getNewData() {
        reload();
    }

    public void nextData() {
        // In-memory buffer is fully loaded in reload
    }

    @NonNull
    @Override
    public MyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new LogAdapter.MyViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.log_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MyViewHolder holder, int position) {
        LogLine ll = datas.get(position);
        holder.txtDate.setText(ll.date);
        holder.txtMsg.setText(ll.message);
    }

    @Override
    public int getItemCount() {
        return (datas == null) ? 0 : datas.size();
    }
}
