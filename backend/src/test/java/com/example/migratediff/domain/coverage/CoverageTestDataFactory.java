package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.shared.utils.CoverageUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 覆盖率测试数据工厂类
 * 
 * 提供各种标准化的测试数据，用于覆盖率算法的验证
 */
public class CoverageTestDataFactory {

    // ========== 标准噪音内容 ==========

    /**
     * 标准的纯import内容
     */
    public static final String STANDARD_IMPORTS = 
        "import java.util.List;\n" +
        "import java.util.Map;\n" +
        "import java.util.Set;";

    /**
     * 标准的纯注释内容
     */
    public static final String STANDARD_COMMENTS = 
        "// 这是单行注释\n" +
        "/* 这是多行注释\n" +
        "   第二行 */\n" +
        "/**\n" +
        " * JavaDoc注释\n" +
        " * @param name 参数\n" +
        " * @return 返回值\n" +
        " */";

    /**
     * 标准的package语句
     */
    public static final String STANDARD_PACKAGE = 
        "package com.example.test;";

    /**
     * 标准的纯代码内容
     */
    public static final String STANDARD_CODE = 
        "public class Test {\n" +
        "    private String data;\n" +
        "    \n" +
        "    public void method() {\n" +
        "        System.out.println(\"test\");\n" +
        "    }\n" +
        "}";

    /**
     * 标准的混合内容（包含所有类型的噪音和代码）
     */
    public static final String STANDARD_MIXED = 
        STANDARD_PACKAGE + "\n\n" +
        STANDARD_IMPORTS + "\n\n" +
        STANDARD_COMMENTS + "\n\n" +
        STANDARD_CODE;

    // ========== 边界情况内容 ==========

    /**
     * 空内容
     */
    public static final String EMPTY_CONTENT = "";

    /**
     * 只有空格的内容
     */
    public static final String WHITESPACE_ONLY = "   \n   \n   ";

    /**
     * 只有换行符的内容
     */
    public static final String NEWLINES_ONLY = "\n\n\n";

    /**
     * 不完整的import
     */
    public static final String INCOMPLETE_IMPORT = "import java.util";

    /**
     * 只有注释标记
     */
    public static final String COMMENT_MARKERS_ONLY = "//\n/*\n/**";

    /**
     * Unicode和特殊字符内容
     */
    public static final String UNICODE_CONTENT = 
        "public class 测试类 {\n" +
        "    private String 数据 = \"中文\";\n" +
        "    private String emoji = \"😀😃😄\";\n" +
        "    private String special = \"\\n\\t\\r\";\n" +
        "    private String latin1 = \"café\";\n" +
        "    private String mixed = \"测试😀café\";\n" +
        "}";

    // ========== 重构场景内容 ==========

    /**
     * 简单的Java类（重构前）
     */
    public static final String SIMPLE_CLASS_BEFORE = 
        "package com.example.old;\n\n" +
        "import java.util.List;\n" +
        "import java.util.ArrayList;\n\n" +
        "/**\n" +
        " * 简单用户服务\n" +
        " */\n" +
        "public class UserService {\n" +
        "    private List<String> users;\n" +
        "    \n" +
        "    public UserService() {\n" +
        "        this.users = new ArrayList<>();\n" +
        "    }\n" +
        "    \n" +
        "    public void addUser(String user) {\n" +
        "        this.users.add(user);\n" +
        "    }\n" +
        "}";

    /**
     * 简单的Java类（重构后）
     */
    public static final String SIMPLE_CLASS_AFTER = 
        "package com.example.new;\n\n" +
        "import java.util.List;\n" +
        "import java.util.LinkedList;\n\n" +
        "/**\n" +
        " * 改进的用户服务\n" +
        " * @version 2.0\n" +
        " */\n" +
        "public class UserService {\n" +
        "    private List<String> userList;\n" +
        "    \n" +
        "    public UserService() {\n" +
        "        this.userList = new LinkedList<>();\n" +
        "    }\n" +
        "    \n" +
        "    public boolean addUser(String user) {\n" +
        "        return this.userList.add(user);\n" +
        "    }\n" +
        "    \n" +
        "    public int getUserCount() {\n" +
        "        return this.userList.size();\n" +
        "    }\n" +
        "}";

