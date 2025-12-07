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
 * 测试import语句和注释对块映射逻辑的影响
 */
@SpringBootTest
@TestPropertySource(properties = {
    "coverage.filter.noise.enabled=false"  // 默认关闭噪音过滤
})
class ImportCommentIssueTest {

    @Autowired
    private CoverageEvaluator coverageEvaluator;

    @Test
    void testImportStatementImpactOnSimilarity() {
        // 创建两个只有import语句不同的块
        DiffBlock block1 = DiffBlock.builder()
                .contentFrom("import java.util.List;\nimport java.util.Map;\npublic class Test { }")
                .contentTo("import java.util.List;\nimport java.util.Map;\npublic class Test { }")
                .startLineTo(1)
                .endLineTo(3)
                .type(DiffType.ADD)
                .build();

        DiffBlock block2 = DiffBlock.builder()
                .contentFrom("import java.util.List;\nimport java.util.Set;\npublic class Test { }")
                .contentTo("import java.util.List;\nimport java.util.Set;\npublic class Test { }")
                .startLineTo(1)
                .endLineTo(3)
                .type(DiffType.ADD)
                .build();

        DiffFile file1 = DiffFile.builder()
                .relativePath("Test.java")
                .blocks(java.util.Collections.singletonList(block1))
                .build();

        DiffFile file2 = DiffFile.builder()
                .relativePath("Test.java")
                .blocks(java.util.Collections.singletonList(block2))
                .build();

        CoverageDetail detail = coverageEvaluator.evaluateFile(file1, file2);

        System.out.println("=== Import语句影响测试 ===");
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("块1内容: " + block1.getContentTo());
        System.out.println("块2内容: " + block2.getContentTo());
        
        // 由于import语句不同（Map vs Set），相似度应该较低
        // 但实际代码逻辑应该相似（只有import不同）
        assertTrue(detail.getCoverage() < 0.9, "只有import不同的块相似度应该较低，但实际为: " + detail.getCoverage());
    }

    @Test
    void testCommentImpactOnSimilarity() {
        // 创建两个只有注释不同的块
        DiffBlock block1 = DiffBlock.builder()
                .contentFrom("// 这是注释\npublic int calculate(int a, int b) {\n    return a + b;\n}")
                .contentTo("// 这是注释\npublic int calculate(int a, int b) {\n    return a + b;\n}")
                .startLineTo(1)
                .endLineTo(4)
                .type(DiffType.ADD)
                .build();

        DiffBlock block2 = DiffBlock.builder()
                .contentFrom("// 另一个注释\npublic int calculate(int a, int b) {\n    return a + b;\n}")
                .contentTo("// 另一个注释\npublic int calculate(int a, int b) {\n    return a + b;\n}")
                .startLineTo(1)
                .endLineTo(4)
                .type(DiffType.ADD)
                .build();

        DiffFile file1 = DiffFile.builder()
                .relativePath("Calculator.java")
                .blocks(java.util.Collections.singletonList(block1))
                .build();

        DiffFile file2 = DiffFile.builder()
                .relativePath("Calculator.java")
                .blocks(java.util.Collections.singletonList(block2))
                .build();

        CoverageDetail detail = coverageEvaluator.evaluateFile(file1, file2);

        System.out.println("=== 注释影响测试 ===");
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("块1内容: " + block1.getContentTo());
        System.out.println("块2内容: " + block2.getContentTo());
        
        // 只有注释不同，实际代码相同，但相似度会降低
        assertTrue(detail.getCoverage() < 1.0, "只有注释不同的块相似度应该降低，但实际为: " + detail.getCoverage());
    }

    @Test
    void testMixedImportAndCode() {
        // 测试更复杂的情况：import语句不同但核心代码相同
        DiffBlock block1 = DiffBlock.builder()
                .contentFrom("import java.util.ArrayList;\nimport java.util.List;\n\npublic class DataProcessor {\n    private List<String> data;\n}")
                .contentTo("import java.util.ArrayList;\nimport java.util.List;\n\npublic class DataProcessor {\n    private List<String> data;\n}")
                .startLineTo(1)
                .endLineTo(6)
                .type(DiffType.ADD)
                .build();

        DiffBlock block2 = DiffBlock.builder()
                .contentFrom("import java.util.LinkedList;\nimport java.util.List;\n\npublic class DataProcessor {\n    private List<String> data;\n}")
                .contentTo("import java.util.LinkedList;\nimport java.util.List;\n\npublic class DataProcessor {\n    private List<String> data;\n}")
                .startLineTo(1)
                .endLineTo(6)
                .type(DiffType.ADD)
                .build();

        DiffFile file1 = DiffFile.builder()
                .relativePath("DataProcessor.java")
                .blocks(java.util.Collections.singletonList(block1))
                .build();

        DiffFile file2 = DiffFile.builder()
                .relativePath("DataProcessor.java")
                .blocks(java.util.Collections.singletonList(block2))
                .build();

        CoverageDetail detail = coverageEvaluator.evaluateFile(file1, file2);

        System.out.println("=== 混合Import和代码测试 ===");
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("块1内容: " + block1.getContentTo());
        System.out.println("块2内容: " + block2.getContentTo());
        
        // 核心类定义和字段完全相同，只有import的实现不同
        // 如果过滤噪音，相似度应该很高
        assertTrue(detail.getCoverage() < 0.8, "由于import噪音，相似度应该明显低于1.0，实际为: " + detail.getCoverage());
    }

    @Test
    void testBlockMappingWithImportNoise() {
        // 测试块映射在import噪音下的表现
        DiffBlock oBlock = DiffBlock.builder()
                .contentFrom("import java.util.Map;\npublic class Service {\n    private Map<String, String> cache;\n}")
                .contentTo("import java.util.Map;\npublic class Service {\n    private Map<String, String> cache;\n}")
                .startLineTo(1)
                .endLineTo(4)
                .type(DiffType.ADD)
                .build();

        DiffBlock gBlock = DiffBlock.builder()
                .contentFrom("import java.util.HashMap;\npublic class Service {\n    private Map<String, String> cache;\n}")
                .contentTo("import java.util.HashMap;\npublic class Service {\n    private Map<String, String> cache;\n}")
                .startLineTo(1)
                .endLineTo(4)
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

        System.out.println("=== 块映射噪音测试 ===");
        System.out.println("映射覆盖率: " + mapping.getCoverage());
        System.out.println("匹配的O块数: " + mapping.getMatchedOracleBlocks().size());
        System.out.println("匹配的G块数: " + mapping.getMatchedGaussBlocks().size());
        
        if (!mapping.getMatchedOracleBlocks().isEmpty()) {
            double similarity = mapping.getSimilarity(mapping.getMatchedOracleBlocks().get(0));
            System.out.println("块相似度: " + similarity);
            
            // 由于import语句不同，相似度应该受到影响
            assertTrue(similarity < 1.0, "import噪音应该降低相似度，但实际为: " + similarity);
        }
    }
}
