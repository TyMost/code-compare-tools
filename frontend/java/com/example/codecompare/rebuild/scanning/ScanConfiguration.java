package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.example.codecompare.rebuild.repository.AgentSuggestionRepository;
import com.example.codecompare.rebuild.repository.BlockDecisionRepository;
import com.example.codecompare.rebuild.repository.DiffSnapshotRepository;
import com.example.codecompare.rebuild.repository.FileMetadataRepository;
import com.example.codecompare.rebuild.repository.config.StorageProperties;
import com.example.codecompare.rebuild.repository.filesystem.FileSystemScanResultRepository;
import com.example.codecompare.rebuild.repository.support.StoragePurgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 扫描模块自动配置，声明扫描相关核心 Bean。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ScanProperties.class)
public class ScanConfiguration {

    @Bean
    public ScanResultRepository scanResultRepository(FileMetadataRepository fileMetadataRepository,
                                                     StorageProperties storageProperties,
                                                     ObjectMapper objectMapper) {
        return new FileSystemScanResultRepository(fileMetadataRepository, storageProperties, objectMapper);
    }

    @Bean
    public FileFingerprintCalculator fileFingerprintCalculator(FileMetadataRepository fileMetadataRepository,
                                                               ScanProperties scanProperties,
                                                               Clock clock) {
        return new FileFingerprintCalculator(fileMetadataRepository, scanProperties, clock);
    }

    @Bean
    public IgnoredArtifactCleaner ignoredArtifactCleaner(StorageProperties storageProperties,
                                                         ScanResultRepository scanResultRepository,
                                                         BlockDecisionRepository blockDecisionRepository,
                                                         DiffSnapshotRepository diffSnapshotRepository,
                                                         AgentSuggestionRepository agentSuggestionRepository) {
        return new IgnoredArtifactCleaner(storageProperties, scanResultRepository,
                blockDecisionRepository, diffSnapshotRepository, agentSuggestionRepository);
    }

    @Bean
    public FullProjectScanService fullProjectScanService(FileFingerprintCalculator fileFingerprintCalculator,
                                                         ScanResultRepository scanResultRepository,
                                                         ScanProperties scanProperties,
                                                         Clock clock,
                                                         IgnoredArtifactCleaner ignoredArtifactCleaner) {
        return new FullProjectScanService(fileFingerprintCalculator, scanResultRepository,
                scanProperties, clock, ignoredArtifactCleaner);
    }

    @Bean
    public FileScanService fileScanService(FullProjectScanService fullProjectScanService,
                                           GitChangeScanner gitChangeScanner,
                                           ScanResultRepository scanResultRepository,
                                           ApplicationEventPublisher eventPublisher,
                                           ProjectRootRegistry projectRootRegistry,
                                           ScanProperties scanProperties,
                                           Clock clock,
                                           StoragePurgeService storagePurgeService) {
        return new FileScanService(fullProjectScanService, gitChangeScanner, scanResultRepository,
                eventPublisher, projectRootRegistry, scanProperties, clock, storagePurgeService);
    }
}
