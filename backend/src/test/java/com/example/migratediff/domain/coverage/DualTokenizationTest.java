package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证双Tokenization机制的测试
 * 测试在块匹配时保留噪音，在相似度计算时去除噪音的功能
 */
@SpringBootTest
@TestPropertySource(properties = {
    "coverage.filter.noise.enabled=true"  // 启用噪音过滤
})
class DualTokenizationTest {

    @Autowired
    private CoverageEvaluator coverageEvaluator;

    @Autowired
    private OrderAwareBlockMapper blockMapper;

    @Test
    void testBlockMatchingPreservesNoise() {
        // 创建两个包含不同import但相同逻辑的块
        DiffBlock oBlock = DiffBlock.builder()
                .contentFrom("import java.util.Map;\nimport java.util.List;\n// 这是注释\npublic void test() {\n    // 逻辑代码\n}")
                .contentTo("import java.util.Map;\nimport java.util.List;\n// 这是注释\npublic void test() {\n    // 逻辑代码\n}")
                .startLineTo(1)
                .endLineTo(5)
                .type(DiffType.ADD)
                .build();

        DiffBlock gBlock = DiffBlock.builder()
                .contentFrom("import java.util.HashMap;\nimport java.util.ArrayList;\n// 不同注释\npublic void test() {\n    // 相同逻辑代码\n}")
                .contentTo("import java.util.HashMap;\nimport java.util.ArrayList;\n// 不同注释\npublic void test() {\n    // 相同逻辑代码\n}")
                .startLineTo(1)
                .endLineTo(5)
                .type(DiffType.ADD)
                .build();

        DiffFile oFile = DiffFile.builder()
                .relativePath("Test.java")
                .blocks(java.util.Collections.singletonList(oBlock))
                .build();

        DiffFile gFile = DiffFile.builder()
                .relativePath("Test.java")
                .blocks(java.util.Collections.singletonList(gBlock))
                .build();

        // 测试块映射 - 应该能够匹配（保留噪音信息）
        BlockMapping mapping = coverageEvaluator.getBlockMapping(oFile, gFile);

        System.out.println("=== 双Tokenization测试 ===");
        System.out.println("映射覆盖率: " + mapping.getCoverage());
        System.out.println("匹配的O块数: " + mapping.getMatchedOracleBlocks().size());
        System.out.println("匹配的G块数: " + mapping.getMatchedGaussBlocks().size());
        
        if (!mapping.getMatchedOracleBlocks().isEmpty()) {
            double similarity = mapping.getSimilarity(mapping.getMatchedOracleBlocks().get(0));
            System.out.println("块匹配相似度（保留噪音）: " + similarity);
            
            // 块匹配应该成功，因为保留了完整的import信息
            assertTrue(similarity > 0.5, "块匹配应该成功，相似度: " + similarity);
            assertEquals(1, mapping.getMatchedOracleBlocks().size(), "应该有1个匹配的O块");
            assertEquals(0, mapping.getUnmatchedOracle().size(), "不应该有未匹配的O块");
        }
    }

    @Test
    void testCoverageCalculationFiltersNoise() {
        // 创建包含不同import但相同核心逻辑的块
        DiffBlock oBlock = DiffBlock.builder()
                .contentFrom("import java.util.Map;\n// Oracle注释\npublic class Calculator {\n    public int add(int a, int b) {\n        return a + b;\n    }\n}")
                .contentTo("import java.util.Map;\n// Oracle注释\npublic class Calculator {\n    public int add(int a, int b) {\n        return a + b;\n    }\n}")
                .startLineTo(1)
                .endLineTo(6)
                .type(DiffType.ADD)
                .build();

        DiffBlock gBlock = DiffBlock.builder()
                .contentFrom("import java.util.HashMap;\n// Gauss注释\npublic class Calculator {\n    public int add(int a, int b) {\n        return a + b;\n    }\n}")
                .contentTo("import java.util.HashMap;\n// Gauss注释\npublic class Calculator {\n    public int add(int a, int b) {\n        return a + b;\n    }\n}")
                .startLineTo(1)
                .endLineTo(6)
                .type(DiffType.ADD)
                .build();

        DiffFile oFile = DiffFile.builder()
                .relativePath("Calculator.java")
                .blocks(java.util.Collections.singletonList(oBlock))
                .build();

        DiffFile gFile = DiffFile.builder()
                .relativePath("Calculator.java")
                .blocks(java.util.Collections.singletonList(gBlock))
                .build();

        // 测试覆盖率计算 - 应该过滤噪音得到高相似度
        CoverageDetail coverageDetail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.85);

        System.out.println("=== 覆盖率计算噪音过滤验证 ===");
        System.out.println("覆盖率: " + coverageDetail.getCoverage());
        System.out.println("匹配行数: " + coverageDetail.getMatchedLines());
        System.out.println("总行数: " + coverageDetail.getTotalLines());
        System.out.println("高相似度块数: " + coverageDetail.getMatchedBlocks().size());
        System.out.println("低相似度块数: " + coverageDetail.getUnmatchedBlocks().size());
        
