package com.ibnux.smsgateway.data;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class LiveLogBuffer {
    private static final LinkedList<LogLine> buffer = new LinkedList<>();
    private static int maxEntries = 100;

    public static synchronized void setMaxEntries(int max) {
        maxEntries = max;
        truncate();
    }

    public static synchronized LogLine add(LogLine log) {
        buffer.addFirst(log); // Add to top
        truncate();
        return log;
    }

    public static synchronized void updateLatestStatus(String status) {
        for (LogLine line : buffer) {
            if (line.status == null || line.status.isEmpty()) {
                line.status = status;
                break;
            }
        }
    }

    /**
     * Update the status of a specific LogLine instance (by reference).
     * If the LogLine is no longer in the buffer (e.g., truncated), this is a no-op.
     */
    public static synchronized void updateLatestStatus(LogLine log, String status) {
        if (log == null) {
            updateLatestStatus(status);
            return;
        }
        // Scan by reference equality — only updates the exact object
        for (LogLine line : buffer) {
            if (line == log) {
                line.status = status;
                return;
            }
        }
        // Not found — it was truncated; do nothing to avoid corrupting another entry
    }

    private static void truncate() {
        while (buffer.size() > maxEntries) {
            buffer.removeLast();
        }
    }

    public static synchronized void clear() {
        buffer.clear();
    }

    public static synchronized List<LogLine> getLogs() {
        return new ArrayList<>(buffer);
    }
    
    public static synchronized List<LogLine> search(String query) {
        if (query == null || query.isEmpty()) return getLogs();
        List<LogLine> result = new ArrayList<>();
        String q = query.toLowerCase();
        for (LogLine line : buffer) {
            if (line.message != null && line.message.toLowerCase().contains(q)) {
                result.add(line);
            }
        }
        return result;
    }
}
