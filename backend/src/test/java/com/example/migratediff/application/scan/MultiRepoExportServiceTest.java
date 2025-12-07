package com.example.migratediff.application.scan;

import com.example.migratediff.api.dto.FileCommitHistoryDTO;
import com.example.migratediff.api.dto.GitCommitInfoDTO;
import com.example.migratediff.application.commit.GitCommitHistoryService;
import com.example.migratediff.config.MigrationDiffProperties;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MultiRepoExportService 单元测试
 * 测试仓库级别时间筛选功能
 */
@ExtendWith(MockitoExtension.class)
class MultiRepoExportServiceTest {

    @Mock
    private ScanResultStore scanResultStore;

    @Mock
    private DiffMatrixAssembler diffMatrixAssembler;

    @Mock
    private DiffMatrixFilter diffMatrixFilter;

    @Mock
    private CommitInfoAggregatorService commitInfoAggregatorService;

    @Mock
    private GitCommitHistoryService gitCommitHistoryService;

    @Mock
    private MigrationDiffProperties migrationDiffProperties;

    @InjectMocks
    private MultiRepoExportService multiRepoExportService;

    private MultiRepoExportRequest testRequest;
    private ScanReport testScanReport;
    private DiffMatrixRow testRow;

    @BeforeEach
    void setUp() {
        // 准备测试数据 - 使用Builder模式创建对象
        testRequest = MultiRepoExportRequest.builder()
                .repos(Arrays.asList(
                        MultiRepoExportRequest.RepoSelection.builder()
                                .presetName("test-preset")
                                .alias("Test Repository")
                                .build()
                ))
                .build();
        
        // 准备扫描报告 - 使用现有的构造函数
        testScanReport = createTestScanReport("task-123", "test-preset");
        
        // 准备差异矩阵行 - 使用反射或Builder模式
        testRow = createTestDiffMatrixRow();
    }

