package com.example.demo.report;

import java.time.LocalDate;
import java.util.List;

public class UsageReport {

    public String renderDailySummary(int totalRequests) {
        return "[B] Daily usage: " + totalRequests;
    }

    public String renderDeploymentBanner(LocalDate date) {
        return "Deploy on " + date + " with feature flags enabled.";
    }

    public String renderWarnings(List<String> warnings) {
        return String.join(";", warnings);
    }
}
