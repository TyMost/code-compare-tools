package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry.ProjectRootDescriptor;
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
 * Coordinates full and incremental project scans and publishes their results.
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
     * Executes a full rescan and persists the result.
     */
    public ScanSummary scanAll(ProjectScanRequest request) {
        ProjectScanRequest normalized = normalize(request);
        log.info("Starting full scan, project={}, roots={}",
                normalized.getProjectCode(), normalized.getProjectRoots());
        return executeFullScan(normalized, true);
    }

    /**
     * Clears stored information for the given project and performs a fresh full scan.
     */
    public ScanSummary reload(ProjectScanRequest request) {
        ProjectScanRequest normalized = normalize(request);
        log.info("Reload requested, refreshing project {}", normalized.getProjectCode());
        storagePurgeService.purgeAll(normalized.getProjectCode());
        return executeFullScan(normalized, true);
    }

    public ScanSummary reload(String projectCode) {
        if (StringUtils.hasText(projectCode)) {
            ProjectRootDescriptor descriptor = projectRootRegistry.findByCode(projectCode).orElse(null);
            if (descriptor != null) {
                ProjectScanRequest request = ProjectScanRequest.builder()
                        .projectCode(projectCode)
                        .addRoot(descriptor.getPath())
                        .build();
                return reload(request);
            }
            log.warn("Project code {} not found in registry, falling back to defaults.", projectCode);
        }
        return reload((ProjectScanRequest) null);
    }

    /**
     * Performs an incremental scan for the given project, defaulting to the shared registry roots
     * when no explicit descriptor can be resolved.
     */
    public ScanSummary scanIncremental(String projectCode) {
        if (StringUtils.hasText(projectCode)) {
            ProjectRootDescriptor descriptor = projectRootRegistry.findByCode(projectCode).orElse(null);
            if (descriptor != null) {
                ProjectScanRequest request = ProjectScanRequest.builder()
                        .projectCode(projectCode)
                        .addRoot(descriptor.getPath())
                        .build();
                return scanIncremental(request);
            }
            log.warn("Project code {} not found in registry, falling back to defaults.", projectCode);
        }
        return scanIncremental((ProjectScanRequest) null);
    }

    /**
     * Performs an incremental scan based on Git changes. Falls back to the latest full summary if no
     * delta is produced.
     */
    public ScanSummary scanIncremental(ProjectScanRequest request) {
        ProjectScanRequest normalized = normalize(request);
        log.info("Starting incremental scan, project={}", normalized.getProjectCode());

        if ("git".equalsIgnoreCase(scanProperties.getDiffEngine())) {
            log.debug("Purging stored artifacts before Git incremental scan, project={}", normalized.getProjectCode());
            storagePurgeService.purgeAll(normalized.getProjectCode());
        }

        GitIncrementalResult incremental = gitChangeScanner.scanIncremental(normalized);
        if (incremental == null || incremental.isEmpty()) {
            log.info("Incremental scan returned empty result, using latest stored summary.");
            return scanResultRepository.findLatestSummary(normalized.getProjectCode())
                    .orElse(ScanSummary.empty(normalized.getProjectCode(), clock.instant()));
        }

        List<FileRecord> records = incremental.getRecords();
        log.debug("Incremental scan produced {} records and {} git diff files for project {}",
                records == null ? 0 : records.size(),
                incremental.getGitDiffFiles() == null ? 0 : incremental.getGitDiffFiles().size(),
                normalized.getProjectCode());

        if (!CollectionUtils.isEmpty(records)) {
            scanResultRepository.saveAll(records);
        }

        ScanSummary summary = incremental.getSummary()
                .orElse(ScanSummary.empty(normalized.getProjectCode(), clock.instant()));
        log.debug("Incremental summary: projectCode={}, baseCommits={}, latestCommits={}",
                summary.getProjectCode(), summary.getBaseCommits(), summary.getLatestCommits());

        scanResultRepository.saveSummary(summary);
        eventPublisher.publishEvent(new ScanCompletedEvent(
                this,
                summary,
                records,
                incremental.getGitDiffFiles(),
                false));
        return summary;
    }

    private ScanSummary executeFullScan(ProjectScanRequest normalized, boolean fullRescan) {
        FullProjectScanService.FullProjectScanResult result = fullProjectScanService.scan(normalized);
        eventPublisher.publishEvent(new ScanCompletedEvent(
                this,
                result.getSummary(),
                result.getRecords(),
                Collections.emptyList(),
                fullRescan));

        long durationMs = result.getSummary().getDuration() == null
                ? 0L
                : result.getSummary().getDuration().toMillis();
        log.info("Full scan completed in {} ms, files scanned={}",
                durationMs, result.getSummary().getFilesScanned());
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
            throw new IllegalStateException("Project scan roots must not be empty.");
        }
        List<String> ignoreGlobs = request == null ? Collections.emptyList() : request.getIgnoreGlobs();
        Instant requestedAt = request == null ? Instant.now(clock) : request.getRequestedAt();

        return ProjectScanRequest.builder()
                .projectCode(projectCode)
                .roots(roots)
                .ignoreGlobs(ignoreGlobs)
                .skipHidden(request == null || request.isSkipHidden())
                .requestedAt(requestedAt)
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
            throw new IllegalStateException("Project scan roots must not be empty.");
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
