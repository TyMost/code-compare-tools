package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.shared.utils.CoverageUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证双Tokenization修复的测试
 * 确保calculateSimilarityWithoutNoiseTokens真正使用无噪音数据
 */
@SpringBootTest
@TestPropertySource(properties = {
    "coverage.filter.noise.enabled=true",
    "coverage.skip.unmatched.noise.blocks=true"
})
class DualTokenizationFixTest {

    @Autowired
    private CoverageEvaluator coverageEvaluator;

    @Test
    void testNoiseFilteringEffect() {
        // 验证CoverageUtils.filterCodeNoise的正确性
        String contentWithImport = "import java.util.List;\npublic class Test {\n    private List<String> data;\n}";
        String contentWithoutImport = "public class Test {\n    private List<String> data;\n}";
        
        String filteredContent = CoverageUtils.filterCodeNoise(contentWithImport);
        
        System.out.println("=== 噪音过滤效果测试 ===");
        System.out.println("原始内容: " + contentWithImport);
        System.out.println("过滤后内容: " + filteredContent);
        System.out.println("纯代码内容: " + contentWithoutImport);
        
        // 过滤后的内容应该不包含import
        assertNotNull(filteredContent, "过滤后内容不应该为null");
        assertFalse(filteredContent.contains("import"), "过滤后内容不应该包含import");
        assertTrue(filteredContent.contains("class"), "过滤后内容应该包含class");
    }

    @Test
    void testCoverageCalculationWithNoiseFix() {
        // 创建包含import噪音的文件
        DiffBlock mixedBlock = DiffBlock.builder()
                .contentFrom("import java.util.List;\npublic class Test {\n    private List<String> data;\n}")
                .contentTo("import java.util.List;\npublic class Test {\n    private List<String> data;\n}")
                .build();

        DiffBlock pureCodeBlock = DiffBlock.builder()
                .contentFrom("public class Test {\n    private List<String> data;\n}")
                .contentTo("public class Test {\n    private List<String> data;\n}")
                .build();

        DiffFile oFile = DiffFile.builder()
                .relativePath("Test.java")
                .blocks(java.util.Arrays.asList(mixedBlock))
                .build();

        DiffFile gFile = DiffFile.builder()
                .relativePath("Test.java")
                .blocks(java.util.Arrays.asList(pureCodeBlock))
                .build();

        // 使用带映射的覆盖率计算
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);

        System.out.println("=== 覆盖率计算验证测试 ===");
        System.out.println("O端内容: " + mixedBlock.getContentFrom());
        System.out.println("G端内容: " + pureCodeBlock.getContentFrom());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        System.out.println("总行数: " + detail.getTotalLines());

        // 由于使用了无噪音相似度计算，import噪音应该被过滤掉
        // 相似度应该很高（代码部分完全相同）
        assertTrue(detail.getCoverage() > 0.8, 
                  "去掉import噪音后，覆盖率应该很高，实际: " + detail.getCoverage());
        
