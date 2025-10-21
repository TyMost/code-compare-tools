package com.example.codecompare.rebuild.scanning.batch;

import com.example.codecompare.rebuild.api.dto.DualIncrementalComparisonView;
import com.example.codecompare.rebuild.api.dto.GitComparisonFileView;
import com.example.codecompare.rebuild.api.dto.GitComparisonResponseView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffFileDetailView;
import com.example.codecompare.rebuild.core.properties.ApplicationProperties;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry.ProjectRootDescriptor;
import com.example.codecompare.rebuild.scanning.FileScanService;
import com.example.codecompare.rebuild.scanning.IncrementalDiffFacade;
import com.example.codecompare.rebuild.scanning.ProjectScanRequest;
import com.example.codecompare.rebuild.scanning.ScanProperties;
import com.example.codecompare.rebuild.scanning.ScanResultRepository;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    private final FileScanService fileScanService;
    private final ScanResultRepository scanResultRepository;
    private final Clock clock;
    private final ObjectMapper yamlMapper;

    public GitComparisonBatchService(IncrementalDiffFacade incrementalDiffFacade,
                                     ApplicationProperties applicationProperties,
                                     ScanProperties scanProperties,
                                     ProjectRootRegistry projectRootRegistry,
                                     FileScanService fileScanService,
                                     ScanResultRepository scanResultRepository,
                                     Clock clock) {
        this.incrementalDiffFacade = incrementalDiffFacade;
        this.applicationProperties = applicationProperties;
        this.scanProperties = scanProperties;
        this.projectRootRegistry = projectRootRegistry;
        this.fileScanService = fileScanService;
        this.scanResultRepository = scanResultRepository;
        this.clock = clock;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.findAndRegisterModules();
    }

    public GitComparisonBatchExportResult export(String configPath, boolean refresh) {
        GitComparisonBatchConfig config = resolveConfig(configPath);
        validateConfig(config);

        List<ProjectEntry> sources = toProjectEntries(config.getSources(), "source");
        List<ProjectEntry> targets = toProjectEntries(config.getTargets(), "target");
        List<ProjectPair> pairs = resolvePairs(config, sources, targets);
        if (pairs.isEmpty()) {
            throw new IllegalArgumentException("No source/target combinations are available for batch export.");
        }

        GitReferenceSnapshot snapshot = GitReferenceSnapshot.capture(scanProperties);
        applyGitReferences(config);

        try {
            initializeProjects(pairs, refresh);
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
            throw new IllegalArgumentException("Configuration file does not exist: " + path);
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length == 0) {
                throw new IllegalArgumentException("Configuration file is empty: " + path);
            }
            return yamlMapper.readValue(bytes, GitComparisonBatchConfig.class);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read configuration file: " + path, ex);
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
        if (!CollectionUtils.isEmpty(config.getPairs())) {
            for (GitComparisonBatchConfig.PairEntry pairEntry : config.getPairs()) {
                ProjectEntry source = toProjectEntry(pairEntry == null ? null : pairEntry.getSource(), "pair source");
                ProjectEntry target = toProjectEntry(pairEntry == null ? null : pairEntry.getTarget(), "pair target");
                validateProjectEntry(source, "source");
                validateProjectEntry(target, "target");
            }
            return;
        }
        List<ProjectEntry> sources = toProjectEntries(config.getSources(), "source");
        List<ProjectEntry> targets = toProjectEntries(config.getTargets(), "target");
        if (CollectionUtils.isEmpty(sources)) {
            throw new IllegalArgumentException("Configuration must declare at least one source project.");
        }
        if (CollectionUtils.isEmpty(targets)) {
            throw new IllegalArgumentException("Configuration must declare at least one target project.");
        }
        sources.forEach(entry -> validateProjectEntry(entry, "source"));
        targets.forEach(entry -> validateProjectEntry(entry, "target"));
    }

    private List<ProjectEntry> toProjectEntries(List<GitComparisonBatchConfig.ProjectEntry> entries, String role) {
        if (CollectionUtils.isEmpty(entries)) {
            return Collections.emptyList();
        }
        List<ProjectEntry> result = new ArrayList<>(entries.size());
        for (GitComparisonBatchConfig.ProjectEntry entry : entries) {
            ProjectEntry converted = toProjectEntry(entry, role);
            if (converted != null) {
                result.add(converted);
            }
        }
        return result;
    }

    private ProjectEntry toProjectEntry(GitComparisonBatchConfig.ProjectEntry entry, String role) {
        if (entry == null) {
            throw new IllegalArgumentException("Project entry for " + role + " is missing.");
        }
        String code = entry.getCode() == null ? null : entry.getCode().trim();
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("Project entry for " + role + " is missing a code value.");
        }
        String path = null;
        if (entry.getPath() != null) {
            String trimmed = entry.getPath().trim();
            if (StringUtils.hasText(trimmed)) {
                path = resolveAndVerifyPath(trimmed).toString();
            }
        }
        return new ProjectEntry(code, path);
    }

    private List<ProjectPair> resolvePairs(GitComparisonBatchConfig config,
                                           List<ProjectEntry> sources,
                                           List<ProjectEntry> targets) {
        if (!CollectionUtils.isEmpty(config.getPairs())) {
            List<ProjectPair> result = new ArrayList<>(config.getPairs().size());
            for (GitComparisonBatchConfig.PairEntry pairEntry : config.getPairs()) {
                ProjectEntry source = toProjectEntry(pairEntry == null ? null : pairEntry.getSource(), "pair source");
                ProjectEntry target = toProjectEntry(pairEntry == null ? null : pairEntry.getTarget(), "pair target");
                if (source != null && target != null) {
                    result.add(new ProjectPair(
                            source,
                            target,
                            pairEntry.getGitBaseRefSource(),
                            pairEntry.getGitTargetRefSource(),
                            pairEntry.getGitBaseRefTarget(),
                            pairEntry.getGitTargetRefTarget()));
                }
            }
            return result;
        }
        List<ProjectPair> pairs = new ArrayList<>();
        for (ProjectEntry source : sources) {
            for (ProjectEntry target : targets) {
                pairs.add(new ProjectPair(source, target, null, null, null, null));
            }
        }
        return pairs;
    }

    private void initializeProjects(List<ProjectPair> pairs, boolean refresh) {
        Map<String, ProjectEntry> combined = new LinkedHashMap<>();
        for (ProjectPair pair : pairs) {
            combined.put(pair.source.code, pair.source);
            combined.put(pair.target.code, pair.target);
        }
        for (ProjectEntry entry : combined.values()) {
            ensureProjectInitialized(entry, refresh);
        }
    }

    private void validateProjectEntry(ProjectEntry entry, String role) {
        if (entry == null) {
            throw new IllegalArgumentException("Project entry for " + role + " is missing.");
        }
        if (!StringUtils.hasText(entry.code)) {
            throw new IllegalArgumentException("Configured " + role + " entry is missing a code value.");
        }
        Optional<ProjectRootDescriptor> descriptorOptional = projectRootRegistry.findByCode(entry.code);
        if (descriptorOptional.isPresent() && StringUtils.hasText(entry.path)) {
            Path normalizedPath = resolveAndVerifyPath(entry.path);
            Path descriptorPath = descriptorOptional.get().getPath().toAbsolutePath().normalize();
            if (!descriptorPath.equals(normalizedPath)) {
                throw new IllegalArgumentException("Configured path " + normalizedPath + " does not match registered path " + descriptorPath + ".");
            }
        }
        if (!descriptorOptional.isPresent()) {
            if (!StringUtils.hasText(entry.path)) {
                throw new IllegalArgumentException("Project " + entry.code + " is not registered and no path was provided.");
            }
            Path normalizedPath = resolveAndVerifyPath(entry.path);
            if (!Files.exists(normalizedPath)) {
                throw new IllegalArgumentException("Project " + entry.code + " path does not exist: " + normalizedPath);
            }
            if (!Files.isDirectory(normalizedPath)) {
                throw new IllegalArgumentException("Project " + entry.code + " path is not a directory: " + normalizedPath);
            }
        }
    }

    private void ensureProjectInitialized(ProjectEntry entry, boolean refresh) {
        Optional<ProjectRootDescriptor> descriptorOptional = projectRootRegistry.findByCode(entry.code);
        if (descriptorOptional.isPresent()) {
            boolean summaryExists = scanResultRepository.findLatestSummary(entry.code).isPresent();
            boolean shouldRefresh = refresh || !summaryExists;
            incrementalDiffFacade.loadOverview(entry.code, shouldRefresh);
            return;
        }
        Path normalizedPath = resolveAndVerifyPath(entry.path);
        if (!Files.exists(normalizedPath)) {
            throw new IllegalArgumentException("Project " + entry.code + " path does not exist: " + normalizedPath);
        }
        if (!Files.isDirectory(normalizedPath)) {
            throw new IllegalArgumentException("Project " + entry.code + " path is not a directory: " + normalizedPath);
        }
        boolean summaryExists = scanResultRepository.findLatestSummary(entry.code).isPresent();
        if (refresh || !summaryExists) {
            log.info("Running incremental scan for project {} using path {}", entry.code, normalizedPath);
            ProjectScanRequest request = ProjectScanRequest.builder()
                    .projectCode(entry.code)
                    .addRoot(normalizedPath)
                    .skipHidden(true)
                    .build();
            fileScanService.scanIncremental(request);
        }
    }

    private GitReferenceSnapshot applyPairGitReferences(ProjectPair pair) {
        if (pair == null || !pair.hasGitOverrides()) {
            return null;
        }
        GitReferenceSnapshot snapshot = GitReferenceSnapshot.capture(scanProperties);
        if (StringUtils.hasText(pair.gitBaseRefSource)) {
            scanProperties.setGitBaseRefSource(pair.gitBaseRefSource);
        }
        if (StringUtils.hasText(pair.gitTargetRefSource)) {
            scanProperties.setGitTargetRefSource(pair.gitTargetRefSource);
        }
        if (StringUtils.hasText(pair.gitBaseRefTarget)) {
            scanProperties.setGitBaseRefTarget(pair.gitBaseRefTarget);
        }
        if (StringUtils.hasText(pair.gitTargetRefTarget)) {
            scanProperties.setGitTargetRefTarget(pair.gitTargetRefTarget);
        }
        return snapshot;
    }

    private Path resolveAndVerifyPath(String rawPath) {
        try {
            return Paths.get(rawPath).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid path value: " + rawPath, ex);
        }
    }

    private BatchComputationResult computePairs(List<ProjectPair> pairs) {
        List<FileRow> fileRows = new ArrayList<>();
        Map<String, RepositoryAggregate> aggregates = new LinkedHashMap<>();
        for (ProjectPair pair : pairs) {
            GitReferenceSnapshot overrideSnapshot = applyPairGitReferences(pair);
            try {
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
            } finally {
                if (overrideSnapshot != null) {
                    overrideSnapshot.restore(scanProperties);
                }
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
            throw new IllegalStateException("Failed to generate Excel workbook", ex);
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
        if (!StringUtils.hasText(sourceNames) && !CollectionUtils.isEmpty(config.getPairs())) {
            sourceNames = config.getPairs().stream()
                    .map(pair -> pair.getSource() == null ? null : pair.getSource().getCode())
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("_"));
        }
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
        return value == null ? 0d : value.doubleValue();
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
        private final String gitBaseRefSource;
        private final String gitTargetRefSource;
        private final String gitBaseRefTarget;
        private final String gitTargetRefTarget;

        private ProjectPair(ProjectEntry source,
                             ProjectEntry target,
                             String gitBaseRefSource,
                             String gitTargetRefSource,
                             String gitBaseRefTarget,
                             String gitTargetRefTarget) {
            this.source = Objects.requireNonNull(source, "source must not be null");
            this.target = Objects.requireNonNull(target, "target must not be null");
            this.gitBaseRefSource = gitBaseRefSource;
            this.gitTargetRefSource = gitTargetRefSource;
            this.gitBaseRefTarget = gitBaseRefTarget;
            this.gitTargetRefTarget = gitTargetRefTarget;
        }

        private boolean hasGitOverrides() {
            return StringUtils.hasText(gitBaseRefSource)
                    || StringUtils.hasText(gitTargetRefSource)
                    || StringUtils.hasText(gitBaseRefTarget)
                    || StringUtils.hasText(gitTargetRefTarget);
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
