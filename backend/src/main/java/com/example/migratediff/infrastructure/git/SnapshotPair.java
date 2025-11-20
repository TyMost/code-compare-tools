package com.example.migratediff.infrastructure.git;

import org.eclipse.jgit.lib.ObjectId;

import java.time.Instant;

/**
 * Holds earliest/latest commit information for snapshot diff.
 */
public class SnapshotPair {

    private final ObjectId earliestCommitId;
    private final ObjectId latestCommitId;
    private final Instant earliestCommitTime;
    private final Instant latestCommitTime;

    public SnapshotPair(ObjectId earliestCommitId, Instant earliestCommitTime,
                        ObjectId latestCommitId, Instant latestCommitTime) {
        this.earliestCommitId = earliestCommitId;
        this.earliestCommitTime = earliestCommitTime;
        this.latestCommitId = latestCommitId;
        this.latestCommitTime = latestCommitTime;
    }

    public ObjectId getEarliestCommitId() {
        return earliestCommitId;
    }

    public ObjectId getLatestCommitId() {
        return latestCommitId;
    }

    public Instant getEarliestCommitTime() {
        return earliestCommitTime;
    }

    public Instant getLatestCommitTime() {
        return latestCommitTime;
    }

    /**
     * 兼容性方法，返回最早提交时间
     */
    public Instant getEarliestInstant() {
        return earliestCommitTime;
    }

    /**
     * 兼容性方法，返回最新提交时间
     */
    public Instant getLatestInstant() {
        return latestCommitTime;
    }
}
