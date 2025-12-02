package com.example.migratediff.shared.utils;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CoverageUtilsTest {

    @Test
    public void testTokenizeWithoutFilter() {
        String content = "import java.util.List;\n" +
                       "// This is a comment\n" +
                       "public class Test {\n" +
                       "    private String name;\n" +
                       "}";
        
        List<String> tokens = CoverageUtils.tokenize(content);
        
        // 默认不过滤，应该包含所有内容
        assertFalse(tokens.isEmpty());
        assertTrue(tokens.contains("import"));
        assertTrue(tokens.contains("java.util.List;"));
        assertTrue(tokens.contains("//"));
        assertTrue(tokens.contains("class"));
    }

    @Test
    public void testTokenizeWithFilter() {
        String content = "import java.util.List;\n" +
                       "/* Multi-line comment */\n" +
                       "// Single line comment\n" +
                       "import java.util.Map;\n" +
                       "/**\n" +
                       " * JavaDoc comment\n" +
                       " */\n" +
                       "public class Test {\n" +
                       "    private String name;\n" +
                       "}";
        
        List<String> tokens = CoverageUtils.tokenize(content, true);
        
        // 打印tokens来调试
        System.out.println("Filtered tokens: " + tokens);
        
        // 过滤后，应该不包含import和注释
        assertFalse(tokens.isEmpty());
        
        // 检查核心代码是否保留
        assertTrue(tokens.contains("public"));
        assertTrue(tokens.contains("class"));
        assertTrue(tokens.contains("Test"));
        assertTrue(tokens.contains("private"));
        assertTrue(tokens.contains("String"));
        assertTrue(tokens.contains("name;"));
    }

    @Test
    public void testFilterCodeNoise() throws Exception {
        String content = "import java.util.List;\n" +
                       "// Comment\n" +
                       "/* Multi\n" +
                       " * line\n" +
                       " * comment\n" +
                       " */\n" +
                       "public class Test {}\n";
        
        // 使用反射调用私有方法进行测试
        java.lang.reflect.Method method = CoverageUtils.class.getDeclaredMethod("filterCodeNoise", String.class);
        method.setAccessible(true);
        String result = (String) method.invoke(null, content);
        
        System.out.println("Filtered content: " + result);
        
        // 验证import和注释被过滤
        assertFalse(result.contains("import"));
        assertFalse(result.contains("//"));
        assertFalse(result.contains("/*"));
        assertFalse(result.contains("*"));
        
        // 验证核心代码保留
        assertTrue(result.contains("public class Test"));
    }

    @Test
    public void testEmptyContent() {
        List<String> tokens1 = CoverageUtils.tokenize("");
        List<String> tokens2 = CoverageUtils.tokenize("", true);
        
        assertTrue(tokens1.isEmpty());
        assertTrue(tokens2.isEmpty());
    }

    @Test
    public void testNullContent() {
        List<String> tokens1 = CoverageUtils.tokenize(null);
        List<String> tokens2 = CoverageUtils.tokenize(null, true);
        
        assertTrue(tokens1.isEmpty());
        assertTrue(tokens2.isEmpty());
    }
}
