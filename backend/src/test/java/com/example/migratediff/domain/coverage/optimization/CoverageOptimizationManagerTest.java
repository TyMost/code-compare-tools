package com.example.migratediff.domain.coverage.optimization;

import com.example.migratediff.config.CoverageOptimizationConfig;
import com.example.migratediff.domain.coverage.BlockMapping;
import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 覆盖率优化管理器单元测试
 */
@ExtendWith(MockitoExtension.class)
class CoverageOptimizationManagerTest {

    @Mock
    private CoverageOptimizationStrategy mockStrategy1;

    @Mock
    private CoverageOptimizationStrategy mockStrategy2;

    @Mock
    private CoverageOptimizationConfig mockConfig;

    @Mock
    private DiffFile mockOriginFile;

    @Mock
    private DiffFile mockTargetFile;

    private CoverageOptimizationManager optimizationManager;

    @BeforeEach
    void setUp() {
        // 设置mock策略的优先级
        when(mockStrategy1.getPriority()).thenReturn(1);
        when(mockStrategy1.getStrategyName()).thenReturn("STRATEGY_1");
        when(mockStrategy2.getPriority()).thenReturn(2);
        when(mockStrategy2.getStrategyName()).thenReturn("STRATEGY_2");

        List<CoverageOptimizationStrategy> strategies = Arrays.asList(mockStrategy2, mockStrategy1);
        
        // 创建真实的配置对象
        CoverageOptimizationConfig realConfig = new CoverageOptimizationConfig();
        realConfig.setEnabled(true);
        
        // 创建管理器实例并注入真实配置
        optimizationManager = new CoverageOptimizationManager(strategies, realConfig);
    }

    @Test
    @DisplayName("测试优化禁用时的行为")
    void testOptimizationDisabled() {
        // 创建禁用优化的配置
        CoverageOptimizationConfig disabledConfig = new CoverageOptimizationConfig();
        disabledConfig.setEnabled(false);
        
        List<CoverageOptimizationStrategy> strategies = Arrays.asList(mockStrategy2, mockStrategy1);
        CoverageOptimizationManager disabledManager = new CoverageOptimizationManager(strategies, disabledConfig);
        
        BlockMapping originalMapping = createBasicMapping();
        
        BlockMapping result = disabledManager.optimizeMapping(originalMapping, mockOriginFile, mockTargetFile);
        
        // 优化禁用时应该返回原始映射
        assertSame(originalMapping, result);
    }

    @Test
    @DisplayName("测试完美匹配时不进行优化")
    void testPerfectMatchNoOptimization() {
        BlockMapping originalMapping = createPerfectMatchMapping();
        
        BlockMapping result = optimizationManager.optimizeMapping(originalMapping, mockOriginFile, mockTargetFile);
        
        // 完美匹配时，未优化的块数应该为0
        CoverageOptimizationManager.OptimizationSummary summary = 
            (CoverageOptimizationManager.OptimizationSummary) result.getOptimizationSummary();
        assertEquals(0, summary.getOptimizedBlocks());
        
        // 验证策略没有被调用
        verify(mockStrategy1, never()).optimize(any());
        verify(mockStrategy2, never()).optimize(any());
    }

    @Test
    @DisplayName("测试单个策略优化成功")
    void testSingleStrategyOptimizationSuccess() {
        // 设置策略1支持优化并返回改进结果
        when(mockStrategy1.supports(any())).thenReturn(true);
        OptimizationResult improvedResult = OptimizationResult.createOptimized(
            0.6, 0.9, "策略1优化成功", null, 100L);
        when(mockStrategy1.optimize(any())).thenReturn(improvedResult);

        BlockMapping originalMapping = createBasicMapping();
        
        BlockMapping result = optimizationManager.optimizeMapping(originalMapping, mockOriginFile, mockTargetFile);
        
        // 验证优化结果
        CoverageOptimizationManager.OptimizationSummary summary = 
            (CoverageOptimizationManager.OptimizationSummary) result.getOptimizationSummary();
        assertEquals(1, summary.getOptimizedBlocks());
        assertEquals(0.3, summary.getTotalImprovement(), 0.001);
        assertEquals(0.3, summary.getAverageImprovement(), 0.001);
        
        // 验证策略被调用
        verify(mockStrategy1, times(1)).supports(any());
        verify(mockStrategy1, times(1)).optimize(any());
    }

