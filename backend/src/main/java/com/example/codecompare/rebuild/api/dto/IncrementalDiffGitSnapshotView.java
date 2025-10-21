package com.example.codecompare.rebuild.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.Instant;

/**
 * Represents a single Git snapshot associated with a diff file or block.
 */
@JsonDeserialize(builder = IncrementalDiffGitSnapshotView.Builder.class)
public final class IncrementalDiffGitSnapshotView {

    private final String projectCode;
    private final String projectType;
    private final String gitCommitId;
    private final String gitAuthor;
    private final Instant gitTimestamp;
    private final String gitBranch;
    private final boolean workingTreeChange;
    private final String changeType;
    private final String previousPath;
    private final Instant scannedAt;

    private IncrementalDiffGitSnapshotView(Builder builder) {
        this.projectCode = builder.projectCode;
        this.projectType = builder.projectType;
        this.gitCommitId = builder.gitCommitId;
        this.gitAuthor = builder.gitAuthor;
        this.gitTimestamp = builder.gitTimestamp;
        this.gitBranch = builder.gitBranch;
        this.workingTreeChange = builder.workingTreeChange;
        this.changeType = builder.changeType;
        this.previousPath = builder.previousPath;
        this.scannedAt = builder.scannedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getProjectType() {
        return projectType;
    }

    public String getGitCommitId() {
        return gitCommitId;
    }

    public String getGitAuthor() {
        return gitAuthor;
    }

    public Instant getGitTimestamp() {
        return gitTimestamp;
    }

    public String getGitBranch() {
        return gitBranch;
    }

    public boolean isWorkingTreeChange() {
        return workingTreeChange;
    }

    public String getChangeType() {
        return changeType;
    }

    public String getPreviousPath() {
        return previousPath;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String projectCode;
        private String projectType;
        private String gitCommitId;
        private String gitAuthor;
        private Instant gitTimestamp;
        private String gitBranch;
        private boolean workingTreeChange;
        private String changeType;
        private String previousPath;
        private Instant scannedAt;

        public Builder projectCode(@JsonProperty("projectCode") String projectCode) {
            this.projectCode = projectCode;
            return this;
        }

        public Builder projectType(@JsonProperty("projectType") String projectType) {
            this.projectType = projectType;
            return this;
        }

        public Builder gitCommitId(@JsonProperty("gitCommitId") String gitCommitId) {
            this.gitCommitId = gitCommitId;
            return this;
        }

        public Builder gitAuthor(@JsonProperty("gitAuthor") String gitAuthor) {
            this.gitAuthor = gitAuthor;
            return this;
        }

        public Builder gitTimestamp(@JsonProperty("gitTimestamp") Instant gitTimestamp) {
            this.gitTimestamp = gitTimestamp;
            return this;
        }

        public Builder gitBranch(@JsonProperty("gitBranch") String gitBranch) {
            this.gitBranch = gitBranch;
            return this;
        }

        public Builder workingTreeChange(@JsonProperty("workingTreeChange") boolean workingTreeChange) {
            this.workingTreeChange = workingTreeChange;
            return this;
        }

        public Builder changeType(@JsonProperty("changeType") String changeType) {
            this.changeType = changeType;
            return this;
        }

        public Builder previousPath(@JsonProperty("previousPath") String previousPath) {
            this.previousPath = previousPath;
            return this;
        }

        public Builder scannedAt(@JsonProperty("scannedAt") Instant scannedAt) {
            this.scannedAt = scannedAt;
            return this;
        }

        public IncrementalDiffGitSnapshotView build() {
            return new IncrementalDiffGitSnapshotView(this);
        }
    }
}
