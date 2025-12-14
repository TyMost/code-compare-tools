package com.example.migratediff.api.controller;

import com.example.migratediff.api.dto.ApiResponse;
import com.example.migratediff.infrastructure.persistence.filesystem.StorageProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 测试管理控制器的持久化状态检查功能
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("管理控制器测试")
class AdminControllerTest {

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private StorageProperties storageProperties;

    private AdminController controller;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("test-admin-controller");
        
        // 创建控制器实例
        controller = new AdminController(applicationContext, storageProperties);
        
        // 模拟存储属性
        when(storageProperties.isEnabled()).thenReturn(true);
        when(storageProperties.resolveRootPath()).thenReturn(tempDir);
        when(storageProperties.getRootPath()).thenReturn(tempDir.toString());
        
        // 模拟所有Repository Bean都存在
        when(applicationContext.containsBean(anyString())).thenReturn(true);
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
    @DisplayName("应该能够获取持久化状态")
    void shouldGetPersistenceStatus() {
        // 执行测试
        ApiResponse<Map<String, Object>> response = controller.getPersistenceStatus();
        
        // 验证结果
        assertEquals("success", response.getStatus());
        assertNotNull(response.getData());
        
        Map<String, Object> status = response.getData();
        assertTrue(status.containsKey("storageEnabled"));
        assertTrue(status.containsKey("storagePath"));
        assertTrue(status.containsKey("storageExists"));
        assertTrue(status.containsKey("storageWritable"));
        assertTrue(status.containsKey("repositories"));
        assertTrue(status.containsKey("config"));
        assertTrue(status.containsKey("directories"));
        
        // 验证存储状态
        assertTrue((Boolean) status.get("storageEnabled"));
        assertTrue((Boolean) status.get("storageExists"));
        assertTrue((Boolean) status.get("storageWritable"));
        
        // 验证Repository状态
        @SuppressWarnings("unchecked")
        Map<String, Boolean> repositories = (Map<String, Boolean>) status.get("repositories");
        assertTrue(repositories.get("coverageRepository"));
        assertTrue(repositories.get("scanReportRepository"));
        
        // 验证配置状态
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) status.get("config");
        assertTrue((Boolean) config.get("fileStorageEnabled"));
        
        // 验证目录状态
        @SuppressWarnings("unchecked")
        Map<String, Object> directories = (Map<String, Object>) status.get("directories");
        assertTrue(directories.containsKey("exists"));
        assertTrue(directories.containsKey("writable"));
    }

    @Test
    @DisplayName("当存储未启用时应该反映在状态中")
    void shouldReflectDisabledStorageInStatus() {
        // 模拟存储未启用
        when(storageProperties.isEnabled()).thenReturn(false);
        
        // 执行测试
        ApiResponse<Map<String, Object>> response = controller.getPersistenceStatus();
        
        // 验证结果
        assertEquals("success", response.getStatus());
        Map<String, Object> status = response.getData();
        assertFalse((Boolean) status.get("storageEnabled"));
    }

    @Test
    @DisplayName("当存储目录不存在时应该反映在状态中")
    void shouldReflectNonExistentStorageInStatus() throws IOException {
        // 删除临时目录
        Files.deleteIfExists(tempDir);
        
        // 执行测试
        ApiResponse<Map<String, Object>> response = controller.getPersistenceStatus();
        
        // 验证结果
        assertEquals("success", response.getStatus());
        Map<String, Object> status = response.getData();
        assertFalse((Boolean) status.get("storageExists"));
        assertFalse((Boolean) status.get("storageWritable"));
    }

    @Test
    @DisplayName("当部分Repository Bean不存在时应该反映在状态中")
    void shouldReflectMissingRepositoryBeansInStatus() {
        // 模拟部分Repository Bean不存在
        when(applicationContext.containsBean("coverageRepository")).thenReturn(false);
        when(applicationContext.containsBean("scanReportRepository")).thenReturn(false);
        
        // 执行测试
        ApiResponse<Map<String, Object>> response = controller.getPersistenceStatus();
        
        // 验证结果
        assertEquals("success", response.getStatus());
        Map<String, Object> status = response.getData();
        
        @SuppressWarnings("unchecked")
        Map<String, Boolean> repositories = (Map<String, Boolean>) status.get("repositories");
        assertFalse(repositories.get("coverageRepository"));
        assertFalse(repositories.get("scanReportRepository"));
    }

    @Test
    @DisplayName("应该能够检查存储目录结构")
    void shouldCheckStorageDirectoryStructure() throws IOException {
        // 创建预期的目录结构
        Files.createDirectories(tempDir.resolve("coverage"));
        Files.createDirectories(tempDir.resolve("scan-reports"));
        
        // 执行测试
        ApiResponse<Map<String, Object>> response = controller.getPersistenceStatus();
        
        // 验证结果
        assertEquals("success", response.getStatus());
        Map<String, Object> status = response.getData();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> directories = (Map<String, Object>) status.get("directories");
        @SuppressWarnings("unchecked")
        Map<String, Boolean> exists = (Map<String, Boolean>) directories.get("exists");
        @SuppressWarnings("unchecked")
        Map<String, Boolean> writable = (Map<String, Boolean>) directories.get("writable");
        
        assertTrue(exists.get("coverage"));
        assertTrue(writable.get("coverage"));
        assertTrue(exists.get("scan-reports"));
        assertTrue(writable.get("scan-reports"));
    }

    @Test
    @DisplayName("应该能够处理存储属性异常")
    void shouldHandleStoragePropertiesException() {
        // 模拟存储属性抛出异常
        when(storageProperties.resolveRootPath()).thenThrow(new RuntimeException("模拟异常"));
        
        // 执行测试
        ApiResponse<Map<String, Object>> response = controller.getPersistenceStatus();
        
        // 验证结果
        assertEquals("success", response.getStatus());
        assertNotNull(response.getData());
    }

    @Test
    @DisplayName("应该能够处理应用上下文异常")
    void shouldHandleApplicationContextException() {
        // 模拟应用上下文抛出异常
        when(applicationContext.containsBean(anyString())).thenThrow(new RuntimeException("模拟异常"));
        
        // 执行测试
        ApiResponse<Map<String, Object>> response = controller.getPersistenceStatus();
        
        // 验证结果
        assertEquals("success", response.getStatus());
        assertNotNull(response.getData());
    }
}