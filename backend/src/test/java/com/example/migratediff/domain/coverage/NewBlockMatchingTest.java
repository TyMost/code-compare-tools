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
 * 验证新增块匹配修复的测试
 * 专门测试o1->o2和g1->g2都是从0开始新增的情况
 */
@SpringBootTest
@TestPropertySource(properties = {
    "coverage.filter.noise.enabled=true"  // 启用噪音过滤
})
class NewBlockMatchingTest {

    @Autowired
    private CoverageEvaluator coverageEvaluator;

    @Autowired
    private OrderAwareBlockMapper blockMapper;

    @Test
    void testNewBlocksCanMatch() {
        // 创建两个都是新增的块（o1->o2 和 g1->g2）
        DiffBlock oBlock = DiffBlock.builder()
                .contentFrom("")  // 空的from，因为是新增
                .contentTo("public class TestService {\n    public void test() {}\n}")
                .startLineTo(1)
                .endLineTo(3)
                .type(DiffType.ADD)
                .build();

        DiffBlock gBlock = DiffBlock.builder()
                .contentFrom("")  // 空的from，因为是新增
                .contentTo("public class TestService {\n    public void test() {}\n}")
                .startLineTo(1)
                .endLineTo(3)
                .type(DiffType.ADD)
                .build();

        DiffFile oFile = DiffFile.builder()
                .relativePath("TestService.java")
                .blocks(java.util.Collections.singletonList(oBlock))
                .build();

        DiffFile gFile = DiffFile.builder()
                .relativePath("TestService.java")
                .blocks(java.util.Collections.singletonList(gBlock))
                .build();

        // 测试块映射 - 新增块应该能够匹配
        BlockMapping mapping = coverageEvaluator.getBlockMapping(oFile, gFile);

        System.out.println("=== 新增块匹配测试 ===");
        System.out.println("映射覆盖率: " + mapping.getCoverage());
        System.out.println("匹配的O块数: " + mapping.getMatchedOracleBlocks().size());
        System.out.println("匹配的G块数: " + mapping.getMatchedGaussBlocks().size());
        System.out.println("未匹配的O块数: " + mapping.getUnmatchedOracle().size());
        System.out.println("未匹配的G块数: " + mapping.getUnmatchedGauss().size());
        
        if (!mapping.getMatchedOracleBlocks().isEmpty()) {
            double similarity = mapping.getSimilarity(mapping.getMatchedOracleBlocks().get(0));
            System.out.println("块相似度: " + similarity);
            
            // 关键断言：新增块应该能够匹配，相似度应该较高
            assertTrue(similarity > 0.8, "新增块应该能够正确匹配，相似度: " + similarity);
        }
        
        // 应该没有未匹配的块
        assertEquals(0, mapping.getUnmatchedOracle().size(), "不应该有未匹配的O块");
        assertEquals(0, mapping.getUnmatchedGauss().size(), "不应该有未匹配的G块");
    }

    @Test
    void testDifferentNewBlocksCanMatch() {
        // 创建内容不同但结构相似的新增块
        DiffBlock oBlock = DiffBlock.builder()
                .contentFrom("")
                .contentTo("import java.util.List;\npublic class ServiceA {\n    private List<String> data;\n}")
                .startLineTo(1)
                .endLineTo(3)
                .type(DiffType.ADD)
                .build();

        DiffBlock gBlock = DiffBlock.builder()
                .contentFrom("")
                .contentTo("import java.util.ArrayList;\npublic class ServiceA {\n    private List<String> data;\n}")
                .startLineTo(1)
                .endLineTo(3)
                .type(DiffType.ADD)
                .build();

        DiffFile oFile = DiffFile.builder()
                .relativePath("ServiceA.java")
                .blocks(java.util.Collections.singletonList(oBlock))
                .build();

        DiffFile gFile = DiffFile.builder()
                .relativePath("ServiceA.java")
                .blocks(java.util.Collections.singletonList(gBlock))
                .build();

        BlockMapping mapping = coverageEvaluator.getBlockMapping(oFile, gFile);

        System.out.println("=== 不同新增块匹配测试 ===");
        System.out.println("映射覆盖率: " + mapping.getCoverage());
        System.out.println("匹配的O块数: " + mapping.getMatchedOracleBlocks().size());
        
        if (!mapping.getMatchedOracleBlocks().isEmpty()) {
            double similarity = mapping.getSimilarity(mapping.getMatchedOracleBlocks().get(0));
            System.out.println("块相似度: " + similarity);
            
            // 即使内容不同，也应该能匹配（只是相似度稍低）
            assertTrue(similarity > 0.5, "不同的新增块也应该能匹配，相似度: " + similarity);
        }
        
        // 应该没有未匹配的块
        assertEquals(0, mapping.getUnmatchedOracle().size(), "不应该有未匹配的O块");
    }

