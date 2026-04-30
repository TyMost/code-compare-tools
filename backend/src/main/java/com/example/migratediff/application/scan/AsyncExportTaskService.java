package com.example.migratediff.application.scan;

import com.example.migratediff.api.dto.MultiRepoExportRequestDTO;
import com.example.migratediff.api.dto.RepoSelectionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 异步导出任务服务
 */
@Service
@Slf4j
public class AsyncExportTaskService {

    private final ConcurrentHashMap<String, ExportTask> taskStore = new ConcurrentHashMap<>();
    private final MultiRepoExportService multiRepoExportService;
    private final ExcelMultiRepoReportWriter excelWriter;
    private final CsvMultiRepoReportWriter csvWriter;

    @Value("${export.temp.directory:/tmp/exports}")
    private String exportTempDir;

    @Value("${export.file.cleanup.hours:24}")
    private int cleanupHours;

    @Value("${export.max.concurrent.tasks:5}")
    private int maxConcurrentTasks;

    private int currentProcessingTasks = 0;

    public AsyncExportTaskService(MultiRepoExportService multiRepoExportService,
                               ExcelMultiRepoReportWriter excelWriter,
                               CsvMultiRepoReportWriter csvWriter) {
        this.multiRepoExportService = multiRepoExportService;
        this.excelWriter = excelWriter;
        this.csvWriter = csvWriter;
    }

    /**
     * 创建导出任务
     */
    public String createExportTask(MultiRepoExportRequestDTO request) {
        // 检查并发限制
        if (currentProcessingTasks >= maxConcurrentTasks) {
            throw new IllegalStateException("当前并发导出任务数量已达上限: " + maxConcurrentTasks);
        }

        String taskId = UUID.randomUUID().toString();
        ExportTask task = ExportTask.builder()
                .taskId(taskId)
                .status(ExportTaskStatus.PENDING)
                .request(request)
                .createdAt(Instant.now())
                .progressPercentage(0)
                .build();

        taskStore.put(taskId, task);
        log.info("创建导出任务: {}, 格式: {}, 包含提交信息: {}", 
            taskId, request.getFormat(), request.isIncludeCommitInfo());

        // 异步执行导出
        processExportAsync(task);
        return taskId;
    }

    /**
     * 异步处理导出任务
     */
    @Async("exportTaskExecutor")
    public void processExportAsync(ExportTask task) {
        synchronized (this) {
            if (task.getStatus() != ExportTaskStatus.PENDING) {
                log.warn("任务状态已变更，跳过处理: {}", task.getTaskId());
                return;
            }
            
            if (currentProcessingTasks >= maxConcurrentTasks) {
                log.warn("并发任务数已达上限，延迟处理: {}", task.getTaskId());
                return;
            }
            
            currentProcessingTasks++;
            task.markAsStarted();
        }

        try {
            log.info("开始处理导出任务: {}", task.getTaskId());
            processExport(task);
        } catch (Exception e) {
            log.error("导出任务处理失败: {}", task.getTaskId(), e);
            task.markAsFailed("处理失败: " + e.getMessage());
        } finally {
            synchronized (this) {
                currentProcessingTasks--;
            }
        }
    }