        // 覆盖率应该很高，因为核心逻辑相同（过滤了import和注释噪音）
        assertTrue(coverageDetail.getCoverage() > 0.8, 
                  "覆盖率应该很高，因为过滤了噪音: " + coverageDetail.getCoverage());
        
        // 应该被归类为高相似度块
        assertEquals(1, coverageDetail.getMatchedBlocks().size(), "应该有1个高相似度块");
        assertEquals(0, coverageDetail.getUnmatchedBlocks().size(), "不应该有低相似度块");
    }

    @Test
    void testMixedNoiseContent() {
        // 创建包含混合噪音内容的块：import、注释、核心逻辑
        DiffBlock oBlock = DiffBlock.builder()
                .contentFrom("import java.util.List;\nimport java.util.Map;\n/**\n * 这是一个JavaDoc注释\n * 多行注释\n */\n// 单行注释\npublic class Service {\n    private Map<String, String> cache;\n}")
                .contentTo("import java.util.List;\nimport java.util.Map;\n/**\n * 这是一个JavaDoc注释\n * 多行注释\n */\n// 单行注释\npublic class Service {\n    private Map<String, String> cache;\n}")
                .startLineTo(1)
                .endLineTo(10)
                .type(DiffType.ADD)
                .build();

        DiffBlock gBlock = DiffBlock.builder()
                .contentFrom("import java.util.ArrayList;\nimport java.util.HashMap;\n/**\n * 不同的JavaDoc注释\n * 也是多行\n */\n// 不同的单行注释\npublic class Service {\n    private Map<String, String> cache;\n}")
                .contentTo("import java.util.ArrayList;\nimport java.util.HashMap;\n/**\n * 不同的JavaDoc注释\n * 也是多行\n */\n// 不同的单行注释\npublic class Service {\n    private Map<String, String> cache;\n}")
                .startLineTo(1)
                .endLineTo(10)
                .type(DiffType.ADD)
                .build();

        DiffFile oFile = DiffFile.builder()
                .relativePath("Service.java")
                .blocks(java.util.Collections.singletonList(oBlock))
                .build();

        DiffFile gFile = DiffFile.builder()
                .relativePath("Service.java")
                .blocks(java.util.Collections.singletonList(gBlock))
                .build();

        // 测试块映射
        BlockMapping mapping = coverageEvaluator.getBlockMapping(oFile, gFile);

        System.out.println("=== 混合噪音内容测试 ===");
        System.out.println("映射覆盖率: " + mapping.getCoverage());
        
        // 测试覆盖率计算
        CoverageDetail coverageDetail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.7);
        System.out.println("最终覆盖率: " + coverageDetail.getCoverage());
        
        if (!mapping.getMatchedOracleBlocks().isEmpty()) {
            double blockSimilarity = mapping.getSimilarity(mapping.getMatchedOracleBlocks().get(0));
            System.out.println("块相似度: " + blockSimilarity);
            
            // 块匹配应该成功
            assertTrue(blockSimilarity > 0.5, "混合噪音内容应该能匹配: " + blockSimilarity);
            
            // 覆盖率应该更高（因为过滤了噪音）
            assertTrue(coverageDetail.getCoverage() >= blockSimilarity, 
                      "过滤噪音后的覆盖率应该不低于块相似度: " + coverageDetail.getCoverage() + " vs " + blockSimilarity);
        }
    }

    @Test
    void testPerformanceWithCaching() {
        // 测试缓存机制的性能
        DiffBlock oBlock = DiffBlock.builder()
                .contentFrom("import java.util.Map;\n// 注释\npublic void method() {\n    // 实现\n}")
                .contentTo("import java.util.Map;\n// 注释\npublic void method() {\n    // 实现\n}")
                .startLineTo(1)
                .endLineTo(4)
                .type(DiffType.ADD)
                .build();

        DiffBlock gBlock = DiffBlock.builder()
                .contentFrom("import java.util.HashMap;\n// 不同注释\npublic void method() {\n    // 实现\n}")
                .contentTo("import java.util.HashMap;\n// 不同注释\npublic void method() {\n    // 实现\n}")
                .startLineTo(1)
                .endLineTo(4)
                .type(DiffType.ADD)
                .build();

        DiffFile oFile = DiffFile.builder()
                .relativePath("PerformanceTest.java")
                .blocks(java.util.Collections.singletonList(oBlock))
                .build();

        DiffFile gFile = DiffFile.builder()
                .relativePath("PerformanceTest.java")
                .blocks(java.util.Collections.singletonList(gBlock))
                .build();

        long startTime = System.nanoTime();
        
        // 多次计算，测试缓存效果
        for (int i = 0; i < 100; i++) {
            coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        }
        
        long endTime = System.nanoTime();
        double durationMs = (endTime - startTime) / 1_000_000.0;
        
        System.out.println("=== 性能测试 ===");
        System.out.println("100次计算耗时: " + durationMs + " ms");
        System.out.println("平均每次计算: " + (durationMs / 100) + " ms");
        
        // 性能应该合理，每次计算应该在几毫秒内
        assertTrue(durationMs < 1000, "100次计算应该在1秒内完成，实际: " + durationMs + " ms");
    }
}
