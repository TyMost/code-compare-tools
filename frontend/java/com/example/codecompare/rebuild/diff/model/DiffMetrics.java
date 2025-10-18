package com.example.codecompare.rebuild.diff.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 重构版的差异度量模型，提供与原实现兼容的基础指标供规则模块使用。
 */
@JsonDeserialize(builder = DiffMetrics.Builder.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class DiffMetrics {

    private static final DiffMetrics EMPTY = new DiffMetrics("", "", 0d, false, false, 0);

    private final String sourceContent;
    private final String targetContent;
    private final double similarityPercent;
    private final boolean existsInA;
    private final boolean existsInB;
    private final int changedLines;
    private final Map<String, Integer> replaceCache = new ConcurrentHashMap<>();

    private DiffMetrics(String sourceContent,
                        String targetContent,
                        double similarityPercent,
                        boolean existsInA,
                        boolean existsInB,
                        int changedLines) {
        this.sourceContent = sourceContent;
        this.targetContent = targetContent;
        this.similarityPercent = similarityPercent;
        this.existsInA = existsInA;
        this.existsInB = existsInB;
        this.changedLines = changedLines;
    }

    public static DiffMetrics empty() {
        return EMPTY;
    }

    public static DiffMetrics of(String sourceContent,
                                 String targetContent,
                                 double similarityPercent) {
        String normalizedSource = normalize(sourceContent);
        String normalizedTarget = normalize(targetContent);
        boolean existsInA = StringUtils.hasText(normalizedSource);
        boolean existsInB = StringUtils.hasText(normalizedTarget);
        int changedLines = computeChangedLines(normalizedSource, normalizedTarget);
        return new DiffMetrics(normalizedSource, normalizedTarget, similarityPercent, existsInA, existsInB, changedLines);
    }

    private static String normalize(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("\r\n", "\n");
    }

    private static int computeChangedLines(String source, String target) {
        if (!StringUtils.hasText(source) && !StringUtils.hasText(target)) {
            return 0;
        }
        List<String> sourceLines = splitLines(source);
        List<String> targetLines = splitLines(target);
        int length = Math.max(sourceLines.size(), targetLines.size());
        int changes = 0;
        for (int i = 0; i < length; i++) {
            String left = i < sourceLines.size() ? sourceLines.get(i) : "";
            String right = i < targetLines.size() ? targetLines.get(i) : "";
            if (!Objects.equals(left, right)) {
                changes++;
            }
        }
        return changes;
    }

    private static List<String> splitLines(String content) {
        if (!StringUtils.hasText(content)) {
            return Collections.emptyList();
        }
        String[] lines = content.split("\n", -1);
        return Arrays.asList(lines);
    }

    public boolean isExistsInA() {
        return existsInA;
    }

    public boolean isExistsInB() {
        return existsInB;
    }

    public double getSimilarityPercent() {
        return similarityPercent;
    }

    public double getSimilarity() {
        return similarityPercent / 100d;
    }

    public int getChangedLines() {
        return changedLines;
    }

    /**
     * 简化实现：根据源/目标内容的出现次数判断关键字替换次数。
     */
    public int replace(String from, String to) {
        if (!StringUtils.hasText(from) || !StringUtils.hasText(to)) {
            return 0;
        }
        String key = from + "->" + to;
        return replaceCache.computeIfAbsent(key, ignored -> computeReplacementCount(from, to));
    }

    private int computeReplacementCount(String from, String to) {
        int sourceCount = countOccurrences(sourceContent, from);
        if (sourceCount == 0) {
            return 0;
        }
        int targetFrom = countOccurrences(targetContent, from);
        int targetTo = countOccurrences(targetContent, to);
        int removed = Math.max(0, sourceCount - targetFrom);
        if (removed == 0 || targetTo == 0) {
            return 0;
        }
        return Math.min(removed, targetTo);
    }

    private int countOccurrences(String content, String needle) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(needle)) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String sourceContent;
        private String targetContent;
        private double similarityPercent;
        private boolean existsInA;
        private boolean existsInB;
        private int changedLines;

        public Builder() {
        }

        public Builder sourceContent(@JsonProperty("sourceContent") String sourceContent) {
            this.sourceContent = sourceContent;
            return this;
        }

        public Builder targetContent(@JsonProperty("targetContent") String targetContent) {
            this.targetContent = targetContent;
            return this;
        }

        public Builder similarityPercent(@JsonProperty("similarityPercent") double similarityPercent) {
            this.similarityPercent = similarityPercent;
            return this;
        }

        public Builder existsInA(@JsonProperty("existsInA") boolean existsInA) {
            this.existsInA = existsInA;
            return this;
        }

        public Builder existsInB(@JsonProperty("existsInB") boolean existsInB) {
            this.existsInB = existsInB;
            return this;
        }

        public Builder changedLines(@JsonProperty("changedLines") int changedLines) {
            this.changedLines = changedLines;
            return this;
        }

        public Builder similarity(@JsonProperty("similarity") double similarity) {
            return this;
        }

        public DiffMetrics build() {
            String normalizedSource = normalize(sourceContent);
            String normalizedTarget = normalize(targetContent);
            return new DiffMetrics(
                    normalizedSource,
                    normalizedTarget,
                    similarityPercent,
                    existsInA,
                    existsInB,
                    changedLines);
        }
    }
}
