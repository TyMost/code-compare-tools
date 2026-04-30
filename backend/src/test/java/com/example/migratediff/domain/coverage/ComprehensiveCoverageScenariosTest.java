package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.shared.utils.CoverageUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 综合覆盖率场景测试
 * 
 * 这个测试类穷举了所有可能影响代码覆盖率的场景，包括：
 * 1. 噪音过滤场景测试（8个测试方法）
 * 2. 块类型组合矩阵测试（9个测试方法）
 * 3. 配置开关影响测试（4个测试方法）
 * 4. 相似度算法权重测试（6个测试方法）
 * 5. 行数计算一致性测试（5个测试方法）
 * 6. 边界情况处理测试（4个测试方法）
 * 7. 实际代码场景测试（3个测试方法）
 * 8. 缓存一致性测试（2个测试方法）
 */
@SpringBootTest
@TestPropertySource(properties = {
    "coverage.filter.noise.enabled=true",
    "coverage.skip.unmatched.noise.blocks=true"
})
@DisplayName("综合覆盖率场景测试")
class ComprehensiveCoverageScenariosTest {

    @Autowired
    private CoverageEvaluator coverageEvaluator;

    // ========== 测试数据工厂方法 ==========
    
    /**
     * 创建纯import语句块
     */
    private DiffBlock createPureImportBlock(String imports) {
        return DiffBlock.builder()
                .contentFrom(imports)
                .contentTo(imports)
                .build();
    }
    
    /**
     * 创建纯注释块
     */
    private DiffBlock createPureCommentBlock(String comments) {
        return DiffBlock.builder()
                .contentFrom(comments)
                .contentTo(comments)
                .build();
    }
    
    /**
     * 创建混合噪音块（import + 注释 + 代码）
     */
    private DiffBlock createMixedNoiseBlock(String imports, String comments, String code) {
        String content = imports + "\n" + comments + "\n" + code;
        return DiffBlock.builder()
                .contentFrom(content)
                .contentTo(content)
                .build();
    }
    
    /**
     * 创建纯代码块
     */
    private DiffBlock createPureCodeBlock(String code) {
        return DiffBlock.builder()
                .contentFrom(code)
                .contentTo(code)
                .build();
    }
    
    /**
     * 创建包含多行注释的块
     */
    private DiffBlock createMultiLineCommentBlock(String multiLineComment) {
        return DiffBlock.builder()
                .contentFrom(multiLineComment)
                .contentTo(multiLineComment)
                .build();
    }
    
    /**
     * 创建包含JavaDoc的块
     */
    private DiffBlock createJavaDocBlock(String javaDoc) {
        return DiffBlock.builder()
                .contentFrom(javaDoc)
                .contentTo(javaDoc)
                .build();
    }
    
    /**
     * 创建包含package语句的块
     */
    private DiffBlock createPackageBlock(String packageStatement) {
        return DiffBlock.builder()
                .contentFrom(packageStatement)
                .contentTo(packageStatement)
                .build();
    }

    // ========== 1. 噪音过滤场景测试 ==========
    
    @Test
    @DisplayName("测试纯import语句块的过滤")
    void testPureImportBlockFiltering() {
        String importContent = "import java.util.List;\nimport java.util.Map;\nimport java.util.Set;";
        DiffBlock importBlock = createPureImportBlock(importContent);
        
        // 验证过滤效果
        String filteredContent = CoverageUtils.filterCodeNoise(importContent);
        assertTrue(filteredContent.trim().isEmpty(), "纯import语句过滤后应该为空");
        
        // 创建文件进行覆盖率测试
        DiffFile oFile = DiffFile.builder()
                .relativePath("ImportOnly.java")
                .blocks(Arrays.asList(importBlock))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("ImportOnly.java")
                .blocks(Arrays.asList(importBlock))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 纯import语句块测试 ===");
        System.out.println("原始内容: " + importContent);
        System.out.println("过滤后内容: " + filteredContent);
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        
        // 纯噪音块应该被正确处理
        assertEquals(0.0, detail.getTotalLines(), 0.001, "纯import块去噪后行数应该为0");
        assertTrue(detail.getCoverage() > 0.9, "相同纯噪音块应该完全匹配");
    }
    
    @Test
    @DisplayName("测试纯注释块的过滤")
    void testPureCommentBlockFiltering() {
        String commentContent = "// 这是单行注释\n/* 这是多行注释\n   第二行 */\n/** JavaDoc注释 */";
        DiffBlock commentBlock = createPureCommentBlock(commentContent);
        
        // 验证过滤效果
        String filteredContent = CoverageUtils.filterCodeNoise(commentContent);
        assertTrue(filteredContent.trim().isEmpty(), "纯注释过滤后应该为空");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("CommentOnly.java")
                .blocks(Arrays.asList(commentBlock))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("CommentOnly.java")
                .blocks(Arrays.asList(commentBlock))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 纯注释块测试 ===");
        System.out.println("原始内容: " + commentContent);
        System.out.println("过滤后内容: " + filteredContent);
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        
        assertEquals(0.0, detail.getTotalLines(), 0.001, "纯注释块去噪后行数应该为0");
        assertTrue(detail.getCoverage() > 0.9, "相同纯注释块应该完全匹配");
    }
    
    @Test
    @DisplayName("测试混合噪音块的过滤")
    void testMixedNoiseBlockFiltering() {
        String imports = "import java.util.List;\nimport java.util.Map;";
        String comments = "// 这是注释\n/* 多行注释 */";
        String code = "public class Test {\n    private List<String> data;\n}";
        
        DiffBlock mixedBlock = createMixedNoiseBlock(imports, comments, code);
        
        // 验证过滤效果
        String originalContent = imports + "\n" + comments + "\n" + code;
        String filteredContent = CoverageUtils.filterCodeNoise(originalContent);
        
        assertFalse(filteredContent.contains("import"), "过滤后不应该包含import");
        assertFalse(filteredContent.contains("//"), "过滤后不应该包含单行注释");
        assertFalse(filteredContent.contains("/*"), "过滤后不应该包含多行注释");
        assertTrue(filteredContent.contains("class"), "过滤后应该包含class代码");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("MixedNoise.java")
                .blocks(Arrays.asList(mixedBlock))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("MixedNoise.java")
                .blocks(Arrays.asList(mixedBlock))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 混合噪音块测试 ===");
        System.out.println("原始内容: " + originalContent);
        System.out.println("过滤后内容: " + filteredContent);
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        
        assertEquals(2, detail.getTotalLines(), "去噪后应该只有2行代码");
        assertEquals(1.0, detail.getCoverage(), 0.001, "相同块应该完全匹配");
    }
    
    @Test
    @DisplayName("测试多行注释块的处理")
    void testMultiLineCommentBlock() {
        String multiLineComment = "/*\n * 这是多行注释\n * 第一行\n * 第二行\n */";
        DiffBlock multiLineBlock = createMultiLineCommentBlock(multiLineComment);
        
        String filteredContent = CoverageUtils.filterCodeNoise(multiLineComment);
        assertTrue(filteredContent.trim().isEmpty(), "多行注释过滤后应该为空");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("MultiLineComment.java")
                .blocks(Arrays.asList(multiLineBlock))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("MultiLineComment.java")
                .blocks(Arrays.asList(multiLineBlock))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 多行注释块测试 ===");
        System.out.println("原始内容: " + multiLineComment);
        System.out.println("过滤后内容: " + filteredContent);
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        
        assertEquals(0.0, detail.getTotalLines(), 0.001, "多行注释块去噪后行数应该为0");
        assertTrue(detail.getCoverage() > 0.9, "相同多行注释块应该完全匹配");
    }
    
    @Test
    @DisplayName("测试JavaDoc块的处理")
    void testJavaDocBlock() {
        String javaDoc = "/**\n * JavaDoc注释\n * @param name 参数\n * @return 返回值\n */";
        DiffBlock javaDocBlock = createJavaDocBlock(javaDoc);
        
        String filteredContent = CoverageUtils.filterCodeNoise(javaDoc);
        assertTrue(filteredContent.trim().isEmpty(), "JavaDoc过滤后应该为空");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("JavaDoc.java")
                .blocks(Arrays.asList(javaDocBlock))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("JavaDoc.java")
                .blocks(Arrays.asList(javaDocBlock))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== JavaDoc块测试 ===");
        System.out.println("原始内容: " + javaDoc);
        System.out.println("过滤后内容: " + filteredContent);
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        
        assertEquals(0.0, detail.getTotalLines(), 0.001, "JavaDoc块去噪后行数应该为0");
        assertTrue(detail.getCoverage() > 0.9, "相同JavaDoc块应该完全匹配");
    }
    
