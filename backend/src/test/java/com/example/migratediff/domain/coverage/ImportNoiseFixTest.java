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
 * 验证import语句和注释过滤修复的测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "coverage.filter.noise.enabled=true"  // 启用噪音过滤
})
class ImportNoiseFixTest {

    @Autowired
    private CoverageEvaluator coverageEvaluator;

    @Autowired
    private OrderAwareBlockMapper blockMapper;

    @Test
    void testImportOnlyBlocksCanMatch() {
        // 创建两个只包含import语句的块
        DiffBlock oBlock = DiffBlock.builder()
                .contentFrom("import java.util.Map;\nimport java.util.List;\n")
                .contentTo("import java.util.Map;\nimport java.util.List;\n")
                .startLineTo(1)
                .endLineTo(2)
                .type(DiffType.ADD)
                .build();

        DiffBlock gBlock = DiffBlock.builder()
                .contentFrom("import java.util.Map;\nimport java.util.List;\n")
                .contentTo("import java.util.Map;\nimport java.util.List;\n")
                .startLineTo(1)
                .endLineTo(2)
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

        // 测试块映射 - 应该能够匹配
        BlockMapping mapping = coverageEvaluator.getBlockMapping(oFile, gFile);

        System.out.println("=== Import-only块匹配测试 ===");
        System.out.println("映射覆盖率: " + mapping.getCoverage());
        System.out.println("匹配的O块数: " + mapping.getMatchedOracleBlocks().size());
        System.out.println("匹配的G块数: " + mapping.getMatchedGaussBlocks().size());
        System.out.println("未匹配的O块数: " + mapping.getUnmatchedOracle().size());
        System.out.println("未匹配的G块数: " + mapping.getUnmatchedGauss().size());
        
        if (!mapping.getMatchedOracleBlocks().isEmpty()) {
            double similarity = mapping.getSimilarity(mapping.getMatchedOracleBlocks().get(0));
            System.out.println("块相似度: " + similarity);
            
            // 关键断言：import-only块应该能够匹配，相似度应该较高
            assertTrue(similarity > 0.8, "Import-only块应该能够正确匹配，相似度: " + similarity);
        }
        
        // 应该没有未匹配的块
        assertEquals(0, mapping.getUnmatchedOracle().size(), "不应该有未匹配的O块");
        assertEquals(0, mapping.getUnmatchedGauss().size(), "不应该有未匹配的G块");
    }

    @Test
    void testDifferentImportBlocksCanMatch() {
        // 创建包含不同import的块，但结构相似
        DiffBlock oBlock = DiffBlock.builder()
                .contentFrom("import java.util.ArrayList;\nimport java.util.List;\n")
                .contentTo("import java.util.ArrayList;\nimport java.util.List;\n")
                .startLineTo(1)
                .endLineTo(2)
                .type(DiffType.ADD)
                .build();

        DiffBlock gBlock = DiffBlock.builder()
                .contentFrom("import java.util.LinkedList;\nimport java.util.List;\n")
                .contentTo("import java.util.LinkedList;\nimport java.util.List;\n")
                .startLineTo(1)
                .endLineTo(2)
                .type(DiffType.ADD)
                .build();

        DiffFile oFile = DiffFile.builder()
                .relativePath("DataStructure.java")
                .blocks(java.util.Collections.singletonList(oBlock))
                .build();

        DiffFile gFile = DiffFile.builder()
                .relativePath("DataStructure.java")
                .blocks(java.util.Collections.singletonList(gBlock))
                .build();

        BlockMapping mapping = coverageEvaluator.getBlockMapping(oFile, gFile);

        System.out.println("=== 不同Import块匹配测试 ===");
        System.out.println("映射覆盖率: " + mapping.getCoverage());
        System.out.println("匹配的O块数: " + mapping.getMatchedOracleBlocks().size());
        
        if (!mapping.getMatchedOracleBlocks().isEmpty()) {
            double similarity = mapping.getSimilarity(mapping.getMatchedOracleBlocks().get(0));
            System.out.println("块相似度: " + similarity);
            
            // 即使import不同，也应该能匹配（只是相似度稍低）
            assertTrue(similarity > 0.5, "不同import的块也应该能匹配，相似度: " + similarity);
        }
        
        // 应该没有未匹配的块
        assertEquals(0, mapping.getUnmatchedOracle().size(), "不应该有未匹配的O块");
    }

