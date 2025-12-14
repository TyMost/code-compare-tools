package com.example.migratediff.infrastructure.persistence.filesystem;

import com.example.migratediff.domain.coverage.CoverageDetail;
import com.example.migratediff.domain.coverage.CoverageSummary;
import com.example.migratediff.infrastructure.persistence.CoverageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 测试文件系统覆盖率仓库的持久化功能
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("文件系统覆盖率仓库测试")
class FileSystemCoverageRepositoryTest {

    @Mock
    private FileStorageSupport storageSupport;

    private FileSystemCoverageRepository repository;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        repository = new FileSystemCoverageRepository(storageSupport);
        tempDir = Files.createTempDirectory("test-coverage");
        when(storageSupport.resolve(any(String.class))).thenReturn(tempDir.resolve("test.json"));
        when(storageSupport.resolve(any(String.class), any(String.class))).thenReturn(tempDir.resolve("coverage/test.json"));
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
    @DisplayName("应该能够保存覆盖率摘要")
    void shouldSaveCoverageSummary() {
        // 准备测试数据
        CoverageSummary summary = createTestCoverageSummary("test-task-1");
        
        // 执行测试
        repository.save(summary);
        
        // 验证结果
        verify(storageSupport).writeJson(any(Path.class), eq(summary));
    }

    @Test
    @DisplayName("保存null覆盖率摘要应该不抛出异常")
    void shouldHandleNullCoverageSummary() {
        // 执行测试并验证不抛出异常
        assertDoesNotThrow(() -> repository.save(null));
        
        // 验证没有调用存储支持
        verify(storageSupport, never()).writeJson(any(Path.class), any());
    }

    @Test
    @DisplayName("应该能够根据任务ID查找覆盖率摘要")
    void shouldFindCoverageSummaryByTaskId() {
        // 准备测试数据
        String taskId = "test-task-2";
        CoverageSummary expectedSummary = createTestCoverageSummary(taskId);
        
        // 模拟存储支持返回数据
        when(storageSupport.readJson(any(Path.class), eq(CoverageSummary.class)))
                .thenReturn(Optional.of(expectedSummary));
        
        // 执行测试
        Optional<CoverageSummary> result = repository.findByTaskId(taskId);
        
        // 验证结果
        assertTrue(result.isPresent());
        assertEquals(expectedSummary.getTaskId(), result.get().getTaskId());
        assertEquals(expectedSummary.getOverallCoverage(), result.get().getOverallCoverage());
        verify(storageSupport).readJson(any(Path.class), eq(CoverageSummary.class));
    }

    @Test
    @DisplayName("查找不存在的任务ID应该返回空")
    void shouldReturnEmptyForNonExistentTaskId() {
        // 准备测试数据
        String taskId = "non-existent-task";
        
        // 模拟存储支持返回空
        when(storageSupport.readJson(any(Path.class), eq(CoverageSummary.class)))
                .thenReturn(Optional.empty());
        
        // 执行测试
        Optional<CoverageSummary> result = repository.findByTaskId(taskId);
        
        // 验证结果
        assertFalse(result.isPresent());
        verify(storageSupport).readJson(any(Path.class), eq(CoverageSummary.class));
    }

    @Test
    @DisplayName("查找空任务ID应该返回空")
    void shouldReturnEmptyForEmptyTaskId() {
        // 执行测试
        Optional<CoverageSummary> result = repository.findByTaskId("");
        
        // 验证结果
        assertFalse(result.isPresent());
        verify(storageSupport, never()).readJson(any(Path.class), any());
    }

    @Test
    @DisplayName("查找null任务ID应该返回空")
    void shouldReturnEmptyForNullTaskId() {
        // 执行测试
        Optional<CoverageSummary> result = repository.findByTaskId(null);
        
        // 验证结果
        assertFalse(result.isPresent());
        verify(storageSupport, never()).readJson(any(Path.class), any());
    }

    /**
     * 创建测试用的覆盖率摘要
     */
    private CoverageSummary createTestCoverageSummary(String taskId) {
        List<CoverageDetail> details = new ArrayList<>();
        details.add(CoverageDetail.builder()
                .filePath("/src/main/java/TestFile.java")
                .totalLines(100)
                .matchedLines(80)
                .coverage(0.8)
                .build());
        
        return CoverageSummary.builder()
                .taskId(taskId)
                .details(details)
                .overallCoverage(0.8)
                .totalMatchedLines(80.0)
                .totalLines(100)
                .build();
    }
}