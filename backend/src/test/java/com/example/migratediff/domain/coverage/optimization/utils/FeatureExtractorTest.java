package com.example.migratediff.domain.coverage.optimization.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 特征提取器单元测试
 */
class FeatureExtractorTest {
    
    @Test
    @DisplayName("测试方法签名提取")
    void testExtractMethodSignatures() {
        String code = "public class UserService {\n" +
                    "    public void setName(String name) {\n" +  // 第2行
                    "        this.name = name;\n" +
                    "    }\n" +
                    "    \n" +
                    "    private String getName() {\n" +           // 第6行
                    "        return name;\n" +
                    "    }\n" +
                    "    \n" +
                    "    public static void printMessage(String msg) {\n" +  // 第10行
                    "        System.out.println(msg);\n" +
                    "    }\n" +
                    "}";
        
        Map<String, List<Integer>> methodSignatures = FeatureExtractor.extractMethodSignatures(code);
        
        assertNotNull(methodSignatures);
        assertEquals(3, methodSignatures.size());
        
        // 检查方法签名和位置
        assertTrue(methodSignatures.containsKey("setName"));
        assertTrue(methodSignatures.containsKey("getName"));
        assertTrue(methodSignatures.containsKey("printMessage"));
        
        List<Integer> setNamePositions = methodSignatures.get("setName");
        assertEquals(1, setNamePositions.size());
        assertEquals(2, setNamePositions.get(0).intValue()); // 第2行
        
        List<Integer> getNamePositions = methodSignatures.get("getName");
        assertEquals(1, getNamePositions.size());
        assertEquals(6, getNamePositions.get(0).intValue()); // 第6行
        
        List<Integer> printMessagePositions = methodSignatures.get("printMessage");
        assertEquals(1, printMessagePositions.size());
        assertEquals(10, printMessagePositions.get(0).intValue()); // 第10行
    }
    
    @Test
    @DisplayName("测试类签名提取")
    void testExtractClassSignatures() {
        String code = "package com.example;\n" +
                    "\n" +
                    "public class UserService {\n" +           // 第3行
                    "    // class content\n" +
                    "}\n" +
                    "\n" +
                    "interface UserRepository {\n" +            // 第7行
                    "    List<User> findAll();\n" +
                    "}\n" +
                    "\n" +
                    "enum UserType {\n" +                     // 第11行
                    "    ADMIN, USER, GUEST\n" +
                    "}";
        
        Map<String, List<Integer>> classSignatures = FeatureExtractor.extractClassSignatures(code);
        
        assertNotNull(classSignatures);
        assertEquals(3, classSignatures.size());
        
        assertTrue(classSignatures.containsKey("UserService"));
        assertTrue(classSignatures.containsKey("UserRepository"));
        assertTrue(classSignatures.containsKey("UserType"));
        
        assertEquals(3, classSignatures.get("UserService").get(0).intValue());   // 第3行
        assertEquals(7, classSignatures.get("UserRepository").get(0).intValue()); // 第7行
        assertEquals(11, classSignatures.get("UserType").get(0).intValue());    // 第11行
    }
    
    @Test
    @DisplayName("测试独特标识符提取")
    void testExtractUniqueIdentifiers() {
        String code = "public class Config {\n" +
                    "    private static final int MAX_RETRY_COUNT = 3;\n" +  // 常量
                    "    private static final String DEFAULT_NAME = \"DEFAULT\";\n" + // 常量
                    "    private UserService userService;           // 普通变量，但不够独特\n" +
                    "    private UserAccountManager accountManager;   // 独特的变量名\n" +
                    "    \n" +
                    "    public void processUserAccount() {\n" +  // 独特的方法名
                    "        // implementation\n" +
                    "    }\n" +
                    "    \n" +
                    "    public void getName() {\n" +             // 普通方法名，不够独特
                    "        return DEFAULT_NAME;\n" +
                    "    }\n" +
                    "}";
        
        Set<String> uniqueIdentifiers = FeatureExtractor.extractUniqueIdentifiers(code);
        
        assertNotNull(uniqueIdentifiers);
        
        // 应该包含常量
        assertTrue(uniqueIdentifiers.contains("MAX_RETRY_COUNT"));
        assertTrue(uniqueIdentifiers.contains("DEFAULT_NAME"));
        
        // 应该包含独特的变量名
        assertTrue(uniqueIdentifiers.contains("accountManager"));
        assertTrue(uniqueIdentifiers.contains("UserAccountManager"));
        
        // 应该包含独特的方法名
        assertTrue(uniqueIdentifiers.contains("processUserAccount"));
        
        // 可能不包含常见的方法名
        assertFalse(uniqueIdentifiers.contains("getName"));
    }
    
