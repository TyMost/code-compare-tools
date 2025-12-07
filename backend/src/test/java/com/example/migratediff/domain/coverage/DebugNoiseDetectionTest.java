package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.shared.utils.CoverageUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 调试噪音检测功能的测试
 */
@SpringBootTest
@TestPropertySource(properties = {
    "coverage.filter.noise.enabled=true",
    "coverage.skip.unmatched.noise.blocks=true"
})
class DebugNoiseDetectionTest {

    @Autowired
    private CoverageEvaluator coverageEvaluator;

    @Test
    void testFilterCodeNoiseDirectly() {
        // 直接测试filterCodeNoise方法
        String importOnly = "import java.util.Map;";
        String filtered = CoverageUtils.filterCodeNoise(importOnly);
        
        System.out.println("=== 直接测试filterCodeNoise ===");
        System.out.println("原始内容: '" + importOnly + "'");
        System.out.println("过滤后内容: '" + filtered + "'");
        System.out.println("过滤后是否为空: " + (filtered.trim().isEmpty()));
        
        assertTrue(filtered.trim().isEmpty(), "import语句应该被完全过滤掉");
    }

    @Test
    void testPureNoiseBlockDetection() {
        // 测试纯import块的检测
        DiffBlock importBlock = DiffBlock.builder()
                .contentFrom("import java.util.Map;")
                .contentTo("import java.util.Map;")
                .build();

        // 使用反射调用私有方法来测试
        try {
            java.lang.reflect.Method method = CoverageEvaluator.class.getDeclaredMethod("isPureNoiseBlock", DiffBlock.class);
            method.setAccessible(true);
            boolean isPureNoise = (Boolean) method.invoke(coverageEvaluator, importBlock);
            
            System.out.println("=== 纯噪音块检测测试 ===");
            System.out.println("import块是否被识别为纯噪音: " + isPureNoise);
            
            assertTrue(isPureNoise, "纯import块应该被识别为纯噪音");
        } catch (Exception e) {
            fail("反射调用失败: " + e.getMessage());
        }
    }

    @Test
    void testMixedContentBlockDetection() {
        // 测试混合内容块的检测
        DiffBlock mixedBlock = DiffBlock.builder()
                .contentFrom("import java.util.Map;\npublic class Test {}")
                .contentTo("import java.util.Map;\npublic class Test {}")
                .build();

        try {
            java.lang.reflect.Method method = CoverageEvaluator.class.getDeclaredMethod("isPureNoiseBlock", DiffBlock.class);
            method.setAccessible(true);
            boolean isPureNoise = (Boolean) method.invoke(coverageEvaluator, mixedBlock);
            
            System.out.println("=== 混合内容块检测测试 ===");
            System.out.println("混合块是否被识别为纯噪音: " + isPureNoise);
            
            assertFalse(isPureNoise, "混合内容块不应该被识别为纯噪音");
        } catch (Exception e) {
            fail("反射调用失败: " + e.getMessage());
        }
    }

    @Test
    void testCommentOnlyBlockDetection() {
        // 测试纯注释块的检测
        DiffBlock commentBlock = DiffBlock.builder()
                .contentFrom("// 这是注释\n/** JavaDoc */")
                .contentTo("// 这是注释\n/** JavaDoc */")
                .build();

        try {
            java.lang.reflect.Method method = CoverageEvaluator.class.getDeclaredMethod("isPureNoiseBlock", DiffBlock.class);
            method.setAccessible(true);
            boolean isPureNoise = (Boolean) method.invoke(coverageEvaluator, commentBlock);
            
            System.out.println("=== 纯注释块检测测试 ===");
            System.out.println("注释块是否被识别为纯噪音: " + isPureNoise);
            
            assertTrue(isPureNoise, "纯注释块应该被识别为纯噪音");
        } catch (Exception e) {
            fail("反射调用失败: " + e.getMessage());
        }
    }
}
