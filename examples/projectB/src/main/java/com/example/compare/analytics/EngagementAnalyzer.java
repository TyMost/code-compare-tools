package com.example.compare.analytics;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class EngagementAnalyzer {
    private final List<SessionRecord> records;
    public EngagementAnalyzer(List<SessionRecord> records) {
        this.records = new ArrayList<>(records);
        this.generatedAt = Instant.now();
    }
    public List<SessionRecord> filterActiveSessions(Duration threshold) {
        return records.stream().filter(record -> record.duration().compareTo(threshold) >= 0).collect(Collectors.toList());
    }
    public String describeSummary() {
        double average = computeAverageDuration();
        return "Generated at " + generatedAt + " with " + total + " sessions; average duration " + average + "s";
    }
    public record SessionRecord(String channel, Instant startedAt, Duration duration) {}
}