    @Test
    void testDeleteBlocksCanMatch() {
        // 创建两个都是删除的块
        DiffBlock oBlock = DiffBlock.builder()
                .contentFrom("public class OldService {\n    public void oldMethod() {}\n}")
                .contentTo("")  // 空的to，因为是删除
                .startLineFrom(1)
                .endLineFrom(3)
                .type(DiffType.DELETE)
                .build();

        DiffBlock gBlock = DiffBlock.builder()
                .contentFrom("public class OldService {\n    public void oldMethod() {}\n}")
                .contentTo("")  // 空的to，因为是删除
                .startLineFrom(1)
                .endLineFrom(3)
                .type(DiffType.DELETE)
                .build();

        DiffFile oFile = DiffFile.builder()
                .relativePath("OldService.java")
                .blocks(java.util.Collections.singletonList(oBlock))
                .build();

        DiffFile gFile = DiffFile.builder()
                .relativePath("OldService.java")
                .blocks(java.util.Collections.singletonList(gBlock))
                .build();

        BlockMapping mapping = coverageEvaluator.getBlockMapping(oFile, gFile);

        System.out.println("=== 删除块匹配测试 ===");
        System.out.println("映射覆盖率: " + mapping.getCoverage());
        System.out.println("匹配的O块数: " + mapping.getMatchedOracleBlocks().size());
        
        if (!mapping.getMatchedOracleBlocks().isEmpty()) {
            double similarity = mapping.getSimilarity(mapping.getMatchedOracleBlocks().get(0));
            System.out.println("块相似度: " + similarity);
            
            // 删除块应该能够匹配
            assertTrue(similarity > 0.8, "删除块应该能够正确匹配，相似度: " + similarity);
        }
        
        // 应该没有未匹配的块
        assertEquals(0, mapping.getUnmatchedOracle().size(), "不应该有未匹配的O块");
    }

    @Test
    void testMixedNewAndDeleteBlocks() {
        // 创建混合的新增和删除块
        DiffBlock oAddBlock = DiffBlock.builder()
                .contentFrom("")
                .contentTo("public class NewService {\n}")
                .startLineTo(1)
                .endLineTo(1)
                .type(DiffType.ADD)
                .build();

        DiffBlock oDeleteBlock = DiffBlock.builder()
                .contentFrom("public class OldService {\n}")
                .contentTo("")
                .startLineFrom(1)
                .endLineFrom(1)
                .type(DiffType.DELETE)
                .build();

        DiffBlock gAddBlock = DiffBlock.builder()
                .contentFrom("")
                .contentTo("public class NewService {\n}")
                .startLineTo(1)
                .endLineTo(1)
                .type(DiffType.ADD)
                .build();

        DiffBlock gDeleteBlock = DiffBlock.builder()
                .contentFrom("public class OldService {\n}")
                .contentTo("")
                .startLineFrom(1)
                .endLineFrom(1)
                .type(DiffType.DELETE)
                .build();

        DiffFile oFile = DiffFile.builder()
                .relativePath("MixedService.java")
                .blocks(java.util.Arrays.asList(oDeleteBlock, oAddBlock)) // 按顺序：先删除后新增
                .build();

        DiffFile gFile = DiffFile.builder()
                .relativePath("MixedService.java")
                .blocks(java.util.Arrays.asList(gDeleteBlock, gAddBlock)) // 按顺序：先删除后新增
                .build();

        BlockMapping mapping = coverageEvaluator.getBlockMapping(oFile, gFile);

        System.out.println("=== 混合新增删除块匹配测试 ===");
        System.out.println("映射覆盖率: " + mapping.getCoverage());
        System.out.println("匹配的O块数: " + mapping.getMatchedOracleBlocks().size());
        System.out.println("未匹配的O块数: " + mapping.getUnmatchedOracle().size());
        
        // 应该有两个匹配（一个删除，一个新增）
        assertEquals(2, mapping.getMatchedOracleBlocks().size(), "应该有2个匹配的O块");
        assertEquals(0, mapping.getUnmatchedOracle().size(), "不应该有未匹配的O块");
        
        // 验证两个匹配的相似度
        for (DiffBlock matchedBlock : mapping.getMatchedOracleBlocks()) {
            double similarity = mapping.getSimilarity(matchedBlock);
            System.out.println("匹配块相似度: " + similarity);
            assertTrue(similarity > 0.8, "每个匹配块都应该有较高相似度: " + similarity);
        }
    }

