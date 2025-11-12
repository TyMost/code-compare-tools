package com.example.migratediff.infrastructure.persistence.filesystem;

import java.time.Instant;

/**
 * repo/index.json 中的单条记录。
 */
public class RepoIndexEntry {

    private String id;
    private String absolutePath;
    private String branchFrom;
    private String branchTo;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public void setAbsolutePath(String absolutePath) {
        this.absolutePath = absolutePath;
    }

    public String getBranchFrom() {
        return branchFrom;
    }

    public void setBranchFrom(String branchFrom) {
        this.branchFrom = branchFrom;
    }

    public String getBranchTo() {
        return branchTo;
    }

    public void setBranchTo(String branchTo) {
        this.branchTo = branchTo;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
