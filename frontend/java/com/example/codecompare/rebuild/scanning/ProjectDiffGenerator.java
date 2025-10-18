package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.block.model.CodeSnapshot;
import com.example.codecompare.rebuild.core.support.IgnorePatternMatcher;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry.ProjectRootDescriptor;
import com.example.codecompare.rebuild.diff.DiffRequest;
import com.example.codecompare.rebuild.diff.DiffResult;
import com.example.codecompare.rebuild.diff.DiffService;
import com.example.codecompare.rebuild.repository.AgentSuggestionRepository;
import com.example.codecompare.rebuild.repository.BlockDecisionRepository;
import com.example.codecompare.rebuild.repository.DiffSnapshotRepository;
import com.example.codecompare.rebuild.repository.model.BlockDecisionRecord;
import com.example.codecompare.rebuild.repository.model.BlockDecisionSnapshot;
import com.example.codecompare.rebuild.repository.model.DiffSnapshotDocument;
import com.example.codecompare.rebuild.repository.model.FileChangeType;
import com.example.codecompare.rebuild.repository.model.FileRecord;
import com.example.codecompare.rebuild.scanning.ScanProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Generates diff snapshots and block decision data based on example projects.
 */
@Component
public class ProjectDiffGenerator implements ApplicationListener<ScanCompletedEvent> {

    private static final Logger log = LoggerFactory.getLogger(ProjectDiffGenerator.class);

    private final DiffService diffService;
    private final BlockDecisionRepository blockDecisionRepository;
    private final DiffSnapshotRepository diffSnapshotRepository;
    private final AgentSuggestionRepository agentSuggestionRepository;
    private final ProjectRootRegistry projectRootRegistry;
    private final BlockDiffLabeler blockDiffLabeler;
    private final ScanProperties scanProperties;

    public ProjectDiffGenerator(DiffService diffService,
                                BlockDecisionRepository blockDecisionRepository,
                                DiffSnapshotRepository diffSnapshotRepository,
                                AgentSuggestionRepository agentSuggestionRepository,
                                ProjectRootRegistry projectRootRegistry,
                                BlockDiffLabeler blockDiffLabeler,
                                ScanProperties scanProperties) {
        this.diffService = diffService;
        this.blockDecisionRepository = blockDecisionRepository;
        this.diffSnapshotRepository = diffSnapshotRepository;
        this.agentSuggestionRepository = agentSuggestionRepository;
        this.projectRootRegistry = projectRootRegistry;
        this.blockDiffLabeler = blockDiffLabeler;
        this.scanProperties = scanProperties;
    }