    @Test
    @DisplayName("测试语义块分割")
    void testSegmentSemanticBlocks() {
        String code = "public class UserService {\n" +
                    "    private String name;\n" +
                    "    \n" +
                    "    public void setName(String name) {\n" +
                    "        this.name = name;\n" +
                    "    }\n" +
                    "    \n" +
                    "    public String getName() {\n" +
                    "        return name;\n" +
                    "    }\n" +
                    "}\n" +
                    "\n" +
                    "class HelperClass {\n" +
                    "    public void help() {\n" +
                    "        // help method\n" +
                    "    }\n" +
                    "}";
        
        List<FeatureExtractor.CodeBlock> blocks = FeatureExtractor.segmentSemanticBlocks(code);
        
        assertNotNull(blocks);
        assertEquals(2, blocks.size()); // UserService类和HelperClass类
        
        // 检查UserService块
        FeatureExtractor.CodeBlock userServiceBlock = blocks.get(0);
        assertEquals("public class UserService {", userServiceBlock.getSignature());
        assertEquals(1, userServiceBlock.getStartLine());
        assertEquals(11, userServiceBlock.getEndLine());
        assertEquals(11, userServiceBlock.getLength());
        assertTrue(userServiceBlock.getContent().contains("setName"));
        assertTrue(userServiceBlock.getContent().contains("getName"));
        
        // 检查HelperClass块
        FeatureExtractor.CodeBlock helperBlock = blocks.get(1);
        assertEquals("class HelperClass {", helperBlock.getSignature());
        assertEquals(13, helperBlock.getStartLine());
        assertEquals(17, helperBlock.getEndLine());
        assertEquals(5, helperBlock.getLength());
        assertTrue(helperBlock.getContent().contains("help"));
    }
    
    @Test
    @DisplayName("测试查询特征提取")
    void testExtractQueryFeatures() {
        String query = "public void processUserAccount(UserAccount account) {\n" +
                      "    String USER_STATUS = \"ACTIVE\";\n" +
                      "    account.setStatus(USER_STATUS);\n" +
                      "}";
        
        FeatureExtractor.QueryFeatures features = FeatureExtractor.extractQueryFeatures(query);
        
        assertNotNull(features);
        assertFalse(features.isEmpty());
        
        // 检查Token
        assertTrue(features.getTokens().contains("processUserAccount"));
        assertTrue(features.getTokens().contains("UserAccount"));
        assertTrue(features.getTokens().contains("USER_STATUS"));
        assertTrue(features.getTokens().contains("setStatus"));
        assertTrue(features.getTokens().contains("ACTIVE"));
        
        // 检查方法名
        assertTrue(features.getMethodNames().contains("processUserAccount"));
        assertTrue(features.getMethodNames().contains("setStatus"));
        
        // 检查常量
        assertTrue(features.getConstants().contains("USER_STATUS"));
        
        // 检查查询长度
        assertTrue(features.getQueryLength() > 0);
        assertEquals(query.length(), features.getQueryLength());
    }
    
    @Test
    @DisplayName("测试空输入处理")
    void testEmptyInput() {
        // 测试空字符串
        Map<String, List<Integer>> emptyMethods = FeatureExtractor.extractMethodSignatures("");
        assertTrue(emptyMethods.isEmpty());
        
        Map<String, List<Integer>> emptyClasses = FeatureExtractor.extractClassSignatures("");
        assertTrue(emptyClasses.isEmpty());
        
        Set<String> emptyIdentifiers = FeatureExtractor.extractUniqueIdentifiers("");
        assertTrue(emptyIdentifiers.isEmpty());
        
        List<FeatureExtractor.CodeBlock> emptyBlocks = FeatureExtractor.segmentSemanticBlocks("");
        assertTrue(emptyBlocks.isEmpty());
        
        FeatureExtractor.QueryFeatures emptyFeatures = FeatureExtractor.extractQueryFeatures("");
        assertTrue(emptyFeatures.isEmpty());
        assertEquals(0, emptyFeatures.getQueryLength());
    }
    
