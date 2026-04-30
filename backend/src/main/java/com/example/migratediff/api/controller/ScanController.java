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
import com.example.migratediff.application.scan.ExcelMultiRepoReportWriter;
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
import com.example.migratediff.application.scan.AsyncExportTaskService;
import com.example.migratediff.application.scan.ExportTask;
import com.example.migratediff.domain.coverage.BlockMapping;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;
import javax.validation.Valid;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final ExcelMultiRepoReportWriter excelReportWriter;
    private final ScanSnapshotService scanSnapshotService;
    private final GitCommitHistoryService gitCommitHistoryService;
    private final AsyncExportTaskService asyncExportTaskService;
    private final com.example.migratediff.application.CoverageAppService coverageAppService;

    public ScanController(ScanAppService scanAppService,
                          ScanMapper scanMapper,
                          ScanPresetProperties presetProperties,
                           ScanResultStore scanResultStore,
                           MultiRepoExportService multiRepoExportService,
                           CsvMultiRepoReportWriter csvReportWriter,
                           ExcelMultiRepoReportWriter excelReportWriter,
                          ScanSnapshotService scanSnapshotService,
                          GitCommitHistoryService gitCommitHistoryService,
                          AsyncExportTaskService asyncExportTaskService,
                          com.example.migratediff.application.CoverageAppService coverageAppService) {
        this.scanAppService = scanAppService;
        this.scanMapper = scanMapper;
        this.presetProperties = presetProperties;
        this.scanResultStore = scanResultStore;
        this.multiRepoExportService = multiRepoExportService;
        this.csvReportWriter = csvReportWriter;
        this.excelReportWriter = excelReportWriter;
        this.scanSnapshotService = scanSnapshotService;
        this.gitCommitHistoryService = gitCommitHistoryService;
        this.asyncExportTaskService = asyncExportTaskService;
        this.coverageAppService = coverageAppService;
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

    /**
     * 获取文件的块映射关系
     */
    @PostMapping("/block-mapping")
    public ApiResponse<?> getBlockMapping(@Valid @RequestBody DiffDetailRequestDTO requestDTO) {
        DiffDetail detail = scanAppService.fetchDetail(requestDTO.getTaskId(), requestDTO.getFilePath())
                .orElseThrow(() -> new NotFoundException("Failed to locate scan record for file: " + requestDTO.getFilePath()));
        BlockMapping blockMapping = scanAppService.getBlockMapping(detail);
        return ApiResponse.success(blockMapping);
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

    /**
     * 动态添加配置到预设列表
     */
    @PostMapping("/add-preset")
    public ApiResponse<?> addPreset(@RequestBody Map<String, Object> request) {
        try {
            String name = (String) request.get("name");
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) request.get("source");
            @SuppressWarnings("unchecked")
            Map<String, Object> target = (Map<String, Object>) request.get("target");
            
            // 创建新的预设
            ScanPresetProperties.ScanPreset newPreset = new ScanPresetProperties.ScanPreset();
            newPreset.setName(name);
            
            // 设置源仓库配置
            ScanPresetProperties.RepoPreset sourcePreset = new ScanPresetProperties.RepoPreset();
            sourcePreset.setCode((String) source.get("code"));
            sourcePreset.setPath((String) source.get("path"));
            sourcePreset.setScanStrategy("SNAPSHOT");
            sourcePreset.setTimeFrom((String) source.get("timeFrom"));
            sourcePreset.setTimeTo((String) source.get("timeTo"));
            sourcePreset.setDeltaType("DELTA_O");
            sourcePreset.setIncludeWorkingTree(false);
            sourcePreset.setFetchIfMissing(true);
            sourcePreset.setRemoteName("origin");
            // 新增：处理快照选项
            sourcePreset.setSnapshotIncludeRemoteRefs(Boolean.TRUE.equals(source.get("snapshotIncludeRemoteRefs")));
            sourcePreset.setSnapshotIncludeTags(Boolean.TRUE.equals(source.get("snapshotIncludeTags")));
            sourcePreset.setSnapshotMaxRefs((Integer) source.getOrDefault("snapshotMaxRefs", 256));
            newPreset.setSource(sourcePreset);
            
            // 设置目标仓库配置
            ScanPresetProperties.RepoPreset targetPreset = new ScanPresetProperties.RepoPreset();
            targetPreset.setCode((String) target.get("code"));
            targetPreset.setPath((String) target.get("path"));
            targetPreset.setScanStrategy("SNAPSHOT");
            targetPreset.setTimeFrom((String) target.get("timeFrom"));
            targetPreset.setTimeTo((String) target.get("timeTo"));
            targetPreset.setDeltaType("DELTA_G");
            targetPreset.setIncludeWorkingTree(false);
            targetPreset.setFetchIfMissing(true);
            targetPreset.setRemoteName("origin");
            // 新增：处理快照选项
            targetPreset.setSnapshotIncludeRemoteRefs(Boolean.TRUE.equals(target.get("snapshotIncludeRemoteRefs")));
            targetPreset.setSnapshotIncludeTags(Boolean.TRUE.equals(target.get("snapshotIncludeTags")));
            targetPreset.setSnapshotMaxRefs((Integer) target.getOrDefault("snapshotMaxRefs", 256));
            newPreset.setTarget(targetPreset);
            
            // 追加到预设列表
            List<ScanPresetProperties.ScanPreset> currentPresets = new ArrayList<>(presetProperties.getPresets());
            currentPresets.add(newPreset);
            presetProperties.setPresets(currentPresets);
            
            log.info("成功添加新预设: {} (时间范围: {} 至 {})", 
                name, source.get("timeFrom"), source.get("timeTo"));
            return ApiResponse.success("配置已添加，请重启后端服务使配置生效", null);
            
        } catch (Exception e) {
            log.error("添加配置失败", e);
            return ApiResponse.<Object>error("添加配置失败: " + e.getMessage());
        }
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
        log.info("收到多仓库导出请求: repos={}, format={}", 
            requestDTO.getRepos().size(), requestDTO.getFormat());
        
        // 记录每个仓库选择的详细信息
        requestDTO.getRepos().forEach(repo -> 
            log.debug("仓库选择: taskId={}, presetName={}, alias={}", 
                repo.getTaskId(), repo.getPresetName(), repo.getAlias()));
        
        MultiRepoExportRequest request = toExportRequest(requestDTO);
        log.debug("转换后的请求: repos={}, format={}", 
            request.getRepos().size(), request.getFormat());
        
        try {
            MultiRepoExportResult result = multiRepoExportService.export(request);
            log.info("多仓库导出完成: 生成文件，仓库数量={}", result.getRepoReports().size());
            
            byte[] bytes;
            MediaType contentType;
            
            if ("excel".equals(request.getFormat())) {
                bytes = excelReportWriter.write(result);
                contentType = excelReportWriter.contentType();
            } else {
                // 默认使用CSV格式
                bytes = csvReportWriter.write(result);
                contentType = csvReportWriter.contentType();
            }
            
            ByteArrayResource resource = new ByteArrayResource(bytes);
            String filename = buildFileName(request.getFormat(), result);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentLength(bytes.length)
                    .contentType(contentType)
                    .body(resource);
        } catch (Exception e) {
            log.error("多仓库导出失败: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "导出失败: " + e.getMessage());
        }
    }

    private MultiRepoExportRequest toExportRequest(MultiRepoExportRequestDTO requestDTO) {
        DiffMatrixFilterCriteria criteria = DiffMatrixFilterCriteria.builder()
                .statuses(normalizeStatuses(requestDTO.getStatuses()))
                .coverageMin(requestDTO.getCoverageMin())
                .coverageMax(requestDTO.getCoverageMax())
                .includeEmptyCoverage(requestDTO.isIncludeEmptyCoverage())
                .fileExtensions(normalizeFileExtensions(requestDTO.getFileExtensions()))
                .excludeTestFiles(requestDTO.isExcludeTestFiles())
                .excludePatterns(normalizeExcludePatterns(requestDTO.getExcludePatterns()))
                .includeCommitInfo(requestDTO.isIncludeCommitInfo())
                .authorTypeFilter(requestDTO.getAuthorTypeFilter())
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

    private List<String> normalizeFileExtensions(List<String> fileExtensions) {
        if (fileExtensions == null) {
            return java.util.Collections.emptyList();
        }
        return fileExtensions.stream()
                .filter(StringUtils::hasText)
                .map(ext -> ext.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
    }

    private List<String> normalizeExcludePatterns(List<String> excludePatterns) {
        if (excludePatterns == null) {
            return java.util.Collections.emptyList();
        }
        return excludePatterns.stream()
                .filter(StringUtils::hasText)
                .map(pattern -> pattern.trim())
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

    // ========== 异步导出相关API ==========

    /**
     * 创建异步导出任务
     */
    @PostMapping("/export/async/create")
    public ApiResponse<?> createAsyncExportTask(@Valid @RequestBody MultiRepoExportRequestDTO requestDTO) {
        log.info("收到异步导出任务创建请求: repos={}, format={}", 
            requestDTO.getRepos().size(), requestDTO.getFormat());
        
        try {
            String taskId = asyncExportTaskService.createExportTask(requestDTO);
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("taskId", taskId);
            result.put("status", "PENDING");
            result.put("message", "任务已提交，正在处理中...");
            return ApiResponse.success("异步导出任务已创建", result);
        } catch (IllegalStateException e) {
            log.warn("异步导出任务创建失败: {}", e.getMessage());
            return ApiResponse.<Object>error(e.getMessage());
        } catch (Exception e) {
            log.error("异步导出任务创建失败", e);
            return ApiResponse.<Object>error("创建异步导出任务失败: " + e.getMessage());
        }
    }

    /**
     * 查询异步导出任务状态
     */
    @GetMapping("/export/async/status/{taskId}")
    public ApiResponse<?> getAsyncExportTaskStatus(@PathVariable String taskId) {
        try {
            ExportTask task = asyncExportTaskService.getTaskStatus(taskId);
            
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("taskId", task.getTaskId());
            result.put("status", task.getStatus().name());
            result.put("progressPercentage", task.getProgressPercentage());
            result.put("currentStage", task.getCurrentStage());
            result.put("progressMessage", task.getProgressMessage());
            result.put("createdAt", task.getCreatedAt());
            result.put("startedAt", task.getStartedAt());
            result.put("completedAt", task.getCompletedAt());
            result.put("isProcessing", task.isProcessing());
            result.put("isFinished", task.isFinished());
            result.put("isDownloadable", task.isDownloadable());
            result.put("fileName", task.getFileName());
            result.put("fileSize", task.getFileSize());
            
            if (task.getErrorMessage() != null) {
                result = new java.util.HashMap<>(result);
                result.put("errorMessage", task.getErrorMessage());
            }
            
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.<Object>error(e.getMessage());
        } catch (Exception e) {
            log.error("查询异步导出任务状态失败: {}", taskId, e);
            return ApiResponse.<Object>error("查询任务状态失败: " + e.getMessage());
        }
    }

    /**
     * 下载异步导出任务生成的文件
     */
    @GetMapping("/export/async/download/{taskId}")
    public ResponseEntity<ByteArrayResource> downloadAsyncExportFile(@PathVariable String taskId) {
        try {
            ExportTask task = asyncExportTaskService.getTaskStatus(taskId);
            
            if (!task.isDownloadable()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "任务未完成或文件不可下载，当前状态: " + task.getStatus());
            }
            
            java.nio.file.Path filePath = asyncExportTaskService.getTaskFile(taskId);
            byte[] fileData = java.nio.file.Files.readAllBytes(filePath);
            
            ByteArrayResource resource = new ByteArrayResource(fileData);
            MediaType mediaType = task.getFileName().endsWith(".xls") ?
                MediaType.parseMediaType("application/vnd.ms-excel") :
                MediaType.parseMediaType("text/csv");
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                        "attachment; filename=\"" + task.getFileName() + "\"")
                    .contentLength(fileData.length)
                    .contentType(mediaType)
                    .body(resource);
                    
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("下载异步导出文件失败: {}", taskId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                "下载文件失败: " + e.getMessage());
        }
    }

    /**
     * 取消异步导出任务
     */
    @DeleteMapping("/export/async/cancel/{taskId}")
    public ApiResponse<?> cancelAsyncExportTask(@PathVariable String taskId) {
        try {
            boolean cancelled = asyncExportTaskService.cancelTask(taskId);
            if (cancelled) {
                Map<String, Object> cancelResult = new java.util.HashMap<>();
                cancelResult.put("taskId", taskId);
                return ApiResponse.success("任务已取消", cancelResult);
            } else {
                return ApiResponse.<Object>error("任务无法取消，可能已完成或不存在");
            }
        } catch (Exception e) {
            log.error("取消异步导出任务失败: {}", taskId, e);
            return ApiResponse.<Object>error("取消任务失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有异步导出任务列表
     */
    @GetMapping("/export/async/tasks")
    public ApiResponse<?> getAllAsyncExportTasks() {
        try {
            List<ExportTask> tasks = asyncExportTaskService.getAllTasks();
            
            java.util.List<Map<String, Object>> taskList = tasks.stream()
                .map(task -> {
                    Map<String, Object> taskInfo = new java.util.HashMap<>();
                    taskInfo.put("taskId", task.getTaskId());
                    taskInfo.put("status", task.getStatus().name());
                    taskInfo.put("progressPercentage", task.getProgressPercentage());
                    taskInfo.put("currentStage", task.getCurrentStage());
                    taskInfo.put("progressMessage", task.getProgressMessage());
                    taskInfo.put("createdAt", task.getCreatedAt());
                    taskInfo.put("startedAt", task.getStartedAt());
                    taskInfo.put("completedAt", task.getCompletedAt());
                    taskInfo.put("isProcessing", task.isProcessing());
                    taskInfo.put("isFinished", task.isFinished());
                    taskInfo.put("isDownloadable", task.isDownloadable());
                    taskInfo.put("fileName", task.getFileName());
                    taskInfo.put("fileSize", task.getFileSize());
                    
                    if (task.getErrorMessage() != null) {
                        taskInfo.put("errorMessage", task.getErrorMessage());
                    }
                    
                    return taskInfo;
                })
                .collect(Collectors.toList());
            
            Map<String, Object> resultList = new java.util.HashMap<>();
            resultList.put("tasks", taskList);
            resultList.put("total", taskList.size());
            resultList.put("currentProcessing", asyncExportTaskService.getCurrentProcessingTasks());
            resultList.put("maxConcurrent", asyncExportTaskService.getMaxConcurrentTasks());
            return ApiResponse.success(resultList);
        } catch (Exception e) {
            log.error("获取异步导出任务列表失败", e);
            return ApiResponse.<Object>error("获取任务列表失败: " + e.getMessage());
        }
    }

    /**
     * 清理过期的异步导出任务
     */
    @DeleteMapping("/export/async/cleanup")
    public ApiResponse<?> cleanupExpiredAsyncExportTasks() {
        try {
            asyncExportTaskService.cleanupExpiredTasks();
            return ApiResponse.success("过期任务清理完成", null);
        } catch (Exception e) {
            log.error("清理过期异步导出任务失败", e);
            return ApiResponse.<Object>error("清理任务失败: " + e.getMessage());
        }
    }

    // ========== 覆盖率算法相关API ==========

    /**
     * 获取当前覆盖率算法信息
     */
    @GetMapping("/coverage/algorithm")
    public ApiResponse<?> getCurrentAlgorithm() {
        try {
            Map<String, Object> result = new java.util.HashMap<>();
            result.put("current", coverageAppService.getCurrentAlgorithm());
            result.put("available", coverageAppService.getAvailableAlgorithms());
            
            // 添加算法描述
            Map<String, String> descriptions = new java.util.HashMap<>();
            descriptions.put("legacy", "传统算法：基于位置窗口的块匹配，使用Jaccard相似度");
            descriptions.put("strong", "强匹配算法：基于业务特征的全局匹配，使用平均分聚合");
            result.put("descriptions", descriptions);
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取覆盖率算法信息失败", e);
            return ApiResponse.<Object>error("获取算法信息失败: " + e.getMessage());
        }
    }
}