    private Map<String, Path> collectFiles(Path root, IgnorePatternMatcher ignoreMatcher) {
        Map<String, Path> files = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !isIgnored(root, path, ignoreMatcher))
                    .filter(this::isSupportedFile)
                    .forEach(path -> files.put(
                            root.relativize(path).toString().replace('\\', '/'),
                            path));
        } catch (IOException ex) {
            log.warn("遍历示例项目文件失败，root={}", root, ex);
        }
        return files;
    }

    private String readContent(Path path) {
        if (path == null) {
            return "";
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.debug("读取文件失败，path={}", path, ex);
            return "";
        }
    }

    private String resolveStatus(BlockDiff diff) {
        if (diff == null) {
            return BlockLabelConstants.STATUS_OTHER;
        }
        BlockDiff.LabelDescriptor primary = diff.getPrimaryLabel();
        if (primary != null && StringUtils.hasText(primary.getStatusKey())) {
            return primary.getStatusKey();
        }
        List<String> labelIds = diff.getLabelIds();
        if (!CollectionUtils.isEmpty(labelIds)) {
            return labelIds.get(0);
        }
        return BlockLabelConstants.STATUS_OTHER;
    }

    private String resolveStatusLabel(BlockDiff diff, String status) {
        if (diff != null) {
            BlockDiff.LabelDescriptor primary = diff.getPrimaryLabel();
            if (primary != null && StringUtils.hasText(primary.getLabelName())) {
                return primary.getLabelName();
            }
            if (primary != null && StringUtils.hasText(primary.getLabelId())) {
                return primary.getLabelId();
            }
            List<String> labels = diff.getLabels();
            if (!CollectionUtils.isEmpty(labels)) {
                return labels.get(0);
            }
        }
        if (!StringUtils.hasText(status)) {
            return "其他";
        }
        if (BlockLabelConstants.STATUS_REVIEW.equalsIgnoreCase(status)) {
            return "待复核";
        }
        if (BlockLabelConstants.STATUS_UNMIGRATED.equalsIgnoreCase(status)) {
            return "未迁移";
        }
        if (BlockLabelConstants.STATUS_NEW_CODE.equalsIgnoreCase(status)) {
            return "适配迁移";
        }
        if (BlockLabelConstants.STATUS_MIGRATED.equalsIgnoreCase(status)) {
            return "已迁移";
        }
        return "其他";
    }

    @Override
    public void onApplicationEvent(ScanCompletedEvent event) {
        if (event == null || event.getSummary() == null) {
            return;
        }
        String projectCode = event.getSummary().getProjectCode();
        try {
            if (event.isFullRescan()) {
                regenerateAll(projectCode);
                return;
            }
            List<FileRecord> changedRecords = event.getChangedRecords();
            if (CollectionUtils.isEmpty(changedRecords)) {
                log.debug("扫描事件未包含增量文件，跳过 diff 更新，projectCode={}", projectCode);
                return;
            }
            generateIncremental(projectCode, changedRecords);
        } catch (Exception ex) {
            log.warn("生成示例 diff 数据失败，projectCode={}", projectCode, ex);
        }
    }

    void generate(String projectCode) {
        regenerateAll(projectCode);
    }

    private void regenerateAll(String projectCode) {
        List<ProjectRootDescriptor> sources = projectRootRegistry.getSources();
        List<ProjectRootDescriptor> targets = projectRootRegistry.getTargets();
        if (CollectionUtils.isEmpty(sources) || CollectionUtils.isEmpty(targets)) {
            log.debug("未配置源或目标项目根目录，跳过 diff 生成");
            return;
        }
        ProjectRootDescriptor sourceDescriptor = sources.get(0);
        ProjectRootDescriptor targetDescriptor = targets.get(0);
        Path sourceRoot = sourceDescriptor.getPath();
        Path targetRoot = targetDescriptor.getPath();
        String sourceCode = resolveProjectCode(sourceDescriptor);
        String targetCode = resolveProjectCode(targetDescriptor);
        if (!Files.exists(sourceRoot) || !Files.exists(targetRoot)) {
            log.debug("示例项目根目录不存在，source={}, target={}", sourceRoot, targetRoot);
            return;
        }
        clearExistingData(projectCode);

        IgnorePatternMatcher ignoreMatcher = buildIgnoreMatcher();
        Map<String, Path> sourceFiles = collectFiles(sourceRoot, ignoreMatcher);
        Map<String, Path> targetFiles = collectFiles(targetRoot, ignoreMatcher);
        Set<String> allPaths = new TreeSet<>();
        allPaths.addAll(sourceFiles.keySet());
        allPaths.addAll(targetFiles.keySet());

        int processed = 0;
        for (String relativePath : allPaths) {
            Path sourcePath = sourceFiles.get(relativePath);
            Path targetPath = targetFiles.get(relativePath);
            if (processFile(projectCode, sourceCode, targetCode, relativePath, sourcePath, targetPath)) {
                processed++;
            }
        }
        log.info("示例项目 diff 数据生成完成，projectCode={}，处理文件数={}", projectCode, processed);
    }

    private void generateIncremental(String projectCode, List<FileRecord> changedRecords) {
        List<ProjectRootDescriptor> sources = projectRootRegistry.getSources();
        List<ProjectRootDescriptor> targets = projectRootRegistry.getTargets();
        if (CollectionUtils.isEmpty(sources) || CollectionUtils.isEmpty(targets)) {
            log.debug("未配置源或目标项目根目录，跳过增量 diff 生成");
            return;
        }
        ProjectRootDescriptor sourceDescriptor = sources.get(0);
        ProjectRootDescriptor targetDescriptor = targets.get(0);
        Path sourceRoot = sourceDescriptor.getPath();
        Path targetRoot = targetDescriptor.getPath();
        String sourceCode = resolveProjectCode(sourceDescriptor);
        String targetCode = resolveProjectCode(targetDescriptor);
        if (!Files.exists(sourceRoot) || !Files.exists(targetRoot)) {
            log.debug("示例项目根目录不存在，source={}, target={}", sourceRoot, targetRoot);
            return;
        }

        IgnorePatternMatcher ignoreMatcher = buildIgnoreMatcher();
        Map<String, Path> sourceFiles = collectFiles(sourceRoot, ignoreMatcher);
        Map<String, Path> targetFiles = collectFiles(targetRoot, ignoreMatcher);

        Set<String> pathsToProcess = new LinkedHashSet<>();
        Set<String> pathsToDelete = new LinkedHashSet<>();

        for (FileRecord record : changedRecords) {
            String previousPath = normalizeRecordPath(record.getGitPreviousPath());
            String currentPath = normalizeRecordPath(record.getPath());
            if (StringUtils.hasText(previousPath) && !previousPath.equals(currentPath)) {
                pathsToDelete.add(previousPath);
            }
            if (record.getChangeType() == FileChangeType.DELETED) {
                if (StringUtils.hasText(currentPath)) {
                    pathsToDelete.add(currentPath);
                }
                continue;
            }
            if (StringUtils.hasText(currentPath)) {
                pathsToProcess.add(currentPath);
            }
        }

        for (String path : pathsToDelete) {
            deleteFileArtifacts(projectCode, path);
        }

        int processed = 0;
        for (String relativePath : pathsToProcess) {
            Path sourcePath = sourceFiles.get(relativePath);
            Path targetPath = targetFiles.get(relativePath);
            if (sourcePath == null && targetPath == null) {
                deleteFileArtifacts(projectCode, relativePath);
                continue;
            }
            if (processFile(projectCode, sourceCode, targetCode, relativePath, sourcePath, targetPath)) {
                processed++;
            }
        }
        log.info("示例项目增量 diff 生成完成，projectCode={}，处理文件数={}，删除文件数={}",
                projectCode, processed, pathsToDelete.size());
    }

    private boolean processFile(String projectCode,
                                String sourceCode,
                                String targetCode,
                                String relativePath,
                                Path sourcePath,
                                Path targetPath) {
        String sourceContent = readContent(sourcePath);
        String targetContent = readContent(targetPath);
        if (!StringUtils.hasText(sourceContent) && !StringUtils.hasText(targetContent)) {
            deleteFileArtifacts(projectCode, relativePath);
            return false;
        }

        deleteFileArtifacts(projectCode, relativePath);

        CodeSnapshot sourceSnapshot = CodeSnapshot.of(
                detectLanguage(relativePath),
                relativePath,
                sourceContent);
        CodeSnapshot targetSnapshot = CodeSnapshot.of(
                detectLanguage(relativePath),
                relativePath,
                targetContent);

        DiffResult result = diffService.analyze(DiffRequest.builder()
                .source(sourceSnapshot)
                .target(targetSnapshot)
                .build());
        List<BlockDiff> blocks = result == null ? new ArrayList<>() : result.getSegments();
        List<BlockDecisionRecord> records = new ArrayList<>();
        Map<String, Integer> labelCounts = new LinkedHashMap<>();
        Map<String, Integer> lineCounts = new LinkedHashMap<>();
        double similaritySum = 0d;
        int unlabeled = 0;
        List<String> decisionIds = new ArrayList<>();
        AtomicInteger index = new AtomicInteger(1);
        int changedLineTotal = 0;
        int fileTotalLines = countLines(StringUtils.hasText(targetContent) ? targetContent : sourceContent);

        for (BlockDiff block : blocks) {
            BlockDiff normalized = blockDiffLabeler.label(block);
            if (normalized == null || normalized.isFilteredOut()) {
                continue;
            }
            int changedLines = normalized.getChangedLineCount();
            int deltaLines = Math.max(1, changedLines);
            changedLineTotal += deltaLines;
            similaritySum += normalized.getSimilarityScore();
            if (CollectionUtils.isEmpty(normalized.getLabels())) {
                unlabeled++;
            }

            String status = resolveStatus(normalized);
            String statusLabel = resolveStatusLabel(normalized, status);

            Map<String, Object> metadata = new LinkedHashMap<>();
            int segmentStart = normalized.getTargetStartLine();
            int segmentEnd = segmentStart + deltaLines - 1;
            metadata.put("startLine", segmentStart);
            metadata.put("endLine", segmentEnd);
            metadata.put("similarity", normalized.getSimilarityScore());
            metadata.put("changedLines", changedLines);
            metadata.put("type", normalized.getType().name());

            BlockDecisionRecord record = BlockDecisionRecord.builder()
                    .comparisonId(projectCode)
                    .filePath(relativePath)
                    .blockIdentifier(relativePath + "#B" + index.getAndIncrement())
                    .status(status)
                    .riskLevel(statusLabel)
                    .action("review")
                    .metadata(metadata)
                    .diff(normalized)
                    .build();
            records.add(record);
            decisionIds.add(record.getId());

            if (CollectionUtils.isEmpty(normalized.getLabelIds())) {
                labelCounts.merge(BlockLabelConstants.STATUS_REVIEW, 1, Integer::sum);
                lineCounts.merge(BlockLabelConstants.STATUS_REVIEW, deltaLines, Integer::sum);
            } else {
                for (String label : normalized.getLabelIds()) {
                    labelCounts.merge(label, 1, Integer::sum);
                    lineCounts.merge(label, deltaLines, Integer::sum);
                }
            }
        }

        if (records.isEmpty()) {
            int migratedLines = countLines(StringUtils.hasText(targetContent) ? targetContent : sourceContent);
            if (migratedLines > 0) {
                Map<String, Integer> migratedLabelCounts = new LinkedHashMap<>();
                migratedLabelCounts.put(BlockLabelConstants.STATUS_MIGRATED, 1);
                Map<String, Integer> migratedLineCounts = new LinkedHashMap<>();
                migratedLineCounts.put(BlockLabelConstants.STATUS_MIGRATED, migratedLines);

                DiffSnapshotDocument snapshotDocument = DiffSnapshotDocument.builder()
                        .comparisonId(projectCode)
                        .filePath(relativePath)
                        .sourceProjectCode(sourceCode)
                        .targetProjectCode(targetCode)
                        .totalBlocks(0)
                        .unlabeledBlocks(0)
                        .averageSimilarity(100d)
                        .labelCounts(migratedLabelCounts)
                        .lineCounts(migratedLineCounts)
                        .totalLineCount(migratedLines)
                        .decisionIds(Collections.emptyList())
                        .build();
                diffSnapshotRepository.save(snapshotDocument);
                return true;
            }
            return false;
        }

        if (fileTotalLines > changedLineTotal) {
            int migratedLines = fileTotalLines - changedLineTotal;
            labelCounts.merge(BlockLabelConstants.STATUS_MIGRATED, 1, Integer::sum);
            lineCounts.merge(BlockLabelConstants.STATUS_MIGRATED, migratedLines, Integer::sum);
        }

        blockDecisionRepository.save(BlockDecisionSnapshot.builder()
                .comparisonId(projectCode)
                .filePath(relativePath)
                .sourceProjectCode(sourceCode)
                .targetProjectCode(targetCode)
                .records(records)
                .build());

        DiffSnapshotDocument snapshotDocument = DiffSnapshotDocument.builder()
                .comparisonId(projectCode)
                .filePath(relativePath)
                .sourceProjectCode(sourceCode)
                .targetProjectCode(targetCode)
                .totalBlocks(records.size())
                .unlabeledBlocks(unlabeled)
                .averageSimilarity(records.isEmpty() ? 0d : similaritySum / records.size())
                .labelCounts(labelCounts)
                .lineCounts(lineCounts)
                .totalLineCount(fileTotalLines)
                .decisionIds(decisionIds)
                .build();
        diffSnapshotRepository.save(snapshotDocument);
        return true;
    }

    private void deleteFileArtifacts(String projectCode, String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return;
        }
        blockDecisionRepository.delete(projectCode, relativePath);
        diffSnapshotRepository.delete(projectCode, relativePath);
        agentSuggestionRepository.deleteByFile(projectCode, relativePath);
    }

    private String normalizeRecordPath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        return path.replace('\\', '/').trim();
    }

    private boolean isSupportedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".java")
                || name.endsWith(".kt")
                || name.endsWith(".kts")
                || name.endsWith(".js")
                || name.endsWith(".ts")
                || name.endsWith(".tsx")
                || name.endsWith(".jsx")
                || name.endsWith(".py")
                || name.endsWith(".go")
                || name.endsWith(".rb")
                || name.endsWith(".cs")
                || name.endsWith(".php")
                || name.endsWith(".sql")
                || name.endsWith(".xml")
                || name.endsWith(".yml")
                || name.endsWith(".yaml")
                || name.endsWith(".json")
                || name.endsWith(".md")
                || name.endsWith(".txt");
    }

    private IgnorePatternMatcher buildIgnoreMatcher() {
        List<String> patterns = scanProperties == null ? Collections.<String>emptyList() : scanProperties.getIgnoreGlobs();
        return IgnorePatternMatcher.from(patterns);
    }

    private boolean isIgnored(Path root, Path path, IgnorePatternMatcher matcher) {
        return matcher != null && !matcher.isEmpty() && matcher.matches(root, path);
    }

    private String detectLanguage(String path) {
        String lowered = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (lowered.endsWith(".java")) {
            return "java";
        }
        if (lowered.endsWith(".kt") || lowered.endsWith(".kts")) {
            return "kotlin";
        }
        if (lowered.endsWith(".js")) {
            return "javascript";
        }
        if (lowered.endsWith(".ts") || lowered.endsWith(".tsx")) {
            return "typescript";
        }
        if (lowered.endsWith(".jsx")) {
            return "jsx";
        }
        if (lowered.endsWith(".py")) {
            return "python";
        }
        if (lowered.endsWith(".go")) {
            return "go";
        }
        if (lowered.endsWith(".css") || lowered.endsWith(".scss")) {
            return "css";
        }
        if (lowered.endsWith(".xml")) {
            return "xml";
        }
        if (lowered.endsWith(".yml") || lowered.endsWith(".yaml")) {
            return "yaml";
        }
        if (lowered.endsWith(".json")) {
            return "json";
        }
        if (lowered.endsWith(".md")) {
            return "markdown";
        }
        return "plain";
    }

    private void clearExistingData(String projectCode) {
        blockDecisionRepository.deleteAll(projectCode);
        diffSnapshotRepository.deleteAll(projectCode);
        agentSuggestionRepository.deleteAll(projectCode);
    }

    private int countLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private String resolveProjectCode(ProjectRootDescriptor descriptor) {
        if (descriptor == null) {
            return "";
        }
        if (StringUtils.hasText(descriptor.getCode())) {
            return descriptor.getCode();
        }
        Path path = descriptor.getPath();
        if (path.getFileName() != null) {
            return path.getFileName().toString();
        }
        return path.toString();
    }
}
