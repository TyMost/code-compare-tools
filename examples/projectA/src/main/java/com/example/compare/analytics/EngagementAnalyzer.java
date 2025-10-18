package com.example.compare.analytics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EngagementAnalyzer {
    private final List<SessionRecord> records;
    private final Instant generatedAt;

    public EngagementAnalyzer(List<SessionRecord> records) {
        this.records = new ArrayList<>(records);
        this.generatedAt = Instant.now();
    }
    public double computeAverageDuration() {
        return records.stream().mapToLong(record -> record.duration().toMillis()).average().orElse(0.0) / 1000.0;
    }
    public List<SessionRecord> filterActiveSessions(Duration threshold) {
        return records.stream().filter(record -> record.duration().compareTo(threshold) >= 0).collect(Collectors.toList());
    }
    public Map<String, Long> groupByChannel() {
        return records.stream().collect(Collectors.groupingBy(SessionRecord::channel, Collectors.counting()));
    }

    public List<SessionRecord> detectAnomalies(Duration ceiling) {
        return records.stream().filter(record -> record.duration().compareTo(ceiling) > 0).collect(Collectors.toCollection(ArrayList::new));
    }
    public String describeSummary() {
        double average = computeAverageDuration();
        long total = records.size();
        return "Generated at " + generatedAt + " with " + total + " sessions; average duration " + average + "s";
    }

    public List<SessionRecord> sortByDurationDescending() {
        return records.stream().sorted((a, b) -> Long.compare(b.duration().toMillis(), a.duration().toMillis())).collect(Collectors.toList());
    }
    public List<SessionRecord> highlightRecentSessions(Duration window) {
        Instant cutoff = generatedAt.minus(window);
        return records.stream().filter(record -> record.startedAt().isAfter(cutoff)).collect(Collectors.toList());
    }
    public record SessionRecord(String channel, Instant startedAt, Duration duration) {}
}
