package com.example.migratediff.api.controller;

import com.example.migratediff.api.dto.ApiResponse;
import com.example.migratediff.infrastructure.persistence.CoverageRepository;
import com.example.migratediff.infrastructure.persistence.DiffRepository;
import com.example.migratediff.infrastructure.persistence.MigrationRepository;
import com.example.migratediff.infrastructure.persistence.ScanReportRepository;
import com.example.migratediff.infrastructure.persistence.ScanSnapshotRepository;
import com.example.migratediff.infrastructure.persistence.filesystem.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理员控制器，提供系统状态和诊断信息
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final ApplicationContext applicationContext;
    private final StorageProperties storageProperties;

    @Autowired
    public AdminController(ApplicationContext applicationContext,
                         StorageProperties storageProperties) {
        this.applicationContext = applicationContext;
        this.storageProperties = storageProperties;
    }

    /**
     * 获取持久化功能状态
     */
    @GetMapping("/persistence/status")
    public ApiResponse<Map<String, Object>> getPersistenceStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 检查存储目录状态
        Path storagePath = storageProperties.resolveRootPath();
        status.put("storageEnabled", storageProperties.isEnabled());
        status.put("storagePath", storagePath.toString());
        status.put("storageExists", Files.exists(storagePath));
        status.put("storageWritable", checkWritePermission(storagePath));
        
        // 检查Repository Bean状态
        Map<String, Boolean> repositoryStatus = new HashMap<>();
        repositoryStatus.put("coverageRepository", applicationContext.containsBean("coverageRepository"));
        repositoryStatus.put("diffRepository", applicationContext.containsBean("diffRepository"));
        repositoryStatus.put("migrationRepository", applicationContext.containsBean("migrationRepository"));
        repositoryStatus.put("scanReportRepository", applicationContext.containsBean("scanReportRepository"));
        repositoryStatus.put("scanSnapshotRepository", applicationContext.containsBean("scanSnapshotRepository"));
        
        status.put("repositories", repositoryStatus);
        
        // 检查配置状态
        Map<String, Object> configStatus = new HashMap<>();
        configStatus.put("fileStorageEnabled", storageProperties.isEnabled());
        configStatus.put("fileStoragePath", storageProperties.getRootPath());
        
        status.put("config", configStatus);
        
        // 检查存储目录结构
        Map<String, Object> directoryStatus = checkDirectoryStructure(storagePath);
        status.put("directories", directoryStatus);
        
        log.info("持久化状态检查完成: {}", status);
        
        return ApiResponse.success(status);
    }

    /**
     * 检查存储目录写权限
     */
    private boolean checkWritePermission(Path path) {
        try {
            Path testFile = path.resolve(".permission-test");
            Files.write(testFile, "test".getBytes());
            Files.deleteIfExists(testFile);
            return true;
        } catch (Exception e) {
            log.warn("存储目录写权限检查失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查存储目录结构
     */
    private Map<String, Object> checkDirectoryStructure(Path storagePath) {
        Map<String, Object> directoryStatus = new HashMap<>();
        
        String[] expectedDirectories = {
            "coverage", "diff", "migration", "scan-snapshots", "scan-reports"
        };
        
        Map<String, Boolean> dirExists = new HashMap<>();
        Map<String, Boolean> dirWritable = new HashMap<>();
        
        for (String dirName : expectedDirectories) {
            Path dirPath = storagePath.resolve(dirName);
            boolean exists = Files.exists(dirPath);
            boolean writable = exists && checkWritePermission(dirPath);
            
            dirExists.put(dirName, exists);
            dirWritable.put(dirName, writable);
        }
        
        directoryStatus.put("exists", dirExists);
        directoryStatus.put("writable", dirWritable);
        
        return directoryStatus;
    }
}