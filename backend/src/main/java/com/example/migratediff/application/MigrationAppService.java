package com.example.migratediff.application;

import com.example.migratediff.domain.diff.DeltaGroup;
import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.domain.migration.DecisionType;
import com.example.migratediff.domain.migration.MigrationBlockResult;
import com.example.migratediff.domain.migration.MigrationResult;
import com.example.migratediff.domain.migration.MigrationStatus;
import com.example.migratediff.domain.migration.MigrationSummary;
import com.example.migratediff.domain.migration.MigrationTask;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.domain.repo.RepoPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 应用层服务：负责聚合模板预览、落盘与回退，并协调覆盖率分析。
 */
@Service
public class MigrationAppService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationAppService.class);
    private final CoverageAppService coverageAppService;
    private final DiffAppService diffAppService;
    private final GenerateAppService generateAppService;

    private final Map<String, MigrationSummary> summaryStore = new ConcurrentHashMap<>();
    private final Map<String, MigrationResult> resultStore = new ConcurrentHashMap<>();

    public MigrationAppService(CoverageAppService coverageAppService,
                               DiffAppService diffAppService,
                               GenerateAppService generateAppService) {
        this.coverageAppService = coverageAppService;
        this.diffAppService = diffAppService;
        this.generateAppService = generateAppService;
    }

    /**
     * 创建或补全迁移任务基础信息。
     */
    public MigrationTask createTask(MigrationSummary summary) {
        MigrationTask task = Optional.ofNullable(summary).map(MigrationSummary::getTask).orElse(null);
        if (task == null) {
            task = new MigrationTask();
        }
        if (!StringUtils.hasText(task.getId())) {
            task.setId(UUID.randomUUID().toString());
        }
        if (!StringUtils.hasText(task.getTaskName())) {
            task.setTaskName("migration-" + task.getId());
        }
        task.setCreatedAt(Optional.ofNullable(task.getCreatedAt()).orElse(LocalDateTime.now()));
        task.setStatus(MigrationStatus.PENDING);
        if (summary != null) {
            summary.setTask(task);
        }
        return task;
    }

    /**
     * 生成迁移模板预览，结果会缓存用于后续应用与回退。
     */
    public MigrationSummary preview(MigrationSummary summary) {
        if (summary == null) {
            return null;
        }
        MigrationTask task = createTask(summary);
        if (task.getDeltaGroup() == null) {
            task.setDeltaGroup(diffAppService.mergeDelta(summary.getDeltaOSummary(), summary.getDeltaGSummary()));
        }
        MigrationResult result = buildPreviewResult(summary);
        summary.setResult(result);
        summaryStore.put(task.getId(), summary);
        resultStore.put(task.getId(), result);
        return summary;
    }

    /**
     * 在已有预览基础上执行模板写入。
     */
    public MigrationSummary apply(MigrationSummary summary) {
        if (summary == null) {
            return null;
        }
        previewIfNeeded(summary);
        MigrationResult result = applyInternal(summary);
        summary.setResult(result);
        summaryStore.put(summary.getTask().getId(), summary);
        resultStore.put(summary.getTask().getId(), result);
        return summary;
    }

    /**
     * 根据任务编号应用模板，用于控制层仅携带 taskId 的场景。
     */
    public MigrationResult apply(MigrationTask task) {
        if (task == null || !StringUtils.hasText(task.getId())) {
            return failure("任务信息缺失，无法写入迁移模板。");
        }
        MigrationSummary stored = summaryStore.get(task.getId());
        if (stored == null) {
            return failure("未找到任务 " + task.getId() + " 的预览结果，请先生成预览。");
        }
        MigrationSummary updated = apply(stored);
        return updated.getResult();
    }

    /**
     * 回退模板写入，恢复备份文件。
     */
    public MigrationResult revert(MigrationTask task) {
        if (task == null || !StringUtils.hasText(task.getId())) {
            return failure("任务信息缺失，无法回退。");
        }
        MigrationSummary stored = summaryStore.get(task.getId());
        if (stored == null) {
            return failure("未找到任务 " + task.getId() + " 的缓存信息，无法回退。");
        }
        previewIfNeeded(stored);
        Path targetFile = resolveTargetFile(stored);
        if (targetFile == null) {
            return failure("未能解析目标文件路径，无法回退。");
        }
        Path backup = backupPath(targetFile);
        if (!Files.exists(backup)) {
            return failure("未找到备份文件 " + backup.getFileName() + "，请手动检查。");
        }
        try {
            Files.copy(backup, targetFile, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(backup);
            MigrationResult result = stored.getResult();
            result.setSuccess(true);
            result.setMessage("已根据备份恢复文件：" + targetFile);
            return result;
        } catch (IOException ex) {
            return failure("回退过程中发生异常：" + ex.getMessage());
        }
    }

    /**
     * 控制层可通过任务编号获取最近一次预览结果。
     */
    public Optional<MigrationSummary> findSummary(String taskId) {
        return Optional.ofNullable(summaryStore.get(taskId));
    }

    private MigrationSummary previewIfNeeded(MigrationSummary summary) {
        if (summary.getResult() == null || CollectionUtils.isEmpty(summary.getResult().getBlockResults())) {
            return preview(summary);
        }
        return summary;
    }

    private MigrationResult buildPreviewResult(MigrationSummary summary) {
        MigrationTask task = summary.getTask();
        if (task == null || task.getDeltaGroup() == null) {
            return failure("DeltaGroup 为空，无法生成迁移模板。");
        }
        List<MigrationBlockResult> blockResults = new ArrayList<>();
        List<String> targetSnapshot = readTargetFileLines(resolveTargetFile(summary));
        StringBuilder previewBuilder = new StringBuilder();
        boolean hasError = false;
        int index = 1;
        for (BlockPair pair : collectBlockPairs(task.getDeltaGroup())) {
            try {
                DiffBlock gaussBlock = pair.getGaussBlock();
                DiffBlock oracleBlock = pair.getOracleBlock();
                String gaussSnippet = extractSnippet(gaussBlock);
                String oracleSnippet = extractSnippet(oracleBlock);
                DecisionType decision = generateAppService.classifyBlock(oracleSnippet, gaussSnippet);
                String template = generateAppService.generateTemplate(oracleSnippet, gaussSnippet, decision);
                String message = messageFor(decision);
                if (StringUtils.hasText(template)) {
                    if (previewBuilder.length() > 0) {
                        previewBuilder.append(System.lineSeparator()).append(System.lineSeparator());
                    }
                    previewBuilder.append(template);
                }
                blockResults.add(MigrationBlockResult.builder()
                        .index(index++)
                        .decisionType(decision)
                        .block(gaussBlock != null ? gaussBlock : oracleBlock)
                        .oracleBlock(oracleBlock)
                        .gaussBlock(gaussBlock)
                        .oracleSnippet(oracleSnippet)
                        .gaussSnippet(gaussSnippet)
                        .template(template)
                        .message(message)
                        .startLine(resolveStartLine(gaussBlock, oracleBlock))
                        .endLine(resolveEndLine(gaussBlock, oracleBlock))
                        .insertBeforeLine(resolveInsertBeforeLine(gaussBlock, oracleBlock, decision))
                        .originalSnippet(gaussSnippet)
                        .snapshotSnippet(resolveSnapshotSnippet(gaussBlock, targetSnapshot))
                        .build());
            } catch (Exception ex) {
                hasError = true;
                blockResults.add(MigrationBlockResult.builder()
                        .index(index++)
                        .decisionType(DecisionType.SKIP)
                        .block(pair.getGaussBlock() != null ? pair.getGaussBlock() : pair.getOracleBlock())
                        .template("")
                        .message("生成模板失败，已跳过该差异块。")
                        .errorMessage(ex.getMessage())
                        .build());
            }
        }
        return MigrationResult.builder()
                .task(task)
                .success(!hasError)
                .message(buildSummaryMessage(blockResults))
                .blockResults(blockResults)
                .previewContent(previewBuilder.toString())
                .affectedFiles(new ArrayList<>(collectAffectedFiles(summary)))
                .build();
    }    private MigrationResult applyInternal(MigrationSummary summary) {

        MigrationResult preview = summary.getResult();

        if (preview == null || CollectionUtils.isEmpty(preview.getBlockResults())) {

            return failure("预览结果为空，无法写入迁移模板。");
        }

        Path targetFile = resolveTargetFile(summary);

        if (targetFile == null) {

            return failure("未能解析目标文件路径，无法写入。");
        }

        try {

            Files.createDirectories(targetFile.getParent());

            Path backup = backupPath(targetFile);

            String currentContent = "";

            if (Files.exists(targetFile)) {

                Files.copy(targetFile, backup, StandardCopyOption.REPLACE_EXISTING);

                currentContent = readUtf8WithoutBom(targetFile);

            }

            ReplacementOutcome outcome = applyBlockResultsByLines(currentContent, preview.getBlockResults());
            if (!outcome.isChanged()) {

                preview.setSuccess(false);

                preview.setMessage("未产生新的迁移模板，请调整参数后重试。");
                return preview;

            }

            Files.write(targetFile, outcome.getContent().getBytes(StandardCharsets.UTF_8));

            preview.setSuccess(true);

            preview.setMessage(buildApplyMessage(targetFile, outcome.getWarnings()));

            coverageAppService.analyzeCoverage(summary.getTask().getId(), summary.getTask().getDeltaGroup(), true);

        } catch (IOException ex) {

            preview.setSuccess(false);

            preview.setMessage("写入迁移模板失败：" + ex.getMessage());
        }

        return preview;

    }



    private List<BlockPair> collectBlockPairs(DeltaGroup deltaGroup) {
        List<BlockPair> pairs = new ArrayList<>();
        if (deltaGroup == null) {
            return pairs;
        }
        List<DiffBlock> oracleBlocks = extractBlocks(deltaGroup.getDeltaO());
        List<DiffBlock> gaussBlocks = extractBlocks(deltaGroup.getDeltaG());
        int max = Math.max(oracleBlocks.size(), gaussBlocks.size());
        for (int index = 0; index < max; index++) {
            DiffBlock oracleBlock = index < oracleBlocks.size() ? oracleBlocks.get(index) : null;
            DiffBlock gaussBlock = index < gaussBlocks.size() ? gaussBlocks.get(index) : null;
            if (oracleBlock == null && gaussBlock == null) {
                continue;
            }
            pairs.add(new BlockPair(oracleBlock, gaussBlock));
        }
        return pairs;
    }

    private List<DiffBlock> extractBlocks(DiffFile diffFile) {
        if (diffFile == null || CollectionUtils.isEmpty(diffFile.getBlocks())) {
            return Collections.emptyList();
        }
        return diffFile.getBlocks();
    }

    private Set<String> collectAffectedFiles(MigrationSummary summary) {
        Set<String> files = new LinkedHashSet<>();
        if (summary == null || summary.getTask() == null || summary.getTask().getDeltaGroup() == null) {
            return files;
        }
        DeltaGroup deltaGroup = summary.getTask().getDeltaGroup();
        addFile(files, resolveRepositoryRoot(summary), deltaGroup.getDeltaO());
        addFile(files, resolveRepositoryRoot(summary), deltaGroup.getDeltaG());
        return files;
    }

    private void addFile(Set<String> files, String root, DiffFile diffFile) {
        if (diffFile == null || !StringUtils.hasText(diffFile.getRelativePath())) {
            return;
        }
        if (StringUtils.hasText(root)) {
            files.add(Paths.get(root, diffFile.getRelativePath()).toString());
        } else {
            files.add(diffFile.getRelativePath());
        }
    }

    private Integer normalizeLineNumber(int lineNumber) {
        return lineNumber > 0 ? lineNumber : null;
    }

    private Integer resolveStartLine(DiffBlock gaussBlock, DiffBlock oracleBlock) {
        Integer line = gaussBlock != null ? normalizeLineNumber(gaussBlock.getStartLineTo()) : null;
        if (line != null) {
            return line;
        }
        return oracleBlock != null ? normalizeLineNumber(oracleBlock.getStartLineTo()) : null;
    }

    private Integer resolveEndLine(DiffBlock gaussBlock, DiffBlock oracleBlock) {
        Integer line = gaussBlock != null ? normalizeLineNumber(gaussBlock.getEndLineTo()) : null;
        if (line != null) {
            return line;
        }
        return oracleBlock != null ? normalizeLineNumber(oracleBlock.getEndLineTo()) : null;
    }

    private Integer resolveInsertBeforeLine(DiffBlock gaussBlock, DiffBlock oracleBlock, DecisionType decision) {
        Integer line = resolveStartLine(gaussBlock, oracleBlock);
        if (line != null) {
            return line;
        }
        return decision == DecisionType.INSERT ? 1 : null;
    }

    private String extractSnippet(DiffBlock block) {
        return block == null ? null : block.getContentTo();
    }
    private List<String> readTargetFileLines(Path targetFile) {
        if (targetFile == null || !Files.exists(targetFile)) {
            return Collections.emptyList();
        }
        try {
            List<String> lines = Files.readAllLines(targetFile, StandardCharsets.UTF_8);
            if (!lines.isEmpty()) {
                String first = lines.get(0);
                if (first != null && first.startsWith("\uFEFF")) {
                    lines.set(0, first.substring(1));
                }
            }
            return lines;
        } catch (IOException ex) {
            LOGGER.warn("读取目标文件失败，无法记录原始片段: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private String resolveSnapshotSnippet(DiffBlock block, List<String> targetSnapshot) {
        if (block == null || CollectionUtils.isEmpty(targetSnapshot)) {
            return null;
        }
        Integer startLine = normalizeLineNumber(block.getStartLineTo());
        Integer endLine = normalizeLineNumber(block.getEndLineTo());
        if (startLine == null || endLine == null || endLine < startLine) {
            return null;
        }
        int fromIndex = Math.max(0, startLine - 1);
        int toIndex = Math.min(targetSnapshot.size(), endLine);
        if (fromIndex >= toIndex) {
            return null;
        }
        return joinLines(targetSnapshot.subList(fromIndex, toIndex));
    }

    private String joinLines(List<String> lines) {
        if (CollectionUtils.isEmpty(lines)) {
            return "";
        }
        return StringUtils.collectionToDelimitedString(lines, System.lineSeparator());
    }

    private String resolveRepositoryRoot(MigrationSummary summary) {
        RepoConfig config = Optional.ofNullable(summary)
                .map(MigrationSummary::getDeltaGSummary)
                .map(DiffSummary::getRepoConfig)
                .orElseGet(() -> Optional.ofNullable(summary)
                        .map(MigrationSummary::getDeltaOSummary)
                        .map(DiffSummary::getRepoConfig)
                        .orElse(null));
        if (config == null) {
            return null;
        }
        RepoPath repoPath = config.getRepoPath();
        return repoPath != null ? repoPath.getAbsolutePath() : null;
    }

    private Path resolveTargetFile(MigrationSummary summary) {
        String root = resolveRepositoryRoot(summary);
        DeltaGroup deltaGroup = Optional.ofNullable(summary)
                .map(MigrationSummary::getTask)
                .map(MigrationTask::getDeltaGroup)
                .orElse(null);
        if (deltaGroup == null) {
            return null;
        }
        String relativePath = Optional.ofNullable(deltaGroup.getDeltaG())
                .map(DiffFile::getRelativePath)
                .filter(StringUtils::hasText)
                .orElseGet(() -> Optional.ofNullable(deltaGroup.getDeltaO())
                        .map(DiffFile::getRelativePath)
                        .orElse(null));
        if (!StringUtils.hasText(relativePath)) {
            return null;
        }
        if (StringUtils.hasText(root)) {
            return Paths.get(root, relativePath);
        }
        return Paths.get(relativePath);
    }

    private String messageFor(DecisionType decision) {
        switch (decision) {
            case INSERT:
                return "目标缺失实现，建议插入源端代码。";
            case UPDATE:
                return "目标存在差异，建议更新。";
            case DELETE:
                return "源端无实现，请评估是否删除目标代码。";
            default:
                return "无需处理，已跳过。";
        }
    }

    private String buildSummaryMessage(List<MigrationBlockResult> blockResults) {
        long insert = count(blockResults, DecisionType.INSERT);
        long update = count(blockResults, DecisionType.UPDATE);
        long delete = count(blockResults, DecisionType.DELETE);
        long skip = count(blockResults, DecisionType.SKIP);
        return String.format("预览完成：插入=%d，更新=%d，删除提示=%d，跳过=%d。", insert, update, delete, skip);
    }

    private long count(List<MigrationBlockResult> blockResults, DecisionType type) {
        if (CollectionUtils.isEmpty(blockResults)) {
            return 0L;
        }
        return blockResults.stream().filter(result -> result.getDecisionType() == type).count();
    }

    private MigrationResult failure(String message) {
        MigrationResult result = new MigrationResult();
        result.setSuccess(false);
        result.setMessage(message);
        result.setAffectedFiles(new ArrayList<>());
        return result;
    }

    private Path backupPath(Path targetFile) {
        String fileName = targetFile.getFileName().toString();
        return targetFile.resolveSibling(fileName + ".migrationBak");
    }

    private String buildApplyMessage(Path targetFile, List<String> warnings) {
        StringBuilder builder = new StringBuilder();
        builder.append("迁移模板写入完成：").append(targetFile);
        if (!CollectionUtils.isEmpty(warnings)) {
            builder.append("; 提示: ").append(StringUtils.collectionToDelimitedString(warnings, "; "));
        }
        return builder.toString();
    }

    private String readUtf8WithoutBom(Path targetFile) throws IOException {
        byte[] bytes = Files.readAllBytes(targetFile);
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.startsWith("\uFEFF")) {
            return content.substring(1);
        }
        return content;
    }

    private ReplacementOutcome applyBlockResultsByLines(String currentContent, List<MigrationBlockResult> blockResults) {
        if (CollectionUtils.isEmpty(blockResults)) {
            return new ReplacementOutcome(currentContent == null ? "" : currentContent, Collections.<String>emptyList(), false);
        }
        LineBuffer buffer = new LineBuffer(currentContent);
        List<MigrationBlockResult> actionable = new ArrayList<>();
        for (MigrationBlockResult blockResult : blockResults) {
            if (blockResult == null) {
                continue;
            }
            DecisionType decisionType = blockResult.getDecisionType();
            if (decisionType == null || decisionType == DecisionType.SKIP) {
                continue;
            }
            if (!StringUtils.hasText(blockResult.getTemplate()) && decisionType != DecisionType.DELETE) {
                continue;
            }
            actionable.add(blockResult);
        }
        actionable.sort((left, right) -> Integer.compare(resolveEffectiveLine(right), resolveEffectiveLine(left)));

        List<String> warnings = new ArrayList<>();
        boolean changed = false;
        for (MigrationBlockResult blockResult : actionable) {
            ApplyFeedback feedback = applySingleBlock(buffer, blockResult);
            warnings.addAll(feedback.getWarnings());
            if (feedback.isChanged()) {
                changed = true;
            }
        }
        return new ReplacementOutcome(buffer.asText(), warnings, changed);
    }

    private ApplyFeedback applySingleBlock(LineBuffer buffer, MigrationBlockResult blockResult) {
        List<String> warnings = new ArrayList<>();
        DecisionType decision = blockResult.getDecisionType();
        if (decision == null) {
            return new ApplyFeedback(false, warnings);
        }
        System.out.println("Applying block#" + blockResult.getIndex() + " decision=" + decision);
        String normalizedTemplate = normalizeTemplate(blockResult.getTemplate());
        List<String> templateLines = splitTemplateLines(normalizedTemplate);
        int insertIndex = resolveInsertIndex(buffer, blockResult);
        LineMatch lineMatch = locateBySnippet(buffer, blockResult, blockResult.getOriginalSnippet());
        if (!lineMatch.isFound()) {
            lineMatch = locateBySnippet(buffer, blockResult, blockResult.getSnapshotSnippet());
        }
        if (!lineMatch.isFound() && !CollectionUtils.isEmpty(templateLines)) {
            LineMatch markerMatch = locateByMarkers(buffer, templateLines, insertIndex);
            if (markerMatch.isFound()) {
                lineMatch = markerMatch;
            }
        }
        boolean matched = lineMatch.isFound();
        if (!matched) {
            warnings.add(buildFallbackMessage(blockResult));
        } else if (lineMatch.getStrategy() == MatchStrategy.FUZZY) {
            warnings.add(buildFuzzyMessage(blockResult));
        }
        boolean changed = false;
        switch (decision) {
            case UPDATE:
                if (!CollectionUtils.isEmpty(templateLines)) {
                    if (matched) {
                        buffer.replace(lineMatch.getStartIndex(), lineMatch.getEndIndex(), templateLines);
                    } else {
                        buffer.insertBefore(insertIndex, templateLines);
                    }
                    changed = true;
                }
                break;
            case INSERT:
                if (!CollectionUtils.isEmpty(templateLines)) {
                    if (matched) {
                        buffer.replace(lineMatch.getStartIndex(), lineMatch.getEndIndex(), templateLines);
                    } else {
                        buffer.insertBefore(insertIndex, templateLines);
                    }
                    changed = true;
                }
                break;
            case DELETE:
                if (matched) {
                    if (CollectionUtils.isEmpty(templateLines)) {
                        buffer.replace(lineMatch.getStartIndex(), lineMatch.getEndIndex(), Collections.<String>emptyList());
                    } else {
                        buffer.replace(lineMatch.getStartIndex(), lineMatch.getEndIndex(), templateLines);
                    }
                    changed = true;
                } else if (!CollectionUtils.isEmpty(templateLines)) {
                    buffer.insertBefore(insertIndex, templateLines);
                    changed = true;
                }
                break;
            default:
                break;
        }
        return new ApplyFeedback(changed, warnings);
    }

    private LineMatch locateBySnippet(LineBuffer buffer, MigrationBlockResult blockResult, String snippet) {
        List<String> snippetLines = splitSnippetLines(snippet);
        if (CollectionUtils.isEmpty(snippetLines)) {
            return LineMatch.notFound();
        }
        if (buffer.lineCount() < snippetLines.size()) {
            return LineMatch.notFound();
        }
        Integer startLine = blockResult.getStartLine();
        Integer endLine = blockResult.getEndLine();
        if (startLine != null && startLine > 0 && endLine != null && endLine >= startLine) {
            int startIndex = Math.max(0, Math.min(startLine - 1, buffer.lineCount() - snippetLines.size()));
            if (segmentMatches(buffer, startIndex, snippetLines)) {
                return LineMatch.of(startIndex, startIndex + snippetLines.size() - 1, MatchStrategy.DIRECT);
            }
        }
        return fuzzySearch(buffer, snippetLines, blockResult.getStartLine());
    }

    private LineMatch fuzzySearch(LineBuffer buffer, List<String> snippetLines, Integer anchorLine) {
        if (CollectionUtils.isEmpty(snippetLines) || buffer.lineCount() < snippetLines.size()) {
            return LineMatch.notFound();
        }
        int window = 20;
        int snippetSize = snippetLines.size();
        int maxStart = buffer.lineCount() - snippetSize;
        if (maxStart < 0) {
            return LineMatch.notFound();
        }
        if (anchorLine != null && anchorLine > 0) {
            int anchorIndex = Math.max(0, Math.min(anchorLine - 1, maxStart));
            int from = Math.max(0, anchorIndex - window);
            int to = Math.min(maxStart, anchorIndex + window);
            LineMatch ranged = scanRange(buffer, snippetLines, from, to, MatchStrategy.FUZZY);
            if (ranged.isFound()) {
                return ranged;
            }
        }
        return scanRange(buffer, snippetLines, 0, maxStart, MatchStrategy.FUZZY);
    }

    private LineMatch scanRange(LineBuffer buffer, List<String> snippetLines, int from, int to, MatchStrategy strategy) {
        if (to < from) {
            return LineMatch.notFound();
        }
        for (int start = from; start <= to; start++) {
            if (segmentMatches(buffer, start, snippetLines)) {
                return LineMatch.of(start, start + snippetLines.size() - 1, strategy);
            }
        }
        return LineMatch.notFound();
    }

    private boolean segmentMatches(LineBuffer buffer, int startIndex, List<String> snippetLines) {
        if (snippetLines.isEmpty() || startIndex < 0 || startIndex + snippetLines.size() > buffer.lineCount()) {
            return false;
        }
        for (int offset = 0; offset < snippetLines.size(); offset++) {
            String expected = sanitize(snippetLines.get(offset));
            String actual = sanitize(buffer.getLine(startIndex + offset));
            if (!Objects.equals(expected, actual)) {
                return false;
            }
        }
        return true;
    }

    private LineMatch locateByMarkers(LineBuffer buffer, List<String> templateLines, int hintIndex) {
        if (CollectionUtils.isEmpty(templateLines)) {
            return LineMatch.notFound();
        }
        String startMarker = templateLines.get(0);
        String endMarker = templateLines.get(templateLines.size() - 1);
        if (!StringUtils.hasText(startMarker) || !StringUtils.hasText(endMarker)) {
            return LineMatch.notFound();
        }
        int window = 200;
        LineMatch local = scanMarkerWindow(buffer, startMarker, endMarker,
                Math.max(0, hintIndex - window), Math.min(buffer.lineCount() - 1, hintIndex + window));
        if (local.isFound()) {
            return local;
        }
        return scanMarkerWindow(buffer, startMarker, endMarker, 0, buffer.lineCount() - 1);
    }

    private LineMatch scanMarkerWindow(LineBuffer buffer, String startMarker, String endMarker, int from, int to) {
        if (buffer.lineCount() == 0 || to < from) {
            return LineMatch.notFound();
        }
        for (int index = from; index <= to; index++) {
            if (matchesMarker(buffer.getLine(index), startMarker)) {
                int end = findNextLine(buffer, endMarker, index);
                if (end >= index) {
                    return LineMatch.of(index, end, MatchStrategy.MARKER);
                }
            }
        }
        System.out.println("Marker not found between lines " + from + " and " + to + " for start: " + startMarker);
        return LineMatch.notFound();
    }

    private int findNextLine(LineBuffer buffer, String marker, int from) {
        for (int index = from; index < buffer.lineCount(); index++) {
            if (matchesMarker(buffer.getLine(index), marker)) {
                return index;
            }
        }
        return -1;
    }

    private int resolveInsertIndex(LineBuffer buffer, MigrationBlockResult blockResult) {
        Integer candidate = blockResult.getInsertBeforeLine();
        if (candidate == null || candidate <= 0) {
            candidate = blockResult.getStartLine();
        }
        if (candidate == null || candidate <= 0) {
            return buffer.lineCount();
        }
        int index = candidate - 1;
        if (index > buffer.lineCount()) {
            return buffer.lineCount();
        }
        return Math.max(0, index);
    }

    private String buildFallbackMessage(MigrationBlockResult blockResult) {
        return "Block#" + blockResult.getIndex() + " 定位失败，模板已在预期位置附近插入";
    }

    private String buildFuzzyMessage(MigrationBlockResult blockResult) {
        return "Block#" + blockResult.getIndex() + " 通过偏移匹配定位成功";
    }

    private int resolveEffectiveLine(MigrationBlockResult blockResult) {
        if (blockResult == null) {
            return Integer.MIN_VALUE;
        }
        if (blockResult.getEndLine() != null && blockResult.getEndLine() > 0) {
            return blockResult.getEndLine();
        }
        if (blockResult.getStartLine() != null && blockResult.getStartLine() > 0) {
            return blockResult.getStartLine();
        }
        if (blockResult.getInsertBeforeLine() != null && blockResult.getInsertBeforeLine() > 0) {
            return blockResult.getInsertBeforeLine();
        }
        return Integer.MIN_VALUE;
    }

    private String sanitize(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("\uFEFF", "");
    }

    private boolean matchesMarker(String content, String marker) {
        if (content == null || marker == null) {
            return false;
        }
        return sanitize(content).trim().equals(marker.trim());
    }

    private String normalizeTemplate(String template) {
        if (template == null) {
            return "";
        }
        String value = template.replace("\uFEFF", "");
        if (value.endsWith(System.lineSeparator())) {
            value = value.substring(0, value.length() - System.lineSeparator().length());
        }
        return value;
    }

    private List<String> splitTemplateLines(String template) {
        if (!StringUtils.hasText(template)) {
            return Collections.emptyList();
        }
        return splitLinesPreserveEmpty(template);
    }

    private List<String> splitSnippetLines(String snippet) {
        if (snippet == null || snippet.isEmpty()) {
            return Collections.emptyList();
        }
        return splitLinesPreserveEmpty(snippet);
    }

    private List<String> splitLinesPreserveEmpty(String value) {
        List<String> lines = new ArrayList<>();
        if (value == null) {
            return lines;
        }
        String normalized = value.replace("\r\n", "\n").replace("\r", "\n");
        String[] parts = normalized.split("\n", -1);
        Collections.addAll(lines, parts);
        return lines;
    }

    private static final class ReplacementOutcome {
        private final String content;
        private final List<String> warnings;
        private final boolean changed;

        private ReplacementOutcome(String content, List<String> warnings, boolean changed) {
            this.content = content;
            this.warnings = warnings;
            this.changed = changed;
        }

        private String getContent() {
            return content;
        }

        private List<String> getWarnings() {
            return warnings;
        }

        private boolean isChanged() {
            return changed;
        }
    }

    private static final class ApplyFeedback {
        private final boolean changed;
        private final List<String> warnings;

        private ApplyFeedback(boolean changed, List<String> warnings) {
            this.changed = changed;
            this.warnings = warnings;
        }

        private boolean isChanged() {
            return changed;
        }

        private List<String> getWarnings() {
            return warnings;
        }
    }

    private static final class LineMatch {
        private static final LineMatch NOT_FOUND = new LineMatch(false, -1, -1, null);
        private final boolean found;
        private final int startIndex;
        private final int endIndex;
        private final MatchStrategy strategy;

        private LineMatch(boolean found, int startIndex, int endIndex, MatchStrategy strategy) {
            this.found = found;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.strategy = strategy;
        }

        private static LineMatch of(int startIndex, int endIndex, MatchStrategy strategy) {
            return new LineMatch(true, startIndex, endIndex, strategy);
        }

        private static LineMatch notFound() {
            return NOT_FOUND;
        }

        private boolean isFound() {
            return found;
        }

        private int getStartIndex() {
            return startIndex;
        }

        private int getEndIndex() {
            return endIndex;
        }

        private MatchStrategy getStrategy() {
            return strategy;
        }
    }

    private enum MatchStrategy {
        DIRECT,
        FUZZY,
        MARKER
    }

    private static final class BlockPair {
        private final DiffBlock oracleBlock;
        private final DiffBlock gaussBlock;

        private BlockPair(DiffBlock oracleBlock, DiffBlock gaussBlock) {
            this.oracleBlock = oracleBlock;
            this.gaussBlock = gaussBlock;
        }

        private DiffBlock getOracleBlock() {
            return oracleBlock;
        }

        private DiffBlock getGaussBlock() {
            return gaussBlock;
        }
    }

    /**
     * 行缓冲结构，基于行号进行插入 / 替换，方便倒序操作。
     */
    private static final class LineBuffer {
        private final List<String> lines;
        private final String lineSeparator;

        private LineBuffer(String content) {
            this.lineSeparator = detectSeparator(content);
            this.lines = new ArrayList<>(splitLinesStatic(content));
        }

        private String detectSeparator(String content) {
            if (content != null) {
                if (content.contains("\r\n")) {
                    return "\r\n";
                }
                if (content.contains("\n")) {
                    return "\n";
                }
            }
            return System.lineSeparator();
        }

        private static List<String> splitLinesStatic(String value) {
            List<String> result = new ArrayList<>();
            if (value == null || value.isEmpty()) {
                return result;
            }
            String normalized = value.replace("\r\n", "\n").replace("\r", "\n");
            String[] parts = normalized.split("\n", -1);
            Collections.addAll(result, parts);
            return result;
        }

        private int lineCount() {
            return lines.size();
        }

        private String getLine(int index) {
            if (index < 0 || index >= lines.size()) {
                return null;
            }
            return lines.get(index);
        }

        private void replace(int startIndex, int endIndex, List<String> replacements) {
            if (lines.isEmpty()) {
                if (!CollectionUtils.isEmpty(replacements)) {
                    lines.addAll(replacements);
                }
                return;
            }
            int safeStart = Math.max(0, Math.min(startIndex, lines.size() - 1));
            int safeEnd = Math.max(safeStart, Math.min(endIndex, lines.size() - 1));
            for (int i = safeEnd; i >= safeStart; i--) {
                lines.remove(i);
            }
            if (!CollectionUtils.isEmpty(replacements)) {
                lines.addAll(safeStart, replacements);
            }
        }

        private void insertBefore(int index, List<String> additions) {
            if (CollectionUtils.isEmpty(additions)) {
                return;
            }
            int safeIndex = Math.max(0, Math.min(index, lines.size()));
            lines.addAll(safeIndex, additions);
        }

        private String asText() {
            if (lines.isEmpty()) {
                return "";
            }
            return String.join(lineSeparator, lines);
        }
    }
}






