package com.example.migratediff.infrastructure.persistence;

import com.example.migratediff.domain.config.RepoConfig;

import java.util.List;
import java.util.Optional;

/**
 * 仓库配置持久化接口
 */
public interface RepoConfigRepository {
    
    /**
     * 保存仓库配置
     */
    RepoConfig save(RepoConfig config);
    
    /**
     * 根据ID查找配置
     */
    Optional<RepoConfig> findById(String id);
    
    /**
     * 根据名称查找配置
     */
    Optional<RepoConfig> findByName(String name);
    
    /**
     * 获取所有配置
     */
    List<RepoConfig> findAll();
    
    /**
     * 根据ID删除配置
     */
    void deleteById(String id);
    
    /**
     * 检查配置是否存在
     */
    boolean existsById(String id);
    
    /**
     * 检查配置名是否存在
     */
    boolean existsByName(String name);
}
