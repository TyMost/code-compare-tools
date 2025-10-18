package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 在示例环境下自动触发一次全量扫描，生成演示数据。
 */
@Component
public class DemoDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final FileScanService fileScanService;
    private final ScanResultRepository scanResultRepository;
    private final ProjectRootRegistry projectRootRegistry;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public DemoDataInitializer(FileScanService fileScanService,
                               ScanResultRepository scanResultRepository,
                               ProjectRootRegistry projectRootRegistry) {
        this.fileScanService = fileScanService;
        this.scanResultRepository = scanResultRepository;
        this.projectRootRegistry = projectRootRegistry;
    }

    @EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onApplicationReady() {
        triggerScanIfNecessary();
    }

    @PostConstruct
    void triggerScanIfNecessary() {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        List<ProjectRootRegistry.ProjectRootDescriptor> sources = projectRootRegistry.getSources();
        List<ProjectRootRegistry.ProjectRootDescriptor> targets = projectRootRegistry.getTargets();
        if (CollectionUtils.isEmpty(sources) || CollectionUtils.isEmpty(targets)) {
            return;
        }
        String defaultProjectCode = resolveDefaultProjectCode();
        if (scanResultRepository.findLatestSummary(defaultProjectCode).isPresent()) {
            return;
        }
        try {
            log.info("首次启动未检测到示例扫描结果，自动执行全量扫描...");
            fileScanService.scanAll(null);
        } catch (Exception ex) {
            log.warn("自动扫描示例项目失败", ex);
        }
    }

    private String resolveDefaultProjectCode() {
        List<ProjectRootRegistry.ProjectRootDescriptor> sources = projectRootRegistry.getSources();
        ProjectRootRegistry.ProjectRootDescriptor descriptor = CollectionUtils.isEmpty(sources)
                ? null
                : sources.get(0);
        if (descriptor == null) {
            descriptor = projectRootRegistry.getDescriptors().stream().findFirst().orElse(null);
        }
        if (descriptor != null) {
            if (StringUtils.hasText(descriptor.getCode())) {
                return descriptor.getCode();
            }
            Path path = descriptor.getPath();
            if (path != null && path.getFileName() != null) {
                return path.getFileName().toString();
            }
        }
        return "default-project";
    }
}
