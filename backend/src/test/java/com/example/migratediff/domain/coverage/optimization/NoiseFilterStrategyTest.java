package com.example.migratediff.domain.coverage.optimization;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 噪音过滤策略单元测试
 */
class NoiseFilterStrategyTest {

    private NoiseFilterStrategy noiseFilterStrategy;

    @Mock
    private DiffFile mockOriginFile;

    @Mock
    private DiffFile mockTargetFile;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        noiseFilterStrategy = new NoiseFilterStrategy();
    }

    @Test
    @DisplayName("测试策略名称和优先级")
    void testStrategyMetadata() {
        assertEquals("NOISE_FILTER", noiseFilterStrategy.getStrategyName());
        assertEquals(1, noiseFilterStrategy.getPriority());
    }

    @Test
    @DisplayName("测试完美匹配情况 - 不支持优化")
    void testPerfectMatchNotSupported() {
        DiffBlock oBlock = createDiffBlock("import java.util.List;\nimport java.util.Map;\npublic class Test { }");
        DiffBlock gBlock = createDiffBlock("import java.util.List;\nimport java.util.Map;\npublic class Test { }");

        OptimizationContext context = OptimizationContext.builder()
                .originBlock(oBlock)
                .targetBlock(gBlock)
                .currentSimilarity(1.0)
                .originFile(mockOriginFile)
                .targetFile(mockTargetFile)
                .build();

        assertFalse(noiseFilterStrategy.supports(context));
    }

    @Test
    @DisplayName("测试非完美匹配情况 - 支持优化")
    void testNonPerfectMatchSupported() {
        DiffBlock oBlock = createDiffBlock("import java.util.List;\npublic class Test { private List<String> items; }");
        DiffBlock gBlock = createDiffBlock("import java.util.Map;\npublic class Test { private Map<String, Object> items; }");

        OptimizationContext context = OptimizationContext.builder()
                .originBlock(oBlock)
                .targetBlock(gBlock)
                .currentSimilarity(0.8)
                .originFile(mockOriginFile)
                .targetFile(mockTargetFile)
                .build();

        assertTrue(noiseFilterStrategy.supports(context));
    }

    @Test
    @DisplayName("测试噪音过滤优化 - 有改进")
    void testNoiseFilterOptimizationWithImprovement() {
        // 原始块包含大量噪音（import语句）
        String originContent = "import java.util.List;\nimport java.util.Map;\nimport java.io.File;\n" +
                           "/** 这是一个Java类 */\n" +
                           "public class UserService {\n" +
                           "    private List<User> users;\n" +
                           "    private Map<String, Object> config;\n" +
                           "}";

        // 目标块包含相同业务逻辑但不同的import
        String targetContent = "import java.util.ArrayList;\nimport java.util.HashMap;\nimport java.io.Serializable;\n" +
                            "/** 用户服务类 */\n" +
                            "public class UserService {\n" +
                            "    private ArrayList<User> users;\n" +
                            "    private HashMap<String, Object> config;\n" +
                            "}";

        DiffBlock oBlock = createDiffBlock(originContent);
        DiffBlock gBlock = createDiffBlock(targetContent);

        OptimizationContext context = OptimizationContext.builder()
                .originBlock(oBlock)
                .targetBlock(gBlock)
                .currentSimilarity(0.6) // 假设原始相似度较低
                .originFile(mockOriginFile)
                .targetFile(mockTargetFile)
                .build();

        OptimizationResult result = noiseFilterStrategy.optimize(context);

        assertTrue(result.isOptimized());
        assertTrue(result.getOptimizedSimilarity() > result.getOriginalSimilarity());
        assertEquals("噪音过滤后相似度提升", result.getReason());
        assertTrue(result.getExecutionTimeMs() >= 0);

        // 验证详细信息
        assertNotNull(result.getDetails());
        assertTrue(result.getDetails().containsKey("originNoiseRatio"));
        assertTrue(result.getDetails().containsKey("targetNoiseRatio"));
    }

    @Test
    @DisplayName("测试噪音过滤优化 - 无改进")
    void testNoiseFilterOptimizationWithoutImprovement() {
        String content = "public class UserService {\n    private List<User> users;\n}";

        DiffBlock oBlock = createDiffBlock(content);
        DiffBlock gBlock = createDiffBlock(content);

        OptimizationContext context = OptimizationContext.builder()
                .originBlock(oBlock)
                .targetBlock(gBlock)
                .currentSimilarity(0.9)
                .originFile(mockOriginFile)
                .targetFile(mockTargetFile)
                .build();

        OptimizationResult result = noiseFilterStrategy.optimize(context);

        assertFalse(result.isOptimized());
        assertEquals(0.9, result.getOptimizedSimilarity());
        assertTrue(result.getReason().contains("相似度未提升"));
    }

    @Test
    @DisplayName("测试噪音过滤后完全匹配")
    void testNoiseFilterPerfectMatch() {
        // 原始和目标块的业务逻辑相同，只有import不同
        String originContent = "import java.util.List;\npublic class Test { private List<String> items; }";
        String targetContent = "import java.util.ArrayList;\npublic class Test { private List<String> items; }";

        DiffBlock oBlock = createDiffBlock(originContent);
        DiffBlock gBlock = createDiffBlock(targetContent);

        OptimizationContext context = OptimizationContext.builder()
                .originBlock(oBlock)
                .targetBlock(gBlock)
                .currentSimilarity(0.7)
                .originFile(mockOriginFile)
                .targetFile(mockTargetFile)
                .build();

        OptimizationResult result = noiseFilterStrategy.optimize(context);

        assertTrue(result.isOptimized());
        assertEquals(1.0, result.getOptimizedSimilarity());
        assertTrue(result.getReason().contains("完全匹配"));
        assertTrue(result.isPerfectMatch());
    }

    @Test
    @DisplayName("测试空内容处理")
    void testEmptyContent() {
        DiffBlock oBlock = createDiffBlock("");
        DiffBlock gBlock = createDiffBlock("");

        OptimizationContext context = OptimizationContext.builder()
                .originBlock(oBlock)
                .targetBlock(gBlock)
                .currentSimilarity(0.5)
                .originFile(mockOriginFile)
                .targetFile(mockTargetFile)
                .build();

        OptimizationResult result = noiseFilterStrategy.optimize(context);

        // 空内容应该被视为完全匹配
        assertEquals(1.0, result.getOptimizedSimilarity());
        assertTrue(result.isOptimized() || result.getOriginalSimilarity() == 1.0);
    }

    @Test
    @DisplayName("测试null块处理")
    void testNullBlock() {
        OptimizationContext context = OptimizationContext.builder()
                .originBlock(null)
                .targetBlock(createDiffBlock("content"))
                .currentSimilarity(0.5)
                .originFile(mockOriginFile)
                .targetFile(mockTargetFile)
                .build();

        assertFalse(noiseFilterStrategy.supports(context));
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
}