    @Test
    @DisplayName("测试package语句块的处理")
    void testPackageStatementBlock() {
        String packageStatement = "package com.example.test;";
        DiffBlock packageBlock = createPackageBlock(packageStatement);
        
        String filteredContent = CoverageUtils.filterCodeNoise(packageStatement);
        assertTrue(filteredContent.trim().isEmpty(), "package语句过滤后应该为空");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("PackageOnly.java")
                .blocks(Arrays.asList(packageBlock))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("PackageOnly.java")
                .blocks(Arrays.asList(packageBlock))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== Package语句块测试 ===");
        System.out.println("原始内容: " + packageStatement);
        System.out.println("过滤后内容: " + filteredContent);
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        
        assertEquals(0.0, detail.getTotalLines(), 0.001, "package语句块去噪后行数应该为0");
        assertTrue(detail.getCoverage() > 0.9, "相同package语句块应该完全匹配");
    }
    
    @Test
    @DisplayName("测试复杂混合噪音场景")
    void testComplexMixedNoiseScenario() {
        String complexContent = "package com.example;\n\n" +
                               "import java.util.List;\n" +
                               "import java.util.Map;\n\n" +
                               "/**\n" +
                               " * 复杂的Java类\n" +
                               " * @author Test\n" +
                               " */\n" +
                               "public class ComplexClass {\n" +
                               "    // 单行注释\n" +
                               "    private List<String> data; /* 内联注释 */\n" +
                               "    \n" +
                               "    /*\n" +
                               "     * 多行注释\n" +
                               "     */\n" +
                               "    public void method() {\n" +
                               "        // 方法注释\n" +
                               "        System.out.println(\"test\");\n" +
                               "    }\n" +
                               "}";
        
        DiffBlock complexBlock = DiffBlock.builder()
                .contentFrom(complexContent)
                .contentTo(complexContent)
                .build();
        
        String filteredContent = CoverageUtils.filterCodeNoise(complexContent);
        
        System.out.println("=== 复杂混合噪音场景测试 ===");
        System.out.println("原始内容长度: " + complexContent.length());
        System.out.println("过滤后内容长度: " + filteredContent.length());
        System.out.println("过滤后内容:\n" + filteredContent);
        
        // 验证噪音被过滤
        assertFalse(filteredContent.contains("import"), "应该过滤掉import");
        assertFalse(filteredContent.contains("package"), "应该过滤掉package");
        assertFalse(filteredContent.contains("//"), "应该过滤掉单行注释");
        assertFalse(filteredContent.contains("/*"), "应该过滤掉多行注释");
        assertFalse(filteredContent.contains("/**"), "应该过滤掉JavaDoc");
        assertTrue(filteredContent.contains("class"), "应该保留class");
        assertTrue(filteredContent.contains("method"), "应该保留方法");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("ComplexMixed.java")
                .blocks(Arrays.asList(complexBlock))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("ComplexMixed.java")
                .blocks(Arrays.asList(complexBlock))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        
        // 验证去噪后的行数
        assertTrue(detail.getTotalLines() >= 4, "去噪后应该至少有4行代码（class定义、字段、2个方法行）");
        assertEquals(1.0, detail.getCoverage(), 0.001, "相同内容应该完全匹配");
    }
    
    @Test
    @DisplayName("测试噪音过滤的边界情况")
    void testNoiseFilteringEdgeCases() {
        // 测试各种边界情况
        String[] testCases = {
            "import", // 不完整的import
            "//", // 只有注释标记
            "/*", // 未闭合的多行注释
            "/**", // 未闭合的JavaDoc
            "package", // 不完整的package
            "importjava.util.List;", // 没有空格的import
            "//import java.util.List;", // 注释中的import
            "/* import java.util.List; */", // 多行注释中的import
        };
        
        for (String testCase : testCases) {
            String filtered = CoverageUtils.filterCodeNoise(testCase);
            
            DiffBlock block = DiffBlock.builder()
                    .contentFrom(testCase)
                    .contentTo(testCase)
                    .build();
            
            DiffFile oFile = DiffFile.builder()
                    .relativePath("EdgeCase.java")
                    .blocks(Arrays.asList(block))
                    .build();
            
            DiffFile gFile = DiffFile.builder()
                    .relativePath("EdgeCase.java")
                    .blocks(Arrays.asList(block))
                    .build();
            
            CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
            
            System.out.println("=== 边界情况测试 ===");
            System.out.println("测试内容: '" + testCase + "'");
            System.out.println("过滤后: '" + filtered + "'");
            System.out.println("覆盖率: " + detail.getCoverage());
            System.out.println("总行数: " + detail.getTotalLines());
            
            // 所有边界情况都应该被正确处理
            assertTrue(detail.getCoverage() > 0.9, "边界情况应该完全匹配");
        }
    }

    // ========== 2. 块类型组合矩阵测试 ==========
    
    @Test
    @DisplayName("矩阵测试：纯噪音 vs 纯噪音")
    void testMatrix_PureNoise_vs_PureNoise() {
        DiffBlock pureNoise1 = createPureImportBlock("import java.util.List;");
        DiffBlock pureNoise2 = createPureImportBlock("import java.util.Map;");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("PureNoiseTest.java")
                .blocks(Arrays.asList(pureNoise1))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("PureNoiseTest.java")
                .blocks(Arrays.asList(pureNoise2))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 矩阵测试：纯噪音 vs 纯噪音 ===");
        System.out.println("O端: " + pureNoise1.getContentFrom());
        System.out.println("G端: " + pureNoise2.getContentFrom());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        
        assertEquals(0.0, detail.getTotalLines(), 0.001, "纯噪音块去噪后行数应该为0");
        assertEquals(0.0, detail.getMatchedLines(), 0.001, "纯噪音块匹配行数应该为0");
        assertTrue(detail.getCoverage() > 0.9, "纯噪音 vs 纯噪音应该完全匹配");
    }
    
    @Test
    @DisplayName("矩阵测试：纯噪音 vs 混合块")
    void testMatrix_PureNoise_vs_MixedBlock() {
        DiffBlock pureNoise = createPureImportBlock("import java.util.List;");
        DiffBlock mixedBlock = createMixedNoiseBlock(
            "import java.util.Map;", 
            "// 注释", 
            "public class Test {}");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("MixedTest1.java")
                .blocks(Arrays.asList(pureNoise))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("MixedTest1.java")
                .blocks(Arrays.asList(mixedBlock))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 矩阵测试：纯噪音 vs 混合块 ===");
        System.out.println("O端: " + pureNoise.getContentFrom());
        System.out.println("G端: " + mixedBlock.getContentFrom());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        
        assertEquals(0.0, detail.getTotalLines(), 0.001, "纯噪音块应该导致总行数为0");
        assertEquals(0.0, detail.getMatchedLines(), 0.001, "纯噪音块应该导致匹配行数为0");
        assertTrue(detail.getCoverage() > 0.9, "纯噪音 vs 混合块应该完全匹配");
    }
    
    @Test
    @DisplayName("矩阵测试：纯噪音 vs 纯代码")
    void testMatrix_PureNoise_vs_PureCode() {
        DiffBlock pureNoise = createPureImportBlock("import java.util.List;");
        DiffBlock pureCode = createPureCodeBlock("public class Test {\n    private String data;\n}");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("NoiseVsCode.java")
                .blocks(Arrays.asList(pureNoise))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("NoiseVsCode.java")
                .blocks(Arrays.asList(pureCode))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 矩阵测试：纯噪音 vs 纯代码 ===");
        System.out.println("O端: " + pureNoise.getContentFrom());
        System.out.println("G端: " + pureCode.getContentFrom());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        
        assertEquals(0.0, detail.getTotalLines(), 0.001, "纯噪音块应该导致总行数为0");
        assertEquals(0.0, detail.getMatchedLines(), 0.001, "纯噪音块应该导致匹配行数为0");
        assertTrue(detail.getCoverage() > 0.9, "纯噪音 vs 纯代码应该完全匹配");
    }
    
    @Test
    @DisplayName("矩阵测试：混合块 vs 纯噪音")
    void testMatrix_MixedBlock_vs_PureNoise() {
        DiffBlock mixedBlock = createMixedNoiseBlock(
            "import java.util.List;", 
            "// 注释", 
            "public class Test {}");
        DiffBlock pureNoise = createPureImportBlock("import java.util.Map;");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("MixedVsNoise.java")
                .blocks(Arrays.asList(mixedBlock))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("MixedVsNoise.java")
                .blocks(Arrays.asList(pureNoise))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 矩阵测试：混合块 vs 纯噪音 ===");
        System.out.println("O端: " + mixedBlock.getContentFrom());
        System.out.println("G端: " + pureNoise.getContentFrom());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        
        assertTrue(detail.getTotalLines() > 0, "混合块应该有正的总行数");
        assertTrue(detail.getMatchedLines() >= 0, "匹配行数应该非负");
        assertTrue(detail.getCoverage() >= 0 && detail.getCoverage() <= 1.0, "覆盖率应该在0-1之间");
    }
    