    /**
     * 处理导出任务的核心逻辑
     */
    private void processExport(ExportTask task) {
        MultiRepoExportRequestDTO request = task.getRequest();
        String format = request.getFormat() != null ? request.getFormat() : "csv";
        
        try {
            // 生成文件名
            String fileName = generateFileName(request, format);
            Path filePath = Paths.get(exportTempDir, fileName);
            
            // 确保目录存在
            Files.createDirectories(filePath.getParent());

            // 更新进度：开始数据准备
            task.updateProgress("准备数据", "正在准备导出数据...", 0);

            // 获取导出数据
            MultiRepoExportResult exportResult = multiRepoExportService.export(convertToDomainRequest(request));
            
            // 更新进度：开始文件生成
            task.updateProgress("生成文件", "正在生成导出文件...", 50);

            // 生成文件
            byte[] fileData;
            if ("excel".equalsIgnoreCase(format)) {
                fileData = excelWriter.write(exportResult);
            } else {
                fileData = csvWriter.write(exportResult);
            }

            // 写入文件
            Files.write(filePath, fileData);

            // 更新进度：完成
            task.updateProgress("完成", "导出完成", 100);

            // 标记任务完成
            task.markAsCompleted(filePath.toString(), fileName, fileData.length);
            
            log.info("导出任务完成: {}, 文件: {}, 大小: {} bytes", 
                task.getTaskId(), fileName, fileData.length);

        } catch (Exception e) {
            log.error("导出任务失败: {}", task.getTaskId(), e);
            task.markAsFailed("导出失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务状态
     */
    public ExportTask getTaskStatus(String taskId) {
        ExportTask task = taskStore.get(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        return task;
    }

    /**
     * 获取任务文件
     */
    public Path getTaskFile(String taskId) {
        ExportTask task = getTaskStatus(taskId);
        if (!task.isDownloadable()) {
            throw new IllegalStateException("任务未完成或文件不可下载: " + taskId);
        }
        return Paths.get(task.getFilePath());
    }

    /**
     * 取消任务
     */
    public boolean cancelTask(String taskId) {
        ExportTask task = taskStore.get(taskId);
        if (task == null) {
            return false;
        }

        if (task.isFinished()) {
            return false;
        }

        task.markAsCancelled();
        log.info("任务已取消: {}", taskId);
        return true;
    }

    /**
     * 获取所有任务列表
     */
    public List<ExportTask> getAllTasks() {
        return new ArrayList<>(taskStore.values());
    }

    /**
     * 清理过期任务
     */
    public void cleanupExpiredTasks() {
        Instant cutoffTime = Instant.now().minusSeconds(cleanupHours * 3600L);
        
        taskStore.entrySet().removeIf(entry -> {
            ExportTask task = entry.getValue();
            
            // 清理超过24小时的已完成任务
            if (task.isFinished() && task.getCompletedAt().isBefore(cutoffTime)) {
                // 删除文件
                if (task.getFilePath() != null) {
                    try {
                        Files.deleteIfExists(Paths.get(task.getFilePath()));
                        log.debug("删除过期文件: {}", task.getFilePath());
                    } catch (IOException e) {
                        log.warn("删除过期文件失败: {}", task.getFilePath(), e);
                    }
                }
                
                log.debug("清理过期任务: {}", task.getTaskId());
                return true;
            }
            
            return false;
        });
    }

    /**
     * 获取当前并发任务数
     */
    public int getCurrentProcessingTasks() {
        return currentProcessingTasks;
    }

    /**
     * 获取最大并发任务数
     */
    public int getMaxConcurrentTasks() {
        return maxConcurrentTasks;
    }

    /**
     * 生成文件名
     */
    private String generateFileName(MultiRepoExportRequestDTO request, String format) {
        String timestamp = Instant.now().toString().replace(":", "-");
        String extension = "excel".equalsIgnoreCase(format) ? ".xls" : ".csv";
        
        if (request.getRepos() != null && request.getRepos().size() == 1) {
            // 单仓库
            String repoName = request.getRepos().get(0).getAlias();
            if (repoName == null || repoName.trim().isEmpty()) {
                repoName = request.getRepos().get(0).getPresetName();
            }
            if (repoName == null || repoName.trim().isEmpty()) {
                repoName = request.getRepos().get(0).getTaskId();
            }
            if (repoName == null || repoName.trim().isEmpty()) {
                repoName = "export";
            }
            return String.format("%s-%s%s", sanitizeFileName(repoName), timestamp, extension);
        } else {
            // 多仓库
            return String.format("multi-repo-export-%s%s", timestamp, extension);
        }
    }

    /**
     * 清理文件名中的非法字符
     */
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * 转换DTO到领域对象
     */
    private MultiRepoExportRequest convertToDomainRequest(MultiRepoExportRequestDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("导出请求不能为空");
        }

        // 转换筛选条件
        DiffMatrixFilterCriteria criteria = DiffMatrixFilterCriteria.builder()
                .statuses(normalizeStatuses(dto.getStatuses()))
                .coverageMin(dto.getCoverageMin() != null ? dto.getCoverageMin() : 0.0)
                .coverageMax(dto.getCoverageMax() != null ? dto.getCoverageMax() : 1.0)
                .includeEmptyCoverage(dto.isIncludeEmptyCoverage())
                .fileExtensions(normalizeFileExtensions(dto.getFileExtensions()))
                .excludeTestFiles(dto.isExcludeTestFiles())
                .excludePatterns(normalizeExcludePatterns(dto.getExcludePatterns()))
                .includeCommitInfo(dto.isIncludeCommitInfo())
                .authorTypeFilter(dto.getAuthorTypeFilter())
                .build();

        // 转换仓库选择
        List<MultiRepoExportRequest.RepoSelection> selections = new ArrayList<>();
        if (dto.getRepos() != null) {
            for (RepoSelectionDTO repoDto : dto.getRepos()) {
                if (repoDto != null) {
                    MultiRepoExportRequest.RepoSelection selection = MultiRepoExportRequest.RepoSelection.builder()
                            .taskId(StringUtils.hasText(repoDto.getTaskId()) ? repoDto.getTaskId().trim() : null)
                            .presetName(StringUtils.hasText(repoDto.getPresetName()) ? repoDto.getPresetName().trim() : null)
                            .alias(StringUtils.hasText(repoDto.getAlias()) ? repoDto.getAlias().trim() : null)
                            .build();
                    selections.add(selection);
                }
            }
        }

        String format = StringUtils.hasText(dto.getFormat()) ? dto.getFormat().trim().toLowerCase() : "csv";

        return MultiRepoExportRequest.builder()
                .repos(selections)
                .filterCriteria(criteria)
                .format(format)
                .build();
    }

    private List<String> normalizeStatuses(List<String> statuses) {
        if (statuses == null) {
            return new ArrayList<>();
        }
        return statuses.stream()
                .filter(StringUtils::hasText)
                .map(status -> status.trim().toLowerCase())
                .collect(Collectors.toList());
    }

    private List<String> normalizeFileExtensions(List<String> fileExtensions) {
        if (fileExtensions == null) {
            return new ArrayList<>();
        }
        return fileExtensions.stream()
                .filter(StringUtils::hasText)
                .map(ext -> ext.trim().toLowerCase())
                .collect(Collectors.toList());
    }

    private List<String> normalizeExcludePatterns(List<String> excludePatterns) {
        if (excludePatterns == null) {
            return new ArrayList<>();
        }
        return excludePatterns.stream()
                .filter(StringUtils::hasText)
                .map(pattern -> pattern.trim())
                .collect(Collectors.toList());
    }

    private List<String> normalizeAuthorFilters(List<String> authorFilters) {
        if (authorFilters == null) {
            return new ArrayList<>();
        }
        return authorFilters.stream()
                .filter(StringUtils::hasText)
                .map(author -> author.trim())
                .collect(Collectors.toList());
    }
}