    @Test
    @DisplayName("测试null输入处理")
    void testNullInput() {
        // 测试null输入
        Map<String, List<Integer>> nullMethods = FeatureExtractor.extractMethodSignatures(null);
        assertTrue(nullMethods.isEmpty());
        
        Map<String, List<Integer>> nullClasses = FeatureExtractor.extractClassSignatures(null);
        assertTrue(nullClasses.isEmpty());
        
        Set<String> nullIdentifiers = FeatureExtractor.extractUniqueIdentifiers(null);
        assertTrue(nullIdentifiers.isEmpty());
        
        List<FeatureExtractor.CodeBlock> nullBlocks = FeatureExtractor.segmentSemanticBlocks(null);
        assertTrue(nullBlocks.isEmpty());
        
        FeatureExtractor.QueryFeatures nullFeatures = FeatureExtractor.extractQueryFeatures(null);
        assertTrue(nullFeatures.isEmpty());
        assertEquals(0, nullFeatures.getQueryLength());
    }
    
    @Test
    @DisplayName("测试复杂方法签名")
    void testComplexMethodSignatures() {
        String code = "public class ComplexClass {\n" +
                    "    @Override\n" +
                    "    public <T> List<T> genericMethod(String param1, int param2) throws IOException {\n" +  // 第3行
                    "        return null;\n" +
                    "    }\n" +
                    "    \n" +
                    "    private static final synchronized void complexMethod() {\n" +  // 第7行
                    "        // synchronized static method\n" +
                    "    }\n" +
                    "}";
        
        Map<String, List<Integer>> methodSignatures = FeatureExtractor.extractMethodSignatures(code);
        
        assertNotNull(methodSignatures);
        assertEquals(2, methodSignatures.size());
        
        assertTrue(methodSignatures.containsKey("genericMethod"));
        assertTrue(methodSignatures.containsKey("complexMethod"));
        
        assertEquals(3, methodSignatures.get("genericMethod").get(0).intValue());   // 第3行
        assertEquals(7, methodSignatures.get("complexMethod").get(0).intValue());   // 第7行
    }
    
    @Test
    @DisplayName("测试嵌套代码块")
    void testNestedCodeBlocks() {
        String code = "public class OuterClass {\n" +
                    "    public void outerMethod() {\n" +
                    "        if (true) {\n" +
                    "            for (int i = 0; i < 10; i++) {\n" +
                    "                System.out.println(i);\n" +
                    "            }\n" +
                    "        }\n" +
                    "    }\n" +
                    "}";
        
        List<FeatureExtractor.CodeBlock> blocks = FeatureExtractor.segmentSemanticBlocks(code);
        
        assertNotNull(blocks);
        assertEquals(1, blocks.size()); // 只有一个类块
        
        FeatureExtractor.CodeBlock block = blocks.get(0);
        assertEquals("public class OuterClass {", block.getSignature());
        assertEquals(1, block.getStartLine());
        assertEquals(9, block.getEndLine());
        
        // 验证块内容包含所有嵌套代码
        String content = block.getContent();
        assertTrue(content.contains("outerMethod"));
        assertTrue(content.contains("if (true)"));
        assertTrue(content.contains("for (int i = 0; i < 10; i++)"));
        assertTrue(content.contains("System.out.println(i)"));
    }
    
    @Test
    @DisplayName("测试特征提取的边界情况")
    void testEdgeCases() {
        // 只有声明的代码
        String declarationOnly = "interface EmptyInterface {}";
        Map<String, List<Integer>> interfaces = FeatureExtractor.extractClassSignatures(declarationOnly);
        assertEquals(1, interfaces.size());
        assertTrue(interfaces.containsKey("EmptyInterface"));
        
        // 没有方法的类
        String noMethods = "public class NoMethods {\n" +
                          "    private int value;\n" +
                          "}";
        Map<String, List<Integer>> noMethodSignatures = FeatureExtractor.extractMethodSignatures(noMethods);
        assertTrue(noMethodSignatures.isEmpty());
        
        // 只有常量的类
        String onlyConstants = "public class Constants {\n" +
                             "    public static final String NAME = \"TEST\";\n" +
                             "    public static final int COUNT = 100;\n" +
                             "}";
        Set<String> constants = FeatureExtractor.extractUniqueIdentifiers(onlyConstants);
        assertTrue(constants.contains("NAME"));
        assertTrue(constants.contains("COUNT"));
    }
}
