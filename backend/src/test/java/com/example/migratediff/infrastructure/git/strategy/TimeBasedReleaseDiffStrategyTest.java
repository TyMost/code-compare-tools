package com.example.migratediff.infrastructure.git.strategy;

import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.domain.repo.RepoBranch;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.domain.repo.RepoPath;
import com.example.migratediff.domain.repo.RepoType;
import com.example.migratediff.infrastructure.git.GitDiffParser;
import com.example.migratediff.infrastructure.git.GitRepositoryHelper;
import com.example.migratediff.infrastructure.config.GitScanProperties;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TimeBasedReleaseDiffStrategy 单元测试
 */
@SpringBootTest
class TimeBasedReleaseDiffStrategyTest {

    @Mock
    private GitRepositoryHelper gitRepositoryHelper;

    @Mock
    private GitDiffParser gitDiffParser;

    @Mock
    private GitScanProperties gitScanProperties;

    @Mock
    private Repository repository;

    private TimeBasedReleaseDiffStrategy strategy;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 设置默认的配置值
        when(gitScanProperties.getMainBranches()).thenReturn(Arrays.asList("master"));
        when(gitScanProperties.getReleasePattern()).thenReturn("release_*");
        
        strategy = new TimeBasedReleaseDiffStrategy(gitRepositoryHelper, gitDiffParser, gitScanProperties);
    }

    @Test
    void testScan_WithValidConfig_ShouldReturnDiffSummary() {
        // 准备测试数据
        RepoConfig repoConfig = createValidRepoConfig();
        
        try {
            when(gitRepositoryHelper.openRepository(any(RepoConfig.class)))
                    .thenReturn(repository);
            
            when(gitRepositoryHelper.prepareTreeIterator(any(Repository.class), any()))
                    .thenReturn(null);
            
            when(gitRepositoryHelper.createDiffFormatter(any(Repository.class)))
                    .thenReturn(null);
            
            when(gitDiffParser.parse(any(), any(), any(), any(RepoConfig.class)))
                    .thenReturn(java.util.Collections.emptyList());

            // 执行测试
            DiffSummary result = strategy.scan(repoConfig);

            // 验证结果
            assertNotNull(result);
            assertEquals(repoConfig, result.getRepoConfig());
            
            // 验证方法调用
            verify(gitRepositoryHelper).openRepository(repoConfig);
            verify(gitRepositoryHelper).closeRepository(repository);
        } catch (Exception e) {
            fail("Test should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testScan_WithInvalidConfig_ShouldReturnEmptyDiff() {
        // 测试无效配置
        RepoConfig invalidConfig = new RepoConfig();
        
        // 执行测试
        DiffSummary result = strategy.scan(invalidConfig);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.getDiffFiles().isEmpty());
        
        // 验证没有调用 Git 相关方法
        verifyNoInteractions(gitRepositoryHelper);
    }

    @Test
    void testScan_WithMissingTimeWindow_ShouldReturnEmptyDiff() throws IOException {
        // 测试缺少时间窗口的配置
        RepoConfig repoConfig = new RepoConfig();
        RepoPath repoPath = RepoPath.builder()
                .absolutePath(tempDir.toString())
                .type(RepoType.ORACLE)
                .build();
        repoConfig.setRepoPath(repoPath);
        
        RepoBranch branchFrom = new RepoBranch();
        branchFrom.setName("test");
        repoConfig.setBranchFrom(branchFrom);
        
        RepoBranch branchTo = new RepoBranch();
        branchTo.setName("test");
        repoConfig.setBranchTo(branchTo);

        // 执行测试
        DiffSummary result = strategy.scan(repoConfig);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.getDiffFiles().isEmpty());
        
        // 验证没有调用 Git 相关方法
        verifyNoInteractions(gitRepositoryHelper);
    }

    @Test
    void testScan_WithException_ShouldReturnEmptyDiff() throws IOException {
        // 准备测试数据
        RepoConfig repoConfig = createValidRepoConfig();
        
        when(gitRepositoryHelper.openRepository(any(RepoConfig.class)))
                .thenThrow(new RuntimeException("Test exception"));

        // 执行测试
        DiffSummary result = strategy.scan(repoConfig);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.getDiffFiles().isEmpty());
        
        // 验证方法调用
        verify(gitRepositoryHelper).openRepository(repoConfig);
    }

    private RepoConfig createValidRepoConfig() {
        RepoConfig config = new RepoConfig();
        RepoPath repoPath = RepoPath.builder()
                .absolutePath(tempDir.toString())
                .type(RepoType.ORACLE)
                .build();
        config.setRepoPath(repoPath);
        config.setFetchIfMissing(true);
        config.setRemoteName("origin");

        RepoBranch branchFrom = new RepoBranch();
        branchFrom.setName("time-range");
        branchFrom.setTimeFrom(Instant.parse("2025-11-01T00:00:00Z"));
        config.setBranchFrom(branchFrom);

        RepoBranch branchTo = new RepoBranch();
        branchTo.setName("time-range");
        branchTo.setTimeTo(Instant.parse("2025-12-01T00:00:00Z"));
        config.setBranchTo(branchTo);

        return config;
    }
}
