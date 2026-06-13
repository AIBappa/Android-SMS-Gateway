package com.ibnux.smsgateway.data;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
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
        TextView txtDate, txtMsg, txtStatus;

        public MyViewHolder(View v) {
            super(v);
            txtMsg = v.findViewById(R.id.txtMsg);
            txtDate = v.findViewById(R.id.txtDate);
            txtStatus = v.findViewById(R.id.txtStatus);
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

        if (ll.status != null && !ll.status.isEmpty()) {
            holder.txtStatus.setVisibility(View.VISIBLE);

            // Build status text with a green dot symbol + status text
            String statusText = " ● " + ll.status;

            if (ll.status.startsWith("ACK")) {
                SpannableString span = new SpannableString(statusText);
                span.setSpan(new ForegroundColorSpan(Color.parseColor("#2E7D32")), 0, statusText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                holder.txtStatus.setText(span);
            } else {
                SpannableString span = new SpannableString(statusText);
                span.setSpan(new ForegroundColorSpan(Color.parseColor("#C62828")), 0, statusText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                holder.txtStatus.setText(span);
            }
        } else {
            holder.txtStatus.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return (datas == null) ? 0 : datas.size();
    }
}
