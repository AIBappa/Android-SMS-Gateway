package com.ibnux.smsgateway.data;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

@Entity
public class ActionLog {
    @Id
    public long id;
    public long time;
    public String date;
    public String action; // e.g., "Changed Secret", "Updated URL"
    public String details; // e.g., "New URL: https://..."
}
