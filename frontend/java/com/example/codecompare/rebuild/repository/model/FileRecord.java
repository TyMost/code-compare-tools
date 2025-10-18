package com.example.codecompare.rebuild.repository.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 文件元数据记录，封装扫描结果与 Git 信息。
 */
@JsonDeserialize(builder = FileRecord.Builder.class)
public final class FileRecord {

    private final String id;
    private final String projectCode;
    private final String path;
    private final String language;
    private final String contentHash;
    private final String previousHash;
    private final long sizeInBytes;
    private final Instant lastModified;
    private final Instant scannedAt;
    private final FileChangeType changeType;
    private final String gitCommitId;
    private final Instant gitCommitTime;
    private final String gitBranch;
    private final String gitAuthor;
    private final boolean workingTreeChange;
    private final String gitPreviousPath;

    private FileRecord(Builder builder) {
        this.id = builder.id == null ? UUID.randomUUID().toString() : builder.id;
        this.projectCode = Objects.requireNonNull(builder.projectCode, "projectCode must not be null");
        this.path = normalizePath(builder.path);
        this.language = builder.language;
        this.contentHash = builder.contentHash;
        this.previousHash = builder.previousHash;
        this.sizeInBytes = builder.sizeInBytes;
        this.lastModified = builder.lastModified;
        this.scannedAt = builder.scannedAt;
        this.changeType = builder.changeType == null ? FileChangeType.MODIFIED : builder.changeType;
        this.gitCommitId = builder.gitCommitId;
        this.gitCommitTime = builder.gitCommitTime;
        this.gitBranch = builder.gitBranch;
        this.gitAuthor = builder.gitAuthor;
        this.workingTreeChange = builder.workingTreeChange;
        this.gitPreviousPath = normalizeOptionalPath(builder.gitPreviousPath);
    }

    private String normalizePath(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        return raw.replace('\\', '/');
    }

    private String normalizeOptionalPath(String raw) {
        return raw == null ? null : raw.replace('\\', '/');
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder().copyOf(this);
    }

    public String getId() {
        return id;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public String getPath() {
        return path;
    }

    public String getLanguage() {
        return language;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public long getSizeInBytes() {
        return sizeInBytes;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public Instant getScannedAt() {
        return scannedAt;
    }

    public FileChangeType getChangeType() {
        return changeType;
    }

    public String getGitCommitId() {
        return gitCommitId;
    }

    public Instant getGitCommitTime() {
        return gitCommitTime;
    }

    public String getGitBranch() {
        return gitBranch;
    }

    public String getGitAuthor() {
        return gitAuthor;
    }

    public boolean isWorkingTreeChange() {
        return workingTreeChange;
    }

    public String getGitPreviousPath() {
        return gitPreviousPath;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String id;
        private String projectCode;
        private String path;
        private String language;
        private String contentHash;
        private String previousHash;
        private long sizeInBytes;
        private Instant lastModified;
        private Instant scannedAt;
        private FileChangeType changeType;
        private String gitCommitId;
        private Instant gitCommitTime;
        private String gitBranch;
        private String gitAuthor;
        private boolean workingTreeChange;
        private String gitPreviousPath;

        public Builder() {
        }

        private Builder copyOf(FileRecord record) {
            this.id = record.id;
            this.projectCode = record.projectCode;
            this.path = record.path;
            this.language = record.language;
            this.contentHash = record.contentHash;
            this.previousHash = record.previousHash;
            this.sizeInBytes = record.sizeInBytes;
            this.lastModified = record.lastModified;
            this.scannedAt = record.scannedAt;
            this.changeType = record.changeType;
            this.gitCommitId = record.gitCommitId;
            this.gitCommitTime = record.gitCommitTime;
            this.gitBranch = record.gitBranch;
            this.gitAuthor = record.gitAuthor;
            this.workingTreeChange = record.workingTreeChange;
            this.gitPreviousPath = record.gitPreviousPath;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder projectCode(String projectCode) {
            this.projectCode = projectCode;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder contentHash(String contentHash) {
            this.contentHash = contentHash;
            return this;
        }

        public Builder previousHash(String previousHash) {
            this.previousHash = previousHash;
            return this;
        }

        public Builder sizeInBytes(long sizeInBytes) {
            this.sizeInBytes = sizeInBytes;
            return this;
        }

        public Builder lastModified(Instant lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder scannedAt(Instant scannedAt) {
            this.scannedAt = scannedAt;
            return this;
        }

        public Builder changeType(FileChangeType changeType) {
            this.changeType = changeType;
            return this;
        }

        public Builder gitCommitId(String gitCommitId) {
            this.gitCommitId = gitCommitId;
            return this;
        }

        public Builder gitCommitTime(Instant gitCommitTime) {
            this.gitCommitTime = gitCommitTime;
            return this;
        }

        public Builder gitBranch(String gitBranch) {
            this.gitBranch = gitBranch;
            return this;
        }

        public Builder gitAuthor(String gitAuthor) {
            this.gitAuthor = gitAuthor;
            return this;
        }

        public Builder workingTreeChange(boolean workingTreeChange) {
            this.workingTreeChange = workingTreeChange;
            return this;
        }

        public Builder gitPreviousPath(String gitPreviousPath) {
            this.gitPreviousPath = gitPreviousPath;
            return this;
        }

        public FileRecord build() {
            return new FileRecord(this);
        }
    }
}