    @Test
    @DisplayName("测试导出功能 - 仓库级别时间筛选正常流程")
    void testExport_RepoLevelTimeFiltering_Normal() {
        // 准备包含提交信息的请求
        DiffMatrixFilterCriteria criteria = DiffMatrixFilterCriteria.builder()
                .includeCommitInfo(true)
                .build();
        testRequest = MultiRepoExportRequest.builder()
                .repos(Arrays.asList(
                        MultiRepoExportRequest.RepoSelection.builder()
                                .presetName("test-preset")
                                .alias("Test Repository")
                                .build()
                ))
                .filterCriteria(criteria)
                .build();
        
        // 准备模拟数据
        when(scanResultStore.findLatestByPreset("test-preset"))
                .thenReturn(Optional.of(testScanReport));
        
        when(diffMatrixAssembler.assemble(testScanReport))
                .thenReturn(Arrays.asList(testRow));
        
        when(diffMatrixFilter.filter(any(), any()))
                .thenReturn(Arrays.asList(testRow));
        
        when(commitInfoAggregatorService.enrichWithCommitInfo(any(), any(), any()))
                .thenReturn(testRow);
        
        when(commitInfoAggregatorService.aggregateAuthorStats(any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        
        // 模拟配置属性返回预设特定的时间范围
        when(migrationDiffProperties.getEarliestTimeFromByPreset("test-preset"))
                .thenReturn(Instant.parse("2025-11-01T00:00:00Z"));
        when(migrationDiffProperties.getLatestTimeToByPreset("test-preset"))
                .thenReturn(Instant.parse("2025-11-30T23:59:59Z"));
        
        
        // 执行导出
        MultiRepoExportResult result = multiRepoExportService.export(testRequest);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.getRepoReports().size());
        assertEquals("test-preset", result.getRepoReports().get(0).getScanReport().getPresetName());
        
        // 验证调用了仓库级别的时间范围获取（会被调用2次：一次用于authorStats，一次用于fileCommitDetails）
        verify(migrationDiffProperties, times(2)).getEarliestTimeFromByPreset("test-preset");
        verify(migrationDiffProperties, times(2)).getLatestTimeToByPreset("test-preset");
        
        // 验证提交者统计使用了正确的时间范围
        verify(commitInfoAggregatorService).aggregateAuthorStats(
                eq(Arrays.asList(testRow)),
                any(),
                any(),
                eq("test-preset"),
                eq(Instant.parse("2025-11-01T00:00:00Z")),
                eq(Instant.parse("2025-11-30T23:59:59Z"))
        );
    }

    @Test
    @DisplayName("测试导出功能 - 预设配置不存在时使用全局默认值")
    void testExport_PresetConfigNotFound_UseGlobalDefault() {
        // 准备包含提交信息的请求
        DiffMatrixFilterCriteria criteria = DiffMatrixFilterCriteria.builder()
                .includeCommitInfo(true)
                .build();
        testRequest = MultiRepoExportRequest.builder()
                .repos(Arrays.asList(
                        MultiRepoExportRequest.RepoSelection.builder()
                                .presetName("test-preset")
                                .build()
                ))
                .filterCriteria(criteria)
                .build();
        
        // 准备模拟数据
        when(scanResultStore.findLatestByPreset("test-preset"))
                .thenReturn(Optional.of(testScanReport));
        
        when(diffMatrixAssembler.assemble(testScanReport))
                .thenReturn(Arrays.asList(testRow));
        
        when(diffMatrixFilter.filter(any(), any()))
                .thenReturn(Arrays.asList(testRow));
        
        when(commitInfoAggregatorService.enrichWithCommitInfo(any(), any(), any()))
                .thenReturn(testRow);
        
        when(commitInfoAggregatorService.aggregateAuthorStats(any(), any(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        
        // 模拟预设配置不存在，返回null
        when(migrationDiffProperties.getEarliestTimeFromByPreset("test-preset"))
                .thenReturn(null);
        when(migrationDiffProperties.getLatestTimeToByPreset("test-preset"))
                .thenReturn(null);
        
        // 模拟全局默认时间范围
        when(migrationDiffProperties.getEarliestTimeFrom())
                .thenReturn(Instant.parse("2025-10-01T00:00:00Z"));
        when(migrationDiffProperties.getLatestTimeTo())
                .thenReturn(Instant.parse("2025-12-31T23:59:59Z"));
        
        // 执行导出
        MultiRepoExportResult result = multiRepoExportService.export(testRequest);
        
        // 验证结果
        assertNotNull(result);
        
        // 验证使用了全局默认时间范围
        verify(commitInfoAggregatorService).aggregateAuthorStats(
                any(),
                any(),
                any(),
                any(),
                eq(Instant.parse("2025-10-01T00:00:00Z")),
                eq(Instant.parse("2025-12-31T23:59:59Z"))
        );
    }

    @Test
    @DisplayName("测试导出功能 - 不包含提交信息时跳过相关处理")
    void testExport_SkipCommitInfo() {
        // 准备不包含提交信息的请求
        DiffMatrixFilterCriteria criteria = DiffMatrixFilterCriteria.builder()
                .includeCommitInfo(false)
                .build();
        testRequest = MultiRepoExportRequest.builder()
                .repos(Arrays.asList(
                        MultiRepoExportRequest.RepoSelection.builder()
                                .presetName("test-preset")
                                .build()
                ))
                .filterCriteria(criteria)
                .build();
        
        // 准备模拟数据
        when(scanResultStore.findLatestByPreset("test-preset"))
                .thenReturn(Optional.of(testScanReport));
        
        when(diffMatrixAssembler.assemble(testScanReport))
                .thenReturn(Arrays.asList(testRow));
        
        when(diffMatrixFilter.filter(any(), any()))
                .thenReturn(Arrays.asList(testRow));
        
        // 执行导出
        MultiRepoExportResult result = multiRepoExportService.export(testRequest);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.getRepoReports().size());
        
        // 验证没有调用提交信息相关的方法
        verify(commitInfoAggregatorService, never()).enrichWithCommitInfo(any(), any(), any());
        verify(commitInfoAggregatorService, never()).aggregateAuthorStats(any(), any(), any(), any(), any(), any());
        verify(gitCommitHistoryService, never()).getFileCommitHistory(any(), any(), any());
    }

    @Test
    @DisplayName("测试导出功能 - 仓库列表为空时抛出异常")
    void testExport_EmptyRepoList_ThrowException() {
        // 准备空仓库列表的请求
        testRequest = MultiRepoExportRequest.builder()
                .repos(Collections.emptyList())
                .build();
        
        // 执行导出并验证异常
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> multiRepoExportService.export(testRequest)
        );
        
        assertEquals("缺少有效的仓库列表", exception.getMessage());
    }

    @Test
    @DisplayName("测试导出功能 - 请求为null时抛出异常")
    void testExport_NullRequest_ThrowException() {
        // 执行导出并验证异常
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> multiRepoExportService.export(null)
        );
        
        assertEquals("缺少有效的仓库列表", exception.getMessage());
    }

    @Test
    @DisplayName("测试导出功能 - 找不到预设时抛出异常")
    void testExport_PresetNotFound_ThrowException() {
        // 准备模拟数据
        when(scanResultStore.findLatestByPreset("test-preset"))
                .thenReturn(Optional.empty());
        
        // 执行导出并验证异常
        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> multiRepoExportService.export(testRequest)
        );
        
        assertTrue(exception.getMessage().contains("预设 test-preset 暂无扫描记录"));
    }