    @Test
    void testMixedContentBlocks() {
        // 创建包含import和实际代码的混合块
        DiffBlock oBlock = DiffBlock.builder()
                .contentFrom("import java.util.Map;\npublic class Test {\n    private Map<String, String> cache;\n}")
                .contentTo("import java.util.Map;\npublic class Test {\n    private Map<String, String> cache;\n}")
                .startLineTo(1)
                .endLineTo(3)
                .type(DiffType.ADD)
                .build();

        DiffBlock gBlock = DiffBlock.builder()
                .contentFrom("import java.util.HashMap;\npublic class Test {\n    private Map<String, String> cache;\n}")
                .contentTo("import java.util.HashMap;\npublic class Test {\n    private Map<String, String> cache;\n}")
                .startLineTo(1)
                .endLineTo(3)
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

        BlockMapping mapping = coverageEvaluator.getBlockMapping(oFile, gFile);

        System.out.println("=== 混合内容块匹配测试 ===");
        System.out.println("映射覆盖率: " + mapping.getCoverage());
        
        if (!mapping.getMatchedOracleBlocks().isEmpty()) {
            double similarity = mapping.getSimilarity(mapping.getMatchedOracleBlocks().get(0));
            System.out.println("块相似度: " + similarity);
            
            // 混合内容应该能正确匹配
            assertTrue(similarity > 0.7, "混合内容块应该能正确匹配，相似度: " + similarity);
        }
        
        // 应该没有未匹配的块
        assertEquals(0, mapping.getUnmatchedOracle().size(), "不应该有未匹配的O块");
    }

    @Test
    void testCoverageCalculationStillUsesFiltering() {
        // 验证覆盖率计算仍然使用噪音过滤
        DiffBlock oBlock = DiffBlock.builder()
                .contentFrom("import java.util.Map;\n// 这是一个注释\npublic int getValue() { return 42; }")
                .contentTo("import java.util.Map;\n// 这是另一个注释\npublic int getValue() { return 42; }")
                .startLineTo(1)
                .endLineTo(3)
                .type(DiffType.ADD)
                .build();

        DiffBlock gBlock = DiffBlock.builder()
                .contentFrom("import java.util.HashMap;\n// 不同注释\npublic int getValue() { return 42; }")
                .contentTo("import java.util.HashMap;\n// 不同注释\npublic int getValue() { return 42; }")
                .startLineTo(1)
                .endLineTo(3)
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

        // 测试覆盖率计算
        CoverageDetail coverageDetail = coverageEvaluator.evaluateFile(oFile, gFile);

        System.out.println("=== 覆盖率计算噪音过滤验证 ===");
        System.out.println("覆盖率: " + coverageDetail.getCoverage());
        System.out.println("匹配行数: " + coverageDetail.getMatchedLines());
        System.out.println("总行数: " + coverageDetail.getTotalLines());
        
        // 覆盖率应该较高，因为核心逻辑相同（过滤了噪音）
        assertTrue(coverageDetail.getCoverage() > 0.7, 
                  "覆盖率计算应该过滤噪音，得到较高相似度: " + coverageDetail.getCoverage());
    }

    @Test
    void testOrderAwareBlockMapperConfig() {
        // 验证OrderAwareBlockMapper的配置信息
        String configInfo = blockMapper.getConfigInfo();
        String cacheStats = blockMapper.getCacheStats();

        System.out.println("=== OrderAwareBlockMapper配置验证 ===");
        System.out.println("配置信息: " + configInfo);
        System.out.println("缓存统计: " + cacheStats);

        // 验证配置信息包含正确的参数
        assertTrue(configInfo.contains("块匹配不过滤噪音"), 
                  "配置信息应该表明块匹配时不过滤噪音");
        assertTrue(configInfo.contains("位置窗口=3"), 
                  "配置信息应该包含位置窗口大小");
    }
}