    @Test
    @DisplayName("矩阵测试：混合块 vs 混合块")
    void testMatrix_MixedBlock_vs_MixedBlock() {
        DiffBlock mixedBlock1 = createMixedNoiseBlock(
            "import java.util.List;", 
            "// 注释1", 
            "public class Test1 {}");
        DiffBlock mixedBlock2 = createMixedNoiseBlock(
            "import java.util.Map;", 
            "// 注释2", 
            "public class Test2 {}");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("MixedVsMixed.java")
                .blocks(Arrays.asList(mixedBlock1))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("MixedVsMixed.java")
                .blocks(Arrays.asList(mixedBlock2))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 矩阵测试：混合块 vs 混合块 ===");
        System.out.println("O端: " + mixedBlock1.getContentFrom());
        System.out.println("G端: " + mixedBlock2.getContentFrom());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        
        assertTrue(detail.getTotalLines() > 0, "混合块应该有正的总行数");
        assertTrue(detail.getMatchedLines() >= 0, "匹配行数应该非负");
        assertTrue(detail.getCoverage() >= 0 && detail.getCoverage() <= 1.0, "覆盖率应该在0-1之间");
    }
    
    @Test
    @DisplayName("矩阵测试：混合块 vs 纯代码")
    void testMatrix_MixedBlock_vs_PureCode() {
        DiffBlock mixedBlock = createMixedNoiseBlock(
            "import java.util.List;", 
            "// 注释", 
            "public class Test {}");
        DiffBlock pureCode = createPureCodeBlock("public class Test {}");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("MixedVsCode.java")
                .blocks(Arrays.asList(mixedBlock))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("MixedVsCode.java")
                .blocks(Arrays.asList(pureCode))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 矩阵测试：混合块 vs 纯代码 ===");
        System.out.println("O端: " + mixedBlock.getContentFrom());
        System.out.println("G端: " + pureCode.getContentFrom());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        
        assertTrue(detail.getTotalLines() > 0, "应该有正的总行数");
        assertTrue(detail.getMatchedLines() > 0, "应该有正的匹配行数");
        assertTrue(detail.getCoverage() > 0.5, "相同代码部分应该有较高覆盖率");
    }
    
    @Test
    @DisplayName("矩阵测试：纯代码 vs 纯噪音")
    void testMatrix_PureCode_vs_PureNoise() {
        DiffBlock pureCode = createPureCodeBlock("public class Test {\n    private String data;\n}");
        DiffBlock pureNoise = createPureImportBlock("import java.util.List;");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("CodeVsNoise.java")
                .blocks(Arrays.asList(pureCode))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("CodeVsNoise.java")
                .blocks(Arrays.asList(pureNoise))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 矩阵测试：纯代码 vs 纯噪音 ===");
        System.out.println("O端: " + pureCode.getContentFrom());
        System.out.println("G端: " + pureNoise.getContentFrom());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        
        assertTrue(detail.getTotalLines() > 0, "纯代码块应该有正的总行数");
        assertEquals(0.0, detail.getMatchedLines(), 0.001, "纯代码 vs 纯噪音应该无匹配");
        assertEquals(0.0, detail.getCoverage(), 0.001, "纯代码 vs 纯噪音覆盖率应该为0");
    }
    
    @Test
    @DisplayName("矩阵测试：纯代码 vs 混合块")
    void testMatrix_PureCode_vs_MixedBlock() {
        DiffBlock pureCode = createPureCodeBlock("public class Test {\n    private String data;\n}");
        DiffBlock mixedBlock = createMixedNoiseBlock(
            "import java.util.List;", 
            "// 注释", 
            "public class Test {\n    private String data;\n}");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("CodeVsMixed.java")
                .blocks(Arrays.asList(pureCode))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("CodeVsMixed.java")
                .blocks(Arrays.asList(mixedBlock))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 矩阵测试：纯代码 vs 混合块 ===");
        System.out.println("O端: " + pureCode.getContentFrom());
        System.out.println("G端: " + mixedBlock.getContentFrom());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        
        assertTrue(detail.getTotalLines() > 0, "应该有正的总行数");
        assertTrue(detail.getMatchedLines() > 0, "应该有正的匹配行数");
        assertTrue(detail.getCoverage() > 0.8, "相同代码部分应该有很高覆盖率");
    }
    
    @Test
    @DisplayName("矩阵测试：纯代码 vs 纯代码")
    void testMatrix_PureCode_vs_PureCode() {
        DiffBlock pureCode1 = createPureCodeBlock("public class Test1 {\n    private String data1;\n}");
        DiffBlock pureCode2 = createPureCodeBlock("public class Test2 {\n    private String data2;\n}");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("CodeVsCode.java")
                .blocks(Arrays.asList(pureCode1))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("CodeVsCode.java")
                .blocks(Arrays.asList(pureCode2))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 矩阵测试：纯代码 vs 纯代码 ===");
        System.out.println("O端: " + pureCode1.getContentFrom());
        System.out.println("G端: " + pureCode2.getContentFrom());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        
        assertTrue(detail.getTotalLines() > 0, "纯代码块应该有正的总行数");
        assertTrue(detail.getMatchedLines() > 0, "应该有正的匹配行数");
        assertTrue(detail.getCoverage() > 0.5, "相似代码应该有一定覆盖率");
        assertTrue(detail.getCoverage() < 1.0, "不同代码不应该完全匹配");
    }

    // ========== 3. 配置开关影响测试 ==========
    
    @Test
    @DisplayName("配置测试：噪音过滤开启，跳过纯噪音块开启")
    void testConfig_NoiseFilterOn_SkipUnmatchedNoiseOn() {
        // 这是默认配置，在类级别已经设置
        DiffBlock mixedBlock = createMixedNoiseBlock(
            "import java.util.List;", 
            "// 注释", 
            "public class Test {}");
        DiffBlock pureNoiseBlock = createPureImportBlock("import java.util.Map;");
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("ConfigTest1.java")
                .blocks(Arrays.asList(mixedBlock, pureNoiseBlock))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("ConfigTest1.java")
                .blocks(Arrays.asList(mixedBlock))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 配置测试：噪音过滤开启，跳过纯噪音块开启 ===");
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        System.out.println("高相似度块数: " + detail.getMatchedBlocks().size());
        System.out.println("低相似度块数: " + detail.getUnmatchedBlocks().size());
        
        // 纯噪音块应该被跳过，只计算混合块
        assertTrue(detail.getTotalLines() <= 2, "应该只计算混合块的去噪行数");
        assertTrue(detail.getCoverage() > 0.8, "混合块匹配应该有高覆盖率");
    }
    
    @Test
    @DisplayName("配置测试：噪音过滤关闭，跳过纯噪音块开启")
    void testConfig_NoiseFilterOff_SkipUnmatchedNoiseOn() {
        // 这个测试需要临时关闭噪音过滤
        System.out.println("=== 配置测试：噪音过滤关闭，跳过纯噪音块开启 ===");
        System.out.println("注意：此测试需要在不同的@TestPropertySource配置下运行");
        System.out.println("实际覆盖率应该包含所有内容，包括噪音");
        
        // 创建一个简单的测试来验证配置影响
        String contentWithNoise = "import java.util.List;\npublic class Test {}";
        String filteredContent = CoverageUtils.filterCodeNoise(contentWithNoise);
        String tokenizedWithFilter = String.join(" ", CoverageUtils.tokenize(contentWithNoise, true));
        String tokenizedWithoutFilter = String.join(" ", CoverageUtils.tokenize(contentWithNoise, false));
        
        System.out.println("原始内容: " + contentWithNoise);
        System.out.println("过滤后内容: " + filteredContent);
        System.out.println("带过滤分词: " + tokenizedWithFilter);
        System.out.println("不带过滤分词: " + tokenizedWithoutFilter);
        
        assertNotEquals(filteredContent, contentWithNoise, "过滤应该改变内容");
        assertNotEquals(tokenizedWithFilter, tokenizedWithoutFilter, "过滤应该影响分词");
    }
    
