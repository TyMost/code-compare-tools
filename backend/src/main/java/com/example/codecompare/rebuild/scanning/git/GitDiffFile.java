package com.example.codecompare.rebuild.scanning.git;

import com.example.codecompare.rebuild.repository.model.FileChangeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Git diff payload for a single file, wrapping the hunk list and path metadata.
 */
public final class GitDiffFile {

    private final String path;
    private final String previousPath;
    private final FileChangeType changeType;
    private final List<GitDiffHunk> hunks;
    private final boolean truncated;
    private final long totalBytes;

    private GitDiffFile(Builder builder) {
        this.path = Objects.requireNonNull(builder.path, "path must not be null");
        this.previousPath = builder.previousPath;
        this.changeType = builder.changeType == null ? FileChangeType.MODIFIED : builder.changeType;
        this.hunks = Collections.unmodifiableList(new ArrayList<>(builder.hunks));
        this.truncated = builder.truncated;
        this.totalBytes = builder.totalBytes < 0 ? 0 : builder.totalBytes;
    }

    public String getPath() {
        return path;
    }

    public String getPreviousPath() {
        return previousPath;
    }

    public FileChangeType getChangeType() {
        return changeType;
    }

    public List<GitDiffHunk> getHunks() {
        return hunks;
    }

    public boolean isTruncated() {
        return truncated;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder().from(this);
    }

    public static final class Builder {
        private String path;
        private String previousPath;
        private FileChangeType changeType;
        private final List<GitDiffHunk> hunks = new ArrayList<>();
        private boolean truncated;
        private long totalBytes;

        private Builder() {
        }

        private Builder from(GitDiffFile file) {
            this.path = file.path;
            this.previousPath = file.previousPath;
            this.changeType = file.changeType;
            this.hunks.clear();
            this.hunks.addAll(file.hunks);
            this.truncated = file.truncated;
            this.totalBytes = file.totalBytes;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder previousPath(String previousPath) {
            this.previousPath = previousPath;
            return this;
        }

        public Builder changeType(FileChangeType changeType) {
            this.changeType = changeType;
            return this;
        }

        public Builder addHunk(GitDiffHunk hunk) {
            if (hunk != null) {
                this.hunks.add(hunk);
            }
            return this;
        }

        public Builder hunks(List<GitDiffHunk> hunks) {
            this.hunks.clear();
            if (hunks != null) {
                this.hunks.addAll(hunks);
            }
            return this;
        }

        public Builder truncated(boolean truncated) {
            this.truncated = truncated;
            return this;
        }

        public Builder totalBytes(long totalBytes) {
            this.totalBytes = totalBytes;
            return this;
        }

        public GitDiffFile build() {
            return new GitDiffFile(this);
        }
    }
}