    @Test
    @DisplayName("测试异步批量获取文件提交历史 - 时间范围筛选")
    void testGetBatchFileCommitDetailsAsync_TimeRangeFiltering() throws Exception {
        // 准备测试数据
        List<String> filePaths = Arrays.asList("test/File1.java", "test/File2.java");
        RepoConfig repoConfig = createTestRepoConfig("/test/repo");
        
        Instant timeFrom = Instant.parse("2025-11-01T00:00:00Z");
        Instant timeTo = Instant.parse("2025-11-30T23:59:59Z");
        
        // 准备提交历史 - 包含在时间范围内和范围外的提交
        FileCommitHistoryDTO commitHistory = new FileCommitHistoryDTO();
        GitCommitInfoDTO commitInRange = createTestCommit("2025-11-15T10:00:00Z");
        GitCommitInfoDTO commitOutOfRange1 = createTestCommit("2025-10-15T10:00:00Z");
        GitCommitInfoDTO commitOutOfRange2 = createTestCommit("2025-12-15T10:00:00Z");
        
        commitHistory.setOracleCommits(Arrays.asList(
                commitOutOfRange1, commitInRange, commitOutOfRange2
        ));
        
        when(gitCommitHistoryService.getFileCommitHistory(anyString(), any(), isNull()))
                .thenReturn(commitHistory);
        
        // 执行异步批量获取
        CompletableFuture<List<FileCommitDetail>> future = multiRepoExportService
                .getBatchFileCommitDetailsAsync(filePaths, "Oracle", repoConfig, timeFrom, timeTo);
        
        List<FileCommitDetail> result = future.get();
        
        // 验证结果 - 所有提交都会被包含，但只有时间范围内的标记为inTimeRange=true
        assertEquals(6, result.size()); // 2个文件，每个文件3个提交（范围内+范围外）
        
        // 验证时间范围标记 - 只有在时间范围内的提交应该标记为true
        long inRangeCount = result.stream()
                .filter(detail -> detail.getInTimeRange())
                .count();
        assertEquals(2, inRangeCount); // 2个文件，每个文件1个在范围内的提交
        
        // 验证时间范围内的提交
        FileCommitDetail inRangeDetail = result.stream()
                .filter(detail -> detail.getInTimeRange())
                .findFirst()
                .orElse(null);
        
        assertNotNull(inRangeDetail);
        assertEquals(Instant.parse("2025-11-15T10:00:00Z"), inRangeDetail.getCommitTime());
        
        // 验证所有调用都使用了正确的时间范围
        verify(gitCommitHistoryService, times(2)).getFileCommitHistory(anyString(), eq(repoConfig), isNull());
    }