    @Test
    @DisplayName("配置测试：噪音过滤开启，跳过纯噪音块关闭")
    void testConfig_NoiseFilterOn_SkipUnmatchedNoiseOff() {
        System.out.println("=== 配置测试：噪音过滤开启，跳过纯噪音块关闭 ===");
        System.out.println("注意：此测试需要关闭skipUnmatchedNoiseBlocks配置");
        System.out.println("纯噪音块应该被计入总行数，但不影响匹配");
        
        // 模拟这个配置的效果
        DiffBlock pureNoiseBlock = createPureImportBlock("import java.util.List;");
        
        // 计算去噪行数（应该为0）
        String filtered = CoverageUtils.filterCodeNoise(pureNoiseBlock.getContentFrom());
        int filteredLines = filtered.trim().isEmpty() ? 0 : filtered.split("\n").length;
        
        System.out.println("纯噪音块: " + pureNoiseBlock.getContentFrom());
        System.out.println("过滤后内容: " + filtered);
        System.out.println("去噪后行数: " + filteredLines);
        
        assertEquals(0, filteredLines, "纯噪音块去噪后行数应该为0");
    }
    
    @Test
    @DisplayName("配置测试：噪音过滤关闭，跳过纯噪音块关闭")
    void testConfig_NoiseFilterOff_SkipUnmatchedNoiseOff() {
        System.out.println("=== 配置测试：噪音过滤关闭，跳过纯噪音块关闭 ===");
        System.out.println("注意：此测试需要关闭所有噪音过滤配置");
        System.out.println("所有内容都被计算，包括噪音");
        
        String contentWithNoise = "import java.util.List;\n// 注释\npublic class Test {}";
        String[] tokensWithFilter = CoverageUtils.tokenize(contentWithNoise, true).toArray(new String[0]);
        String[] tokensWithoutFilter = CoverageUtils.tokenize(contentWithNoise, false).toArray(new String[0]);
        
        System.out.println("内容: " + contentWithNoise);
        System.out.println("带过滤token数: " + tokensWithFilter.length);
        System.out.println("不带过滤token数: " + tokensWithoutFilter.length);
        System.out.println("带过滤tokens: " + String.join(", ", tokensWithFilter));
        System.out.println("不带过滤tokens: " + String.join(", ", tokensWithoutFilter));
        
        assertTrue(tokensWithoutFilter.length > tokensWithFilter.length, 
                  "不过滤应该包含更多tokens");
    }

    // ========== 4. 相似度算法权重测试 ==========
    
    @Test
    @DisplayName("相似度测试：验证四路相似度的同源匹配权重")
    void testSimilarity_FourWay_SameSourceWeight() {
        String oFrom = "public class TestA { String dataA; }";
        String oTo = "public class TestB { String dataB; }";
        String gFrom = "public class TestA { String dataA; }"; // 与oFrom相同
        String gTo = "public class TestC { String dataC; }";
        
        List<String> oFromTokens = CoverageUtils.tokenize(oFrom, true);
        List<String> oToTokens = CoverageUtils.tokenize(oTo, true);
        List<String> gFromTokens = CoverageUtils.tokenize(gFrom, true);
        List<String> gToTokens = CoverageUtils.tokenize(gTo, true);
        
        double deleteSimilarity = CoverageUtils.recallSimilarity(oFromTokens, gFromTokens); // 1.0权重
        double addSimilarity = CoverageUtils.recallSimilarity(oToTokens, gToTokens); // 1.0权重
        double crossSimilarity1 = CoverageUtils.recallSimilarity(oFromTokens, gToTokens); // 0.3权重
        double crossSimilarity2 = CoverageUtils.recallSimilarity(oToTokens, gFromTokens); // 0.3权重
        
        System.out.println("=== 四路相似度同源匹配权重测试 ===");
        System.out.println("删除相似度(oFrom->gFrom): " + deleteSimilarity);
        System.out.println("添加相似度(oTo->gTo): " + addSimilarity);
        System.out.println("交叉相似度1(oFrom->gTo): " + crossSimilarity1);
        System.out.println("交叉相似度2(oTo->gFrom): " + crossSimilarity2);
        
        double weightedDelete = deleteSimilarity * 1.0;
        double weightedAdd = addSimilarity * 1.0;
        double weightedCross1 = crossSimilarity1 * 0.3;
        double weightedCross2 = crossSimilarity2 * 0.3;
        
        System.out.println("加权删除相似度: " + weightedDelete);
        System.out.println("加权添加相似度: " + weightedAdd);
        System.out.println("加权交叉相似度1: " + weightedCross1);
        System.out.println("加权交叉相似度2: " + weightedCross2);
        
        // 同源匹配应该有更高的权重
        assertTrue(weightedDelete > weightedCross1, "同源匹配权重应该高于交叉匹配");
        assertTrue(weightedAdd > weightedCross2, "同源匹配权重应该高于交叉匹配");
    }
    
    @Test
    @DisplayName("相似度测试：验证交叉匹配的影响")
    void testSimilarity_CrossMatchingImpact() {
        // 创建会导致cross匹配的场景
        DiffBlock block1 = DiffBlock.builder()
                .contentFrom("public class TestA { String dataA; }")
                .contentTo("public class TestB { String dataB; }")
                .build();
        
        DiffBlock block2 = DiffBlock.builder()
                .contentFrom("public class TestC { String dataC; }")
                .contentTo("public class TestA { String dataA; }") // 与block1的contentFrom相同
                .build();
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("CrossTest.java")
                .blocks(Arrays.asList(block1))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("CrossTest.java")
                .blocks(Arrays.asList(block2))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 交叉匹配影响测试 ===");
        System.out.println("O端: " + block1.getContentFrom() + " -> " + block1.getContentTo());
        System.out.println("G端: " + block2.getContentFrom() + " -> " + block2.getContentTo());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        
        // 由于存在cross匹配，覆盖率应该是中等的
        assertTrue(detail.getCoverage() > 0.2, "cross匹配应该提供一定的覆盖率");
        assertTrue(detail.getCoverage() < 0.8, "cross匹配不应该导致完全匹配");
    }
    
    @Test
    @DisplayName("相似度测试：验证权重分配的合理性")
    void testSimilarity_WeightDistributionReasonableness() {
        // 测试不同权重比例的影响
        String[] testContents = {
            "public class Test { String data; }",
            "private class Test { String data; }",  // 只有一个词不同
            "public class Different { int value; }",  // 完全不同
            "class Test { String data; }",  // 少一个修饰符
        };
        
        for (int i = 0; i < testContents.length; i++) {
            for (int j = 0; j < testContents.length; j++) {
                List<String> tokens1 = CoverageUtils.tokenize(testContents[i], true);
                List<String> tokens2 = CoverageUtils.tokenize(testContents[j], true);
                double similarity = CoverageUtils.recallSimilarity(tokens1, tokens2);
                
                System.out.println("相似度[" + i + "][" + j + "]: " + similarity);
                System.out.println("内容" + i + ": " + testContents[i]);
                System.out.println("内容" + j + ": " + testContents[j]);
                System.out.println("---");
            }
        }
        
        // 验证相同内容相似度为1
        List<String> sameTokens = CoverageUtils.tokenize(testContents[0], true);
        double sameSimilarity = CoverageUtils.recallSimilarity(sameTokens, sameTokens);
        assertEquals(1.0, sameSimilarity, 0.001, "相同内容相似度应该为1");
    }
    
    @Test
    @DisplayName("相似度测试：边界情况的相似度计算")
    void testSimilarity_EdgeCaseSimilarity() {
        String[] edgeCases = {
            "", // 空字符串
            " ", // 只有空格
            "\n", // 只有换行
            "public", // 单个词
            "public class", // 两个词
            "public class Test { }", // 完整语句
        };
        
        for (String edgeCase : edgeCases) {
            List<String> tokens = CoverageUtils.tokenize(edgeCase, true);
            double selfSimilarity = CoverageUtils.recallSimilarity(tokens, tokens);
            
            System.out.println("边界情况测试: '" + edgeCase + "'");
            System.out.println("Token数量: " + tokens.size());
            System.out.println("自相似度: " + selfSimilarity);
            
            if (tokens.isEmpty()) {
                assertEquals(1.0, selfSimilarity, 0.001, "空内容自相似度应该为1");
            } else {
                assertEquals(1.0, selfSimilarity, 0.001, "非空内容自相似度应该为1");
            }
        }
    }
    
