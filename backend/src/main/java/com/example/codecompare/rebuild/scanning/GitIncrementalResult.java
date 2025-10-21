package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.repository.model.FileRecord;
import com.example.codecompare.rebuild.scanning.git.GitDiffFile;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Git 增量扫描的包裹结果，包含差异文件与扫描摘要信息。
 */
public final class GitIncrementalResult {

    private final List<FileRecord> records;
    private final List<GitDiffFile> gitDiffFiles;
    private final ScanSummary summary;

    private GitIncrementalResult(List<FileRecord> records, List<GitDiffFile> gitDiffFiles, ScanSummary summary) {
        this.records = records == null ? Collections.emptyList() : Collections.unmodifiableList(records);
        this.gitDiffFiles = gitDiffFiles == null ? Collections.emptyList() : Collections.unmodifiableList(gitDiffFiles);
        this.summary = summary;
    }

    public List<FileRecord> getRecords() {
        return records;
    }

    public List<GitDiffFile> getGitDiffFiles() {
        return gitDiffFiles;
    }

    public Optional<ScanSummary> getSummary() {
        return Optional.ofNullable(summary);
    }

    public boolean isEmpty() {
        return records.isEmpty() && gitDiffFiles.isEmpty();
    }

    public static GitIncrementalResult empty(String projectCode) {
        Objects.requireNonNull(projectCode, "projectCode must not be null");
        return new GitIncrementalResult(Collections.emptyList(), Collections.emptyList(),
                ScanSummary.empty(projectCode, Instant.now()));
    }

    public static GitIncrementalResult of(List<FileRecord> records, ScanSummary summary) {
        return new GitIncrementalResult(records, Collections.emptyList(), summary);
    }

    public static GitIncrementalResult of(List<FileRecord> records,
                                          List<GitDiffFile> gitDiffFiles,
                                          ScanSummary summary) {
        return new GitIncrementalResult(records, gitDiffFiles, summary);
    }
}
