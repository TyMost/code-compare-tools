package com.example.migratediff.infrastructure.persistence.filesystem;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * File system persistence configuration gated by file-storage.* properties.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
@ConditionalOnProperty(prefix = "file-storage", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FileSystemPersistenceConfig {

    @Bean(name = "fileStorageObjectMapper")
    public ObjectMapper fileStorageObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    @Bean
    public FileStorageSupport fileStorageSupport(StorageProperties properties,
                                                 @Qualifier("fileStorageObjectMapper")
                                                 ObjectMapper fileStorageObjectMapper) {
        Path rootPath = properties.resolveRootPath();
        return new FileStorageSupport(rootPath, fileStorageObjectMapper);
    }

    @Bean
    public FileRepoKeyResolver fileRepoKeyResolver() {
        return new FileRepoKeyResolver();
    }
}
