package com.example.migratediff.application.scan;

import com.example.migratediff.application.CoverageAppService;
import com.example.migratediff.application.DiffAppService;
import com.example.migratediff.application.GenerateAppService;
import com.example.migratediff.application.MigrationAppService;
import com.example.migratediff.infrastructure.persistence.ScanReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 测试扫描应用服务的持久化功能
 * 使用反射测试私有方法
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("扫描应用服务持久化测试")
class ScanAppServicePersistenceTest {

    @Mock
    private DiffAppService diffAppService;

    @Mock
    private CoverageAppService coverageAppService;

    @Mock
    private GenerateAppService generateAppService;

    @Mock
    private MigrationAppService migrationAppService;

    @Mock
    private ScanResultStore scanResultStore;

    @Mock
    private ScanReportRepository scanReportRepository;

    private ScanAppService service;
    private Method persistReportMethod;

    @BeforeEach
    void setUp() throws Exception {
        service = new ScanAppService(
                diffAppService,
                coverageAppService,
                generateAppService,
                migrationAppService,
                scanResultStore,
                scanReportRepository
        );
        
        // 获取私有方法
        persistReportMethod = ScanAppService.class.getDeclaredMethod("persistReport", ScanReport.class);
        persistReportMethod.setAccessible(true);
    }

    @Test
    @DisplayName("应该能够持久化有效的扫描报告")
    void shouldPersistValidScanReport() throws Exception {
        // 准备测试数据
        ScanReport report = createTestScanReport("test-task-1", true);
        
        // 执行测试
        persistReportMethod.invoke(service, report);
        
        // 验证保存操作被调用
        verify(scanReportRepository, times(1)).save(report);
    }

    @Test
    @DisplayName("不应该持久化null扫描报告")
    void shouldNotPersistNullScanReport() throws Exception {
        // 执行测试
        persistReportMethod.invoke(service, (ScanReport) null);
        
        // 验证保存操作没有被调用
        verify(scanReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("不应该持久化未标记为持久化的扫描报告")
    void shouldNotPersistUnmarkedScanReport() throws Exception {
        // 准备测试数据
        ScanReport report = createTestScanReport("test-task-2", false);
        
        // 执行测试
        persistReportMethod.invoke(service, report);
        
        // 验证保存操作没有被调用
        verify(scanReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("不应该持久化空任务ID的扫描报告")
    void shouldNotPersistScanReportWithEmptyTaskId() throws Exception {
        // 准备测试数据
        ScanReport report = createTestScanReport("", true);
        
        // 执行测试
        persistReportMethod.invoke(service, report);
        
        // 验证保存操作没有被调用
        verify(scanReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("不应该持久化null任务ID的扫描报告")
    void shouldNotPersistScanReportWithNullTaskId() throws Exception {
        // 准备测试数据
        ScanReport report = createTestScanReport(null, true);
        
        // 执行测试
        persistReportMethod.invoke(service, report);
        
        // 验证保存操作没有被调用
        verify(scanReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("当仓库为null时应该不抛出异常")
    void shouldNotThrowExceptionWhenRepositoryIsNull() throws Exception {
        // 使用null仓库创建服务
        ScanAppService serviceWithNullRepo = new ScanAppService(
                diffAppService,
                coverageAppService,
                generateAppService,
                migrationAppService,
                scanResultStore,
                null
        );
        
        // 获取私有方法
        Method method = ScanAppService.class.getDeclaredMethod("persistReport", ScanReport.class);
        method.setAccessible(true);
        
        // 准备测试数据
        ScanReport report = createTestScanReport("test-task-3", true);
        
        // 执行测试并验证不抛出异常
        assertDoesNotThrow(() -> {
            try {
                method.invoke(serviceWithNullRepo, report);
            } catch (Exception e) {
                throw e.getCause();
            }
        });
    }

    @Test
    @DisplayName("当保存操作抛出异常时应该不抛出异常")
    void shouldNotThrowExceptionWhenSaveThrowsException() throws Exception {
        // 准备测试数据
        ScanReport report = createTestScanReport("test-task-4", true);
        
        // 模拟保存操作抛出异常
        doThrow(new RuntimeException("模拟保存异常")).when(scanReportRepository).save(report);
        
        // 执行测试并验证不抛出异常
        assertDoesNotThrow(() -> {
            try {
                persistReportMethod.invoke(service, report);
            } catch (Exception e) {
                throw e.getCause();
            }
        });
        
        // 验证保存操作被调用
        verify(scanReportRepository, times(1)).save(report);
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
                null,
                null,
                null,
                null
        );
    }
}