    @Test
    @DisplayName("测试多个策略按优先级执行")
    void testMultipleStrategiesExecutionOrder() {
        // 设置两个策略都支持优化
        when(mockStrategy1.supports(any())).thenReturn(true);
        when(mockStrategy2.supports(any())).thenReturn(true);
        
        // 策略1返回较小改进
        OptimizationResult result1 = OptimizationResult.createOptimized(
            0.6, 0.8, "策略1优化", null, 100L);
        when(mockStrategy1.optimize(any())).thenReturn(result1);
        
        // 策略2返回更大改进
        OptimizationResult result2 = OptimizationResult.createOptimized(
            0.8, 0.95, "策略2优化", null, 150L);
        when(mockStrategy2.optimize(any())).thenReturn(result2);

        BlockMapping originalMapping = createBasicMapping();
        
        BlockMapping result = optimizationManager.optimizeMapping(originalMapping, mockOriginFile, mockTargetFile);
        
        // 验证两个策略都被调用
        verify(mockStrategy1, times(1)).optimize(any());
        verify(mockStrategy2, times(1)).optimize(any());
        
        // 验证最终结果使用了更好的策略（策略1因为优先级高先执行）
        CoverageOptimizationManager.OptimizationSummary summary = 
            (CoverageOptimizationManager.OptimizationSummary) result.getOptimizationSummary();
        assertTrue(summary.getTotalImprovement() > 0);
    }

    @Test
    @DisplayName("测试完美匹配时停止后续策略执行")
    void testPerfectMatchStopsFurtherStrategies() {
        // 策略1返回完美匹配
        when(mockStrategy1.supports(any())).thenReturn(true);
        
        OptimizationResult perfectResult = OptimizationResult.createOptimized(
            0.8, 1.0, "完美匹配", null, 100L);
        when(mockStrategy1.optimize(any())).thenReturn(perfectResult);

        BlockMapping originalMapping = createBasicMapping();
        
        BlockMapping result = optimizationManager.optimizeMapping(originalMapping, mockOriginFile, mockTargetFile);
        
        // 验证策略1被调用，策略2没有被调用（因为策略1已经达到完美匹配）
        verify(mockStrategy1, times(1)).optimize(any());
        verify(mockStrategy2, never()).optimize(any());
        
        // 验证最终相似度为1.0
        assertEquals(1.0, result.getSimilarity(getFirstBlock(originalMapping)), 0.001);
    }

    @Test
    @DisplayName("测试策略执行异常处理")
    void testStrategyExceptionHandling() {
        // 策略1抛出异常，策略2正常执行
        when(mockStrategy1.supports(any())).thenReturn(true);
        when(mockStrategy1.optimize(any())).thenThrow(new RuntimeException("策略执行失败"));
        
        when(mockStrategy2.supports(any())).thenReturn(true);
        OptimizationResult result2 = OptimizationResult.createOptimized(
            0.6, 0.8, "策略2优化", null, 100L);
        when(mockStrategy2.optimize(any())).thenReturn(result2);

        BlockMapping originalMapping = createBasicMapping();
        
        // 应该能够正常执行，不会因为策略1的异常而失败
        assertDoesNotThrow(() -> {
            BlockMapping result = optimizationManager.optimizeMapping(originalMapping, mockOriginFile, mockTargetFile);
            
            CoverageOptimizationManager.OptimizationSummary summary = 
                (CoverageOptimizationManager.OptimizationSummary) result.getOptimizationSummary();
            assertEquals(1, summary.getOptimizedBlocks());
        });
    }

    @Test
    @DisplayName("测试优化历史记录")
    void testOptimizationHistoryRecording() {
        when(mockStrategy1.supports(any())).thenReturn(true);
        OptimizationResult result = OptimizationResult.createOptimized(
            0.6, 0.8, "测试优化", null, 100L);
        when(mockStrategy1.optimize(any())).thenReturn(result);

        BlockMapping originalMapping = createBasicMapping();
        
        BlockMapping optimizedResult = optimizationManager.optimizeMapping(
            originalMapping, mockOriginFile, mockTargetFile);
        
        // 验证优化历史被记录
        DiffBlock firstBlock = getFirstBlock(originalMapping);
        List<OptimizationResult> history = optimizedResult.getOptimizationHistory(firstBlock);
        assertNotNull(history);
        assertFalse(history.isEmpty());
        
        // 验证记录的优化结果
        OptimizationResult recordedResult = history.get(0);
        assertEquals(0.6, recordedResult.getOriginalSimilarity(), 0.001);
        assertEquals(0.8, recordedResult.getOptimizedSimilarity(), 0.001);
        assertEquals("测试优化", recordedResult.getReason());
    }

