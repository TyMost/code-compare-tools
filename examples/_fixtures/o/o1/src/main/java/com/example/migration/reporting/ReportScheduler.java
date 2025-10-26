package com.example.migration.reporting;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReportScheduler {
    private final List<String> triggers = new ArrayList<>();

    public void scheduleDailyReport() {
        triggers.add(LocalDate.now().toString());
    }

    public List<String> getTriggers() {
        return triggers;
    }
}