    @Test
    void testCoverageCalculationWithNewBlocks() {
        // 验证覆盖率计算仍然正确
        DiffBlock oBlock = DiffBlock.builder()
                .contentFrom("")
                .contentTo("import java.util.Map;\n// 新增的服务类\npublic class Service {\n    private Map<String, Object> cache;\n}")
                .startLineTo(1)
                .endLineTo(3)
                .type(DiffType.ADD)
                .build();

        DiffBlock gBlock = DiffBlock.builder()
                .contentFrom("")
                .contentTo("import java.util.HashMap;\n// 新增的服务类\npublic class Service {\n    private Map<String, Object> cache;\n}")
                .startLineTo(1)
                .endLineTo(3)
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

        // 测试覆盖率计算
        CoverageDetail coverageDetail = coverageEvaluator.evaluateFile(oFile, gFile);

        System.out.println("=== 新增块覆盖率计算测试 ===");
        System.out.println("覆盖率: " + coverageDetail.getCoverage());
        System.out.println("匹配行数: " + coverageDetail.getMatchedLines());
        System.out.println("总行数: " + coverageDetail.getTotalLines());
        
        // 覆盖率应该较高，因为结构相似（即使import不同）
        assertTrue(coverageDetail.getCoverage() > 0.7, 
                  "新增块覆盖率应该较高: " + coverageDetail.getCoverage());
        assertTrue(coverageDetail.getTotalLines() > 0, "应该有有效的总行数");
        assertTrue(coverageDetail.getMatchedLines() > 0, "应该有有效的匹配行数");
    }

    @Test
    void testOrderAwareBlockMapperConfig() {
        // 验证OrderAwareBlockMapper的配置信息
        String configInfo = blockMapper.getConfigInfo();
        String cacheStats = blockMapper.getCacheStats();

        System.out.println("=== OrderAwareBlockMapper配置验证（新增块测试） ===");
        System.out.println("配置信息: " + configInfo);
        System.out.println("缓存统计: " + cacheStats);

        // 验证配置信息包含正确的参数
        assertTrue(configInfo.contains("块匹配不过滤噪音"), 
                  "配置信息应该表明块匹配时不过滤噪音");
        assertTrue(configInfo.contains("位置窗口=3"), 
                  "配置信息应该包含位置窗口大小");
    }

    @Test
    void testEmptyBlocksHandling() {
        // 测试真正的空块（from和to都为空）的处理
        DiffBlock oEmptyBlock = DiffBlock.builder()
                .contentFrom("")
                .contentTo("")
                .startLineTo(1)
                .endLineTo(1)
                .type(DiffType.ADD)
                .build();

        DiffBlock gEmptyBlock = DiffBlock.builder()
                .contentFrom("")
                .contentTo("")
                .startLineTo(1)
                .endLineTo(1)
                .type(DiffType.ADD)
                .build();

        DiffFile oFile = DiffFile.builder()
                .relativePath("Empty.java")
                .blocks(java.util.Collections.singletonList(oEmptyBlock))
                .build();

        DiffFile gFile = DiffFile.builder()
                .relativePath("Empty.java")
                .blocks(java.util.Collections.singletonList(gEmptyBlock))
                .build();

        BlockMapping mapping = coverageEvaluator.getBlockMapping(oFile, gFile);

        System.out.println("=== 真正空块处理测试 ===");
        System.out.println("映射覆盖率: " + mapping.getCoverage());
        System.out.println("匹配的O块数: " + mapping.getMatchedOracleBlocks().size());
        System.out.println("未匹配的O块数: " + mapping.getUnmatchedOracle().size());

        // 真正的空块应该被识别为未匹配
        assertEquals(0, mapping.getMatchedOracleBlocks().size(), "真正的空块不应该匹配");
        assertEquals(1, mapping.getUnmatchedOracle().size(), "真正的空块应该被标记为未匹配");
    }
}
