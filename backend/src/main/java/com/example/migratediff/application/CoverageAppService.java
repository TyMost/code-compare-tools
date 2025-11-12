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
        CoverageSummary summary = coverageEvaluator.evaluateFiles(
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