        // 验证匹配行数和总行数的关系
        assertTrue(detail.getMatchedLines() > 0, "匹配行数应该大于0");
        assertTrue(detail.getTotalLines() > 0, "总行数应该大于0");
        assertTrue(detail.getMatchedLines() <= detail.getTotalLines(), 
                  "匹配行数不应该超过总行数");
    }

    @Test
    void testMixedVsPureNoiseBlocks() {
        // 创建混合块和纯噪音块的对比测试
        DiffBlock mixedBlock = DiffBlock.builder()
                .contentFrom("import java.util.List;\npublic class Test {\n    private List<String> data;\n}")
                .contentTo("import java.util.List;\npublic class Test {\n    private List<String> data;\n}")
                .build();

        DiffBlock importOnlyBlock = DiffBlock.builder()
                .contentFrom("import java.util.List;\nimport java.util.Map;")
                .contentTo("import java.util.List;\nimport java.util.Map;")
                .build();

        DiffFile oFile = DiffFile.builder()
                .relativePath("Test.java")
                .blocks(java.util.Arrays.asList(mixedBlock, importOnlyBlock))
                .build();

        DiffFile gFile = DiffFile.builder()
                .relativePath("Test.java")
                .blocks(java.util.Arrays.asList(mixedBlock))
                .build();

        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);

        System.out.println("=== 混合块vs纯噪音块测试 ===");
        System.out.println("混合块: " + mixedBlock.getContentFrom());
        System.out.println("纯import块: " + importOnlyBlock.getContentFrom());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("高相似度块数: " + detail.getMatchedBlocks().size());
        System.out.println("低相似度块数: " + detail.getUnmatchedBlocks().size());

        // 混合块应该匹配，纯import块应该被跳过
        assertTrue(detail.getCoverage() > 0.8, 
                  "跳过纯import块后，覆盖率应该很高，实际: " + detail.getCoverage());
        
        // 验证总行数是否合理（应该只包含混合块，跳过纯import块）
        // 混合块去噪后约3行，如果跳过了纯import块，总行数应该接近这个数字
        assertTrue(detail.getTotalLines() <= 6, 
                  "总行数应该合理（跳过纯import块），实际: " + detail.getTotalLines());
    }

    @Test
    void testPureNoiseVsPureNoiseSimilarity() {
        // 测试纯噪音块vs纯噪音块的相似度应该为1.0
        DiffBlock pureNoise1 = DiffBlock.builder()
                .contentFrom("import java.util.List;\nimport java.util.Map;")
                .contentTo("import java.util.List;\nimport java.util.Map;")
                .build();

        DiffBlock pureNoise2 = DiffBlock.builder()
                .contentFrom("import java.util.ArrayList;\nimport java.util.HashMap;")
                .contentTo("import java.util.ArrayList;\nimport java.util.HashMap;")
                .build();

        DiffFile oFile = DiffFile.builder()
                .relativePath("PureNoise.java")
                .blocks(java.util.Arrays.asList(pureNoise1))
                .build();

        DiffFile gFile = DiffFile.builder()
                .relativePath("PureNoise.java")
                .blocks(java.util.Arrays.asList(pureNoise2))
                .build();

        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);

        System.out.println("=== 纯噪音块vs纯噪音块相似度测试 ===");
        System.out.println("O端纯噪音: " + pureNoise1.getContentFrom());
        System.out.println("G端纯噪音: " + pureNoise2.getContentFrom());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        System.out.println("总行数: " + detail.getTotalLines());

        // 纯噪音块vs纯噪音块：相似度应该是1.0，但去噪行数为0，所以不影响覆盖率
        assertEquals(0.0, detail.getTotalLines(), 0.001, "纯噪音块去噪后行数应该为0");
        assertEquals(0.0, detail.getMatchedLines(), 0.001, "纯噪音块匹配行数应该为0");
        assertTrue(detail.getCoverage() > 0.9, "纯噪音块vs纯噪音块应该完全匹配");
    }

    @Test
    void testMixedBlockWeightConsistency() {
        // 测试混合块的权重一致性：相似度和权重都基于无噪音内容
        DiffBlock mixedBlock1 = DiffBlock.builder()
                .contentFrom("import java.util.List;\npublic class Test {\n    private List<String> data;\n}")
                .contentTo("import java.util.List;\npublic class Test {\n    private List<String> data;\n}")
                .build();

        DiffBlock mixedBlock2 = DiffBlock.builder()
                .contentFrom("import java.util.Map;\npublic class Test {\n    private Map<String, String> data;\n}")
                .contentTo("import java.util.Map;\npublic class Test {\n    private Map<String, String> data;\n}")
                .build();

        DiffFile oFile = DiffFile.builder()
                .relativePath("MixedTest.java")
                .blocks(java.util.Arrays.asList(mixedBlock1))
                .build();

        DiffFile gFile = DiffFile.builder()
                .relativePath("MixedTest.java")
                .blocks(java.util.Arrays.asList(mixedBlock2))
                .build();

        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);

        System.out.println("=== 混合块权重一致性测试 ===");
        System.out.println("O端混合块: " + mixedBlock1.getContentFrom());
        System.out.println("G端混合块: " + mixedBlock2.getContentFrom());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        System.out.println("总行数: " + detail.getTotalLines());

        // 混合块去噪后只有3行代码
        assertEquals(3, detail.getTotalLines(), "混合块去噪后应该只有3行");
        assertTrue(detail.getCoverage() > 0.5 && detail.getCoverage() < 1.0, 
                 "覆盖率应该在0.5-1.0之间，实际: " + detail.getCoverage());
        
        // 验证匹配行数 = 覆盖率 × 总行数
        double expectedMatchedLines = detail.getCoverage() * detail.getTotalLines();
        assertEquals(expectedMatchedLines, detail.getMatchedLines(), 0.1, 
                    "匹配行数应该等于覆盖率乘以总行数");
    }
}