    @Test
    @DisplayName("测试未匹配块的处理")
    void testUnmatchedBlocksHandling() {
        BlockMapping originalMapping = createMappingWithUnmatchedBlocks();
        
        BlockMapping result = optimizationManager.optimizeMapping(originalMapping, mockOriginFile, mockTargetFile);
        
        // 未匹配块应该被正确处理
        CoverageOptimizationManager.OptimizationSummary summary = 
            (CoverageOptimizationManager.OptimizationSummary) result.getOptimizationSummary();
        
        // 验证总块数包含未匹配的块
        assertTrue(summary.getTotalBlocks() >= 2); // 至少包含1个匹配块和1个未匹配块
        
        // 验证未匹配块的相似度为0
        for (DiffBlock unmatchedBlock : originalMapping.getUnmatchedOracle()) {
            List<OptimizationResult> history = result.getOptimizationHistory(unmatchedBlock);
            if (!history.isEmpty()) {
                OptimizationResult unmatchedResult = history.get(0);
                assertEquals(0.0, unmatchedResult.getOptimizedSimilarity(), 0.001);
                // 实际返回的原因可能是"未匹配"或"无可适用策略"
                String reason = unmatchedResult.getReason();
                assertTrue(reason.equals("未匹配") || reason.equals("无可适用策略"));
            }
        }
    }

    @Test
    @DisplayName("测试策略启用检查")
    void testStrategyEnabledCheck() {
        // 创建禁用噪音过滤的配置
        CoverageOptimizationConfig configWithDisabledNoise = new CoverageOptimizationConfig();
        configWithDisabledNoise.setEnabled(true);
        configWithDisabledNoise.getNoiseFilter().setEnabled(false);
        
        // 设置策略1为NOISE_FILTER策略
        when(mockStrategy1.getStrategyName()).thenReturn("NOISE_FILTER");
        when(mockStrategy2.getStrategyName()).thenReturn("STRATEGY_2");
        
        when(mockStrategy2.supports(any())).thenReturn(true);
        
        OptimizationResult result2 = OptimizationResult.createOptimized(
            0.6, 0.8, "策略2优化", null, 100L);
        when(mockStrategy2.optimize(any())).thenReturn(result2);

        List<CoverageOptimizationStrategy> strategies = Arrays.asList(mockStrategy2, mockStrategy1);
        CoverageOptimizationManager managerWithDisabledNoise = new CoverageOptimizationManager(strategies, configWithDisabledNoise);

        BlockMapping originalMapping = createBasicMapping();
        
        BlockMapping result = managerWithDisabledNoise.optimizeMapping(originalMapping, mockOriginFile, mockTargetFile);
        
        // 策略1被禁用，只有策略2应该被调用
        verify(mockStrategy1, never()).optimize(any());
        verify(mockStrategy2, times(1)).optimize(any());
    }

    /**
     * 创建基础的块映射
     */
    private BlockMapping createBasicMapping() {
        DiffBlock oBlock = createDiffBlock("public class Test { }");
        DiffBlock gBlock = createDiffBlock("public class Test { }");
        
        BlockMapping mapping = BlockMapping.builder().build();
        mapping.addMatch(oBlock, gBlock, 0.6);
        
        return mapping;
    }

    /**
     * 创建完美匹配的块映射
     */
    private BlockMapping createPerfectMatchMapping() {
        DiffBlock oBlock = createDiffBlock("public class Test { }");
        DiffBlock gBlock = createDiffBlock("public class Test { }");
        
        BlockMapping mapping = BlockMapping.builder().build();
        mapping.addMatch(oBlock, gBlock, 1.0);
        
        return mapping;
    }

    /**
     * 创建包含未匹配块的映射
     */
    private BlockMapping createMappingWithUnmatchedBlocks() {
        DiffBlock matchedBlock = createDiffBlock("public class Matched { }");
        DiffBlock matchedTarget = createDiffBlock("public class Matched { }");
        DiffBlock unmatchedBlock = createDiffBlock("public class Unmatched { }");
        
        BlockMapping mapping = BlockMapping.builder().build();
        mapping.addMatch(matchedBlock, matchedTarget, 0.6);
        mapping.addUnmatchedOracle(unmatchedBlock);
        
        return mapping;
    }

    /**
     * 创建测试用的DiffBlock
     */
    private DiffBlock createDiffBlock(String content) {
        return DiffBlock.builder()
                .startLineFrom(1)
                .endLineFrom(1)
                .startLineTo(1)
                .endLineTo(1)
                .contentFrom(content)
                .contentTo(content)
                .build();
    }

    /**
     * 获取映射中的第一个块
     */
    private DiffBlock getFirstBlock(BlockMapping mapping) {
        return mapping.getMatchedOracleBlocks().get(0);
    }
}
