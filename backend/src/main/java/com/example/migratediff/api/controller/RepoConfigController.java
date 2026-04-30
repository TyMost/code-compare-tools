package com.example.migratediff.api.controller;

import com.example.migratediff.api.dto.ApiResponse;
import com.example.migratediff.application.config.RepoConfigService;
import com.example.migratediff.domain.config.RepoConfig;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

/**
 * 仓库配置管理API
 */
@RestController
@RequestMapping("/api/repo-configs")
public class RepoConfigController {

    private final RepoConfigService repoConfigService;

    public RepoConfigController(RepoConfigService repoConfigService) {
        this.repoConfigService = repoConfigService;
    }

    /**
     * 获取所有仓库配置
     */
    @GetMapping
    public ApiResponse<List<RepoConfig>> getAllConfigs() {
        List<RepoConfig> configs = repoConfigService.findAll();
        return ApiResponse.success(configs);
    }

    /**
     * 根据ID获取配置
     */
    @GetMapping("/{id}")
    public ApiResponse<RepoConfig> getConfigById(@PathVariable String id) {
        Optional<RepoConfig> config = repoConfigService.findById(id);
        if (!config.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "配置不存在: " + id);
        }
        return ApiResponse.success(config.get());
    }

    /**
     * 创建新配置
     */
    @PostMapping
    public ApiResponse<RepoConfig> createConfig(@Valid @RequestBody RepoConfig config) {
        // 验证配置
        repoConfigService.validateConfig(config);
        
        // 检查名称是否已存在
        if (repoConfigService.existsByName(config.getName())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "配置名称已存在: " + config.getName());
        }
        
        RepoConfig savedConfig = repoConfigService.saveConfig(config);
        return ApiResponse.success("配置创建成功", savedConfig);
    }

    /**
     * 更新配置
     */
    @PutMapping("/{id}")
    public ApiResponse<RepoConfig> updateConfig(
            @PathVariable String id, 
            @Valid @RequestBody RepoConfig config) {
        
        // 验证配置
        repoConfigService.validateConfig(config);
        
        // 检查配置是否存在
        if (!repoConfigService.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "配置不存在: " + id);
        }
        
        // 检查名称冲突（排除自己）
        Optional<RepoConfig> existingByName = repoConfigService.findByName(config.getName());
        if (existingByName.isPresent() && !existingByName.get().getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "配置名称已存在: " + config.getName());
        }
        
        RepoConfig updatedConfig = repoConfigService.updateConfig(id, config);
        return ApiResponse.success("配置更新成功", updatedConfig);
    }

    /**
     * 删除配置
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteConfig(@PathVariable String id) {
        if (!repoConfigService.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "配置不存在: " + id);
        }
        
        repoConfigService.deleteById(id);
        return ApiResponse.success("配置删除成功", null);
    }

    /**
     * 批量导入配置
     */
    @PostMapping("/import")
    public ApiResponse<List<RepoConfig>> importConfigs(@RequestBody List<RepoConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "配置列表不能为空");
        }
        
        // 验证所有配置
        for (int i = 0; i < configs.size(); i++) {
            try {
                repoConfigService.validateConfig(configs.get(i));
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "配置 #" + (i + 1) + " 验证失败: " + e.getMessage());
            }
        }
        
        List<RepoConfig> result = repoConfigService.importConfigs(configs);
        return ApiResponse.success("配置导入成功", result);
    }

    /**
     * 导出所有配置
     */
    @GetMapping("/export")
    public ApiResponse<ConfigExportBundle> exportConfigs() {
        List<RepoConfig> configs = repoConfigService.exportAllConfigs();
        ConfigExportBundle bundle = new ConfigExportBundle();
        bundle.setConfigs(configs);
        bundle.setExportedAt(java.time.Instant.now());
        bundle.setVersion("1.0");
        bundle.setCount(configs.size());
        
        return ApiResponse.success(bundle);
    }

    /**
     * 检查配置名称是否存在
     */
    @GetMapping("/check-name")
    public ApiResponse<Boolean> checkNameExists(@RequestParam String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称参数不能为空");
        }
        
        boolean exists = repoConfigService.existsByName(name.trim());
        return ApiResponse.success(exists);
    }

    /**
     * 配置导出包
     */
    public static class ConfigExportBundle {
        private String version;
        private java.time.Instant exportedAt;
        private Integer count;
        private List<RepoConfig> configs;
        
        // Getters and Setters
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public java.time.Instant getExportedAt() { return exportedAt; }
        public void setExportedAt(java.time.Instant exportedAt) { this.exportedAt = exportedAt; }
        
        public Integer getCount() { return count; }
        public void setCount(Integer count) { this.count = count; }
        
        public List<RepoConfig> getConfigs() { return configs; }
        public void setConfigs(List<RepoConfig> configs) { this.configs = configs; }
    }
}
