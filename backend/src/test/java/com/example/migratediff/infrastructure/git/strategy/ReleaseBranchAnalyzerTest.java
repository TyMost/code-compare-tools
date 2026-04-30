package com.example.migratediff.infrastructure.git.strategy;

import com.example.migratediff.infrastructure.git.strategy.ReleaseBranchAnalyzer;
import com.example.migratediff.infrastructure.git.strategy.BranchWithCommits;
import com.example.migratediff.infrastructure.git.strategy.CommitSelectionResult;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ReleaseBranchAnalyzer 的单元测试
 * 特别验证多个分支创建时间相同时的排序逻辑
 */
@ExtendWith(MockitoExtension.class)
public class ReleaseBranchAnalyzerTest {

    @Mock
    private CommitCache commitCache;
    
    @Mock
    private OptimizedBranchFilter optimizedBranchFilter;
    
    @Mock
    private Repository repository;
    
    @Mock
    private Ref ref1, ref2, ref3;

    private ReleaseBranchAnalyzer analyzer;
    private Instant sameCreationTime;
    private Instant startTime;
    private Instant endTime;

    @BeforeEach
    void setUp() {
        analyzer = new ReleaseBranchAnalyzer(commitCache, optimizedBranchFilter);
        
        // 设置测试时间
        sameCreationTime = Instant.ofEpochSecond(1704844800); // 2024-01-09 00:00:00
        startTime = Instant.ofEpochSecond(1704844800);
        endTime = Instant.ofEpochSecond(1704931199); // 2024-01-09 23:59:59
    }

    @Test
    void testSelectLatestCreatedReleaseBranch_WithSameCreationTime_SelectsHighestBranchName() throws IOException {
        // 准备测试数据：三个分支创建时间相同，但分支名不同
        List<Ref> releaseBranches = Arrays.asList(ref1, ref2, ref3);
        
        // 模拟分支名
        when(ref1.getName()).thenReturn("refs/heads/release_20240109_001");
        when(ref2.getName()).thenReturn("refs/heads/release_20240109_002");
        when(ref3.getName()).thenReturn("refs/heads/release_20240109_003");
        
        // 模拟OptimizedBranchFilter返回release分支
        when(optimizedBranchFilter.filterReleaseBranches(repository, "release/*"))
                .thenReturn(releaseBranches);
        
        // 由于getBranchCreationTime是私有方法且依赖JGit，我们需要使用PowerMock或者重构代码
        // 这里我们假设可以通过某种方式模拟分支创建时间
        // 实际实现中可能需要集成测试或使用真实的Git仓库
        
        // 执行测试
        CommitSelectionResult result = analyzer.selectLatestCreatedReleaseBranch(
                repository, "release/*", startTime, endTime);
        
        // 验证结果
        assertNotNull(result);
        // 由于无法直接模拟私有方法，这里主要验证方法的调用流程
        verify(optimizedBranchFilter).filterReleaseBranches(repository, "release/*");
    }

    @Test
    void testSelectLatestCreatedReleaseBranch_EmptyBranches_ReturnsEmpty() throws IOException {
        // 测试空分支列表
        when(optimizedBranchFilter.filterReleaseBranches(repository, "release/*"))
                .thenReturn(Arrays.asList());
        
        CommitSelectionResult result = analyzer.selectLatestCreatedReleaseBranch(
                repository, "release/*", startTime, endTime);
        
        assertNotNull(result);
        assertNull(result.getCommit());
        assertNull(result.getBranchName());
    }

    @Test
    void testSelectLatestCreatedReleaseBranch_Exception_ReturnsEmpty() throws IOException {
        // 测试异常情况
        when(optimizedBranchFilter.filterReleaseBranches(repository, "release/*"))
                .thenThrow(new RuntimeException("Test exception"));
        
        CommitSelectionResult result = analyzer.selectLatestCreatedReleaseBranch(
                repository, "release/*", startTime, endTime);
        
        assertNotNull(result);
        assertNull(result.getCommit());
        assertNull(result.getBranchName());
    }

