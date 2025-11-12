package com.example.migratediff.application.scan;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ScanResultStore {

    private final Map<String, ScanReport> reports = new ConcurrentHashMap<>();
    private volatile ScanReport latestReport;

    public void save(ScanReport report) {
        if (report == null) {
            return;
        }
        reports.put(report.getTaskId(), report);
        latestReport = report;
    }

    public Optional<ScanReport> find(String taskId) {
        if (StringUtils.hasText(taskId)) {
            return Optional.ofNullable(reports.get(taskId));
        }
        return Optional.ofNullable(latestReport);
    }
}
