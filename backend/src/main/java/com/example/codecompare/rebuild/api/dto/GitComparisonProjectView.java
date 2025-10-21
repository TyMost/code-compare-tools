package com.example.codecompare.rebuild.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describes a single project's Git comparison metadata.
 */
@JsonDeserialize(builder = GitComparisonProjectView.Builder.class)
public final class GitComparisonProjectView {

    private final String projectCode;
    private final Instant generatedAt;
    private final Map<String, String> projectRoots;
    private final Map<String, String> baseCommits;
    private final Map<String, String> latestCommits;

    private GitComparisonProjectView(Builder builder) {
        this.projectCode = builder.projectCode;
        this.generatedAt = builder.generatedAt;
        this.projectRoots = builder.projectRoots == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.projectRoots));
        this.baseCommits = builder.baseCommits == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.baseCommits));
        this.latestCommits = builder.latestCommits == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(builder.latestCommits));
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getProjectCode() {
        return projectCode;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Map<String, String> getProjectRoots() {
        return projectRoots;
    }

    public Map<String, String> getBaseCommits() {
        return baseCommits;
    }

    public Map<String, String> getLatestCommits() {
        return latestCommits;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String projectCode;
        private Instant generatedAt;
        private Map<String, String> projectRoots;
        private Map<String, String> baseCommits;
        private Map<String, String> latestCommits;

        public Builder projectCode(@JsonProperty("projectCode") String projectCode) {
            this.projectCode = projectCode;
            return this;
        }

        public Builder generatedAt(@JsonProperty("generatedAt") Instant generatedAt) {
            this.generatedAt = generatedAt;
            return this;
        }

        public Builder projectRoots(@JsonProperty("projectRoots") Map<String, String> projectRoots) {
            this.projectRoots = projectRoots;
            return this;
        }

        public Builder baseCommits(@JsonProperty("baseCommits") Map<String, String> baseCommits) {
            this.baseCommits = baseCommits;
            return this;
        }

        public Builder latestCommits(@JsonProperty("latestCommits") Map<String, String> latestCommits) {
            this.latestCommits = latestCommits;
            return this;
        }

        public GitComparisonProjectView build() {
            return new GitComparisonProjectView(this);
        }
    }
}

