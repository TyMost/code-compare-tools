package com.example.migratediff.api.controller;

import com.example.migratediff.api.dto.ApiResponse;
import com.example.migratediff.api.dto.DiffDetailRequestDTO;
import com.example.migratediff.api.dto.FileCommitHistoryDTO;
import com.example.migratediff.api.dto.MultiRepoExportRequestDTO;
import com.example.migratediff.api.dto.ScanRequestDTO;
import com.example.migratediff.api.dto.ScanResponseDTO;
import com.example.migratediff.api.dto.ScanTaskSummaryDTO;
import com.example.migratediff.api.mapper.ScanMapper;
import com.example.migratediff.application.commit.GitCommitHistoryService;
import com.example.migratediff.application.scan.CsvMultiRepoReportWriter;
import com.example.migratediff.application.scan.DiffDetail;
import com.example.migratediff.application.scan.DiffMatrixFilterCriteria;
import com.example.migratediff.application.scan.MultiRepoExportRequest;
import com.example.migratediff.application.scan.MultiRepoExportResult;
import com.example.migratediff.application.scan.MultiRepoExportService;
import com.example.migratediff.application.scan.ScanAppService;
import com.example.migratediff.application.scan.ScanInput;
import com.example.migratediff.application.scan.ScanMode;
import com.example.migratediff.application.scan.ScanPresetProperties;
import com.example.migratediff.application.scan.ScanReport;
import com.example.migratediff.application.scan.ScanResultStore;
import com.example.migratediff.application.scan.ScanSnapshotService;
import com.example.migratediff.shared.exception.NotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;
import javax.validation.Valid;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@Validated
@RequestMapping("/api/scan")
@Slf4j
public class ScanController {

    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withLocale(Locale.CHINA)
            .withZone(java.time.ZoneId.systemDefault());

    private final ScanAppService scanAppService;
    private final ScanMapper scanMapper;
    private final ScanPresetProperties presetProperties;
    private final ScanResultStore scanResultStore;
    private final MultiRepoExportService multiRepoExportService;
    private final CsvMultiRepoReportWriter csvReportWriter;
    private final ScanSnapshotService scanSnapshotService;
    private final GitCommitHistoryService gitCommitHistoryService;

    public ScanController(ScanAppService scanAppService,
                          ScanMapper scanMapper,
                          ScanPresetProperties presetProperties,
                           ScanResultStore scanResultStore,
                           MultiRepoExportService multiRepoExportService,
                           CsvMultiRepoReportWriter csvReportWriter,
                           ScanSnapshotService scanSnapshotService,
                           GitCommitHistoryService gitCommitHistoryService) {
        this.scanAppService = scanAppService;
        this.scanMapper = scanMapper;
        this.presetProperties = presetProperties;
        this.scanResultStore = scanResultStore;
        this.multiRepoExportService = multiRepoExportService;
        this.csvReportWriter = csvReportWriter;
        this.scanSnapshotService = scanSnapshotService;
        this.gitCommitHistoryService = gitCommitHistoryService;
    }

    @PostMapping("/full")
    public ApiResponse<?> scanFull(@Valid @RequestBody ScanRequestDTO requestDTO) {
        ScanInput input = scanMapper.toInput(requestDTO, ScanMode.FULL);
        ScanReport report = scanAppService.scan(input);
        ScanResponseDTO response = scanMapper.toResponse(report);
        scanSnapshotService.saveSnapshot(report, response);
        return ApiResponse.success(response);
    }

    @PostMapping
    public ApiResponse<?> scanIncremental(@Valid @RequestBody ScanRequestDTO requestDTO) {
        ScanInput input = scanMapper.toInput(requestDTO, ScanMode.INCREMENTAL);
        ScanReport report = scanAppService.scan(input);
        return ApiResponse.success(scanMapper.toResponse(report));
    }

    @PostMapping("/detail")
    public ApiResponse<?> detail(@Valid @RequestBody DiffDetailRequestDTO requestDTO) {
        DiffDetail detail = scanAppService.fetchDetail(requestDTO.getTaskId(), requestDTO.getFilePath())
                .orElseThrow(() -> new NotFoundException("Failed to locate scan record for file: " + requestDTO.getFilePath()));
        String migrationDiff = scanAppService.generateMigrationTemplate(detail);
        return ApiResponse.success(scanMapper.toDetailResponse(detail, migrationDiff));
    }