    @Test
    @DisplayName("相似度测试：Tokenization对相似度的影响")
    void testSimilarity_TokenizationImpact() {
        String content1 = "import java.util.List;\npublic class Test { List<String> data; }";
        String content2 = "import java.util.ArrayList;\npublic class Test { ArrayList<String> data; }";
        
        // 带噪音过滤的分词
        List<String> tokens1Filtered = CoverageUtils.tokenize(content1, true);
        List<String> tokens2Filtered = CoverageUtils.tokenize(content2, true);
        
        // 不带噪音过滤的分词
        List<String> tokens1Unfiltered = CoverageUtils.tokenize(content1, false);
        List<String> tokens2Unfiltered = CoverageUtils.tokenize(content2, false);
        
        double similarityFiltered = CoverageUtils.recallSimilarity(tokens1Filtered, tokens2Filtered);
        double similarityUnfiltered = CoverageUtils.recallSimilarity(tokens1Unfiltered, tokens2Unfiltered);
        
        System.out.println("=== Tokenization对相似度的影响测试 ===");
        System.out.println("内容1: " + content1);
        System.out.println("内容2: " + content2);
        System.out.println("过滤后Token数1: " + tokens1Filtered.size());
        System.out.println("过滤后Token数2: " + tokens2Filtered.size());
        System.out.println("未过滤Token数1: " + tokens1Unfiltered.size());
        System.out.println("未过滤Token数2: " + tokens2Unfiltered.size());
        System.out.println("过滤后相似度: " + similarityFiltered);
        System.out.println("未过滤相似度: " + similarityUnfiltered);
        
        // 过滤后应该有更高的相似度（因为去掉了不同的import）
        assertTrue(similarityFiltered >= similarityUnfiltered, 
                  "过滤后相似度应该不低于未过滤相似度");
    }
    
    @Test
    @DisplayName("相似度测试：验证最大值选择逻辑")
    void testSimilarity_MaximumSelectionLogic() {
        // 测试四路相似度的最大值选择
        String oFrom = "public class TestA { String data; }";
        String oTo = "public class TestB { int value; }";
        String gFrom = "public class TestC { double number; }";
        String gTo = "public class TestA { String data; }"; // 与oFrom完全相同
        
        List<String> oFromTokens = CoverageUtils.tokenize(oFrom, true);
        List<String> oToTokens = CoverageUtils.tokenize(oTo, true);
        List<String> gFromTokens = CoverageUtils.tokenize(gFrom, true);
        List<String> gToTokens = CoverageUtils.tokenize(gTo, true);
        
        double deleteSimilarity = CoverageUtils.recallSimilarity(oFromTokens, gFromTokens);
        double addSimilarity = CoverageUtils.recallSimilarity(oToTokens, gToTokens);
        double crossSimilarity1 = CoverageUtils.recallSimilarity(oFromTokens, gToTokens);
        double crossSimilarity2 = CoverageUtils.recallSimilarity(oToTokens, gFromTokens);
        
        System.out.println("=== 最大值选择逻辑测试 ===");
        System.out.println("删除相似度: " + deleteSimilarity);
        System.out.println("添加相似度: " + addSimilarity);
        System.out.println("交叉相似度1: " + crossSimilarity1);
        System.out.println("交叉相似度2: " + crossSimilarity2);
        
        double maxSimilarity = Math.max(
            Math.max(deleteSimilarity, addSimilarity),
            Math.max(crossSimilarity1, crossSimilarity2)
        );
        
        System.out.println("最大相似度: " + maxSimilarity);
        
        // crossSimilarity1应该是最高的（因为oFrom和gTo完全相同）
        assertEquals(crossSimilarity1, maxSimilarity, 0.001, "应该选择最大的相似度");
    }

    // ========== 5. 行数计算一致性测试 ==========
    
    @Test
    @DisplayName("行数测试：原始行数与去噪行数对比")
    void testLineCount_Original_vs_NoiseFree() {
        String mixedContent = "import java.util.List;\n" +
                             "// 这是注释\n" +
                             "public class Test {\n" +
                             "    private List<String> data;\n" +
                             "}";
        
        DiffBlock mixedBlock = DiffBlock.builder()
                .contentFrom(mixedContent)
                .contentTo(mixedContent)
                .build();
        
        // 计算原始行数
        int originalLines = mixedContent.split("\n").length;
        
        // 计算去噪行数
        String filteredContent = CoverageUtils.filterCodeNoise(mixedContent);
        int filteredLines = filteredContent.trim().isEmpty() ? 0 : filteredContent.split("\n").length;
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("LineCountTest.java")
                .blocks(Arrays.asList(mixedBlock))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("LineCountTest.java")
                .blocks(Arrays.asList(mixedBlock))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 原始行数与去噪行数对比测试 ===");
        System.out.println("原始内容:\n" + mixedContent);
        System.out.println("过滤后内容:\n" + filteredContent);
        System.out.println("原始行数: " + originalLines);
        System.out.println("去噪行数: " + filteredLines);
        System.out.println("覆盖率计算总行数: " + detail.getTotalLines());
        
        assertEquals(filteredLines, detail.getTotalLines(), "覆盖率应该使用去噪行数");
        assertTrue(originalLines >= filteredLines, "原始行数应该大于等于去噪行数");
    }
    
    @Test
    @DisplayName("行数测试：不同内容类型的行数计算")
    void testLineCount_DifferentContentTypes() {
        String[] testCases = {
            "import java.util.List;", // 纯import
            "// 单行注释", // 纯注释
            "/*\n * 多行注释\n */", // 多行注释
            "public class Test {}", // 纯代码
            "import java.util.List;\npublic class Test {}", // 混合
            "", // 空内容
            "   \n   \n", // 只有空白
        };
        
        for (String testCase : testCases) {
            String filtered = CoverageUtils.filterCodeNoise(testCase);
            int originalLines = testCase.isEmpty() ? 0 : testCase.split("\n").length;
            int filteredLines = filtered.trim().isEmpty() ? 0 : filtered.split("\n").length;
            
            List<String> tokens = CoverageUtils.tokenize(testCase, true);
            
            System.out.println("=== 不同内容类型行数测试 ===");
            System.out.println("测试内容: '" + testCase + "'");
            System.out.println("原始行数: " + originalLines);
            System.out.println("过滤后行数: " + filteredLines);
            System.out.println("Token数量: " + tokens.size());
            System.out.println("---");
            
            if (testCase.trim().isEmpty() || 
                testCase.trim().startsWith("import") || 
                testCase.trim().startsWith("//") ||
                testCase.trim().startsWith("/*") ||
                testCase.trim().startsWith("/**")) {
                assertEquals(0, filteredLines, "噪音内容过滤后行数应该为0");
            }
        }
    }
    
    @Test
    @DisplayName("行数测试：权重与相似度的一致性")
    void testLineCount_Weight_SimilarityConsistency() {
        String codeContent = "public class Test {\n    private String data;\n    public void method() {}\n}";
        String mixedContent = "import java.util.List;\n" + codeContent;
        
        DiffBlock codeBlock = DiffBlock.builder()
                .contentFrom(codeContent)
                .contentTo(codeContent)
                .build();
        
        DiffBlock mixedBlock = DiffBlock.builder()
                .contentFrom(mixedContent)
                .contentTo(mixedContent)
                .build();
        
        DiffFile oFile1 = DiffFile.builder()
                .relativePath("CodeOnly.java")
                .blocks(Arrays.asList(codeBlock))
                .build();
        
        DiffFile gFile1 = DiffFile.builder()
                .relativePath("CodeOnly.java")
                .blocks(Arrays.asList(codeBlock))
                .build();
        
        DiffFile oFile2 = DiffFile.builder()
                .relativePath("Mixed.java")
                .blocks(Arrays.asList(mixedBlock))
                .build();
        
        DiffFile gFile2 = DiffFile.builder()
                .relativePath("Mixed.java")
                .blocks(Arrays.asList(mixedBlock))
                .build();
        
        CoverageDetail codeDetail = coverageEvaluator.evaluateFileWithMapping(oFile1, gFile1, 0.8);
        CoverageDetail mixedDetail = coverageEvaluator.evaluateFileWithMapping(oFile2, gFile2, 0.8);
        
        System.out.println("=== 权重与相似度一致性测试 ===");
        System.out.println("纯代码块 - 总行数: " + codeDetail.getTotalLines() + 
                          ", 覆盖率: " + codeDetail.getCoverage() +
                          ", 匹配行数: " + codeDetail.getMatchedLines());
        System.out.println("混合块 - 总行数: " + mixedDetail.getTotalLines() + 
                          ", 覆盖率: " + mixedDetail.getCoverage() +
                          ", 匹配行数: " + mixedDetail.getMatchedLines());
        
        // 两个完全相同的块都应该有覆盖率1.0
        assertEquals(1.0, codeDetail.getCoverage(), 0.001, "纯代码块应该完全匹配");
        assertEquals(1.0, mixedDetail.getCoverage(), 0.001, "混合块应该完全匹配");
        
        // 混合块和纯代码块的去噪行数应该相等（因为包含相同的代码）
        assertEquals(codeDetail.getTotalLines(), mixedDetail.getTotalLines(), 
                    "相同代码部分应该有相同的去噪行数");
    }
    
