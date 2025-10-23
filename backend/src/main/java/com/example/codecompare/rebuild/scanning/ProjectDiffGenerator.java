package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.block.model.CodeSnapshot;
import com.example.codecompare.rebuild.core.support.IgnorePatternMatcher;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry.ProjectRootDescriptor;
import com.example.codecompare.rebuild.diff.DiffRequest;
import com.example.codecompare.rebuild.diff.DiffResult;
import com.example.codecompare.rebuild.diff.DiffService;
import com.example.codecompare.rebuild.diff.model.DiffMetrics;
import com.example.codecompare.rebuild.diff.model.DiffSegmentType;
import com.example.codecompare.rebuild.repository.AgentSuggestionRepository;
import com.example.codecompare.rebuild.repository.BlockDecisionRepository;
import com.example.codecompare.rebuild.repository.DiffSnapshotRepository;
import com.example.codecompare.rebuild.repository.model.BlockDecisionRecord;
import com.example.codecompare.rebuild.repository.model.BlockDecisionSnapshot;
import com.example.codecompare.rebuild.repository.model.DiffSnapshotDocument;
import com.example.codecompare.rebuild.repository.model.FileChangeType;
import com.example.codecompare.rebuild.repository.model.FileRecord;
import com.example.codecompare.rebuild.scanning.ScanProperties;
import com.example.codecompare.rebuild.scanning.git.GitDiffFile;
import com.example.codecompare.rebuild.scanning.git.GitDiffHunk;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
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
            log.warn("遍历根目录失败，root={}", root, ex);
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
            log.debug("读取文件内容失败，path={}", path, ex);
            return "";
        }
    }

    private String resolveStatus(BlockDiff diff) {
        if (diff == null) {
            return BlockLabelConstants.STATUS_NO_RULES;
        }
        BlockDiff.LabelDescriptor primary = diff.getPrimaryLabel();
        if (primary != null && StringUtils.hasText(primary.getStatusKey())) {
            return primary.getStatusKey();
        }
        List<String> labelIds = diff.getLabelIds();
        if (!CollectionUtils.isEmpty(labelIds)) {
            return labelIds.get(0);
        }
        return BlockLabelConstants.STATUS_NO_RULES;
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
        if (!StringUtils.hasText(status) || BlockLabelConstants.STATUS_NO_RULES.equalsIgnoreCase(status)) {
            return "无规则命中";
        }
        return status;
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
                log.debug("增量扫描未检测到变更文件，跳过 diff，projectCode={}", projectCode);
                return;
            }
            generateIncremental(projectCode, changedRecords, event.getGitDiffFiles());
        } catch (Exception ex) {
            log.warn("执行增量 diff 时发生异常，projectCode={}", projectCode, ex);
        }
    }

    void generate(String projectCode) {
        regenerateAll(projectCode);
    }

    private void regenerateAll(String projectCode) {
        List<ProjectRootDescriptor> sources = projectRootRegistry.getSources();
        List<ProjectRootDescriptor> targets = projectRootRegistry.getTargets();
        if (CollectionUtils.isEmpty(sources) || CollectionUtils.isEmpty(targets)) {
            log.debug("未配置源或目标根目录，无法执行全量 diff。");
            return;
        }
        ProjectRootDescriptor sourceDescriptor = sources.get(0);
        ProjectRootDescriptor targetDescriptor = targets.get(0);
        Path sourceRoot = sourceDescriptor.getPath();
        Path targetRoot = targetDescriptor.getPath();
        String sourceCode = resolveProjectCode(sourceDescriptor);
        String targetCode = resolveProjectCode(targetDescriptor);
        if (!Files.exists(sourceRoot) || !Files.exists(targetRoot)) {
            log.debug("源或目标目录不存在，source={}, target={}", sourceRoot, targetRoot);
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
            if (processFile(projectCode, sourceCode, targetCode, relativePath, sourcePath, targetPath, Collections.emptyList(), null)) {
                processed++;
            }
        }
        log.info("全量 diff 完成，projectCode={}，处理文件数={}", projectCode, processed);
    }

    private void generateIncremental(String projectCode,
                                     List<FileRecord> changedRecords,
                                     List<GitDiffFile> gitDiffFiles) {
        List<ProjectRootDescriptor> sources = projectRootRegistry.getSources();
        List<ProjectRootDescriptor> targets = projectRootRegistry.getTargets();
        if (CollectionUtils.isEmpty(sources) || CollectionUtils.isEmpty(targets)) {
            log.debug("未配置源或目标根目录，无法执行增量 diff。");
            return;
        }
        ProjectRootDescriptor sourceDescriptor = sources.get(0);
        ProjectRootDescriptor targetDescriptor = targets.get(0);
        Path sourceRoot = sourceDescriptor.getPath();
        Path targetRoot = targetDescriptor.getPath();
        String sourceCode = resolveProjectCode(sourceDescriptor);
        String targetCode = resolveProjectCode(targetDescriptor);
        if (!Files.exists(sourceRoot) || !Files.exists(targetRoot)) {
            log.debug("源或目标目录不存在，source={}, target={}", sourceRoot, targetRoot);
            return;
        }

        IgnorePatternMatcher ignoreMatcher = buildIgnoreMatcher();
        Map<String, Path> sourceFiles = collectFiles(sourceRoot, ignoreMatcher);
        Map<String, Path> targetFiles = collectFiles(targetRoot, ignoreMatcher);
        Map<String, List<FileRecord>> recordsByPath = indexRecordsByPath(changedRecords);
        Map<String, GitDiffFile> gitDiffByPath = indexGitDiffByPath(gitDiffFiles);

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
            List<FileRecord> relatedRecords = recordsByPath.getOrDefault(relativePath, Collections.emptyList());
            GitDiffFile gitDiffFile = resolveGitDiffFile(relativePath, relatedRecords, gitDiffByPath);
            if (processFile(projectCode, sourceCode, targetCode, relativePath, sourcePath, targetPath, relatedRecords, gitDiffFile)) {
                processed++;
            }
        }
        log.info("增量 diff 完成，projectCode={}，处理文件数={}，删除文件数={}",
                projectCode, processed, pathsToDelete.size());
    }

    private boolean processFile(String projectCode,
                                String sourceCode,
                                String targetCode,
                                String relativePath,
                                Path sourcePath,
                                Path targetPath,
                                List<FileRecord> relatedRecords,
                                GitDiffFile gitDiffFile) {
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
                .build(), gitDiffFile);
        List<BlockDiff> blocks = result == null ? new ArrayList<BlockDiff>() : new ArrayList<BlockDiff>(result.getSegments());
        List<Map<String, Object>> gitSnapshots = buildGitSnapshots(relatedRecords, sourceCode, targetCode);
        Map<String, Object> gitDiffPayload = buildGitDiffPayload(gitDiffFile);
        GitDiffLineStats gitLineStats = computeGitDiffLineStats(gitDiffFile);
        int fileTotalLines = countLines(StringUtils.hasText(targetContent) ? targetContent : sourceContent);

        BlockProcessingContext context = new BlockProcessingContext(projectCode, relativePath, gitSnapshots, gitDiffPayload);

        for (BlockDiff block : blocks) {
            processBlock(block, context);
        }

        boolean syntheticEnabled = scanProperties.isMarkSyntheticMigrated();
        if (syntheticEnabled && context.records.isEmpty() && Objects.equals(sourceContent, targetContent)) {
            BlockDiff syntheticBlock = buildFullFileSyntheticBlock(sourceContent, targetContent, fileTotalLines);
            processBlock(syntheticBlock, context);
        }

        if (syntheticEnabled) {
            int remainingLinesAfterBlocks = fileTotalLines - context.changedLineTotal;
            if (remainingLinesAfterBlocks > 0) {
                int residualStart = Math.max(1, context.changedLineTotal + 1);
                BlockDiff residualBlock = buildResidualSyntheticBlock(residualStart, remainingLinesAfterBlocks);
                processBlock(residualBlock, context);
            }
        }

        int weightedDenominator = context.weightedLineCount + gitLineStats.getSameLines();
        double fallbackAverage = context.records.isEmpty() ? 100d : context.similaritySum / context.records.size();
        double fileSimilarity = weightedDenominator > 0
                ? ((double) gitLineStats.getSameLines() + context.weightedLineScore) / weightedDenominator * 100d
                : fallbackAverage;


        log.debug("文件统计完成，projectCode={} path={} sameLines={} changedLines={} weightedLines={} result={}",
                projectCode,
                relativePath,
                gitLineStats.getSameLines(),
                gitLineStats.getChangedLines(),
                context.weightedLineCount,
                fileSimilarity);
        if (!context.gitDiffPayload.isEmpty()) {
            context.gitDiffPayload.put("sameLineCount", gitLineStats.getSameLines());
            context.gitDiffPayload.put("addedLineCount", gitLineStats.getAddedLines());
            context.gitDiffPayload.put("removedLineCount", gitLineStats.getRemovedLines());
            context.gitDiffPayload.put("changedLineCount", gitLineStats.getChangedLines());
            context.gitDiffPayload.put("weightedBlockLineCount", context.weightedLineCount);
            context.gitDiffPayload.put("fileSimilarity", fileSimilarity);
        }
        Map<String, Object> gitDiffContainer = context.gitDiffPayload.isEmpty()
                ? Collections.emptyMap()
                : Collections.singletonMap("gitDiff", context.gitDiffPayload);

        if (context.records.isEmpty()) {
            if (context.syntheticBlocks > 0 || !context.labelCounts.isEmpty()) {
                DiffSnapshotDocument snapshotDocument = DiffSnapshotDocument.builder()
                        .comparisonId(projectCode)
                        .filePath(relativePath)
                        .sourceProjectCode(sourceCode)
                        .targetProjectCode(targetCode)
                        .totalBlocks(context.syntheticBlocks)
                        .unlabeledBlocks(context.unlabeled)
                        .averageSimilarity(fileSimilarity)
                        .labelCounts(context.labelCounts)
                        .lineCounts(context.lineCounts)
                        .totalLineCount(fileTotalLines)
                        .decisionIds(Collections.emptyList())
                        .gitSnapshots(gitSnapshots)
                        .gitDiff(gitDiffContainer)
                        .build();
                diffSnapshotRepository.save(snapshotDocument);
                return true;
            }
            int remainingLines = countLines(StringUtils.hasText(targetContent) ? targetContent : sourceContent);
            if (remainingLines > 0) {
                BlockDiff syntheticBlock = buildFullFileSyntheticBlock(sourceContent, targetContent, remainingLines);
                BlockDiff labelled = blockDiffLabeler.label(syntheticBlock);
                boolean hasLabels = labelled != null && !CollectionUtils.isEmpty(labelled.getLabelIds());
                Map<String, Integer> labelCounts = new LinkedHashMap<>();
                Map<String, Integer> lineCounts = new LinkedHashMap<>();
                if (hasLabels) {
                    for (String label : labelled.getLabelIds()) {
                        labelCounts.merge(label, 1, Integer::sum);
                        lineCounts.merge(label, remainingLines, Integer::sum);
                    }
                } else {
                    labelCounts.put(BlockLabelConstants.STATUS_NO_RULES, 1);
                    lineCounts.put(BlockLabelConstants.STATUS_NO_RULES, remainingLines);
                }

                DiffSnapshotDocument snapshotDocument = DiffSnapshotDocument.builder()
                        .comparisonId(projectCode)
                        .filePath(relativePath)
                        .sourceProjectCode(sourceCode)
                        .targetProjectCode(targetCode)
                        .totalBlocks(0)
                        .unlabeledBlocks(hasLabels ? 0 : 0)
                        .averageSimilarity(fileSimilarity)
                        .labelCounts(labelCounts)
                        .lineCounts(lineCounts)
                        .totalLineCount(remainingLines)
                        .decisionIds(Collections.emptyList())
                        .gitSnapshots(gitSnapshots)
                        .gitDiff(gitDiffContainer)
                        .build();
                diffSnapshotRepository.save(snapshotDocument);
                return true;
            }
            return false;
        }

        if (!syntheticEnabled && fileTotalLines > context.changedLineTotal) {
            int remainingLines = fileTotalLines - context.changedLineTotal;
            context.labelCounts.merge(BlockLabelConstants.STATUS_NO_RULES, 1, Integer::sum);
            context.lineCounts.merge(BlockLabelConstants.STATUS_NO_RULES, remainingLines, Integer::sum);
        }

        blockDecisionRepository.save(BlockDecisionSnapshot.builder()
                .comparisonId(projectCode)
                .filePath(relativePath)
                .sourceProjectCode(sourceCode)
                .targetProjectCode(targetCode)
                .diffMode(resolveDiffMode())
                .records(context.records)
                .build());

        DiffSnapshotDocument snapshotDocument = DiffSnapshotDocument.builder()
                .comparisonId(projectCode)
                .filePath(relativePath)
                .sourceProjectCode(sourceCode)
                .targetProjectCode(targetCode)
                .totalBlocks(context.records.size() + context.syntheticBlocks)
                .unlabeledBlocks(context.unlabeled)
                .averageSimilarity(fileSimilarity)
                .labelCounts(context.labelCounts)
                .lineCounts(context.lineCounts)
                .totalLineCount(fileTotalLines)
                .decisionIds(context.decisionIds)
                .gitSnapshots(gitSnapshots)
                .gitDiff(gitDiffContainer)
                .build();
        diffSnapshotRepository.save(snapshotDocument);
        return true;
    }

    private void processBlock(BlockDiff block, BlockProcessingContext context) {
        if (block == null) {
            return;
        }
        BlockDiff labelled = blockDiffLabeler.label(block);
        if (labelled == null) {
            return;
        }
        boolean synthetic = isSynthetic(block) || isSynthetic(labelled);
        if (labelled.isFilteredOut()) {
            if (synthetic) {
                accumulateMetrics(labelled, resolveStatus(labelled), Math.max(1, labelled.getChangedLineCount()), context, true);
                context.syntheticBlocks++;
            }
            return;
        }
        BlockDiff normalized = sanitizeSyntheticMetadata(labelled);
        if (normalized == null) {
            return;
        }
        if (normalized.isFilteredOut()) {
            if (synthetic) {
                accumulateMetrics(normalized, resolveStatus(normalized), Math.max(1, normalized.getChangedLineCount()), context, true);
                context.syntheticBlocks++;
            }
            return;
        }

        int changedLines = normalized.getChangedLineCount();
        int deltaLines = Math.max(1, changedLines);

        String status = resolveStatus(normalized);
        String statusLabel = resolveStatusLabel(normalized, status);

        accumulateMetrics(normalized, status, deltaLines, context, synthetic);

        if (synthetic) {
            context.syntheticBlocks++;
            return;
        }

        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        int segmentStart = normalized.getTargetStartLine();
        int segmentEnd = segmentStart + deltaLines - 1;
        metadata.put("startLine", segmentStart);
        metadata.put("endLine", segmentEnd);
        metadata.put("similarity", normalized.getSimilarityScore());
        metadata.put("changedLines", changedLines);
        metadata.put("type", normalized.getType().name());

        Map<String, Object> normalizedMetadata = normalized.getMetadata();
        if (normalizedMetadata != null && !normalizedMetadata.isEmpty()) {
            for (Map.Entry<String, Object> entry : normalizedMetadata.entrySet()) {
                if (!metadata.containsKey(entry.getKey())) {
                    metadata.put(entry.getKey(), entry.getValue());
                }
            }
        }

        if (!context.gitMetadataAttached) {
            if (!CollectionUtils.isEmpty(context.gitSnapshots)) {
                metadata.put("gitSnapshots", context.gitSnapshots);
            }
            if (!context.gitDiffPayload.isEmpty()) {
                metadata.put("gitDiff", context.gitDiffPayload);
            }
            context.gitMetadataAttached = true;
        }

        BlockDecisionRecord record = BlockDecisionRecord.builder()
                .comparisonId(context.projectCode)
                .filePath(context.relativePath)
                .blockIdentifier(context.relativePath + "#B" + context.index.getAndIncrement())
                .status(status)
                .riskLevel(statusLabel)
                .action("review")
                .metadata(metadata)
                .diff(normalized)
                .build();
        context.records.add(record);
        context.decisionIds.add(record.getId());
    }

    private BlockDiff buildFullFileSyntheticBlock(String sourceContent,
                                                  String targetContent,
                                                  int fileTotalLines) {
        List<String> sourceLines = splitIntoLines(sourceContent);
        List<String> targetLines = splitIntoLines(targetContent);
        if (targetLines.isEmpty() && !sourceLines.isEmpty()) {
            targetLines = new ArrayList<String>(sourceLines);
        }
        if (sourceLines.isEmpty() && !targetLines.isEmpty()) {
            sourceLines = new ArrayList<String>(targetLines);
        }
        if (targetLines.isEmpty()) {
            targetLines = new ArrayList<String>(Collections.singletonList(""));
        }
        if (sourceLines.isEmpty()) {
            sourceLines = new ArrayList<String>(targetLines);
        }
        int changedLines = Math.max(1, fileTotalLines);
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("syntheticType", "full-file");
        return BlockDiff.builder()
                .type(DiffSegmentType.CHANGE)
                .sourceStartLine(1)
                .targetStartLine(1)
                .changedLineCount(changedLines)
                .similarityScore(100d)
                .diffMetrics(DiffMetrics.of(sourceContent, targetContent, 100d))
                .sourceLines(sourceLines)
                .targetLines(targetLines)
                .metadata(metadata)
                .build();
    }

    private BlockDiff buildResidualSyntheticBlock(int startLine, int remainingLines) {
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("syntheticType", "residual");
        metadata.put("residualLineCount", remainingLines);
        return BlockDiff.builder()
                .type(DiffSegmentType.CHANGE)
                .sourceStartLine(Math.max(1, startLine))
                .targetStartLine(Math.max(1, startLine))
                .changedLineCount(Math.max(1, remainingLines))
                .similarityScore(100d)
                .diffMetrics(DiffMetrics.of("", "", 100d))
                .metadata(metadata)
                .build();
    }

    private BlockDiff sanitizeSyntheticMetadata(BlockDiff diff) {
        if (diff == null) {
            return null;
        }
        Map<String, Object> metadata = diff.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return diff;
        }
        boolean hasSynthetic = metadata.containsKey("syntheticType") || metadata.containsKey("synthetic_type");
        if (!hasSynthetic) {
            return diff;
        }
        Map<String, Object> sanitized = new LinkedHashMap<String, Object>(metadata);
        sanitized.remove("syntheticType");
        sanitized.remove("synthetic_type");
        if (sanitized.isEmpty()) {
            sanitized = Collections.emptyMap();
        }
        return BlockDiff.from(diff)
                .metadata(sanitized)
                .build();
    }

    private void accumulateMetrics(BlockDiff diff,
                                   String status,
                                   int deltaLines,
                                   BlockProcessingContext context,
                                   boolean synthetic) {
        context.changedLineTotal += deltaLines;
        double similarity = diff.getSimilarityScore();
        double similarityRatio = similarity / 100d;
        context.weightedLineScore += deltaLines * similarityRatio;
        context.weightedLineCount += deltaLines;
        if (!synthetic) {
            context.similaritySum += similarity;
            if (CollectionUtils.isEmpty(diff.getLabels())) {
                context.unlabeled++;
            }
        }
        if (CollectionUtils.isEmpty(diff.getLabelIds())) {
            String effectiveStatus = StringUtils.hasText(status) ? status : BlockLabelConstants.STATUS_NO_RULES;
            context.labelCounts.merge(effectiveStatus, 1, Integer::sum);
            context.lineCounts.merge(effectiveStatus, deltaLines, Integer::sum);
        } else {
            for (String label : diff.getLabelIds()) {
                context.labelCounts.merge(label, 1, Integer::sum);
                context.lineCounts.merge(label, deltaLines, Integer::sum);
            }
        }
    }

    private boolean isSynthetic(BlockDiff diff) {
        if (diff == null) {
            return false;
        }
        Map<String, Object> metadata = diff.getMetadata();
        if (CollectionUtils.isEmpty(metadata)) {
            return false;
        }
        if (metadata.containsKey("syntheticType") || metadata.containsKey("synthetic_type")) {
            return true;
        }
        Object synthetic = metadata.get("synthetic");
        if (synthetic instanceof Boolean) {
            return (Boolean) synthetic;
        }
        if (synthetic instanceof Number) {
            return ((Number) synthetic).intValue() != 0;
        }
        if (synthetic instanceof String) {
            String normalized = ((String) synthetic).trim();
            if (normalized.isEmpty()) {
                return false;
            }
            return "true".equalsIgnoreCase(normalized)
                    || "yes".equalsIgnoreCase(normalized)
                    || "on".equalsIgnoreCase(normalized)
                    || "1".equals(normalized);
        }
        return false;
    }

    private List<String> splitIntoLines(String content) {
        if (!StringUtils.hasText(content)) {
            return new ArrayList<String>();
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] values = normalized.split("\n", -1);
        return new ArrayList<String>(Arrays.asList(values));
    }

    private String resolveDiffMode() {
        String engine = scanProperties == null ? null : scanProperties.getDiffEngine();
        if ("git".equalsIgnoreCase(engine)) {
            return "incremental";
        }
        return "full";
    }

    private static final class BlockProcessingContext {
        private final String projectCode;
        private final String relativePath;
        private final List<Map<String, Object>> gitSnapshots;
        private final Map<String, Object> gitDiffPayload;
        private final List<BlockDecisionRecord> records = new ArrayList<BlockDecisionRecord>();
        private final Map<String, Integer> labelCounts = new LinkedHashMap<String, Integer>();
        private final Map<String, Integer> lineCounts = new LinkedHashMap<String, Integer>();
        private final List<String> decisionIds = new ArrayList<String>();
        private final AtomicInteger index = new AtomicInteger(1);
        private double weightedLineScore;
        private int weightedLineCount;
        private double similaritySum;
        private int unlabeled;
        private int changedLineTotal;
        private int syntheticBlocks;
        private boolean gitMetadataAttached;

        private BlockProcessingContext(String projectCode,
                                       String relativePath,
                                       List<Map<String, Object>> gitSnapshots,
                                       Map<String, Object> gitDiffPayload) {
            this.projectCode = projectCode;
            this.relativePath = relativePath;
            this.gitSnapshots = gitSnapshots == null ? Collections.<Map<String, Object>>emptyList() : gitSnapshots;
            this.gitDiffPayload = gitDiffPayload == null || gitDiffPayload.isEmpty()
                    ? new LinkedHashMap<String, Object>()
                    : new LinkedHashMap<String, Object>(gitDiffPayload);
        }
    }

    private Map<String, List<FileRecord>> indexRecordsByPath(List<FileRecord> records) {
        Map<String, List<FileRecord>> indexed = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(records)) {
            return indexed;
        }
        for (FileRecord record : records) {
            if (record == null) {
                continue;
            }
            String path = normalizeRecordPath(record.getPath());
            if (!StringUtils.hasText(path)) {
                continue;
            }
            indexed.computeIfAbsent(path, key -> new ArrayList<>()).add(record);
        }
        return indexed;
    }

    private Map<String, GitDiffFile> indexGitDiffByPath(List<GitDiffFile> gitDiffFiles) {
        Map<String, GitDiffFile> indexed = new LinkedHashMap<>();
        if (CollectionUtils.isEmpty(gitDiffFiles)) {
            return indexed;
        }
        for (GitDiffFile file : gitDiffFiles) {
            if (file == null || !StringUtils.hasText(file.getPath())) {
                continue;
            }
            String normalized = normalizeRecordPath(file.getPath());
            indexed.put(normalized, file);
            if (StringUtils.hasText(file.getPreviousPath())) {
                indexed.put(normalizeRecordPath(file.getPreviousPath()), file);
            }
        }
        return indexed;
    }

    private GitDiffFile resolveGitDiffFile(String relativePath,
                                           List<FileRecord> relatedRecords,
                                           Map<String, GitDiffFile> indexed) {
        if (indexed.isEmpty()) {
            return null;
        }
        GitDiffFile direct = indexed.get(relativePath);
        if (direct != null) {
            return direct;
        }
        if (!CollectionUtils.isEmpty(relatedRecords)) {
            for (FileRecord record : relatedRecords) {
                String previous = normalizeRecordPath(record.getGitPreviousPath());
                if (StringUtils.hasText(previous)) {
                    GitDiffFile candidate = indexed.get(previous);
                    if (candidate != null) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private List<Map<String, Object>> buildGitSnapshots(List<FileRecord> records,
                                                        String sourceCode,
                                                        String targetCode) {
        if (CollectionUtils.isEmpty(records)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> snapshots = new ArrayList<>(records.size());
        for (FileRecord record : records) {
            if (record == null) {
                continue;
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("projectCode", record.getProjectCode());
            snapshot.put("projectType", resolveProjectType(record.getProjectCode(), sourceCode, targetCode));
            snapshot.put("gitCommitId", record.getGitCommitId());
            snapshot.put("gitTimestamp", record.getGitCommitTime());
            snapshot.put("gitAuthor", record.getGitAuthor());
            snapshot.put("gitBranch", record.getGitBranch());
            snapshot.put("workingTreeChange", record.isWorkingTreeChange());
            snapshot.put("changeType", record.getChangeType() == null ? null : record.getChangeType().name());
            snapshot.put("previousPath", record.getGitPreviousPath());
            snapshot.put("scannedAt", record.getScannedAt());
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private Map<String, Object> buildGitDiffPayload(GitDiffFile gitDiffFile) {
        if (gitDiffFile == null || CollectionUtils.isEmpty(gitDiffFile.getHunks())) {
            return Collections.emptyMap();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("path", gitDiffFile.getPath());
        if (StringUtils.hasText(gitDiffFile.getPreviousPath())) {
            payload.put("previousPath", gitDiffFile.getPreviousPath());
        }
        payload.put("changeType", gitDiffFile.getChangeType() == null ? null : gitDiffFile.getChangeType().name());
        payload.put("truncated", gitDiffFile.isTruncated());
        payload.put("totalBytes", gitDiffFile.getTotalBytes());
        List<Map<String, Object>> hunks = new ArrayList<>(gitDiffFile.getHunks().size());
        for (GitDiffHunk hunk : gitDiffFile.getHunks()) {
            if (hunk == null) {
                continue;
            }
            Map<String, Object> hunkMap = new LinkedHashMap<>();
            hunkMap.put("oldStartLine", hunk.getOldRange().getStartLine());
            hunkMap.put("oldLineCount", hunk.getOldRange().getLineCount());
            hunkMap.put("newStartLine", hunk.getNewRange().getStartLine());
            hunkMap.put("newLineCount", hunk.getNewRange().getLineCount());
            hunkMap.put("lines", hunk.getLines());
            hunkMap.put("byteSize", hunk.getByteSize());
            hunks.add(hunkMap);
        }
        payload.put("hunks", hunks);
        return payload;
    }

    private GitDiffLineStats computeGitDiffLineStats(GitDiffFile gitDiffFile) {
        if (gitDiffFile == null || CollectionUtils.isEmpty(gitDiffFile.getHunks())) {
            return GitDiffLineStats.empty();
        }
        int sameLines = 0;
        int addedLines = 0;
        int removedLines = 0;
        for (GitDiffHunk hunk : gitDiffFile.getHunks()) {
            if (hunk == null || CollectionUtils.isEmpty(hunk.getLines())) {
                continue;
            }
            for (String line : hunk.getLines()) {
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                char marker = line.charAt(0);
                if (marker == ' ') {
                    sameLines++;
                } else if (marker == '+') {
                    addedLines++;
                } else if (marker == '-') {
                    removedLines++;
                }
            }
        }
        return new GitDiffLineStats(sameLines, addedLines, removedLines);
    }

    private String resolveProjectType(String projectCode, String sourceCode, String targetCode) {
        if (!StringUtils.hasText(projectCode)) {
            return "UNKNOWN";
        }
        if (projectCode.equals(sourceCode)) {
            return "SOURCE";
        }
        if (projectCode.equals(targetCode)) {
            return "TARGET";
        }
        return "UNKNOWN";
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

    private static final class GitDiffLineStats {
        private static final GitDiffLineStats EMPTY = new GitDiffLineStats(0, 0, 0);
        private final int sameLines;
        private final int addedLines;
        private final int removedLines;

        private GitDiffLineStats(int sameLines, int addedLines, int removedLines) {
            this.sameLines = Math.max(0, sameLines);
            this.addedLines = Math.max(0, addedLines);
            this.removedLines = Math.max(0, removedLines);
        }

        static GitDiffLineStats empty() {
            return EMPTY;
        }

        int getSameLines() {
            return sameLines;
        }

        int getAddedLines() {
            return addedLines;
        }

        int getRemovedLines() {
            return removedLines;
        }

        int getChangedLines() {
            return addedLines + removedLines;
        }
    }
}
