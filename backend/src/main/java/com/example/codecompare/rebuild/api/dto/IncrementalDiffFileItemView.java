package com.example.codecompare.rebuild.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.Instant;

/**
 * Lightweight summary for a diff file used in overview and ranking views.
 */
@JsonDeserialize(builder = IncrementalDiffFileItemView.Builder.class)
public final class IncrementalDiffFileItemView {

    private final String filePath;
    private final double averageSimilarity;
    private final int totalBlocks;
    private final int unlabeledBlocks;
    private final int totalLineCount;
    private final Instant generatedAt;
    private final java.util.List<IncrementalDiffFileCategoryView> categories;

    private IncrementalDiffFileItemView(Builder builder) {
        this.filePath = builder.filePath;
        this.averageSimilarity = builder.averageSimilarity;
        this.totalBlocks = builder.totalBlocks;
        this.unlabeledBlocks = builder.unlabeledBlocks;
        this.totalLineCount = builder.totalLineCount;
        this.generatedAt = builder.generatedAt;
        this.categories = builder.categories == null
                ? java.util.Collections.emptyList()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(builder.categories));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getFilePath() {
        return filePath;
    }

    public double getAverageSimilarity() {
        return averageSimilarity;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getUnlabeledBlocks() {
        return unlabeledBlocks;
    }

    public int getTotalLineCount() {
        return totalLineCount;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public java.util.List<IncrementalDiffFileCategoryView> getCategories() {
        return categories;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String filePath;
        private double averageSimilarity;
        private int totalBlocks;
        private int unlabeledBlocks;
        private int totalLineCount;
        private Instant generatedAt;
        private java.util.List<IncrementalDiffFileCategoryView> categories;

        public Builder filePath(@JsonProperty("filePath") String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder averageSimilarity(@JsonProperty("averageSimilarity") double averageSimilarity) {
            this.averageSimilarity = averageSimilarity;
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

        public Builder totalLineCount(@JsonProperty("totalLineCount") int totalLineCount) {
            this.totalLineCount = totalLineCount;
            return this;
        }

        public Builder generatedAt(@JsonProperty("generatedAt") Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public Builder categories(@JsonProperty("categories") java.util.List<IncrementalDiffFileCategoryView> categories) {
            this.categories = categories;
            return this;
        }

        public IncrementalDiffFileItemView build() {
            return new IncrementalDiffFileItemView(this);
        }
    }
}