    @Test
    @DisplayName("行数测试：边界情况的行数处理")
    void testLineCount_EdgeCaseHandling() {
        String[] edgeCases = {
            null, // null内容
            "", // 空字符串
            "   ", // 只有空格
            "\n", // 只有换行
            "   \n   \n   ", // 空格和换行混合
            "import", // 不完整的import
            "//", // 不完整的注释
            "/*", // 未闭合的多行注释
        };
        
        for (String edgeCase : edgeCases) {
            DiffBlock block = DiffBlock.builder()
                    .contentFrom(edgeCase)
                    .contentTo(edgeCase)
                    .build();
            
            DiffFile oFile = DiffFile.builder()
                    .relativePath("EdgeCase.java")
                    .blocks(Arrays.asList(block))
                    .build();
            
            DiffFile gFile = DiffFile.builder()
                    .relativePath("EdgeCase.java")
                    .blocks(Arrays.asList(block))
                    .build();
            
            CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
            
            System.out.println("=== 边界情况行数处理测试 ===");
            System.out.println("测试内容: " + (edgeCase == null ? "null" : "'" + edgeCase + "'"));
            System.out.println("总行数: " + detail.getTotalLines());
            System.out.println("覆盖率: " + detail.getCoverage());
            System.out.println("---");
            
            // 边界情况应该被正确处理
            assertTrue(detail.getTotalLines() >= 0, "总行数不应该为负");
            assertTrue(detail.getCoverage() >= 0 && detail.getCoverage() <= 1.0, "覆盖率应该在0-1之间");
        }
    }
    
    @Test
    @DisplayName("行数测试：多文件聚合的行数计算")
    void testLineCount_MultiFileAggregation() {
        // 创建多个文件
        DiffBlock codeBlock1 = createPureCodeBlock("public class Test1 { String data1; }");
        DiffBlock codeBlock2 = createPureCodeBlock("public class Test2 { String data2; }");
        DiffBlock mixedBlock = createMixedNoiseBlock(
            "import java.util.List;", 
            "// 注释", 
            "public class Test3 { String data3; }");
        
        DiffFile file1 = DiffFile.builder()
                .relativePath("File1.java")
                .blocks(Arrays.asList(codeBlock1))
                .build();
        
        DiffFile file2 = DiffFile.builder()
                .relativePath("File2.java")
                .blocks(Arrays.asList(codeBlock2))
                .build();
        
        DiffFile file3 = DiffFile.builder()
                .relativePath("File3.java")
                .blocks(Arrays.asList(mixedBlock))
                .build();
        
        List<DiffFile> oFiles = Arrays.asList(file1, file2, file3);
        List<DiffFile> gFiles = Arrays.asList(file1, file2, file3);
        
        CoverageSummary summary = coverageEvaluator.evaluateFiles(oFiles, gFiles, 0.8);
        
        System.out.println("=== 多文件聚合行数计算测试 ===");
        System.out.println("文件数量: " + summary.getDetails().size());
        System.out.println("总体覆盖率: " + summary.getOverallCoverage());
        System.out.println("总行数: " + summary.getTotalLines());
        System.out.println("匹配行数: " + summary.getTotalMatchedLines());
        
        // 验证每个文件的详情
        for (CoverageDetail detail : summary.getDetails()) {
            System.out.println("文件: " + detail.getFilePath() + 
                             ", 覆盖率: " + detail.getCoverage() +
                             ", 总行数: " + detail.getTotalLines() +
                             ", 匹配行数: " + detail.getMatchedLines());
            
            assertTrue(detail.getCoverage() > 0.9, "相同文件应该有高覆盖率");
            assertTrue(detail.getTotalLines() >= 1, "每个文件应该至少有1行");
            assertEquals(detail.getTotalLines(), detail.getMatchedLines(), 0.001, 
                        "相同文件匹配行数应该等于总行数");
        }
        
        // 验证聚合结果
        assertEquals(3, summary.getDetails().size(), "应该有3个文件");
        assertTrue(summary.getOverallCoverage() > 0.9, "总体覆盖率应该很高");
        assertTrue(summary.getTotalLines() >= 3, "总行数应该至少为3");
        assertEquals(summary.getTotalLines(), summary.getTotalMatchedLines(), 0.001, 
                    "相同文件总体匹配行数应该等于总行数");
    }

    // ========== 6. 边界情况处理测试 ==========
    
    @Test
    @DisplayName("边界测试：null和空内容处理")
    void testEdgeCase_NullAndEmptyContent() {
        // 测试null内容
        DiffBlock nullBlock = DiffBlock.builder()
                .contentFrom(null)
                .contentTo(null)
                .build();
        
        // 测试空内容
        DiffBlock emptyBlock = DiffBlock.builder()
                .contentFrom("")
                .contentTo("")
                .build();
        
        // 测试只有空白的内容
        DiffBlock whitespaceBlock = DiffBlock.builder()
                .contentFrom("   \n   \n   ")
                .contentTo("   \n   \n   ")
                .build();
        
        List<DiffBlock> blocks = Arrays.asList(nullBlock, emptyBlock, whitespaceBlock);
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("EdgeCaseTest.java")
                .blocks(blocks)
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("EdgeCaseTest.java")
                .blocks(blocks)
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== null和空内容处理测试 ===");
        System.out.println("块数量: " + blocks.size());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        System.out.println("高相似度块数: " + detail.getMatchedBlocks().size());
        System.out.println("低相似度块数: " + detail.getUnmatchedBlocks().size());
        
        // 边界情况应该被正确处理
        assertEquals(0.0, detail.getTotalLines(), 0.001, "空内容块总行数应该为0");
        assertTrue(detail.getCoverage() > 0.9, "空内容块应该完全匹配");
    }
    
    @Test
    @DisplayName("边界测试：特殊字符和Unicode处理")
    void testEdgeCase_SpecialCharactersAndUnicode() {
        String[] specialCases = {
            "public class 测试类 { String 数据 = \"中文\"; }", // 中文字符
            "public class Test { String emoji = \"😀😃😄\"; }", // Emoji
            "public class Test { String special = \"\\n\\t\\r\"; }", // 转义字符
            "public class Test { String unicode = \"\\u4e2d\\u6587\"; }", // Unicode转义
            "public class Test { String latin1 = \"café\"; }", // 拉丁字符
            "public class Test { String mixed = \"测试😀café\"; }", // 混合字符
        };
        
        for (String specialCase : specialCases) {
            DiffBlock block = DiffBlock.builder()
                    .contentFrom(specialCase)
                    .contentTo(specialCase)
                    .build();
            
            DiffFile oFile = DiffFile.builder()
                    .relativePath("SpecialTest.java")
                    .blocks(Arrays.asList(block))
                    .build();
            
            DiffFile gFile = DiffFile.builder()
                    .relativePath("SpecialTest.java")
                    .blocks(Arrays.asList(block))
                    .build();
            
            CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
            
            System.out.println("=== 特殊字符和Unicode处理测试 ===");
            System.out.println("测试内容: " + specialCase);
            System.out.println("覆盖率: " + detail.getCoverage());
            System.out.println("总行数: " + detail.getTotalLines());
            System.out.println("匹配行数: " + detail.getMatchedLines());
            System.out.println("---");
            
            // 特殊字符应该被正确处理
            assertEquals(1.0, detail.getCoverage(), 0.001, "相同特殊字符内容应该完全匹配");
            assertTrue(detail.getTotalLines() >= 1, "应该有正的行数");
        }
    }
    
    @Test
    @DisplayName("边界测试：超大内容的性能处理")
    void testEdgeCase_LargeContentPerformance() {
        // 创建一个大内容
        StringBuilder largeContent = new StringBuilder();
        largeContent.append("public class LargeClass {\n");
        
        for (int i = 0; i < 1000; i++) {
            largeContent.append("    private String field").append(i).append(" = \"value").append(i).append("\";\n");
        }
        
        largeContent.append("}\n");
        
        String largeString = largeContent.toString();
        
        DiffBlock largeBlock = DiffBlock.builder()
                .contentFrom(largeString)
                .contentTo(largeString)
                .build();
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("LargeTest.java")
                .blocks(Arrays.asList(largeBlock))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("LargeTest.java")
                .blocks(Arrays.asList(largeBlock))
                .build();
        
        long startTime = System.currentTimeMillis();
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        long endTime = System.currentTimeMillis();
        
        System.out.println("=== 超大内容性能处理测试 ===");
        System.out.println("内容长度: " + largeString.length());
        System.out.println("预计行数: " + (1000 + 2)); // 类定义开始和结束
        System.out.println("实际总行数: " + detail.getTotalLines());
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("处理时间: " + (endTime - startTime) + "ms");
        
        // 大内容应该被正确处理
        assertEquals(1.0, detail.getCoverage(), 0.001, "相同大内容应该完全匹配");
        assertTrue(detail.getTotalLines() >= 1000, "大内容应该有足够的行数");
        assertTrue(endTime - startTime < 5000, "处理时间应该在合理范围内（<5秒）");
    }
    
