package com.example.codecompare.rebuild.scanning.batch;

import com.example.codecompare.rebuild.api.dto.DualIncrementalComparisonView;
import com.example.codecompare.rebuild.api.dto.GitComparisonFileView;
import com.example.codecompare.rebuild.api.dto.GitComparisonResponseView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffFileDetailView;
import com.example.codecompare.rebuild.core.properties.ApplicationProperties;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry.ProjectRootDescriptor;
import com.example.codecompare.rebuild.scanning.IncrementalDiffFacade;
import com.example.codecompare.rebuild.scanning.ScanProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service that orchestrates batch Git comparisons driven by configuration files and produces Excel exports.
 */
@Component
public class GitComparisonBatchService {

    private static final Logger log = LoggerFactory.getLogger(GitComparisonBatchService.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final IncrementalDiffFacade incrementalDiffFacade;
    private final ApplicationProperties applicationProperties;
    private final ScanProperties scanProperties;
    private final ProjectRootRegistry projectRootRegistry;
    private final Clock clock;
    private final ObjectMapper yamlMapper;

    public GitComparisonBatchService(IncrementalDiffFacade incrementalDiffFacade,
                                     ApplicationProperties applicationProperties,
                                     ScanProperties scanProperties,
                                     ProjectRootRegistry projectRootRegistry,
                                     Clock clock) {
        this.incrementalDiffFacade = incrementalDiffFacade;
        this.applicationProperties = applicationProperties;
        this.scanProperties = scanProperties;
        this.projectRootRegistry = projectRootRegistry;
        this.clock = clock;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.findAndRegisterModules();
    }

    /**
     * Generates an Excel export for the given configuration path. When {@code configPath} is empty,
     * falls back to {@code application.yml} configuration.
     */
    public GitComparisonBatchExportResult export(String configPath, boolean refresh) {
        GitComparisonBatchConfig config = resolveConfig(configPath);
        validateConfig(config);

        List<ProjectEntry> sources = toProjectEntries(config.getSources());
        List<ProjectEntry> targets = toProjectEntries(config.getTargets());
        List<ProjectPair> pairs = buildPairs(sources, targets);

        if (pairs.isEmpty()) {
            throw new IllegalArgumentException("No source/target combinations are available for batch export.");
        }

        GitReferenceSnapshot snapshot = GitReferenceSnapshot.capture(scanProperties);
        applyGitReferences(config);

        try {
            if (refresh) {
                refreshProjects(sources, targets);
            }
            BatchComputationResult computation = computePairs(pairs);
            byte[] content = writeWorkbook(computation);
            String filename = buildFilename(config);
            return new GitComparisonBatchExportResult(content, filename);
        } finally {
            snapshot.restore(scanProperties);
        }
    }

    private GitComparisonBatchConfig resolveConfig(String configPath) {
        if (!StringUtils.hasText(configPath)) {
            return fromApplicationProperties();
        }
        Path path = Paths.get(configPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("配置文件不存在: " + path);
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length == 0) {
                throw new IllegalArgumentException("配置文件为空: " + path);
            }
            return yamlMapper.readValue(bytes, GitComparisonBatchConfig.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException("无法读取配置文件: " + path, ex);
        }
    }

    private GitComparisonBatchConfig fromApplicationProperties() {
        GitComparisonBatchConfig config = new GitComparisonBatchConfig();
        config.setSources(applicationProperties.getProject().getSources().stream()
                .map(this::fromProjectConfig)
                .collect(Collectors.toList()));
        config.setTargets(applicationProperties.getProject().getTargets().stream()
                .map(this::fromProjectConfig)
                .collect(Collectors.toList()));
        config.setGitBaseRefSource(orNull(scanProperties.getGitBaseRefSource()));
        config.setGitTargetRefSource(orNull(scanProperties.getGitTargetRefSource()));
        config.setGitBaseRefTarget(orNull(scanProperties.getGitBaseRefTarget()));
        config.setGitTargetRefTarget(orNull(scanProperties.getGitTargetRefTarget()));
        return config;
    }

    private GitComparisonBatchConfig.ProjectEntry fromProjectConfig(ApplicationProperties.ProjectRootConfig config) {
        GitComparisonBatchConfig.ProjectEntry entry = new GitComparisonBatchConfig.ProjectEntry();
        entry.setCode(config.getCode());
        entry.setPath(config.getPath());
        return entry;
    }

    private void validateConfig(GitComparisonBatchConfig config) {
        if (CollectionUtils.isEmpty(config.getSources())) {
            throw new IllegalArgumentException("配置中的 sources 列表不能为空。");
        }
        if (CollectionUtils.isEmpty(config.getTargets())) {
            throw new IllegalArgumentException("配置中的 targets 列表不能为空。");
        }
        List<ProjectEntry> sources = toProjectEntries(config.getSources());
        List<ProjectEntry> targets = toProjectEntries(config.getTargets());
        for (ProjectEntry entry : sources) {
            validateProjectEntry(entry, "source");
        }
        for (ProjectEntry entry : targets) {
            validateProjectEntry(entry, "target");
        }
    }

    private void validateProjectEntry(ProjectEntry entry, String role) {
        if (!StringUtils.hasText(entry.code)) {
            throw new IllegalArgumentException("配置中的 " + role + " 项缺少 code 字段。");
        }
        Optional<ProjectRootDescriptor> descriptorOptional = projectRootRegistry.findByCode(entry.code);
        if (!descriptorOptional.isPresent()) {
            throw new IllegalArgumentException("未在项目根目录注册表中找到 code=" + entry.code + " 对应的路径，请确认 application.yml 的 migration.project 配置。");
        }
        if (StringUtils.hasText(entry.path)) {
            Path normalizedPath = Paths.get(entry.path).toAbsolutePath().normalize();
            Path descriptorPath = descriptorOptional.get().getPath().toAbsolutePath().normalize();
            if (!descriptorPath.equals(normalizedPath)) {
                throw new IllegalArgumentException("配置的路径 " + normalizedPath + " 与注册表中的路径 " + descriptorPath + " 不一致。");
            }
        }
    }

    private List<ProjectEntry> toProjectEntries(List<GitComparisonBatchConfig.ProjectEntry> entries) {
        if (CollectionUtils.isEmpty(entries)) {
            return Collections.emptyList();
        }
        List<ProjectEntry> result = new ArrayList<>(entries.size());
        for (GitComparisonBatchConfig.ProjectEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            String code = entry.getCode() == null ? null : entry.getCode().trim();
            String path = entry.getPath() == null ? null : entry.getPath().trim();
            if (StringUtils.hasText(code)) {
                result.add(new ProjectEntry(code, path));
            }
        }
        return result;
    }

    private List<ProjectPair> buildPairs(List<ProjectEntry> sources, List<ProjectEntry> targets) {
        List<ProjectPair> pairs = new ArrayList<>();
        for (ProjectEntry source : sources) {
            for (ProjectEntry target : targets) {
                pairs.add(new ProjectPair(source, target));
            }
        }
        return pairs;
    }

    private void refreshProjects(List<ProjectEntry> sources, List<ProjectEntry> targets) {
        Set<String> refreshed = new LinkedHashSet<>();
        sources.forEach(entry -> refreshed.add(entry.code));
        targets.forEach(entry -> refreshed.add(entry.code));
        for (String projectCode : refreshed) {
            log.info("Refreshing incremental diff for project {}", projectCode);
            incrementalDiffFacade.loadOverview(projectCode, true);
        }
    }

    private BatchComputationResult computePairs(List<ProjectPair> pairs) {
        List<FileRow> fileRows = new ArrayList<>();
        Map<String, RepositoryAggregate> aggregates = new LinkedHashMap<>();
        for (ProjectPair pair : pairs) {
            GitComparisonResponseView response = incrementalDiffFacade.compareProjects(
                    pair.source.code,
                    pair.target.code,
                    false);
            if (response == null || CollectionUtils.isEmpty(response.getFiles())) {
                continue;
            }
            for (GitComparisonFileView fileView : response.getFiles()) {
                String filePath = fileView.getFilePath();
                DualIncrementalComparisonView dual = fileView.getDualComparison();
                double similarity = dual == null ? 0d : safeDouble(dual.getFileSimilarity());
                fileRows.add(new FileRow(pair.source.code, pair.target.code, filePath, similarity));

                int sourceLineCount = totalLines(fileView.getSource());
                int targetLineCount = totalLines(fileView.getTarget());
                accumulateAggregate(aggregates, pair, Role.SOURCE, sourceLineCount, similarity);
                accumulateAggregate(aggregates, pair, Role.TARGET, targetLineCount, similarity);
            }
        }
        return new BatchComputationResult(fileRows, new ArrayList<>(aggregates.values()));
    }

    private void accumulateAggregate(Map<String, RepositoryAggregate> aggregates,
                                     ProjectPair pair,
                                     Role role,
                                     int lineCount,
                                     double similarity) {
        String key = pair.source.code + "->" + pair.target.code + "::" + role.name();
        RepositoryAggregate aggregate = aggregates.computeIfAbsent(key, ignored -> new RepositoryAggregate(
                role == Role.SOURCE ? pair.source.code : pair.target.code,
                role == Role.SOURCE ? pair.target.code : pair.source.code,
                role));
        aggregate.increment(lineCount, similarity);
    }

    private byte[] writeWorkbook(BatchComputationResult computation) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            writeFileSimilaritySheet(workbook, computation.fileRows);
            writeRepositorySheet(workbook, computation.aggregates);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("生成 Excel 报告失败", ex);
        }
    }

    private void writeFileSimilaritySheet(XSSFWorkbook workbook, List<FileRow> rows) {
        Sheet sheet = workbook.createSheet("File Similarity");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Source Project");
        header.createCell(1).setCellValue("Target Project");
        header.createCell(2).setCellValue("File Path");
        header.createCell(3).setCellValue("Similarity (%)");

        int rowIndex = 1;
        for (FileRow row : rows) {
            Row excelRow = sheet.createRow(rowIndex++);
            excelRow.createCell(0).setCellValue(row.sourceProject);
            excelRow.createCell(1).setCellValue(row.targetProject);
            excelRow.createCell(2).setCellValue(row.filePath);
            excelRow.createCell(3).setCellValue(round(row.similarity, 2));
        }
        for (int i = 0; i < 4; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writeRepositorySheet(XSSFWorkbook workbook, List<RepositoryAggregate> aggregates) {
        Sheet sheet = workbook.createSheet("Repository Similarity");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Project");
        header.createCell(1).setCellValue("Role");
        header.createCell(2).setCellValue("Counterpart");
        header.createCell(3).setCellValue("Files");
        header.createCell(4).setCellValue("Total Lines");
        header.createCell(5).setCellValue("Weighted Similarity");
        header.createCell(6).setCellValue("Similarity %");

        CreationHelper helper = workbook.getCreationHelper();
        CellStyle percentStyle = workbook.createCellStyle();
        percentStyle.setDataFormat(helper.createDataFormat().getFormat("0.00%"));

        int rowIndex = 1;
        for (RepositoryAggregate aggregate : aggregates) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(aggregate.projectCode);
            row.createCell(1).setCellValue(aggregate.role.name());
            row.createCell(2).setCellValue(aggregate.counterpartCode);
            row.createCell(3).setCellValue(aggregate.fileCount);
            row.createCell(4).setCellValue(aggregate.totalLineCount);
            row.createCell(5).setCellValue(round(aggregate.weightedSimilarity(), 2));
            Cell percentCell = row.createCell(6);
            percentCell.setCellValue(aggregate.weightedAverage() / 100d);
            percentCell.setCellStyle(percentStyle);
        }
        for (int i = 0; i < 7; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String buildFilename(GitComparisonBatchConfig config) {
        String timestamp = TIMESTAMP.format(LocalDateTime.now(clock));
        String sourceNames = config.getSources().stream()
                .map(GitComparisonBatchConfig.ProjectEntry::getCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining("_"));
        if (!StringUtils.hasText(sourceNames)) {
            sourceNames = "sources";
        }
        return String.format(Locale.ROOT, "git-comparison-%s-%s.xlsx", sourceNames, timestamp);
    }

    private void applyGitReferences(GitComparisonBatchConfig config) {
        if (config.getGitBaseRefSource() != null) {
            scanProperties.setGitBaseRefSource(config.getGitBaseRefSource());
        }
        if (config.getGitTargetRefSource() != null) {
            scanProperties.setGitTargetRefSource(config.getGitTargetRefSource());
        }
        if (config.getGitBaseRefTarget() != null) {
            scanProperties.setGitBaseRefTarget(config.getGitBaseRefTarget());
        }
        if (config.getGitTargetRefTarget() != null) {
            scanProperties.setGitTargetRefTarget(config.getGitTargetRefTarget());
        }
    }

    private double safeDouble(Number value) {
        if (value == null) {
            return 0d;
        }
        return value.doubleValue();
    }

    private int totalLines(IncrementalDiffFileDetailView detailView) {
        if (detailView == null) {
            return 0;
        }
        return Math.max(0, detailView.getTotalLineCount());
    }

    private double round(double value, int scale) {
        double factor = Math.pow(10d, Math.max(scale, 0));
        return Math.round(value * factor) / factor;
    }

    private String orNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static final class ProjectEntry {
        private final String code;
        private final String path;

        private ProjectEntry(String code, String path) {
            this.code = code;
            this.path = path;
        }
    }

    private static final class ProjectPair {
        private final ProjectEntry source;
        private final ProjectEntry target;

        private ProjectPair(ProjectEntry source, ProjectEntry target) {
            this.source = Objects.requireNonNull(source, "source must not be null");
            this.target = Objects.requireNonNull(target, "target must not be null");
        }
    }

    private enum Role {
        SOURCE,
        TARGET
    }

    private static final class FileRow {
        private final String sourceProject;
        private final String targetProject;
        private final String filePath;
        private final double similarity;

        private FileRow(String sourceProject, String targetProject, String filePath, double similarity) {
            this.sourceProject = sourceProject;
            this.targetProject = targetProject;
            this.filePath = filePath;
            this.similarity = similarity;
        }
    }

    private static final class RepositoryAggregate {
        private final String projectCode;
        private final String counterpartCode;
        private final Role role;
        private int fileCount;
        private long totalLineCount;
        private double similarityAccumulator;

        private RepositoryAggregate(String projectCode, String counterpartCode, Role role) {
            this.projectCode = projectCode;
            this.counterpartCode = counterpartCode;
            this.role = role;
            this.fileCount = 0;
            this.totalLineCount = 0L;
            this.similarityAccumulator = 0d;
        }

        private void increment(int lineCount, double similarity) {
            this.fileCount++;
            if (lineCount > 0) {
                this.totalLineCount += lineCount;
                this.similarityAccumulator += similarity * lineCount;
            }
        }

        private double weightedSimilarity() {
            return similarityAccumulator;
        }

        private double weightedAverage() {
            if (totalLineCount <= 0L) {
                return 0d;
            }
            return similarityAccumulator / (double) totalLineCount;
        }
    }

    private static final class BatchComputationResult {
        private final List<FileRow> fileRows;
        private final List<RepositoryAggregate> aggregates;

        private BatchComputationResult(List<FileRow> fileRows, List<RepositoryAggregate> aggregates) {
            this.fileRows = fileRows;
            this.aggregates = aggregates;
        }
    }

    private static final class GitReferenceSnapshot {
        private final String baseSource;
        private final String targetSource;
        private final String baseTarget;
        private final String targetTarget;

        private GitReferenceSnapshot(String baseSource, String targetSource, String baseTarget, String targetTarget) {
            this.baseSource = baseSource;
            this.targetSource = targetSource;
            this.baseTarget = baseTarget;
            this.targetTarget = targetTarget;
        }

        private static GitReferenceSnapshot capture(ScanProperties properties) {
            return new GitReferenceSnapshot(
                    properties.getGitBaseRefSource(),
                    properties.getGitTargetRefSource(),
                    properties.getGitBaseRefTarget(),
                    properties.getGitTargetRefTarget());
        }

        private void restore(ScanProperties properties) {
            properties.setGitBaseRefSource(baseSource);
            properties.setGitTargetRefSource(targetSource);
            properties.setGitBaseRefTarget(baseTarget);
            properties.setGitTargetRefTarget(targetTarget);
        }
    }
}
