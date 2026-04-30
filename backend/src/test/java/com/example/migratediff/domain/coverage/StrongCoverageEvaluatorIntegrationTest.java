package com.example.migratediff.domain.coverage;

import com.example.migratediff.config.CoverageOptimizationConfig;
import com.example.migratediff.domain.coverage.optimization.CoverageOptimizationManager;
import com.example.migratediff.domain.coverage.optimization.NoiseFilterStrategy;
import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

/**
 * StrongCoverageEvaluator集成测试
 * 验证优化策略与覆盖率评估的集成效果
 */
class StrongCoverageEvaluatorIntegrationTest {

    @Mock
    private StrongBlockMapper mockBlockMapper;

    private StrongCoverageEvaluator coverageEvaluator;
    private CoverageOptimizationManager optimizationManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 创建优化配置
        CoverageOptimizationConfig config = new CoverageOptimizationConfig();
        config.setEnabled(true);
        config.getNoiseFilter().setEnabled(true);
        
        // 创建优化管理器
        NoiseFilterStrategy noiseFilterStrategy = new NoiseFilterStrategy();
        List<com.example.migratediff.domain.coverage.optimization.CoverageOptimizationStrategy> strategies = 
            Arrays.asList(noiseFilterStrategy);
        
        optimizationManager = new CoverageOptimizationManager(strategies, config);
        
        // 创建覆盖率评估器
        coverageEvaluator = new StrongCoverageEvaluator(mockBlockMapper, optimizationManager);
    }

    @Test
    @DisplayName("测试噪音过滤优化集成效果")
    void testNoiseFilterOptimizationIntegration() {
        // 创建原始文件（包含大量import）
        DiffFile originFile = createDiffFileWithImports("UserService.java", 
            "import java.util.List;\nimport java.util.Map;\nimport java.io.File;\n" +
            "/** 用户服务 */\n" +
            "public class UserService {\n" +
            "    private List<User> users;\n" +
            "    private Map<String, Object> config;\n" +
            "}");

        // 创建目标文件（包含不同import但相同业务逻辑）
        DiffFile targetFile = createDiffFileWithImports("UserService.java", 
            "import java.util.ArrayList;\nimport java.util.HashMap;\nimport java.io.Serializable;\n" +
            "/** 用户服务类 */\n" +
            "public class UserService {\n" +
            "    private ArrayList<User> users;\n" +
            "    private HashMap<String, Object> config;\n" +
            "}");

        // 创建原始块映射（模拟较低的相似度）
        BlockMapping originalMapping = createLowSimilarityMapping(originFile, targetFile, 0.6);
        when(mockBlockMapper.mapBlocksBestMatch(originFile.getBlocks(), targetFile.getBlocks(), 0.6)).thenReturn(originalMapping);

        // 执行评估
        CoverageDetail result = coverageEvaluator.evaluateFile(originFile, targetFile, 0.8, 0.3);

        // 验证优化效果
        assertNotNull(result);
        assertTrue(result.getCoverage() > 0.6, "优化后覆盖率应该有所提升");
        
        // 验证业务逻辑的准确性
        assertEquals("UserService.java", result.getFilePath());
        assertNotNull(result.getMatchedBlocks());
        assertNotNull(result.getUnmatchedBlocks());
    }

    @Test
    @DisplayName("测试完美匹配时不需要优化")
    void testPerfectMatchNoOptimizationNeeded() {
        // 创建完全相同的文件
        DiffFile originFile = createDiffFileWithImports("TestService.java", 
            "import java.util.List;\n" +
            "public class TestService {\n" +
            "    private List<String> items;\n" +
            "}");

        DiffFile targetFile = createDiffFileWithImports("TestService.java", 
            "import java.util.List;\n" +
            "public class TestService {\n" +
            "    private List<String> items;\n" +
            "}");

        // 创建完美匹配的映射
        BlockMapping perfectMapping = createPerfectSimilarityMapping(originFile, targetFile);
        when(mockBlockMapper.mapBlocksBestMatch(originFile.getBlocks(), targetFile.getBlocks(), 0.6)).thenReturn(perfectMapping);

        // 执行评估
        CoverageDetail result = coverageEvaluator.evaluateFile(originFile, targetFile, 0.8, 0.3);

        // 验证完美匹配
        assertNotNull(result);
        assertEquals(1.0, result.getCoverage(), 0.001, "完美匹配的覆盖率应该为1.0");
        assertEquals(1.0, result.getBlockScore(), 0.001, "完美匹配的块分数应该为1.0");
        assertFalse(result.isCriticalMiss(), "完美匹配不应该有关键丢失");
    }

    /**
     * 创建包含import语句的DiffFile
     */
    private DiffFile createDiffFileWithImports(String fileName, String content) {
        DiffBlock block = DiffBlock.builder()
                .startLineFrom(1)
                .endLineFrom(1)
                .startLineTo(1)
                .endLineTo(1)
                .contentFrom(content)
                .contentTo(content)
                .build();

        return DiffFile.builder()
                .relativePath(fileName)
                .blocks(Arrays.asList(block))
                .build();
    }

    /**
     * 创建低相似度的块映射
     */
    private BlockMapping createLowSimilarityMapping(DiffFile originFile, DiffFile targetFile, double similarity) {
        BlockMapping mapping = BlockMapping.builder().build();
        
        if (!originFile.getBlocks().isEmpty() && !targetFile.getBlocks().isEmpty()) {
            DiffBlock oBlock = originFile.getBlocks().get(0);
            DiffBlock gBlock = targetFile.getBlocks().get(0);
            mapping.addMatch(oBlock, gBlock, similarity);
        }

        return mapping;
    }

    /**
     * 创建完美相似度的块映射
     */
    private BlockMapping createPerfectSimilarityMapping(DiffFile originFile, DiffFile targetFile) {
        return createLowSimilarityMapping(originFile, targetFile, 1.0);
    }
}