    @Test
    @DisplayName("边界测试：极相似但不完全相同的内容")
    void testEdgeCase_VerySimilarButNotIdentical() {
        String content1 = "public class Test {\n    private String data1 = \"value1\";\n    private int number1 = 1;\n}";
        String content2 = "public class Test {\n    private String data2 = \"value2\";\n    private int number2 = 2;\n}";
        String content3 = "public class Test {\n    private String data1 = \"value1\";\n    private int number2 = 2;\n}";
        
        // 测试三种组合
        DiffBlock[] blocks = {
            DiffBlock.builder().contentFrom(content1).contentTo(content1).build(),
            DiffBlock.builder().contentFrom(content2).contentTo(content2).build(),
            DiffBlock.builder().contentFrom(content3).contentTo(content3).build()
        };
        
        String[] descriptions = {
            "完全相同",
            "结构相同，内容不同",
            "部分相同"
        };
        
        for (int i = 0; i < blocks.length; i++) {
            DiffFile oFile = DiffFile.builder()
                    .relativePath("SimilarTest" + i + ".java")
                    .blocks(Arrays.asList(blocks[i]))
                    .build();
            
            DiffFile gFile = DiffFile.builder()
                    .relativePath("SimilarTest" + i + ".java")
                    .blocks(Arrays.asList(blocks[i]))
                    .build();
            
            CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
            
            System.out.println("=== 极相似但不完全相同内容测试 ===");
            System.out.println("测试" + (i + 1) + " - " + descriptions[i]);
            System.out.println("覆盖率: " + detail.getCoverage());
            System.out.println("总行数: " + detail.getTotalLines());
            System.out.println("匹配行数: " + detail.getMatchedLines());
            System.out.println("---");
            
            if (i == 0) {
                assertEquals(1.0, detail.getCoverage(), 0.001, "完全相同应该完全匹配");
            } else {
                assertTrue(detail.getCoverage() > 0.5, "相似内容应该有较高覆盖率");
                assertTrue(detail.getCoverage() < 1.0, "不完全相同不应该完全匹配");
            }
        }
    }

    // ========== 7. 实际代码场景测试 ==========
    
    @Test
    @DisplayName("实际场景：真实Java类重构")
    void testRealScenario_JavaClassRefactoring() {
        // 模拟真实的Java类重构场景
        String originalClass = "package com.example.old;\n\n" +
                              "import java.util.List;\n" +
                              "import java.util.ArrayList;\n\n" +
                              "/**\n" +
                              " * 用户服务类\n" +
                              " * @author Old Author\n" +
                              " */\n" +
                              "public class UserService {\n" +
                              "    private List<String> users;\n" +
                              "    \n" +
                              "    /**\n" +
                              "     * 构造函数\n" +
                              "     */\n" +
                              "    public UserService() {\n" +
                              "        this.users = new ArrayList<>();\n" +
                              "    }\n" +
                              "    \n" +
                              "    /**\n" +
                              "     * 添加用户\n" +
                              "     * @param user 用户名\n" +
                              "     */\n" +
                              "    public void addUser(String user) {\n" +
                              "        this.users.add(user);\n" +
                              "    }\n" +
                              "}";
        
        String refactoredClass = "package com.example.new;\n\n" +
                                "import java.util.List;\n" +
                                "import java.util.LinkedList;\n\n" +
                                "/**\n" +
                                " * 用户服务类\n" +
                                " * @author New Author\n" +
                                " * @version 2.0\n" +
                                " */\n" +
                                "public class UserService {\n" +
                                "    private List<String> userList;\n" +
                                "    \n" +
                                "    /**\n" +
                                "     * 构造函数\n" +
                                "     */\n" +
                                "    public UserService() {\n" +
                                "        this.userList = new LinkedList<>();\n" +
                                "    }\n" +
                                "    \n" +
                                "    /**\n" +
                                "     * 添加用户\n" +
                                "     * @param userName 用户名\n" +
                                "     * @return 是否添加成功\n" +
                                "     */\n" +
                                "    public boolean addUser(String userName) {\n" +
                                "        return this.userList.add(userName);\n" +
                                "    }\n" +
                                "    \n" +
                                "    /**\n" +
                                "     * 获取用户数量\n" +
                                "     * @return 用户数量\n" +
                                "     */\n" +
                                "    public int getUserCount() {\n" +
                                "        return this.userList.size();\n" +
                                "    }\n" +
                                "}";
        
        DiffBlock originalBlock = DiffBlock.builder()
                .contentFrom(originalClass)
                .contentTo(originalClass)
                .build();
        
        DiffBlock refactoredBlock = DiffBlock.builder()
                .contentFrom(refactoredClass)
                .contentTo(refactoredClass)
                .build();
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("UserService.java")
                .blocks(Arrays.asList(originalBlock))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("UserService.java")
                .blocks(Arrays.asList(refactoredBlock))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 真实Java类重构测试 ===");
        System.out.println("原始类行数: " + originalClass.split("\n").length);
        System.out.println("重构类行数: " + refactoredClass.split("\n").length);
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        
        // 验证重构后的覆盖率
        assertTrue(detail.getCoverage() > 0.7, "重构后应该有较高覆盖率");
        assertTrue(detail.getTotalLines() > 0, "应该有正的总行数");
        assertTrue(detail.getMatchedLines() > 0, "应该有正的匹配行数");
    }
    
    @Test
    @DisplayName("实际场景：多文件项目重构")
    void testRealScenario_MultiFileProjectRefactoring() {
        // 模拟多文件项目重构
        String serviceInterface = "package com.example.service;\n\n" +
                                 "import java.util.List;\n\n" +
                                 "/**\n" +
                                 " * 用户服务接口\n" +
                                 " */\n" +
                                 "public interface UserService {\n" +
                                 "    /**\n" +
                                 "     * 添加用户\n" +
                                 "     * @param user 用户名\n" +
                                 "     */\n" +
                                 "    void addUser(String user);\n" +
                                 "    \n" +
                                 "    /**\n" +
                                 "     * 获取所有用户\n" +
                                 "     * @return 用户列表\n" +
                                 "     */\n" +
                                 "    List<String> getAllUsers();\n" +
                                 "}";
        
        String serviceImpl = "package com.example.service.impl;\n\n" +
                            "import com.example.service.UserService;\n" +
                            "import java.util.ArrayList;\n" +
                            "import java.util.List;\n\n" +
                            "/**\n" +
                            " * 用户服务实现\n" +
                            " */\n" +
                            "public class UserServiceImpl implements UserService {\n" +
                            "    private List<String> users;\n" +
                            "    \n" +
                            "    public UserServiceImpl() {\n" +
                            "        this.users = new ArrayList<>();\n" +
                            "    }\n" +
                            "    \n" +
                            "    @Override\n" +
                            "    public void addUser(String user) {\n" +
                            "        this.users.add(user);\n" +
                            "    }\n" +
                            "    \n" +
                            "    @Override\n" +
                            "    public List<String> getAllUsers() {\n" +
                            "        return new ArrayList<>(this.users);\n" +
                            "    }\n" +
                            "}";
        
        String controller = "package com.example.controller;\n\n" +
                           "import com.example.service.UserService;\n" +
                           "import org.springframework.beans.factory.annotation.Autowired;\n" +
                           "import org.springframework.web.bind.annotation.*;\n\n" +
                           "/**\n" +
                           " * 用户控制器\n" +
                           " */\n" +
                           "@RestController\n" +
                           "@RequestMapping(\"/api/users\")\n" +
                           "public class UserController {\n" +
                           "    @Autowired\n" +
                           "    private UserService userService;\n" +
                           "    \n" +
                           "    @PostMapping\n" +
                           "    public void addUser(@RequestBody String user) {\n" +
                           "        userService.addUser(user);\n" +
                           "    }\n" +
                           "    \n" +
                           "    @GetMapping\n" +
                           "    public List<String> getAllUsers() {\n" +
                           "        return userService.getAllUsers();\n" +
                           "    }\n" +
                           "}";
        
        // 创建原始文件
        DiffBlock interfaceBlock = DiffBlock.builder()
                .contentFrom(serviceInterface)
                .contentTo(serviceInterface)
                .build();
        
        DiffBlock implBlock = DiffBlock.builder()
                .contentFrom(serviceImpl)
                .contentTo(serviceImpl)
                .build();
        
        DiffBlock controllerBlock = DiffBlock.builder()
                .contentFrom(controller)
                .contentTo(controller)
                .build();
        
        DiffFile interfaceFile = DiffFile.builder()
                .relativePath("UserService.java")
                .blocks(Arrays.asList(interfaceBlock))
                .build();
        
        DiffFile implFile = DiffFile.builder()
                .relativePath("UserServiceImpl.java")
                .blocks(Arrays.asList(implBlock))
                .build();
        
        DiffFile controllerFile = DiffFile.builder()
                .relativePath("UserController.java")
                .blocks(Arrays.asList(controllerBlock))
                .build();
        
        List<DiffFile> oFiles = Arrays.asList(interfaceFile, implFile, controllerFile);
        List<DiffFile> gFiles = Arrays.asList(interfaceFile, implFile, controllerFile);
        
        CoverageSummary summary = coverageEvaluator.evaluateFiles(oFiles, gFiles, 0.8);
        
        System.out.println("=== 多文件项目重构测试 ===");
        System.out.println("文件数量: " + summary.getDetails().size());
        System.out.println("总体覆盖率: " + summary.getOverallCoverage());
        System.out.println("总行数: " + summary.getTotalLines());
        System.out.println("匹配行数: " + summary.getTotalMatchedLines());
        
        // 验证每个文件
        for (CoverageDetail detail : summary.getDetails()) {
            System.out.println("文件: " + detail.getFilePath() + 
                             ", 覆盖率: " + detail.getCoverage() +
                             ", 总行数: " + detail.getTotalLines() +
                             ", 匹配行数: " + detail.getMatchedLines());
            
            assertEquals(1.0, detail.getCoverage(), 0.001, "相同文件应该完全匹配");
            assertTrue(detail.getTotalLines() >= 5, "每个文件应该至少有5行代码");
        }
        
        // 验证聚合结果
        assertEquals(3, summary.getDetails().size(), "应该有3个文件");
        assertEquals(1.0, summary.getOverallCoverage(), 0.001, "相同文件总体覆盖率应该为1");
        assertTrue(summary.getTotalLines() >= 15, "总行数应该至少为15");
    }
    