    @Test
    @DisplayName("测试提交类型提取")
    void testExtractCommitType() {
        // 使用反射访问私有方法进行测试
        try {
            java.lang.reflect.Method method = MultiRepoExportService.class
                    .getDeclaredMethod("extractCommitType", String.class);
            method.setAccessible(true);
            
            // 测试各种提交类型
            assertEquals("feature", method.invoke(multiRepoExportService, "feat: add new feature"));
            assertEquals("feature", method.invoke(multiRepoExportService, "feature: add new feature"));
            assertEquals("bugfix", method.invoke(multiRepoExportService, "fix: resolve bug"));
            assertEquals("bugfix", method.invoke(multiRepoExportService, "bugfix: resolve bug"));
            assertEquals("refactor", method.invoke(multiRepoExportService, "refactor: improve code"));
            assertEquals("refactor", method.invoke(multiRepoExportService, "refactoring: improve code"));
            assertEquals("hotfix", method.invoke(multiRepoExportService, "hotfix: critical fix"));
            assertEquals("test", method.invoke(multiRepoExportService, "test: add tests"));
            assertEquals("test", method.invoke(multiRepoExportService, "tests: add tests"));
            assertEquals("docs", method.invoke(multiRepoExportService, "docs: update documentation"));
            assertEquals("docs", method.invoke(multiRepoExportService, "doc: update documentation"));
            assertEquals("style", method.invoke(multiRepoExportService, "style: format code"));
            assertEquals("style", method.invoke(multiRepoExportService, "format: format code"));
            assertEquals("chore", method.invoke(multiRepoExportService, "chore: update build"));
            assertEquals("chore", method.invoke(multiRepoExportService, "build: update build"));
            assertEquals("other", method.invoke(multiRepoExportService, "random commit message"));
            assertEquals("other", method.invoke(multiRepoExportService, ""));
            assertEquals("other", method.invoke(multiRepoExportService, (String) null));
            
        } catch (Exception e) {
            fail("反射调用失败: " + e.getMessage());
        }
    }

    // 辅助方法：创建测试用的ScanReport
    private ScanReport createTestScanReport(String taskId, String presetName) {
        return new ScanReport(
                taskId,
                ScanMode.FULL,
                presetName,
                "test-repo-id",
                "test-repo-name",
                true,
                null, // oracleSummary
                null, // gaussSummary
                null  // coverageSummary
        );
    }

    // 辅助方法：创建测试用的DiffMatrixRow
    private DiffMatrixRow createTestDiffMatrixRow() {
        try {
            // 使用反射创建对象，因为可能没有公共构造函数
            DiffMatrixRow row = DiffMatrixRow.class.getDeclaredConstructor().newInstance();
            // 使用反射设置字段，如果需要的话
            try {
                DiffMatrixRow.class.getDeclaredField("filePath").set(row, "test/File.java");
                DiffMatrixRow.class.getDeclaredField("status").set(row, "matched");
                DiffMatrixRow.class.getDeclaredField("hasOracleCommits").set(row, true);
                DiffMatrixRow.class.getDeclaredField("hasGaussCommits").set(row, true);
            } catch (Exception e) {
                // 如果反射失败，忽略，因为测试主要关注时间筛选逻辑
            }
            return row;
        } catch (Exception e) {
            // 如果反射创建失败，返回mock对象
            return mock(DiffMatrixRow.class);
        }
    }

    // 辅助方法：创建测试用的RepoConfig
    private RepoConfig createTestRepoConfig(String path) {
        RepoConfig repoConfig = new RepoConfig();
        try {
            // 使用反射设置repoPath字段
            java.lang.reflect.Field repoPathField = RepoConfig.class.getDeclaredField("repoPath");
            repoPathField.setAccessible(true);
            repoPathField.set(repoConfig, new java.io.File(path));
        } catch (Exception e) {
            // 如果反射失败，忽略，因为测试主要关注时间筛选逻辑
        }
        return repoConfig;
    }

    // 辅助方法：创建测试用的Git提交信息
    private GitCommitInfoDTO createTestCommit(String commitTime) {
        GitCommitInfoDTO commit = new GitCommitInfoDTO();
        commit.setCommitHash("abc123");
        commit.setAuthorName("Test Author");
        commit.setAuthorEmail("test@example.com");
        commit.setMessage("feat: test commit");
        commit.setCommitTime(Instant.parse(commitTime));
        
        // 创建简单的统计信息对象
        try {
            // 尝试创建Stats对象
            Object stats = Class.forName("com.example.migratediff.api.dto.GitCommitInfoDTO$GitCommitStatsDTO")
                    .getDeclaredConstructor().newInstance();
            
            // 使用反射设置字段
            stats.getClass().getDeclaredField("added").set(stats, 10);
            stats.getClass().getDeclaredField("removed").set(stats, 5);
            
            commit.getClass().getDeclaredField("stats").set(commit, stats);
        } catch (Exception e) {
            // 如果反射失败，忽略，因为测试主要关注时间筛选逻辑
        }
        
        return commit;
    }
}