    @PostMapping("/commit-history")
    public ApiResponse<?> getCommitHistory(@Valid @RequestBody DiffDetailRequestDTO requestDTO) {
        try {
            // 从扫描报告中获取仓库配置
            ScanReport report = scanResultStore.find(requestDTO.getTaskId())
                    .orElseThrow(() -> new NotFoundException("Scan report not found for task: " + requestDTO.getTaskId()));
            
            FileCommitHistoryDTO commitHistory = gitCommitHistoryService.getFileCommitHistory(
                requestDTO.getFilePath(),
                report.getOracleSummary() != null ? report.getOracleSummary().getRepoConfig() : null,
                report.getGaussSummary() != null ? report.getGaussSummary().getRepoConfig() : null
            );
            
            return ApiResponse.success(commitHistory);
        } catch (Exception e) {
            log.error("Failed to get commit history for file: {} in task: {}", requestDTO.getFilePath(), requestDTO.getTaskId(), e);
            // 如果获取提交历史失败，返回空结果而不是错误
            return ApiResponse.success(FileCommitHistoryDTO.builder()
                    .filePath(requestDTO.getFilePath())
                    .oracleCommits(new ArrayList<>())
                    .gaussCommits(new ArrayList<>())
                    .totalCount(0)
                    .oracleCount(0)
                    .gaussCount(0)
                    .hasOracleCommits(false)
                    .hasGaussCommits(false)
                    .build());
        }
    }

    @GetMapping("/presets")
    public ApiResponse<?> listPresets() {
        return ApiResponse.success(scanMapper.toPresetDTOs(presetProperties.getPresets()));
    }

    @GetMapping("/tasks")
    public ApiResponse<?> listTasks() {
        List<ScanTaskSummaryDTO> tasks = scanResultStore.listRecent(50).stream()
                .map(scanMapper::toTaskSummary)
                .collect(Collectors.toList());
        return ApiResponse.success(tasks);
    }

    @GetMapping("/cache")
    public ApiResponse<?> listCachedSnapshots() {
        return ApiResponse.success(scanMapper.toCacheEntries(scanSnapshotService.listSnapshots()));
    }

    @DeleteMapping("/cache")
    public ApiResponse<?> clearCachedSnapshots() {
        scanSnapshotService.deleteAll();
        scanAppService.clearRuntimeCaches();
        return ApiResponse.success("缓存已清空", null);
    }

    @PostMapping(value = "/report/aggregate", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ByteArrayResource> exportAggregated(@Valid @RequestBody MultiRepoExportRequestDTO requestDTO) {
        MultiRepoExportRequest request = toExportRequest(requestDTO);
        if (!"csv".equals(request.getFormat())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "目前仅支?CSV 格式导出");
        }
        MultiRepoExportResult result = multiRepoExportService.export(request);
        byte[] bytes = csvReportWriter.write(result);
        ByteArrayResource resource = new ByteArrayResource(bytes);
        String filename = buildFileName(request.getFormat(), result);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentLength(bytes.length)
                .contentType(csvReportWriter.contentType())
                .body(resource);
    }

    private MultiRepoExportRequest toExportRequest(MultiRepoExportRequestDTO requestDTO) {
        DiffMatrixFilterCriteria criteria = DiffMatrixFilterCriteria.builder()
                .statuses(normalizeStatuses(requestDTO.getStatuses()))
                .coverageMin(requestDTO.getCoverageMin())
                .coverageMax(requestDTO.getCoverageMax())
                .includeEmptyCoverage(requestDTO.isIncludeEmptyCoverage())
                .build();
        List<MultiRepoExportRequest.RepoSelection> selections = requestDTO.getRepos().stream()
                .map(repo -> MultiRepoExportRequest.RepoSelection.builder()
                        .taskId(StringUtils.hasText(repo.getTaskId()) ? repo.getTaskId().trim() : null)
                        .presetName(StringUtils.hasText(repo.getPresetName()) ? repo.getPresetName().trim() : null)
                        .alias(StringUtils.hasText(repo.getAlias()) ? repo.getAlias().trim() : null)
                        .build())
                .collect(Collectors.toList());
        String format = sanitizeFormat(requestDTO.getFormat());
        return MultiRepoExportRequest.builder()
                .repos(selections)
                .filterCriteria(criteria)
                .format(format)
                .build();
    }

    private List<String> normalizeStatuses(List<String> statuses) {
        if (statuses == null) {
            return java.util.Collections.emptyList();
        }
        return statuses.stream()
                .filter(StringUtils::hasText)
                .map(status -> status.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    private String sanitizeFormat(String format) {
        if (!StringUtils.hasText(format)) {
            return "csv";
        }
        return format.trim().toLowerCase(Locale.ROOT);
    }

    private String buildFileName(String format, MultiRepoExportResult result) {
        String timestamp = FILE_NAME_FORMATTER.format(result.getGeneratedAt());
        return "scan-report-multi-" + timestamp + "." + format;
    }
}