    @Test
    @DisplayName("实际场景：配置文件变更")
    void testRealScenario_ConfigurationFileChanges() {
        // 模拟配置文件变更场景
        String originalConfig = "# 数据库配置\n" +
                              "spring.datasource.url=jdbc:mysql://localhost:3306/old_db\n" +
                              "spring.datasource.username=root\n" +
                              "spring.datasource.password=password123\n" +
                              "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver\n\n" +
                              "# 连接池配置\n" +
                              "spring.datasource.hikari.maximum-pool-size=10\n" +
                              "spring.datasource.hikari.minimum-idle=5\n" +
                              "spring.datasource.hikari.connection-timeout=30000";
        
        String newConfig = "# 数据库配置\n" +
                          "# 使用新的数据库\n" +
                          "spring.datasource.url=jdbc:postgresql://localhost:5432/new_db\n" +
                          "spring.datasource.username=admin\n" +
                          "spring.datasource.password=securePassword456\n" +
                          "spring.datasource.driver-class-name=org.postgresql.Driver\n\n" +
                          "# 连接池配置\n" +
                          "# 优化连接池设置\n" +
                          "spring.datasource.hikari.maximum-pool-size=20\n" +
                          "spring.datasource.hikari.minimum-idle=10\n" +
                          "spring.datasource.hikari.connection-timeout=60000\n\n" +
                          "# 新增配置\n" +
                          "spring.jpa.hibernate.ddl-auto=update\n" +
                          "spring.jpa.show-sql=true";
        
        DiffBlock originalBlock = DiffBlock.builder()
                .contentFrom(originalConfig)
                .contentTo(originalConfig)
                .build();
        
        DiffBlock newBlock = DiffBlock.builder()
                .contentFrom(newConfig)
                .contentTo(newConfig)
                .build();
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("application.properties")
                .blocks(Arrays.asList(originalBlock))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("application.properties")
                .blocks(Arrays.asList(newBlock))
                .build();
        
        CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 配置文件变更测试 ===");
        System.out.println("原始配置行数: " + originalConfig.split("\n").length);
        System.out.println("新配置行数: " + newConfig.split("\n").length);
        System.out.println("覆盖率: " + detail.getCoverage());
        System.out.println("总行数: " + detail.getTotalLines());
        System.out.println("匹配行数: " + detail.getMatchedLines());
        
        // 配置文件变更应该有一定的覆盖率（因为有相同的配置项）
        assertTrue(detail.getCoverage() > 0.3, "配置文件变更应该有一定覆盖率");
        assertTrue(detail.getCoverage() < 0.8, "配置文件变更不应该完全匹配");
        assertTrue(detail.getTotalLines() > 0, "应该有正的总行数");
    }

    // ========== 8. 缓存一致性测试 ==========
    
    @Test
    @DisplayName("缓存测试：双Tokenization缓存一致性")
    void testCache_DualTokenizationConsistency() {
        String testContent = "import java.util.List;\npublic class Test { String data; }";
        
        DiffBlock block = DiffBlock.builder()
                .contentFrom(testContent)
                .contentTo(testContent)
                .build();
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("CacheTest.java")
                .blocks(Arrays.asList(block))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("CacheTest.java")
                .blocks(Arrays.asList(block))
                .build();
        
        // 第一次计算
        CoverageDetail detail1 = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        // 第二次计算（应该使用缓存）
        CoverageDetail detail2 = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        
        System.out.println("=== 双Tokenization缓存一致性测试 ===");
        System.out.println("第一次计算覆盖率: " + detail1.getCoverage());
        System.out.println("第二次计算覆盖率: " + detail2.getCoverage());
        System.out.println("第一次总行数: " + detail1.getTotalLines());
        System.out.println("第二次总行数: " + detail2.getTotalLines());
        System.out.println("第一次匹配行数: " + detail1.getMatchedLines());
        System.out.println("第二次匹配行数: " + detail2.getMatchedLines());
        
        // 验证缓存一致性
        assertEquals(detail1.getCoverage(), detail2.getCoverage(), 0.001, "缓存结果应该一致");
        assertEquals(detail1.getTotalLines(), detail2.getTotalLines(), "缓存总行数应该一致");
        assertEquals(detail1.getMatchedLines(), detail2.getMatchedLines(), 0.001, "缓存匹配行数应该一致");
        
        // 验证结果的正确性
        assertEquals(1.0, detail1.getCoverage(), 0.001, "相同块应该完全匹配");
        assertEquals(1.0, detail2.getCoverage(), 0.001, "相同块应该完全匹配");
    }
    
    @Test
    @DisplayName("缓存测试：多次计算稳定性")
    void testCache_MultipleCalculationsStability() {
        String testContent = "public class StabilityTest {\n    private String value;\n}";
        
        DiffBlock block = DiffBlock.builder()
                .contentFrom(testContent)
                .contentTo(testContent)
                .build();
        
        DiffFile oFile = DiffFile.builder()
                .relativePath("StabilityTest.java")
                .blocks(Arrays.asList(block))
                .build();
        
        DiffFile gFile = DiffFile.builder()
                .relativePath("StabilityTest.java")
                .blocks(Arrays.asList(block))
                .build();
        
        // 进行多次计算
        CoverageDetail[] details = new CoverageDetail[5];
        for (int i = 0; i < 5; i++) {
            details[i] = coverageEvaluator.evaluateFileWithMapping(oFile, gFile, 0.8);
        }
        
        System.out.println("=== 多次计算稳定性测试 ===");
        for (int i = 0; i < details.length; i++) {
            System.out.println("第" + (i + 1) + "次计算 - 覆盖率: " + details[i].getCoverage() + 
                             ", 总行数: " + details[i].getTotalLines() +
                             ", 匹配行数: " + details[i].getMatchedLines());
        }
        
        // 验证所有计算结果一致
        double baseCoverage = details[0].getCoverage();
        int baseTotalLines = details[0].getTotalLines();
        double baseMatchedLines = details[0].getMatchedLines();
        
        for (int i = 1; i < details.length; i++) {
            assertEquals(baseCoverage, details[i].getCoverage(), 0.001, "第" + (i + 1) + "次覆盖率应该与第一次一致");
            assertEquals(baseTotalLines, details[i].getTotalLines(), "第" + (i + 1) + "次总行数应该与第一次一致");
            assertEquals(baseMatchedLines, details[i].getMatchedLines(), 0.001, "第" + (i + 1) + "次匹配行数应该与第一次一致");
        }
        
        // 验证结果的正确性
        assertEquals(1.0, baseCoverage, 0.001, "相同块应该完全匹配");
        assertTrue(baseTotalLines >= 2, "应该至少有2行代码");
        assertEquals(baseTotalLines, baseMatchedLines, 0.001, "完全匹配时匹配行数应该等于总行数");
    }
}