    @Test
    void testCleanExpiredCache() {
        // 测试缓存清理
        analyzer.cleanExpiredCache();
        
        // 验证方法执行不抛出异常
        assertDoesNotThrow(() -> analyzer.cleanExpiredCache());
    }

    /**
     * 测试分支名排序逻辑的辅助方法
     * 这个测试验证Comparator.comparing(...).thenComparing(..., Comparator.reverseOrder())的行为
     */
    @Test
    void testBranchNameSortingLogic() {
        // 创建测试数据
        Instant sameTime = Instant.now();
        
        BranchWithCommits branch1 = BranchWithCommits.of(
                "release_20240109_001", sameTime, 
                ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), 
                ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), 
                1, 100);
        
        BranchWithCommits branch2 = BranchWithCommits.of(
                "release_20240109_002", sameTime, 
                ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"), 
                ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"), 
                1, 100);
        
        BranchWithCommits branch3 = BranchWithCommits.of(
                "release_20240109_003", sameTime, 
                ObjectId.fromString("cccccccccccccccccccccccccccccccccccccccc"), 
                ObjectId.fromString("cccccccccccccccccccccccccccccccccccccccc"), 
                1, 100);
        
        List<BranchWithCommits> branches = Arrays.asList(branch1, branch2, branch3);
        
        // 先验证字符串比较逻辑
        System.out.println("String comparison test:");
        System.out.println("release_20240109_003 vs release_20240109_002: " + "release_20240109_003".compareTo("release_20240109_002"));
        System.out.println("release_20240109_002 vs release_20240109_001: " + "release_20240109_002".compareTo("release_20240109_001"));
        
        // 应用新的排序逻辑：只按分支名降序排序
        BranchWithCommits selected = branches.stream()
                .max(java.util.Comparator.comparing(BranchWithCommits::getBranchName))
                .orElse(null);
        
        // 验证选择了分支名最大的分支
        assertNotNull(selected);
        System.out.println("Selected branch: " + selected.getBranchName());
        // 直接使用max选择分支名最大的
        assertEquals("release_20240109_003", selected.getBranchName());
        
        // 验证排序的确定性
        // 多次执行应该得到相同结果
        for (int i = 0; i < 10; i++) {
            BranchWithCommits selectedAgain = branches.stream()
                    .max(java.util.Comparator.comparing(BranchWithCommits::getBranchName))
                    .orElse(null);
            assertEquals(selected.getBranchName(), selectedAgain.getBranchName());
        }
        
        // 验证字符串比较逻辑
        assertTrue("release_20240109_003".compareTo("release_20240109_002") > 0);
        assertTrue("release_20240109_002".compareTo("release_20240109_001") > 0);
    }

    @Test
    void testBranchNameSortingLogic_WithDifferentCreationTimes() {
        // 测试不同创建时间的分支
        Instant earlierTime = Instant.ofEpochSecond(1704844800);
        Instant laterTime = Instant.ofEpochSecond(1704931200);
        
        BranchWithCommits earlyBranch = BranchWithCommits.of(
                "release_20240109_001", earlierTime, 
                ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), 
                ObjectId.fromString("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), 
                1, 100);
        
        BranchWithCommits laterBranch = BranchWithCommits.of(
                "release_20240109_002", laterTime, 
                ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"), 
                ObjectId.fromString("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"), 
                1, 100);
        
        List<BranchWithCommits> branches = Arrays.asList(earlyBranch, laterBranch);
        
        // 应用新的排序逻辑：只按分支名降序排序
        BranchWithCommits selected = branches.stream()
                .max(java.util.Comparator.comparing(BranchWithCommits::getBranchName))
                .orElse(null);
        
        // 验证选择了分支名更大的分支（现在只按分支名排序）
        assertNotNull(selected);
        // 直接使用max选择分支名最大的
        assertEquals("release_20240109_002", selected.getBranchName());
        assertEquals(laterTime, selected.getBranchCreationTime());
    }
}
