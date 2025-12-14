package com.example.migratediff.application;

import com.example.migratediff.domain.coverage.CoverageAlgorithm;
import com.example.migratediff.domain.coverage.CoverageEvaluator;
import com.example.migratediff.domain.coverage.CoverageSummary;
import com.example.migratediff.domain.coverage.CalculationContext;
import com.example.migratediff.domain.diff.DeltaGroup;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.infrastructure.persistence.CoverageRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CoverageAppService {

    private final Map<String, CoverageAlgorithm> algorithms;
    private final CoverageEvaluator coverageEvaluator; // 保持向后兼容
    private final CoverageRepository coverageRepository;
    
    @Value("${coverage.algorithm:legacy}")
    private String algorithm;

    public CoverageAppService(Map<String, CoverageAlgorithm> algorithms,
                              CoverageEvaluator coverageEvaluator,
                              @Nullable CoverageRepository coverageRepository) {
        this.algorithms = algorithms;
        this.coverageEvaluator = coverageEvaluator;
        this.coverageRepository = coverageRepository;
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
        return Optional.ofNullable(coverageRepository)
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
            Optional.ofNullable(coverageRepository)
                    .ifPresent(repository -> repository.save(summary));
        }
        return summary;
    }

    /**
     * 使用基于块映射的新方法计算覆盖率
     */
    private CoverageSummary evaluateFilesWithMapping(java.util.List<DiffFile> deltaOFiles,
                                                      java.util.List<DiffFile> deltaGFiles) {
        // 创建计算上下文
        CalculationContext context = CalculationContext.builder()
                .deltaOFiles(deltaOFiles)
                .deltaGFiles(deltaGFiles)
                .displayThreshold(0.85)
                .persistResult(true)
                .config(buildAlgorithmConfig())
                .build();
        
        // 根据配置选择算法
        CoverageAlgorithm selectedAlgorithm = algorithms.get(algorithm);
        if (selectedAlgorithm == null) {
            throw new IllegalStateException("未找到算法实现: " + algorithm + "，可用算法: " + algorithms.keySet());
        }
        
        return selectedAlgorithm.calculate(context);
    }
    
    /**
     * 构建算法配置
     */
    private CalculationContext.AlgorithmConfig buildAlgorithmConfig() {
        return CalculationContext.AlgorithmConfig.builder()
                .enableNoiseFiltering(true) // 从配置文件读取
                .skipUnmatchedNoiseBlocks(false) // 从配置文件读取
                .positionWindow(50) // Legacy模式使用
                .minSimilarityThreshold(0.1) // Strong模式使用
                .criticalMissThreshold(0.3) // Strong模式使用
                .build();
    }
    
    /**
     * 新增：使用指定算法计算覆盖率
     */
    public CoverageSummary analyzeCoverageWithAlgorithm(String taskId, 
                                                      List<DiffFile> deltaOFiles,
                                                      List<DiffFile> deltaGFiles,
                                                      boolean persistResult) {
        CalculationContext context = CalculationContext.builder()
                .taskId(taskId)
                .deltaOFiles(deltaOFiles)
                .deltaGFiles(deltaGFiles)
                .displayThreshold(0.85)
                .persistResult(persistResult)
                .config(buildAlgorithmConfig())
                .build();
        
        CoverageAlgorithm selectedAlgorithm = algorithms.get(algorithm);
        if (selectedAlgorithm == null) {
            throw new IllegalStateException("未找到算法实现: " + algorithm);
        }
        
        CoverageSummary summary = selectedAlgorithm.calculate(context);
        summary.setTaskId(taskId);
        
        if (persistResult) {
            Optional.ofNullable(coverageRepository)
                    .ifPresent(repository -> repository.save(summary));
        }
        
        return summary;
    }
    
    /**
     * 获取当前算法名称
     */
    public String getCurrentAlgorithm() {
        return algorithm;
    }
    
    /**
     * 设置算法名称，主要用于测试
     */
    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
    
    /**
     * 获取可用算法列表
     */
    public java.util.Set<String> getAvailableAlgorithms() {
        return algorithms.keySet();
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
