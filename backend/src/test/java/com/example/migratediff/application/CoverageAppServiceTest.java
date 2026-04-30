package com.example.migratediff.application;

import com.example.migratediff.domain.coverage.*;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.infrastructure.persistence.CoverageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 测试覆盖率应用服务的持久化功能
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("覆盖率应用服务测试")
class CoverageAppServiceTest {

    @Mock
    private CoverageRepository coverageRepository;

    @Mock
    private CoverageEvaluator coverageEvaluator;

    private CoverageAppService service;
    private Map<String, CoverageAlgorithm> algorithms;

    @BeforeEach
    void setUp() {
        algorithms = new HashMap<>();
        
        // 创建并配置模拟的CoverageAlgorithm
        CoverageAlgorithm mockAlgorithm = mock(CoverageAlgorithm.class);
        lenient().when(mockAlgorithm.calculate(any(CalculationContext.class)))
                .thenReturn(CoverageSummary.builder()
                        .taskId("test-task")
                        .overallCoverage(0.8)
                        .totalMatchedLines(80.0)
                        .totalLines(100)
                        .build());
        
        algorithms.put("test-algorithm", mockAlgorithm);
        
        // 使用修复后的构造函数，直接注入CoverageRepository
        service = new CoverageAppService(algorithms, coverageEvaluator, coverageRepository);
        
        // 设置算法名称，确保与测试中的算法映射匹配
        service.setAlgorithm("test-algorithm");
    }

    @Test
    @DisplayName("应该能够分析覆盖率并保存结果")
    void shouldAnalyzeCoverageAndSaveResult() {
        // 准备测试数据
        String taskId = "test-task-1";
        DiffSummary oracleSummary = createTestDiffSummary();
        DiffSummary gaussSummary = createTestDiffSummary();
        
        // 执行测试
        CoverageSummary result = service.analyzeCoverage(taskId, oracleSummary, gaussSummary, true);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(taskId, result.getTaskId());
        
        // 验证保存操作被调用
        verify(coverageRepository).save(result);
    }

    @Test
    @DisplayName("当仓库为null时应该仍然能够分析覆盖率但不保存")
    void shouldAnalyzeCoverageWithoutSavingWhenRepositoryIsNull() {
        // 使用null仓库创建服务
        CoverageAppService serviceWithNullRepo = new CoverageAppService(algorithms, coverageEvaluator, null);
        
        // 设置算法名称，确保与测试中的算法映射匹配
        serviceWithNullRepo.setAlgorithm("test-algorithm");
        
        // 准备测试数据
        String taskId = "test-task-2";
        DiffSummary oracleSummary = createTestDiffSummary();
        DiffSummary gaussSummary = createTestDiffSummary();
        
        // 执行测试
        CoverageSummary result = serviceWithNullRepo.analyzeCoverage(taskId, oracleSummary, gaussSummary, true);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(taskId, result.getTaskId());
        
        // 验证保存操作没有被调用
        verify(coverageRepository, never()).save(any());
    }

    @Test
    @DisplayName("应该能够根据任务ID查找覆盖率摘要")
    void shouldFindCoverageSummaryByTaskId() {
        // 准备测试数据
        String taskId = "test-task-3";
        CoverageSummary expectedSummary = createTestCoverageSummary(taskId);
        
        // 模拟仓库返回数据
        when(coverageRepository.findByTaskId(taskId))
                .thenReturn(Optional.of(expectedSummary));
        
        // 执行测试
        Optional<CoverageSummary> result = service.findByTaskId(taskId);
        
        // 验证结果
        assertTrue(result.isPresent());
        assertEquals(expectedSummary.getTaskId(), result.get().getTaskId());
        assertEquals(expectedSummary.getOverallCoverage(), result.get().getOverallCoverage());
        
        // 验证查询操作被调用
        verify(coverageRepository).findByTaskId(taskId);
    }

    @Test
    @DisplayName("查找不存在的任务ID应该返回空")
    void shouldReturnEmptyForNonExistentTaskId() {
        // 准备测试数据
        String taskId = "non-existent-task";
        
        // 模拟仓库返回空
        when(coverageRepository.findByTaskId(taskId))
                .thenReturn(Optional.empty());
        
        // 执行测试
        Optional<CoverageSummary> result = service.findByTaskId(taskId);
        
        // 验证结果
        assertFalse(result.isPresent());
        
        // 验证查询操作被调用
        verify(coverageRepository).findByTaskId(taskId);
    }

    @Test
    @DisplayName("当仓库为null时查找任务ID应该返回空")
    void shouldReturnEmptyWhenRepositoryIsNull() {
        // 使用null仓库创建服务
        CoverageAppService serviceWithNullRepo = new CoverageAppService(algorithms, coverageEvaluator, null);
        
        // 准备测试数据
        String taskId = "test-task-4";
        
        // 执行测试
        Optional<CoverageSummary> result = serviceWithNullRepo.findByTaskId(taskId);
        
        // 验证结果
        assertFalse(result.isPresent());
        
        // 验证查询操作没有被调用
        verify(coverageRepository, never()).findByTaskId(any());
    }

    /**
     * 创建测试用的差异摘要
     */
    private DiffSummary createTestDiffSummary() {
        return DiffSummary.builder()
                .repoPath("/test/repo")
                .branchFrom("main")
                .branchTo("feature")
                .baseCommitId("abc123")
                .targetCommitId("def456")
                .build();
    }

    /**
     * 创建测试用的覆盖率摘要
     */
    private CoverageSummary createTestCoverageSummary(String taskId) {
        return CoverageSummary.builder()
                .taskId(taskId)
                .overallCoverage(0.8)
                .totalMatchedLines(80.0)
                .totalLines(100)
                .build();
    }
}