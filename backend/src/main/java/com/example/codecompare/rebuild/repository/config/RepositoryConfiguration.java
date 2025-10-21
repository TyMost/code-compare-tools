package com.example.codecompare.rebuild.repository.config;

import com.example.codecompare.rebuild.repository.AgentSuggestionRepository;
import com.example.codecompare.rebuild.repository.BlockDecisionRepository;
import com.example.codecompare.rebuild.repository.DiffSnapshotRepository;
import com.example.codecompare.rebuild.repository.FileMetadataRepository;
import com.example.codecompare.rebuild.repository.filesystem.FileSystemAgentSuggestionRepository;
import com.example.codecompare.rebuild.repository.filesystem.FileSystemBlockDecisionRepository;
import com.example.codecompare.rebuild.repository.filesystem.FileSystemDiffSnapshotRepository;
import com.example.codecompare.rebuild.repository.filesystem.FileSystemFileMetadataRepository;
import com.example.codecompare.rebuild.repository.filesystem.StorageFileHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 仓储层 Bean 的统一配置，默认使用文件存储实现，可通过配置切换至其他后端。
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class RepositoryConfiguration {

    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;

    public RepositoryConfiguration(StorageProperties storageProperties, ObjectMapper objectMapper) {
        this.storageProperties = storageProperties;
        this.objectMapper = prepareMapper(objectMapper);
    }

    @Bean
    public FileMetadataRepository rebuildFileMetadataRepository() {
        StorageFileHelper.ensureDirectory(storageProperties.resolveFileMetadata());
        return new FileSystemFileMetadataRepository(storageProperties, objectMapper);
    }

    @Bean
    public DiffSnapshotRepository rebuildDiffSnapshotRepository() {
        StorageFileHelper.ensureDirectory(storageProperties.resolveDiffSnapshots());
        return new FileSystemDiffSnapshotRepository(storageProperties, objectMapper);
    }

    @Bean
    public BlockDecisionRepository rebuildBlockDecisionRepository() {
        StorageFileHelper.ensureDirectory(storageProperties.resolveBlockDecisions());
        return new FileSystemBlockDecisionRepository(storageProperties, objectMapper);
    }

    @Bean
    public AgentSuggestionRepository rebuildAgentSuggestionRepository() {
        StorageFileHelper.ensureDirectory(storageProperties.resolveAgentSuggestions());
        return new FileSystemAgentSuggestionRepository(storageProperties, objectMapper);
    }

    private ObjectMapper prepareMapper(ObjectMapper mapper) {
        ObjectMapper copy = mapper.copy();
        copy.registerModule(new JavaTimeModule());
        copy.findAndRegisterModules();
        return copy;
    }
}
