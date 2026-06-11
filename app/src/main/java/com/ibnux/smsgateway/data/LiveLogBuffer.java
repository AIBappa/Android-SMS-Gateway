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

    public static synchronized void add(LogLine log) {
        buffer.addFirst(log); // Add to top
        truncate();
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