    // ========== 块创建方法 ==========

    /**
     * 创建纯import块
     */
    public static DiffBlock createPureImportBlock() {
        return DiffBlock.builder()
                .contentFrom(STANDARD_IMPORTS)
                .contentTo(STANDARD_IMPORTS)
                .build();
    }

    /**
     * 创建纯注释块
     */
    public static DiffBlock createPureCommentBlock() {
        return DiffBlock.builder()
                .contentFrom(STANDARD_COMMENTS)
                .contentTo(STANDARD_COMMENTS)
                .build();
    }

    /**
     * 创建纯package块
     */
    public static DiffBlock createPurePackageBlock() {
        return DiffBlock.builder()
                .contentFrom(STANDARD_PACKAGE)
                .contentTo(STANDARD_PACKAGE)
                .build();
    }

    /**
     * 创建纯代码块
     */
    public static DiffBlock createPureCodeBlock() {
        return DiffBlock.builder()
                .contentFrom(STANDARD_CODE)
                .contentTo(STANDARD_CODE)
                .build();
    }

    /**
     * 创建混合噪音块
     */
    public static DiffBlock createMixedNoiseBlock() {
        return DiffBlock.builder()
                .contentFrom(STANDARD_MIXED)
                .contentTo(STANDARD_MIXED)
                .build();
    }

    /**
     * 创建空块
     */
    public static DiffBlock createEmptyBlock() {
        return DiffBlock.builder()
                .contentFrom(EMPTY_CONTENT)
                .contentTo(EMPTY_CONTENT)
                .build();
    }

    /**
     * 创建只有空格的块
     */
    public static DiffBlock createWhitespaceBlock() {
        return DiffBlock.builder()
                .contentFrom(WHITESPACE_ONLY)
                .contentTo(WHITESPACE_ONLY)
                .build();
    }

    /**
     * 创建Unicode内容块
     */
    public static DiffBlock createUnicodeBlock() {
        return DiffBlock.builder()
                .contentFrom(UNICODE_CONTENT)
                .contentTo(UNICODE_CONTENT)
                .build();
    }

    /**
     * 创建重构前的类块
     */
    public static DiffBlock createBeforeRefactorBlock() {
        return DiffBlock.builder()
                .contentFrom(SIMPLE_CLASS_BEFORE)
                .contentTo(SIMPLE_CLASS_BEFORE)
                .build();
    }

    /**
     * 创建重构后的类块
     */
    public static DiffBlock createAfterRefactorBlock() {
        return DiffBlock.builder()
                .contentFrom(SIMPLE_CLASS_AFTER)
                .contentTo(SIMPLE_CLASS_AFTER)
                .build();
    }

    // ========== 文件创建方法 ==========

    /**
     * 创建包含单个块的文件
     */
    public static DiffFile createSingleBlockFile(String relativePath, DiffBlock block) {
        return DiffFile.builder()
                .relativePath(relativePath)
                .blocks(Arrays.asList(block))
                .build();
    }

    /**
     * 创建包含多个块的文件
     */
    public static DiffFile createMultiBlockFile(String relativePath, List<DiffBlock> blocks) {
        return DiffFile.builder()
                .relativePath(relativePath)
                .blocks(blocks)
                .build();
    }

    /**
     * 创建标准的import文件
     */
    public static DiffFile createImportFile() {
        return createSingleBlockFile("ImportTest.java", createPureImportBlock());
    }

    /**
     * 创建标准的注释文件
     */
    public static DiffFile createCommentFile() {
        return createSingleBlockFile("CommentTest.java", createPureCommentBlock());
    }

    /**
     * 创建标准的代码文件
     */
    public static DiffFile createCodeFile() {
        return createSingleBlockFile("CodeTest.java", createPureCodeBlock());
    }

