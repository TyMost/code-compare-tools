package com.example.codecompare.rebuild.scanning;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.util.StringUtils;

/**
 * 扫描相关配置项，覆盖默认忽略规则与线程池大小等参数。<br/>
 * 可通过 {@code migration.scan.*} 在外部进行重写。
 */
@ConfigurationProperties(prefix = "migration.scan")
public class ScanProperties {

    private int fingerprintThreads = Math.max(Runtime.getRuntime().availableProcessors(), 4);
    private List<String> ignoreGlobs = new ArrayList<>();
    private boolean enableHashCache = true;
    private int batchSize = 500;
    private boolean followSymlinks = false;
    private String gitBaseRefSource = "";
    private String gitTargetRefSource = "HEAD";
    private String gitBaseRefTarget = "";
    private String gitTargetRefTarget = "HEAD";
    private boolean includeWorkingTree = false;
    private boolean sinceLastSummary = true;
    private String diffEngine = "default";
    private boolean gitIncludeRenames = true;
    private boolean gitDetectCopies = false;
    private boolean markSyntheticMigrated = true;
    private long gitMaxDiffBytes = 512 * 1024; // 512 KiB default payload limit
    private long gitMaxFileSizeBytes = 2 * 1024 * 1024; // 2 MiB guardrail
    private boolean autoScanOnStartup = true;
    private boolean gitFetchMissingRefs = false;
    private String gitRemoteName = "origin";

    public int getFingerprintThreads() {
        return fingerprintThreads;
    }

    public void setFingerprintThreads(int fingerprintThreads) {
        this.fingerprintThreads = fingerprintThreads;
    }

    public List<String> getIgnoreGlobs() {
        return Collections.unmodifiableList(ignoreGlobs);
    }

    public void setIgnoreGlobs(List<String> ignoreGlobs) {
        this.ignoreGlobs = ignoreGlobs == null
                ? new ArrayList<>()
                : new ArrayList<>(ignoreGlobs);
    }

    public boolean isEnableHashCache() {
        return enableHashCache;
    }

    public void setEnableHashCache(boolean enableHashCache) {
        this.enableHashCache = enableHashCache;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public boolean isFollowSymlinks() {
        return followSymlinks;
    }

    public void setFollowSymlinks(boolean followSymlinks) {
        this.followSymlinks = followSymlinks;
    }

    public String getGitBaseRefSource() {
        return gitBaseRefSource;
    }

    public void setGitBaseRefSource(String gitBaseRefSource) {
        this.gitBaseRefSource = gitBaseRefSource == null ? "" : gitBaseRefSource.trim();
    }

    public String getGitTargetRefSource() {
        return gitTargetRefSource;
    }

    public void setGitTargetRefSource(String gitTargetRefSource) {
        this.gitTargetRefSource = gitTargetRefSource == null ? "HEAD" : gitTargetRefSource.trim();
    }

    public String getGitBaseRefTarget() {
        return gitBaseRefTarget;
    }

    public void setGitBaseRefTarget(String gitBaseRefTarget) {
        this.gitBaseRefTarget = gitBaseRefTarget == null ? "" : gitBaseRefTarget.trim();
    }

    public String getGitTargetRefTarget() {
        return gitTargetRefTarget;
    }

    public void setGitTargetRefTarget(String gitTargetRefTarget) {
        this.gitTargetRefTarget = gitTargetRefTarget == null ? "HEAD" : gitTargetRefTarget.trim();
    }

    public boolean isIncludeWorkingTree() {
        return includeWorkingTree;
    }

    public void setIncludeWorkingTree(boolean includeWorkingTree) {
        this.includeWorkingTree = includeWorkingTree;
    }

    public boolean isSinceLastSummary() {
        return sinceLastSummary;
    }

    public void setSinceLastSummary(boolean sinceLastSummary) {
        this.sinceLastSummary = sinceLastSummary;
    }

    public String getDiffEngine() {
        return diffEngine;
    }

    public void setDiffEngine(String diffEngine) {
        this.diffEngine = diffEngine == null || diffEngine.trim().isEmpty() ? "default" : diffEngine.trim();
    }

    public boolean isGitIncludeRenames() {
        return gitIncludeRenames;
    }

    public void setGitIncludeRenames(boolean gitIncludeRenames) {
        this.gitIncludeRenames = gitIncludeRenames;
    }

    public boolean isGitDetectCopies() {
        return gitDetectCopies;
    }

    public void setGitDetectCopies(boolean gitDetectCopies) {
        this.gitDetectCopies = gitDetectCopies;
    }

    public boolean isMarkSyntheticMigrated() {
        return markSyntheticMigrated;
    }

    public void setMarkSyntheticMigrated(boolean markSyntheticMigrated) {
        this.markSyntheticMigrated = markSyntheticMigrated;
    }

    public long getGitMaxDiffBytes() {
        return gitMaxDiffBytes;
    }

    public void setGitMaxDiffBytes(long gitMaxDiffBytes) {
        this.gitMaxDiffBytes = gitMaxDiffBytes <= 0 ? 512 * 1024 : gitMaxDiffBytes;
    }

    public long getGitMaxFileSizeBytes() {
        return gitMaxFileSizeBytes;
    }

    public void setGitMaxFileSizeBytes(long gitMaxFileSizeBytes) {
        this.gitMaxFileSizeBytes = gitMaxFileSizeBytes <= 0 ? 2 * 1024 * 1024 : gitMaxFileSizeBytes;
    }

    public boolean isAutoScanOnStartup() {
        return autoScanOnStartup;
    }

    public void setAutoScanOnStartup(boolean autoScanOnStartup) {
        this.autoScanOnStartup = autoScanOnStartup;
    }

    public boolean isGitFetchMissingRefs() {
        return gitFetchMissingRefs;
    }

    public void setGitFetchMissingRefs(boolean gitFetchMissingRefs) {
        this.gitFetchMissingRefs = gitFetchMissingRefs;
    }

    public String getGitRemoteName() {
        return gitRemoteName;
    }

    public void setGitRemoteName(String gitRemoteName) {
        this.gitRemoteName = StringUtils.hasText(gitRemoteName) ? gitRemoteName.trim() : "origin";
    }
}
