package com.example.compare.analytics;
/** 迁移生成的代码片段开始 (blockId=68ebea9a-53b9-4ed3-9c19-c7eb8a057639) 1234 */

import java.time.Duration;
/** 迁移生成的代码片段结束 */

import java.time.Instant;
/** 迁移生成的代码片段开始 (blockId=cd29e2ec-1181-4a6b-8952-9de7d4cdf367) 1234 */
import java.util.ArrayList;
/** 迁移生成的代码片段结束 */

import java.util.List;
/** 迁移生成的代码片段开始 (blockId=c11558a8-055e-4be5-abeb-20dc43884898) 1234 */
import java.util.Map;
/** 迁移生成的代码片段结束 */

import java.util.stream.Collectors;

public class EngagementAnalyzer {
    private final List<SessionRecord> records;
/** 迁移生成的代码片段开始 (blockId=c7d1cddc-807e-4747-9219-aa46e1308ec3) 1234 */
    private final Instant generatedAt;

/** 迁移生成的代码片段结束 */

    public EngagementAnalyzer(List<SessionRecord> records) {
        this.records = new ArrayList<>(records);
        this.generatedAt = Instant.now();
    }
/** 迁移生成的代码片段开始 (blockId=7180b119-6052-4f04-95de-6d4891cd02e1) 1234 */
    public double computeAverageDuration() {
        return records.stream().mapToLong(record -> record.duration().toMillis()).average().orElse(0.0) / 1000.0;
    }
/** 迁移生成的代码片段结束 */

    public List<SessionRecord> filterActiveSessions(Duration threshold) {
        return records.stream().filter(record -> record.duration().compareTo(threshold) >= 0).collect(Collectors.toList());
    }
/** 迁移生成的代码片段开始 (blockId=5601a0c5-9791-4ba1-b04a-32248dfa2f83) 1234 */
    public Map<String, Long> groupByChannel() {
        return records.stream().collect(Collectors.groupingBy(SessionRecord::channel, Collectors.counting()));
    }

    public List<SessionRecord> detectAnomalies(Duration ceiling) {
        return records.stream().filter(record -> record.duration().compareTo(ceiling) > 0).collect(Collectors.toCollection(ArrayList::new));
    }
/** 迁移生成的代码片段结束 */

    public String describeSummary() {
        double average = computeAverageDuration();
/** 迁移生成的代码片段开始 (blockId=96c72b3d-2b9a-42ca-836c-6c408be1b4be) 1234 */
        long total = records.size();
/** 迁移生成的代码片段结束 */

        return "Generated at " + generatedAt + " with " + total + " sessions; average duration " + average + "s";
    }
/** 迁移生成的代码片段开始 (blockId=f396f4f9-40bb-4ac2-b8c8-8600c037bd37) 1234 */

    public List<SessionRecord> sortByDurationDescending() {
        return records.stream().sorted((a, b) -> Long.compare(b.duration().toMillis(), a.duration().toMillis())).collect(Collectors.toList());
    }
    public List<SessionRecord> highlightRecentSessions(Duration window) {
        Instant cutoff = generatedAt.minus(window);
        return records.stream().filter(record -> record.startedAt().isAfter(cutoff)).collect(Collectors.toList());
    }
/** 迁移生成的代码片段结束 */

    public record SessionRecord(String channel, Instant startedAt, Duration duration) {}
}
