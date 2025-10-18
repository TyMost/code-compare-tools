package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.example.codecompare.rebuild.repository.model.FileRecord;
import com.example.codecompare.rebuild.repository.support.StoragePurgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 扫描入口服务，负责协调全量扫描与增量扫描。
 */
public class FileScanService {

    private static final Logger log = LoggerFactory.getLogger(FileScanService.class);

    private final FullProjectScanService fullProjectScanService;
    private final GitChangeScanner gitChangeScanner;
    private final ScanResultRepository scanResultRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ProjectRootRegistry projectRootRegistry;
    private final ScanProperties scanProperties;
    private final Clock clock;
    private final StoragePurgeService storagePurgeService;

    public FileScanService(FullProjectScanService fullProjectScanService,
                           GitChangeScanner gitChangeScanner,
                           ScanResultRepository scanResultRepository,
                           ApplicationEventPublisher eventPublisher,
                           ProjectRootRegistry projectRootRegistry,
                           ScanProperties scanProperties,
                           Clock clock,
                           StoragePurgeService storagePurgeService) {
        this.fullProjectScanService = fullProjectScanService;
        this.gitChangeScanner = gitChangeScanner;
        this.scanResultRepository = scanResultRepository;
        this.eventPublisher = eventPublisher;
        this.projectRootRegistry = projectRootRegistry;
        this.scanProperties = scanProperties;
        this.clock = clock;
        this.storagePurgeService = storagePurgeService;
    }

    /**
     * 执行全量扫描，并触发扫描完成事件。
     */
    public ScanSummary scanAll(ProjectScanRequest request) {
        ProjectScanRequest normalized = normalize(request);
        log.info("触发全量扫描，项目：{}，根目录：{}", normalized.getProjectCode(), normalized.getProjectRoots());
        return executeFullScan(normalized, true);
    }

    /**
     * 清空项目的历史数据后重新执行一次全量扫描。
     */
    public ScanSummary reload(ProjectScanRequest request) {
        ProjectScanRequest normalized = normalize(request);
        log.info("执行重新加载流程，先清理后扫描，项目：{}", normalized.getProjectCode());
        storagePurgeService.purgeAll(normalized.getProjectCode());
        return executeFullScan(normalized, true);
    }

    public ScanSummary reload(String projectCode) {
        if (StringUtils.hasText(projectCode)) {
            ProjectRootRegistry.ProjectRootDescriptor descriptor =
                    projectRootRegistry.findByCode(projectCode).orElse(null);
            if (descriptor != null) {
                ProjectScanRequest request = ProjectScanRequest.builder()
                        .projectCode(projectCode)
                        .addRoot(descriptor.getPath())
                        .build();
                return reload(request);
            }
            log.warn("未找到项目编码对应的根目录，使用默认配置重新加载：{}", projectCode);
        }
        return reload((ProjectScanRequest) null);
    }

    /**
     * 预留的增量扫描逻辑，当前回退至全量摘要。
     */
    public ScanSummary scanIncremental(ProjectScanRequest request) {
        ProjectScanRequest normalized = normalize(request);
        log.info("尝试执行增量扫描，项目：{}", normalized.getProjectCode());
        GitIncrementalResult incremental = gitChangeScanner.scanIncremental(normalized);
        if (incremental == null || incremental.isEmpty()) {
            log.info("增量扫描返回空结果，使用最近一次全量扫描摘要。");
            return scanResultRepository.findLatestSummary(normalized.getProjectCode())
                    .orElse(ScanSummary.empty(normalized.getProjectCode(), clock.instant()));
        }
        List<FileRecord> records = incremental.getRecords();
        if (!CollectionUtils.isEmpty(records)) {
            scanResultRepository.saveAll(records);
        }
        ScanSummary summary = incremental.getSummary()
                .orElse(ScanSummary.empty(normalized.getProjectCode(), clock.instant()));
        scanResultRepository.saveSummary(summary);
        eventPublisher.publishEvent(new ScanCompletedEvent(this, summary, records, false));
        return summary;
    }

    private ScanSummary executeFullScan(ProjectScanRequest normalized, boolean fullRescan) {
        FullProjectScanService.FullProjectScanResult result = fullProjectScanService.scan(normalized);
        eventPublisher.publishEvent(new ScanCompletedEvent(
                this,
                result.getSummary(),
                result.getRecords(),
                fullRescan));
        log.info("全量扫描完成，耗时：{} ms，文件数：{}", result.getSummary().getDuration() == null ? 0
                : result.getSummary().getDuration().toMillis(),
                result.getSummary().getFilesScanned());
        return result.getSummary();
    }

    private ProjectScanRequest normalize(ProjectScanRequest request) {
        String projectCode = request != null && StringUtils.hasText(request.getProjectCode())
                ? request.getProjectCode()
                : defaultProjectCode();
        List<Path> roots = request != null && !CollectionUtils.isEmpty(request.getProjectRoots())
                ? toAbsolutePaths(request.getProjectRoots())
                : resolveDefaultRoots();
        if (CollectionUtils.isEmpty(roots)) {
            throw new IllegalStateException("扫描根目录不能为空，请通过配置或请求明确指定");
        }
        List<String> ignoreGlobs = request == null ? Collections.emptyList() : request.getIgnoreGlobs();
        return ProjectScanRequest.builder()
                .projectCode(projectCode)
                .roots(roots)
                .ignoreGlobs(ignoreGlobs)
                .skipHidden(request == null || request.isSkipHidden())
                .requestedAt(request == null ? Instant.now(clock) : request.getRequestedAt())
                .build();
    }

    private String defaultProjectCode() {
        List<Path> roots = projectRootRegistry.getRoots();
        if (!CollectionUtils.isEmpty(roots)) {
            Path first = roots.get(0);
            if (first != null && first.getFileName() != null) {
                return first.getFileName().toString();
            }
        }
        return "default-project";
    }

    private List<Path> resolveDefaultRoots() {
        List<Path> resolved = projectRootRegistry.getRoots();
        if (CollectionUtils.isEmpty(resolved)) {
            throw new IllegalStateException("扫描根目录不能为空，请通过配置或请求明确指定");
        }
        return new ArrayList<>(resolved);
    }
    private List<Path> toAbsolutePaths(List<Path> paths) {
        List<Path> result = new ArrayList<>(paths.size());
        for (Path path : paths) {
            if (path != null) {
                result.add(path.toAbsolutePath().normalize());
            }
        }
        return result;
    }
}
