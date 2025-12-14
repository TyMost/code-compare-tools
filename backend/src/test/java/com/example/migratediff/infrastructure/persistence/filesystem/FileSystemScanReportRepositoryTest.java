package com.example.migratediff.infrastructure.persistence.filesystem;

import com.example.migratediff.application.scan.ScanMode;
import com.example.migratediff.application.scan.ScanReport;
import com.example.migratediff.domain.coverage.CoverageSummary;
import com.example.migratediff.domain.diff.DiffSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 测试文件系统扫描报告仓库的持久化功能
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("文件系统扫描报告仓库测试")
class FileSystemScanReportRepositoryTest {

    @Mock
    private FileStorageSupport storageSupport;

    private FileSystemScanReportRepository repository;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        repository = new FileSystemScanReportRepository(storageSupport);
        tempDir = Files.createTempDirectory("test-scan-reports");
        when(storageSupport.resolve(any(String.class))).thenReturn(tempDir.resolve("test.json"));
        when(storageSupport.resolve(any(String.class), any(String.class))).thenReturn(tempDir.resolve("scan-reports/test.json"));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // 忽略清理错误
                        }
                    });
        }
    }

    @Test
    @DisplayName("应该能够保存扫描报告")
    void shouldSaveScanReport() {
        // 准备测试数据
        ScanReport report = createTestScanReport("test-task-1", true);
        
        // 执行测试
        repository.save(report);
        
        // 验证结果
        verify(storageSupport).writeJson(any(Path.class), eq(report));
    }

    @Test
    @DisplayName("保存null扫描报告应该不抛出异常")
    void shouldHandleNullScanReport() {
        // 执行测试并验证不抛出异常
        assertDoesNotThrow(() -> repository.save(null));
        
        // 验证没有调用存储支持
        verify(storageSupport, never()).writeJson(any(Path.class), any());
    }

    @Test
    @DisplayName("保存空任务ID的扫描报告应该不抛出异常")
    void shouldHandleScanReportWithEmptyTaskId() {
        // 准备测试数据
        ScanReport report = createTestScanReport("", true);
        
        // 执行测试并验证不抛出异常
        assertDoesNotThrow(() -> repository.save(report));
        
        // 验证没有调用存储支持
        verify(storageSupport, never()).writeJson(any(Path.class), any());
    }

    @Test
    @DisplayName("应该能够根据任务ID查找扫描报告")
    void shouldFindScanReportByTaskId() {
        // 准备测试数据
        String taskId = "test-task-2";
        ScanReport expectedReport = createTestScanReport(taskId, true);
        
        // 模拟存储支持返回数据
        when(storageSupport.readJson(any(Path.class), eq(ScanReport.class)))
                .thenReturn(Optional.of(expectedReport));
        
        // 执行测试
        Optional<ScanReport> result = repository.find(taskId);
        
        // 验证结果
        assertTrue(result.isPresent());
        assertEquals(expectedReport.getTaskId(), result.get().getTaskId());
        assertEquals(expectedReport.isPersisted(), result.get().isPersisted());
        verify(storageSupport).readJson(any(Path.class), eq(ScanReport.class));
    }

    @Test
    @DisplayName("查找不存在的任务ID应该返回空")
    void shouldReturnEmptyForNonExistentTaskId() {
        // 准备测试数据
        String taskId = "non-existent-task";
        
        // 模拟存储支持返回空
        when(storageSupport.readJson(any(Path.class), eq(ScanReport.class)))
                .thenReturn(Optional.empty());
        
        // 执行测试
        Optional<ScanReport> result = repository.find(taskId);
        
        // 验证结果
        assertFalse(result.isPresent());
        verify(storageSupport).readJson(any(Path.class), eq(ScanReport.class));
    }

    @Test
    @DisplayName("查找空任务ID应该返回空")
    void shouldReturnEmptyForEmptyTaskId() {
        // 执行测试
        Optional<ScanReport> result = repository.find("");
        
        // 验证结果
        assertFalse(result.isPresent());
        verify(storageSupport, never()).readJson(any(Path.class), any());
    }

    @Test
    @DisplayName("查找null任务ID应该返回空")
    void shouldReturnEmptyForNullTaskId() {
        // 执行测试
        Optional<ScanReport> result = repository.find(null);
        
        // 验证结果
        assertFalse(result.isPresent());
        verify(storageSupport, never()).readJson(any(Path.class), any());
    }

    @Test
    @DisplayName("应该能够删除指定任务ID的扫描报告")
    void shouldDeleteScanReport() {
        // 准备测试数据
        String taskId = "test-task-delete";
        
        // 执行测试
        repository.delete(taskId);
        
        // 验证结果
        verify(storageSupport).deleteIfExists(any(Path.class));
    }

    @Test
    @DisplayName("删除空任务ID应该不抛出异常")
    void shouldHandleDeleteEmptyTaskId() {
        // 执行测试并验证不抛出异常
        assertDoesNotThrow(() -> repository.delete(""));
        
        // 验证没有调用存储支持
        verify(storageSupport, never()).deleteIfExists(any(Path.class));
    }

    @Test
    @DisplayName("删除null任务ID应该不抛出异常")
    void shouldHandleDeleteNullTaskId() {
        // 执行测试并验证不抛出异常
        assertDoesNotThrow(() -> repository.delete(null));
        
        // 验证没有调用存储支持
        verify(storageSupport, never()).deleteIfExists(any(Path.class));
    }

    @Test
    @DisplayName("应该能够删除所有扫描报告")
    void shouldDeleteAllScanReports() {
        // 执行测试
        repository.deleteAll();
        
        // 验证结果
        verify(storageSupport).resolve("scan-reports");
    }

    /**
     * 创建测试用的扫描报告
     */
    private ScanReport createTestScanReport(String taskId, boolean persisted) {
        return new ScanReport(
                taskId,
                ScanMode.INCREMENTAL,
                "test-preset",
                "test-repo-id",
                "test-repo-name",
                persisted,
                DiffSummary.builder().build(),
                DiffSummary.builder().build(),
                CoverageSummary.builder().build(),
                Instant.now()
        );
    }
}