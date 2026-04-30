package com.example.migratediff.application.scan;

import com.example.migratediff.domain.coverage.CoverageDetail;
import com.example.migratediff.domain.coverage.CoverageSummary;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffSummary;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class ScanReport {

    private final String taskId;
    private final ScanMode mode;
    private final String presetName;
    private final String repoId;
    private final String repoName;
    private final boolean persisted;
    private final DiffSummary oracleSummary;
    private final DiffSummary gaussSummary;
    private final CoverageSummary coverageSummary;
    private final Instant generatedAt;
    @JsonIgnore
    private final Map<String, DiffFile> oracleIndex;
    @JsonIgnore
    private final Map<String, DiffFile> gaussIndex;
    @JsonIgnore
    private final Map<String, CoverageDetail> coverageIndex;

    public ScanReport(String taskId,
                      ScanMode mode,
                      String presetName,
                      String repoId,
                      String repoName,
                      boolean persisted,
                      DiffSummary oracleSummary,
                      DiffSummary gaussSummary,
                      CoverageSummary coverageSummary) {
        this(taskId,
                mode,
                presetName,
                repoId,
                repoName,
                persisted,
                oracleSummary,
                gaussSummary,
                coverageSummary,
                null);
    }

    @JsonCreator
    public ScanReport(@JsonProperty("taskId") String taskId,
                      @JsonProperty("mode") ScanMode mode,
                      @JsonProperty("presetName") String presetName,
                      @JsonProperty("repoId") String repoId,
                      @JsonProperty("repoName") String repoName,
                      @JsonProperty("persisted") boolean persisted,
                      @JsonProperty("oracleSummary") DiffSummary oracleSummary,
                      @JsonProperty("gaussSummary") DiffSummary gaussSummary,
                      @JsonProperty("coverageSummary") CoverageSummary coverageSummary,
                      @JsonProperty("generatedAt") Instant generatedAt) {
        this.taskId = StringUtils.hasText(taskId) ? taskId : UUIDGenerator.randomTaskId();
        this.mode = mode;
        this.presetName = presetName;
        this.repoId = repoId;
        this.repoName = repoName;
        this.persisted = persisted;
        this.oracleSummary = oracleSummary;
        this.gaussSummary = gaussSummary;
        this.coverageSummary = coverageSummary;
        this.generatedAt = generatedAt != null ? generatedAt : Instant.now();
        this.oracleIndex = indexByPath(oracleSummary);
        this.gaussIndex = indexByPath(gaussSummary);
        this.coverageIndex = indexCoverage(coverageSummary);
    }

    /**
     * 返回整体迁移覆盖率（0~1）。
     */
    public double overallCoverage() {
        if (coverageSummary == null) {
            return 0D;
        }
        return coverageSummary.getOverallCoverage();
    }

    public Set<String> filePaths() {
        return Stream.of(oracleIndex.keySet(), gaussIndex.keySet())
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public Optional<DiffFile> findOracle(String filePath) {
        return Optional.ofNullable(oracleIndex.get(filePath));
    }

    public Optional<DiffFile> findGauss(String filePath) {
        return Optional.ofNullable(gaussIndex.get(filePath));
    }

    public Optional<CoverageDetail> findCoverage(String filePath) {
        return Optional.ofNullable(coverageIndex.get(filePath));
    }

    public DiffSummary overviewForOracleFile(String filePath) {
        return cloneSummaryWithSingleFile(oracleSummary, oracleIndex.get(filePath));
    }

    public DiffSummary overviewForGaussFile(String filePath) {
        return cloneSummaryWithSingleFile(gaussSummary, gaussIndex.get(filePath));
    }

    private Map<String, DiffFile> indexByPath(DiffSummary summary) {
        if (summary == null || CollectionUtils.isEmpty(summary.getDiffFiles())) {
            return Collections.emptyMap();
        }
        return summary.getDiffFiles().stream()
                .filter(file -> file != null && StringUtils.hasText(file.getRelativePath()))
                .collect(Collectors.toMap(DiffFile::getRelativePath, file -> file, (left, right) -> right, LinkedHashMap::new));
    }

    private Map<String, CoverageDetail> indexCoverage(CoverageSummary summary) {
        if (summary == null || CollectionUtils.isEmpty(summary.getDetails())) {
            return Collections.emptyMap();
        }
        return summary.getDetails().stream()
                .filter(detail -> detail != null && StringUtils.hasText(detail.getFilePath()))
                .collect(Collectors.toMap(CoverageDetail::getFilePath, detail -> detail, (left, right) -> right, LinkedHashMap::new));
    }

    /**
     * 复制单个文件的 Diff 概览；即便指定文件在目标侧缺失，也要保留仓库配置用于后续落盘。
     */
    private DiffSummary cloneSummaryWithSingleFile(DiffSummary original, DiffFile file) {
        if (original == null) {
            return null;
        }
        List<DiffFile> files = file == null
                ? Collections.emptyList()
                : Collections.singletonList(file);
        DiffSummary clone = DiffSummary.builder()
                .repoConfig(original.getRepoConfig())
                .repoPath(original.getRepoPath())
                .branchFrom(original.getBranchFrom())
                .branchTo(original.getBranchTo())
                .baseCommitId(original.getBaseCommitId())
                .targetCommitId(original.getTargetCommitId())
                .deltaType(original.getDeltaType())
                .scanTime(original.getScanTime())
                .diffFiles(files)
                .build();
        return clone;
    }

    private static class UUIDGenerator {
        private UUIDGenerator() {
        }

        private static String randomTaskId() {
            return java.util.UUID.randomUUID().toString();
        }
    }
}
