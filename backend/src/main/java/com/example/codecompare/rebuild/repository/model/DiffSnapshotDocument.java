package com.example.codecompare.rebuild.repository.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 差异快照文档，用于记录单个文件的 diff 汇总信息以及关联的块决策。
 */
@JsonDeserialize(builder = DiffSnapshotDocument.Builder.class)
public final class DiffSnapshotDocument {

    private final String id;
    private final String comparisonId;
    private final String filePath;
    private final String sourceProjectCode;
    private final String targetProjectCode;
    private final Instant generatedAt;
    private final int totalBlocks;
    private final int unlabeledBlocks;
    private final double averageSimilarity;
    private final Map<String, Integer> labelCounts;
    private final Map<String, Integer> lineCounts;
    private final int totalLineCount;
    private final List<String> decisionIds;
    private final List<Map<String, Object>> gitSnapshots;
    private final Map<String, Object> gitDiff;

    private DiffSnapshotDocument(Builder builder) {
        this.id = builder.id == null ? UUID.randomUUID().toString() : builder.id;
        this.comparisonId = require(builder.comparisonId, "comparisonId");
        this.filePath = normalizePath(require(builder.filePath, "filePath"));
        this.sourceProjectCode = require(builder.sourceProjectCode, "sourceProjectCode");
        this.targetProjectCode = require(builder.targetProjectCode, "targetProjectCode");
        this.generatedAt = builder.generatedAt == null ? Instant.now() : builder.generatedAt;
        this.totalBlocks = Math.max(0, builder.totalBlocks);
        this.unlabeledBlocks = Math.max(0, builder.unlabeledBlocks);
        this.averageSimilarity = builder.averageSimilarity;
        this.labelCounts = builder.labelCounts == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.labelCounts));
        this.lineCounts = builder.lineCounts == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.lineCounts));
        this.totalLineCount = builder.totalLineCount;
        this.decisionIds = builder.decisionIds == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(builder.decisionIds));
        if (builder.gitSnapshots == null || builder.gitSnapshots.isEmpty()) {
            this.gitSnapshots = Collections.emptyList();
        } else {
            List<Map<String, Object>> copies = new ArrayList<>(builder.gitSnapshots.size());
            for (Map<String, Object> snapshot : builder.gitSnapshots) {
                Map<String, Object> copy = snapshot == null
                        ? Collections.emptyMap()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
                copies.add(copy);
            }
            this.gitSnapshots = Collections.unmodifiableList(copies);
        }
        if (builder.gitDiff == null || builder.gitDiff.isEmpty()) {
            this.gitDiff = Collections.emptyMap();
        } else {
            this.gitDiff = Collections.unmodifiableMap(new LinkedHashMap<>(builder.gitDiff));
        }
    }

    private <T> T require(T value, String name) {
        return Objects.requireNonNull(value, name + " must not be null");
    }

    private String normalizePath(String raw) {
        return raw.replace('\\', '/');
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public String getComparisonId() {
        return comparisonId;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getSourceProjectCode() {
        return sourceProjectCode;
    }

    public String getTargetProjectCode() {
        return targetProjectCode;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getUnlabeledBlocks() {
        return unlabeledBlocks;
    }

    public double getAverageSimilarity() {
        return averageSimilarity;
    }

    public Map<String, Integer> getLabelCounts() {
        return labelCounts;
    }

    public Map<String, Integer> getLineCounts() {
        return lineCounts;
    }

    public int getTotalLineCount() {
        return totalLineCount;
    }

    public List<String> getDecisionIds() {
        return decisionIds;
    }

    public List<Map<String, Object>> getGitSnapshots() {
        return gitSnapshots;
    }

    public Map<String, Object> getGitDiff() {
        return gitDiff;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String id;
        private String comparisonId;
        private String filePath;
        private String sourceProjectCode;
        private String targetProjectCode;
        private Instant generatedAt;
        private int totalBlocks;
        private int unlabeledBlocks;
        private double averageSimilarity;
        private Map<String, Integer> labelCounts;
        private Map<String, Integer> lineCounts;
        private int totalLineCount;
        private List<String> decisionIds;
        private List<Map<String, Object>> gitSnapshots;
        private Map<String, Object> gitDiff;

        public Builder() {
        }

        public Builder id(@JsonProperty("id") String id) {
            this.id = id;
            return this;
        }

        public Builder comparisonId(@JsonProperty("comparisonId") String comparisonId) {
            this.comparisonId = comparisonId;
            return this;
        }

        public Builder filePath(@JsonProperty("filePath") String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder sourceProjectCode(@JsonProperty("sourceProjectCode") String sourceProjectCode) {
            this.sourceProjectCode = sourceProjectCode;
            return this;
        }

        public Builder targetProjectCode(@JsonProperty("targetProjectCode") String targetProjectCode) {
            this.targetProjectCode = targetProjectCode;
            return this;
        }

        public Builder generatedAt(@JsonProperty("generatedAt") Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public Builder totalBlocks(@JsonProperty("totalBlocks") int totalBlocks) {
            this.totalBlocks = totalBlocks;
            return this;
        }

        public Builder unlabeledBlocks(@JsonProperty("unlabeledBlocks") int unlabeledBlocks) {
            this.unlabeledBlocks = unlabeledBlocks;
            return this;
        }

        public Builder averageSimilarity(@JsonProperty("averageSimilarity") double averageSimilarity) {
            this.averageSimilarity = averageSimilarity;
            return this;
        }

        public Builder labelCounts(@JsonProperty("labelCounts") Map<String, Integer> labelCounts) {
            this.labelCounts = labelCounts;
            return this;
        }

        public Builder lineCounts(@JsonProperty("lineCounts") Map<String, Integer> lineCounts) {
            this.lineCounts = lineCounts;
            return this;
        }

        public Builder totalLineCount(@JsonProperty("totalLineCount") Integer totalLineCount) {
            this.totalLineCount = totalLineCount == null ? 0 : Math.max(0, totalLineCount);
            return this;
        }

        public Builder decisionIds(@JsonProperty("decisionIds") List<String> decisionIds) {
            this.decisionIds = decisionIds;
            return this;
        }

        public Builder gitSnapshots(@JsonProperty("gitSnapshots") List<Map<String, Object>> gitSnapshots) {
            this.gitSnapshots = gitSnapshots;
            return this;
        }

        public Builder gitDiff(@JsonProperty("gitDiff") Map<String, Object> gitDiff) {
            this.gitDiff = gitDiff;
            return this;
        }

        public DiffSnapshotDocument build() {
            return new DiffSnapshotDocument(this);
        }
    }
}