    /**
     * 创建标准的混合文件
     */
    public static DiffFile createMixedFile() {
        return createSingleBlockFile("MixedTest.java", createMixedNoiseBlock());
    }

    /**
     * 创建重构前的文件
     */
    public static DiffFile createBeforeFile() {
        return createSingleBlockFile("UserService.java", createBeforeRefactorBlock());
    }

    /**
     * 创建重构后的文件
     */
    public static DiffFile createAfterFile() {
        return createSingleBlockFile("UserService.java", createAfterRefactorBlock());
    }

    // ========== 验证方法 ==========

    /**
     * 验证内容是否为纯噪音
     */
    public static boolean isPureNoise(String content) {
        String filtered = CoverageUtils.filterCodeNoise(content);
        return filtered.trim().isEmpty();
    }

    /**
     * 验证内容是否包含代码
     */
    public static boolean containsCode(String content) {
        String filtered = CoverageUtils.filterCodeNoise(content);
        return !filtered.trim().isEmpty();
    }

    /**
     * 计算内容的原始行数
     */
    public static int countOriginalLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return content.split("\n").length;
    }

    /**
     * 计算内容的去噪行数
     */
    public static int countNoiseFreeLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        String filtered = CoverageUtils.filterCodeNoise(content);
        if (filtered.trim().isEmpty()) {
            return 0;
        }
        return filtered.split("\n").length;
    }

    /**
     * 获取内容的token数量
     */
    public static int countTokens(String content) {
        return CoverageUtils.tokenize(content, true).size();
    }

    /**
     * 获取内容的无噪音token数量
     */
    public static int countNoiseFreeTokens(String content) {
        return CoverageUtils.tokenize(content, true).size();
    }

    // ========== 测试场景工厂 ==========

    /**
     * 创建所有标准测试场景的文件列表
     */
    public static List<DiffFile> createAllStandardScenarios() {
        return Arrays.asList(
            createImportFile(),
            createCommentFile(),
            createCodeFile(),
            createMixedFile()
        );
    }

    /**
     * 创建边界情况测试场景
     */
    public static List<DiffFile> createBoundaryScenarios() {
        return Arrays.asList(
            createSingleBlockFile("Empty.java", createEmptyBlock()),
            createSingleBlockFile("Whitespace.java", createWhitespaceBlock()),
            createSingleBlockFile("Unicode.java", createUnicodeBlock())
        );
    }

    /**
     * 创建重构测试场景
     */
    public static List<DiffFile> createRefactoringScenarios() {
        return Arrays.asList(
            createBeforeFile(),
            createAfterFile()
        );
    }

    // ========== 调试辅助方法 ==========

    /**
     * 打印内容的分析信息
     */
    public static void printContentAnalysis(String contentType, String content) {
        System.out.println("=== " + contentType + " 分析 ===");
        System.out.println("内容长度: " + content.length());
        System.out.println("原始行数: " + countOriginalLines(content));
        System.out.println("去噪行数: " + countNoiseFreeLines(content));
        System.out.println("token数量: " + countTokens(content));
        System.out.println("是否纯噪音: " + isPureNoise(content));
        System.out.println("是否包含代码: " + containsCode(content));
        System.out.println("内容预览:\n" + content);
        System.out.println("过滤后内容:\n" + CoverageUtils.filterCodeNoise(content));
        System.out.println("---");
    }

    /**
     * 比较两个内容的相似性
     */
    public static void compareContents(String label1, String content1, String label2, String content2) {
        System.out.println("=== 内容比较: " + label1 + " vs " + label2 + " ===");
        
        List<String> tokens1 = CoverageUtils.tokenize(content1, true);
        List<String> tokens2 = CoverageUtils.tokenize(content2, true);
        
        double similarity = CoverageUtils.recallSimilarity(tokens1, tokens2);
        
        System.out.println(label1 + " token数: " + tokens1.size());
        System.out.println(label2 + " token数: " + tokens2.size());
        System.out.println("相似度: " + similarity);
        System.out.println("---");
    }
}
