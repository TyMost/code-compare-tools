package com.example.migratediff.application.scan;

import com.example.migratediff.application.CoverageAppService;
import com.example.migratediff.application.DiffAppService;
import com.example.migratediff.application.GenerateAppService;
import com.example.migratediff.application.MigrationAppService;
import com.example.migratediff.domain.coverage.CoverageDetail;
import com.example.migratediff.domain.coverage.CoverageSummary;
import com.example.migratediff.domain.diff.DeltaGroup;
import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.domain.migration.DecisionType;
import com.example.migratediff.domain.migration.MigrationResult;
import com.example.migratediff.domain.migration.MigrationSummary;
import com.example.migratediff.domain.migration.MigrationTask;
import com.example.migratediff.infrastructure.persistence.ScanReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.lang.Nullable;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ScanAppService {

    private final DiffAppService diffAppService;
    private final CoverageAppService coverageAppService;
    private final GenerateAppService generateAppService;
    private final MigrationAppService migrationAppService;
    private final ScanResultStore scanResultStore;
    private final ConcurrentMap<MigrationKey, String> migrationTaskIndex = new ConcurrentHashMap<>();
    private final ScanReportRepository scanReportRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ScanAppService(DiffAppService diffAppService,
                          CoverageAppService coverageAppService,
                          GenerateAppService generateAppService,
                          MigrationAppService migrationAppService,
                          ScanResultStore scanResultStore,
                          @Nullable ScanReportRepository scanReportRepository) {
        this.diffAppService = diffAppService;
        this.coverageAppService = coverageAppService;
        this.generateAppService = generateAppService;
        this.migrationAppService = migrationAppService;
        this.scanResultStore = scanResultStore;
        this.scanReportRepository = scanReportRepository;
    }

    public ScanReport scan(ScanInput input) {
        DiffSummary oracle = diffAppService.generateDiff(input.getOracleSummary());
        DiffSummary gauss = diffAppService.generateDiff(input.getGaussSummary());
        CoverageSummary coverageSummary = coverageAppService.analyzeCoverage(
                input.getTaskId(),
                oracle,
                gauss,
                input.isPersistResult()
        );
        ScanReport report = new ScanReport(
                input.getTaskId(),
                input.getMode(),
                input.getPresetName(),
                input.getRepoId(),
                input.getRepoName(),
                input.isPersistResult(),
                oracle,
                gauss,
                coverageSummary);
        scanResultStore.save(report);
        persistReport(report);
        return report;
    }

    public Optional<DiffDetail> fetchDetail(String taskId, String filePath) {
        String normalizedPath = normalizeFilePath(filePath);
        return resolveReport(taskId)
                .flatMap(report -> buildDetail(report, normalizedPath));
    }

    public MigrationOperationResult generateMigration(String taskId, String filePath) {
        PreparedMigrationContext context = prepareMigrationContext(taskId, filePath);
        MigrationSummary previewed = migrationAppService.preview(context.summary);
        String normalizedPath = normalizeFilePath(filePath);
        recordMigrationTask(context.report.getTaskId(), normalizedPath, previewed);
        return new MigrationOperationResult(context.report.getTaskId(), normalizedPath, previewed.getResult());
    }

    public MigrationOperationResult applyMigration(String taskId, String filePath) {
        PreparedMigrationContext context = prepareMigrationContext(taskId, filePath);
        MigrationSummary applied = migrationAppService.apply(context.summary);
        String normalizedPath = normalizeFilePath(filePath);
        recordMigrationTask(context.report.getTaskId(), normalizedPath, applied);
        attachLogId(applied.getResult());
        return new MigrationOperationResult(context.report.getTaskId(), normalizedPath, applied.getResult());
    }

    public MigrationOperationResult revertMigration(String taskId, String filePath) {
        PreparedMigrationContext context = prepareMigrationContext(taskId, filePath);
        String normalizedPath = normalizeFilePath(filePath);
        String migrationTaskId = resolveMigrationTaskId(context.report.getTaskId(), normalizedPath);
        if (!StringUtils.hasText(migrationTaskId)) {
            throw new IllegalStateException("Migration task not found, please generate first");
        }
        MigrationTask task = new MigrationTask();
        task.setId(migrationTaskId);
        MigrationResult result = migrationAppService.revert(task);
        attachLogId(result);
        return new MigrationOperationResult(context.report.getTaskId(), normalizedPath, result);
    }

    private Optional<DiffDetail> buildDetail(ScanReport report, String filePath) {
        if (report == null || !StringUtils.hasText(filePath)) {
            return Optional.empty();
        }
        DiffFile oracleFile = report.findOracle(filePath).orElse(null);
        DiffFile gaussFile = report.findGauss(filePath).orElse(null);
        if (oracleFile == null && gaussFile == null) {
            return Optional.empty();
        }
        CoverageDetail coverageDetail = report.findCoverage(filePath).orElse(null);
        return Optional.of(new DiffDetail(report.getTaskId(), filePath, oracleFile, gaussFile, coverageDetail));
    }

    private PreparedMigrationContext prepareMigrationContext(String taskId, String filePath) {
        String normalizedPath = normalizeFilePath(filePath);
        ScanReport report = resolveReport(taskId)
                .orElseThrow(() -> new IllegalStateException("No cached scan result available, please run a scan first"));
        DiffFile oracleFile = report.findOracle(normalizedPath).orElse(null);
        DiffFile gaussFile = report.findGauss(normalizedPath).orElse(null);
        if (oracleFile == null && gaussFile == null) {
            throw new IllegalStateException("Requested file not present in scan result: " + normalizedPath);
        }

        MigrationSummary summary = new MigrationSummary();
        summary.setDeltaOSummary(report.overviewForOracleFile(normalizedPath));
        summary.setDeltaGSummary(report.overviewForGaussFile(normalizedPath));

        MigrationTask task = new MigrationTask();
        task.setDeltaGroup(DeltaGroup.builder()
                .deltaO(oracleFile)
                .deltaG(gaussFile)
                .intersectBlocks(intersectBlocks(oracleFile, gaussFile))
                .build());
        summary.setTask(task);
        return new PreparedMigrationContext(report, summary);
    }

    private void recordMigrationTask(String taskId, String filePath, MigrationSummary summary) {
        if (summary == null || summary.getTask() == null || !StringUtils.hasText(summary.getTask().getId())) {
            return;
        }
        MigrationKey key = new MigrationKey(taskId, normalizeFilePath(filePath));
        migrationTaskIndex.put(key, summary.getTask().getId());
    }

    private String resolveMigrationTaskId(String taskId, String filePath) {
        MigrationKey key = new MigrationKey(taskId, normalizeFilePath(filePath));
        return migrationTaskIndex.get(key);
    }

    private List<DiffBlock> intersectBlocks(DiffFile oracleFile, DiffFile gaussFile) {
        // Simplified placeholder: downstream services will compute intersections when needed
        return new ArrayList<>();
    }
    public String generateMigrationTemplate(DiffDetail detail) {
        if (detail == null) {
            return "";
        }
        List<DiffBlock> oracleBlocks = extractBlocks(detail.getOracleFile());
        List<DiffBlock> gaussBlocks = extractBlocks(detail.getGaussFile());
        if (CollectionUtils.isEmpty(oracleBlocks) && CollectionUtils.isEmpty(gaussBlocks)) {
            return "";
        }
        int maxSize = Math.max(oracleBlocks.size(), gaussBlocks.size());
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < maxSize; index++) {
            DiffBlock oracleBlock = index < oracleBlocks.size() ? oracleBlocks.get(index) : null;
            DiffBlock gaussBlock = index < gaussBlocks.size() ? gaussBlocks.get(index) : null;
            String oracleSnippet = snippetOf(oracleBlock);
            String gaussSnippet = snippetOf(gaussBlock);
            DecisionType decision = generateAppService.classifyBlock(oracleSnippet, gaussSnippet);
            String fragment = generateAppService.generateTemplate(oracleSnippet, gaussSnippet, decision);
            fragment = adjustGaussSection(fragment, gaussBlock, oracleBlock);
            if (StringUtils.hasText(fragment)) {
                builder.append(removeBom(fragment)).append(System.lineSeparator());
            }
        }
        return removeBom(builder.toString()).trim();
    }

    private List<DiffBlock> extractBlocks(DiffFile file) {
        return file == null || CollectionUtils.isEmpty(file.getBlocks()) ? new ArrayList<>() : file.getBlocks();
    }

    /**
     * 使用 Gauss 差异中的最新实现替换模板中“目标仓库原实现”片段，确保生成文本展示 g2 版本内容。
     */
    private String adjustGaussSection(String fragment, DiffBlock gaussBlock, DiffBlock fallbackBlock) {
        if (!StringUtils.hasText(fragment)) {
            return fragment;
        }
        final String startTag = "/** 目标仓库原实现开始 */";
        final String endTag = "/** 目标仓库原实现结束 */";
        int start = fragment.indexOf(startTag);
        int end = fragment.indexOf(endTag);
        if (start < 0 || end <= start) {
            return fragment;
        }
        DiffBlock sourceBlock = gaussBlock != null ? gaussBlock : fallbackBlock;
        String rawContent = sourceBlock != null ? sourceBlock.getContentTo() : null;
        if (!StringUtils.hasText(rawContent)) {
            return fragment;
        }
        String normalized = removeBom(rawContent);
        if (!StringUtils.hasText(normalized)) {
            return fragment;
        }
        String newline = System.lineSeparator();
        String replacement = newline + normalized + newline;
        String prefix = fragment.substring(0, start + startTag.length());
        String suffix = fragment.substring(end);
        return prefix + replacement + suffix;
    }

    private void attachLogId(MigrationResult result) {
        if (result == null) {
            return;
        }
        result.setMessage(StringUtils.hasText(result.getMessage()) ? result.getMessage() : "Operation completed");
        result.setPreviewContent(result.getPreviewContent() == null ? "" : result.getPreviewContent());
        if (result.getAffectedFiles() == null) {
            result.setAffectedFiles(new ArrayList<>());
        }
        if (result.getBlockResults() == null) {
            result.setBlockResults(new ArrayList<>());
        }
        result.setLogId(Math.abs(secureRandom.nextLong()));
    }

    private String removeBom(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value
                .replace("\uFEFF", "")
                .replace(String.valueOf('\u00EF'), "")
                .replace(String.valueOf('\u00BB'), "")
                .replace(String.valueOf('\u00BF'), "");
    }

    private String snippetOf(DiffBlock block) {
        return block == null ? "" : block.getContentTo();
    }

    public void clearRuntimeCaches() {
        scanResultStore.clear();
        migrationTaskIndex.clear();
    }

    private void persistReport(ScanReport report) {
        if (report == null || scanReportRepository == null || !report.isPersisted()) {
            return;
        }
        scanReportRepository.save(report);
    }

    private Optional<ScanReport> resolveReport(String taskId) {
        Optional<ScanReport> report = scanResultStore.find(taskId);
        if (report.isPresent()) {
            return report;
        }
        if (!StringUtils.hasText(taskId) || scanReportRepository == null) {
            return report;
        }
        return scanReportRepository.find(taskId)
                .map(loaded -> {
                    scanResultStore.save(loaded);
                    return loaded;
                });
    }

    private String normalizeFilePath(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return filePath;
        }
        return filePath.replace('\\', '/').trim();
    }

    private static class MigrationKey {
        private final String taskId;
        private final String filePath;

        private MigrationKey(String taskId, String filePath) {
            this.taskId = StringUtils.hasText(taskId) ? taskId : "";
            this.filePath = StringUtils.hasText(filePath) ? filePath : "";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MigrationKey)) return false;
            MigrationKey that = (MigrationKey) o;
            return taskId.equals(that.taskId) && filePath.equals(that.filePath);
        }

        @Override
        public int hashCode() {
            int result = taskId.hashCode();
            result = 31 * result + filePath.hashCode();
            return result;
        }
    }

    private static class PreparedMigrationContext {
        private final ScanReport report;
        private final MigrationSummary summary;

        private PreparedMigrationContext(ScanReport report, MigrationSummary summary) {
            this.report = report;
            this.summary = summary;
        }
    }
}



