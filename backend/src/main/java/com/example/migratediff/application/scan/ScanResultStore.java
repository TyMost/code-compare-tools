package com.example.migratediff.application.scan;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Component
public class ScanResultStore {

    private static final int MAX_HISTORY = 200;

    private final Map<String, ScanReport> reports = new ConcurrentHashMap<>();
    private final Map<String, ScanReport> latestPresetReports = new ConcurrentHashMap<>();
    private final Deque<ScanReport> history = new ConcurrentLinkedDeque<>();
    private volatile ScanReport latestReport;

    public synchronized void save(ScanReport report) {
        if (report == null) {
            return;
        }
        reports.put(report.getTaskId(), report);
        latestReport = report;
        history.addFirst(report);
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
        if (StringUtils.hasText(report.getPresetName())) {
            latestPresetReports.put(report.getPresetName(), report);
        }
    }

    public Optional<ScanReport> find(String taskId) {
        if (StringUtils.hasText(taskId)) {
            return Optional.ofNullable(reports.get(taskId));
        }
        return Optional.ofNullable(latestReport);
    }

    public Optional<ScanReport> findLatestByPreset(String presetName) {
        if (!StringUtils.hasText(presetName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(latestPresetReports.get(presetName));
    }

    public List<ScanReport> listRecent(int limit) {
        if (limit <= 0) {
            return Collections.emptyList();
        }
        List<ScanReport> snapshot = new ArrayList<>(limit);
        int count = 0;
        for (ScanReport report : history) {
            snapshot.add(report);
            count++;
            if (count >= limit) {
                break;
            }
        }
        return snapshot;
    }

    public synchronized void clear() {
        reports.clear();
        latestPresetReports.clear();
        history.clear();
        latestReport = null;
    }
}
