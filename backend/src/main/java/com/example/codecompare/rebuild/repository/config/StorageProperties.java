package com.example.codecompare.rebuild.repository.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 仓储层存储后端的配置项定义，支持文件与内存两种模式。
 */
@ConfigurationProperties(prefix = "migration.storage")
public class StorageProperties {

    private Backend backend = Backend.FILE;
    private String location = "build/storage";
    private String fileMetadataDir = "file-metadata";
    private String diffSnapshotDir = "diff-snapshots";
    private String blockDecisionDir = "block-decisions";
    private String agentSuggestionDir = "agent-suggestions";
    private String scanSummaryDir = "scan-summaries";
    private boolean prettyPrint = true;

    public Backend getBackend() {
        return backend;
    }

    public void setBackend(Backend backend) {
        this.backend = backend;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getFileMetadataDir() {
        return fileMetadataDir;
    }

    public void setFileMetadataDir(String fileMetadataDir) {
        this.fileMetadataDir = fileMetadataDir;
    }

    public String getDiffSnapshotDir() {
        return diffSnapshotDir;
    }

    public void setDiffSnapshotDir(String diffSnapshotDir) {
        this.diffSnapshotDir = diffSnapshotDir;
    }

    public String getBlockDecisionDir() {
        return blockDecisionDir;
    }

    public void setBlockDecisionDir(String blockDecisionDir) {
        this.blockDecisionDir = blockDecisionDir;
    }

    public String getAgentSuggestionDir() {
        return agentSuggestionDir;
    }

    public void setAgentSuggestionDir(String agentSuggestionDir) {
        this.agentSuggestionDir = agentSuggestionDir;
    }

    public String getScanSummaryDir() {
        return scanSummaryDir;
    }

    public void setScanSummaryDir(String scanSummaryDir) {
        this.scanSummaryDir = scanSummaryDir;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    public Path resolveRoot() {
        return resolve(location);
    }

    public Path resolveFileMetadata() {
        return resolveSub(fileMetadataDir);
    }

    public Path resolveDiffSnapshots() {
        return resolveSub(diffSnapshotDir);
    }

    public Path resolveBlockDecisions() {
        return resolveSub(blockDecisionDir);
    }

    public Path resolveAgentSuggestions() {
        return resolveSub(agentSuggestionDir);
    }

    public Path resolveScanSummaries() {
        return resolveSub(scanSummaryDir);
    }

    private Path resolve(String base) {
        String locationToUse = StringUtils.hasText(base) ? base : "build/storage";
        return Paths.get(locationToUse).normalize().toAbsolutePath();
    }

    private Path resolveSub(String child) {
        Path root = resolveRoot();
        if (!StringUtils.hasText(child)) {
            return root;
        }
        return root.resolve(child).normalize();
    }

    public enum Backend {
        FILE,
        MEMORY
    }
}
