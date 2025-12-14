package com.example.migratediff.domain.coverage.optimization;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffType;
import com.example.migratediff.infrastructure.git.GitRepositoryHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FullFileContextStrategy发现模式单元测试
 */
@ExtendWith(MockitoExtension.class)
class FullFileContextStrategyDiscoveryTest {
    
    @Mock
    private GitRepositoryHelper gitRepositoryHelper;
    
    @InjectMocks
    private FullFileContextStrategy strategy;
    
    private DiffBlock originBlock;
    private OptimizationContext context;
    
    @BeforeEach
    void setUp() {
        // 创建测试用的未匹配DiffBlock
        originBlock = DiffBlock.builder()
                .startLineFrom(10)
                .endLineFrom(20)
                .contentFrom("public class TestService {\n    private String name;\n    public void process() {\n        // business logic\n    }\n}")
                .type(DiffType.MODIFY)
                .build();
        
        // 创建包含Git信息的上下文（发现模式）
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("targetCommitHash", "abc123");
        additionalData.put("targetRepoPath", "/path/to/repo");
        
        DiffFile originFile = new DiffFile();
        originFile.setRelativePath("src/main/java/TestService.java");
        
        DiffFile targetFile = new DiffFile();
        targetFile.setRelativePath("src/main/java/TestService.java");
        
        context = OptimizationContext.builder()
                .originBlock(originBlock)
                .targetBlock(null) // 发现模式：targetBlock为null
                .originFile(originFile)
                .targetFile(targetFile)
                .currentSimilarity(0.0) // 未匹配块相似度为0
                .additionalData(additionalData)
                .build();
    }
    
    @Test
    void testSupportsDiscoveryMode() {
        // 测试发现模式支持条件
        assertTrue(strategy.supports(context), "应该支持发现模式");
        assertTrue(context.isDiscoveryMode(), "应该识别为发现模式");
    }
    
    @Test
    void testSupportsDiscoveryModeDisabled() {
        // 测试发现模式禁用时不支持
        // 注意：由于配置是私有的，这里通过创建无效上下文来测试
        OptimizationContext invalidContext = OptimizationContext.builder()
                .originBlock(originBlock)
                .targetBlock(null)
                .additionalData(new HashMap<>()) // 空的additionalData
                .build();
        
        assertFalse(strategy.supports(invalidContext), "缺少Git信息时不应支持发现模式");
    }
    
    @Test
    void testDiscoverMatchWhenGitAccessFails() {
        // 模拟Git访问失败的情况
        try {
            when(gitRepositoryHelper.openRepository(any()))
                    .thenThrow(new RuntimeException("Git access failed"));
        } catch (Exception e) {
            // 忽略mock设置异常
        }
        
        OptimizationResult result = strategy.optimize(context);
        
        // 应该返回优化失败结果
        assertFalse(result.isOptimized(), "Git访问失败时应该返回未优化");
        assertTrue(result.getReason().contains("发现模式搜索失败"));
    }
    
    @Test
    void testDiscoverMatchWithShortContent() {
        // 测试内容过短时的情况
        DiffBlock shortBlock = DiffBlock.builder()
                .startLineFrom(1)
                .endLineFrom(1)
                .contentFrom("short")
                .type(DiffType.MODIFY)
                .build();
        
        OptimizationContext shortContext = OptimizationContext.builder()
                .originBlock(shortBlock)
                .targetBlock(null)
                .additionalData(context.getAdditionalData())
                .originFile(context.getOriginFile())
                .targetFile(context.getTargetFile())
                .build();
        
        OptimizationResult result = strategy.optimize(shortContext);
        
        // 应该因为内容过短而跳过
        assertFalse(result.isOptimized(), "内容过短时应该跳过发现模式");
        assertTrue(result.getReason().contains("内容长度不足"));
    }
    
    @Test
    void testGetStrategyName() {
        assertEquals("FULL_FILE_CONTEXT", strategy.getStrategyName());
    }
    
    @Test
    void testGetPriority() {
        assertEquals(2, strategy.getPriority());
    }
    
    @Test
    void testDiscoveryModeConfiguration() {
        // 验证发现模式配置参数的默认值
        // 注意：这些是私有字段，我们通过行为来验证
        
        // 创建一个足够长的内容块
        DiffBlock longBlock = DiffBlock.builder()
                .startLineFrom(1)
                .endLineFrom(5)
                .contentFrom("public class LongEnoughService {\n    private String longContent;\n    " +
                        "public void processLongContent() {\n        // This is a long enough content " +
                        "to pass the minimum length check\n    }\n}")
                .type(DiffType.MODIFY)
                .build();
        
        OptimizationContext longContext = OptimizationContext.builder()
                .originBlock(longBlock)
                .targetBlock(null)
                .additionalData(context.getAdditionalData())
                .originFile(context.getOriginFile())
                .targetFile(context.getTargetFile())
                .build();
        
        // 如果Git访问成功，应该能够进入发现逻辑
        // 这里主要验证配置不会导致异常
        assertDoesNotThrow(() -> {
            try {
                strategy.optimize(longContext);
            } catch (Exception e) {
                // 忽略测试中的Git相关异常，我们主要验证配置不会导致编译错误
                fail("不应该抛出异常: " + e.getMessage());
            }
        });
    }
}
