package com.example.migratediff.application;

import com.example.migratediff.domain.coverage.CoverageEvaluator;
import com.example.migratediff.domain.coverage.CoverageSummary;
import com.example.migratediff.domain.diff.DeltaGroup;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.infrastructure.persistence.CoverageRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Service
public class CoverageAppService {

    private final CoverageEvaluator coverageEvaluator;
    private final ObjectProvider<CoverageRepository> coverageRepositoryProvider;

    public CoverageAppService(CoverageEvaluator coverageEvaluator,
                              ObjectProvider<CoverageRepository> coverageRepositoryProvider) {
        this.coverageEvaluator = coverageEvaluator;
        this.coverageRepositoryProvider = coverageRepositoryProvider;
    }

    public CoverageSummary analyzeCoverage(String taskId, DeltaGroup deltaGroup, boolean persistResult) {
        DiffFile deltaO = deltaGroup == null ? null : deltaGroup.getDeltaO();
        DiffFile deltaG = deltaGroup == null ? null : deltaGroup.getDeltaG();
        return analyzeCoverage(
                taskId,
                deltaO == null ? Collections.emptyList() : Collections.singletonList(deltaO),
                deltaG == null ? Collections.emptyList() : Collections.singletonList(deltaG),
                persistResult
        );
    }

    public Optional<CoverageSummary> findByTaskId(String taskId) {
        return Optional.ofNullable(coverageRepositoryProvider.getIfAvailable())
                .flatMap(repository -> repository.findByTaskId(taskId));
    }

    /**
     * 获取CoverageEvaluator实例，供其他服务使用
     */
    public CoverageEvaluator getCoverageEvaluator() {
        return coverageEvaluator;
    }

    public CoverageSummary analyzeCoverage(String taskId,
                                           DiffSummary deltaOSummary,
                                           DiffSummary deltaGSummary,
                                           boolean persistResult) {
        return analyzeCoverage(
                taskId,
                deltaOSummary == null ? Collections.emptyList() : deltaOSummary.getDiffFiles(),
                deltaGSummary == null ? Collections.emptyList() : deltaGSummary.getDiffFiles(),
                persistResult
        );
    }

    private CoverageSummary analyzeCoverage(String taskId,
                                            Iterable<DiffFile> deltaOFiles,
                                            Iterable<DiffFile> deltaGFiles,
                                            boolean persistResult) {
        // 使用新的基于块映射的覆盖率计算方法
        CoverageSummary summary = evaluateFilesWithMapping(
                toList(deltaOFiles),
                toList(deltaGFiles)
        );
        summary.setTaskId(taskId);
        if (persistResult) {
            Optional.ofNullable(coverageRepositoryProvider.getIfAvailable())
                    .ifPresent(repository -> repository.save(summary));
        }
        return summary;
    }

    /**
     * 使用基于块映射的新方法计算覆盖率
     */
    private CoverageSummary evaluateFilesWithMapping(java.util.List<DiffFile> deltaOFiles,
                                                      java.util.List<DiffFile> deltaGFiles) {
        java.util.List<DiffFile> safeDeltaO = deltaOFiles == null ? Collections.emptyList() : deltaOFiles;
        java.util.Map<String, DiffFile> targetIndex = indexByPath(deltaGFiles);

        java.util.List<com.example.migratediff.domain.coverage.CoverageDetail> details = new java.util.ArrayList<>();
        double totalMatchedLines = 0D;
        int totalLines = 0;

        for (DiffFile originFile : safeDeltaO) {
            if (originFile == null) {
                continue;
            }
            DiffFile candidate = targetIndex.getOrDefault(originFile.getRelativePath(), null);
            // 使用新的基于映射的覆盖率计算方法
            com.example.migratediff.domain.coverage.CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(
                    originFile, candidate, 0.85D);
            details.add(detail);
            totalMatchedLines += detail.getMatchedLines();
            totalLines += detail.getTotalLines();
        }

        double overallCoverage = totalLines == 0 ? 1D : (double) totalMatchedLines / (double) totalLines;
        CoverageSummary summary = CoverageSummary.builder()
                .overallCoverage(overallCoverage)
                .totalMatchedLines(totalMatchedLines)
                .totalLines(totalLines)
                .build();
        summary.getDetails().addAll(details);
        return summary;
    }

    private java.util.Map<String, DiffFile> indexByPath(java.util.List<DiffFile> files) {
        java.util.Map<String, DiffFile> index = new java.util.HashMap<>();
        if (files == null) {
            return index;
        }
        for (DiffFile file : files) {
            if (file == null) {
                continue;
            }
            String relativePath = file.getRelativePath();
            if (relativePath != null) {
                index.put(relativePath, file);
            }
        }
        return index;
    }

    private java.util.List<DiffFile> toList(Iterable<DiffFile> iterable) {
        if (iterable == null) {
            return Collections.emptyList();
        }
        java.util.List<DiffFile> list = new java.util.ArrayList<>();
        for (DiffFile file : iterable) {
            if (file != null) {
                list.add(file);
            }
        }
        return list;
    }
}
