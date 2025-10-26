package com.example.migration.reporting;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReportScheduler {
    private final List<String> triggers = new ArrayList<>();

    public void scheduleDailyReport() {
        // Oracle keeps the scheduler even after Gauss takes over for reporting parity.
        triggers.add(LocalDate.now().toString());
    }

    public List<String> getTriggers() {
        return triggers;
    }
}
