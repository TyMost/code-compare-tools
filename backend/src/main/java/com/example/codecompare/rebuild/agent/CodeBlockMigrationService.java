package com.example.codecompare.rebuild.agent;

import com.example.codecompare.rebuild.agent.config.MigrationAnnotationProperties;
import com.example.codecompare.rebuild.agent.migration.LineEndingNormalizer;
import com.example.codecompare.rebuild.agent.migration.FileMigrationGroup;
import com.example.codecompare.rebuild.agent.migration.MigrationCandidate;
import com.example.codecompare.rebuild.agent.migration.MigrationGroupingService;
import com.example.codecompare.rebuild.agent.migration.annotation.AnnotationRenderingService;
import com.example.codecompare.rebuild.agent.migration.annotation.RenderedAnnotation;
import com.example.codecompare.rebuild.agent.migration.diff.DiffSynchronizationService;
import com.example.codecompare.rebuild.agent.migration.io.AnnotatedFileWriter;
import com.example.codecompare.rebuild.agent.migration.io.AnnotatedFileWriter.InsertionResult;
import com.example.codecompare.rebuild.agent.migration.snapshot.BlockDecisionMutationService;
import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.repository.BlockDecisionRepository;
import com.example.codecompare.rebuild.repository.model.BlockDecisionRecord;
import com.example.codecompare.rebuild.repository.model.BlockDecisionSnapshot;
import com.example.codecompare.rebuild.stats.BlockStatsService;
import com.example.codecompare.rebuild.stats.CodeBlockDetailDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.LABEL_ANNOTATED;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.METADATA_TEMPLATE_KEY;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.RISK_ANNOTATED;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.STAGE_ANNOTATED;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.STAGE_APPLIED;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.STAGE_UNDO;

/**
 * Implements the synchronous copy stage required by the two-step migration strategy.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>Generate annotated copies for unmigrated blocks (stage 1)</li>
 *     <li>Apply annotated code into target project files (stage 2)</li>
 *     <li>Persist updated block decisions so that dashboards reflect the latest status</li>
 * </ul>
 */
@Component
public class CodeBlockMigrationService {

    private static final Logger log = LoggerFactory.getLogger(CodeBlockMigrationService.class);

    private final BlockStatsService blockStatsService;
    private final BlockDecisionRepository blockDecisionRepository;
    private final AnnotationRenderingService annotationRenderingService;
    private final BlockDecisionMutationService blockDecisionMutationService;
    private final AnnotatedFileWriter annotatedFileWriter;
    private final DiffSynchronizationService diffSynchronizationService;
    private final MigrationGroupingService migrationGroupingService;
    private final ConcurrentHashMap<String, ReentrantLock> fileLocks = new ConcurrentHashMap<String, ReentrantLock>();
    private final boolean replacementEnabled;

    public CodeBlockMigrationService(BlockStatsService blockStatsService,
                                     BlockDecisionRepository blockDecisionRepository,
                                     AnnotationRenderingService annotationRenderingService,
                                     BlockDecisionMutationService blockDecisionMutationService,
                                     AnnotatedFileWriter annotatedFileWriter,
                                     DiffSynchronizationService diffSynchronizationService,
                                     MigrationAnnotationProperties migrationAnnotationProperties) {
        this.blockStatsService = blockStatsService;
        this.blockDecisionRepository = blockDecisionRepository;
        this.annotationRenderingService = annotationRenderingService;
        this.blockDecisionMutationService = blockDecisionMutationService;
        this.annotatedFileWriter = annotatedFileWriter;
        this.diffSynchronizationService = diffSynchronizationService;
        this.migrationGroupingService = new MigrationGroupingService();
        this.replacementEnabled = migrationAnnotationProperties == null
                || migrationAnnotationProperties.isEnableReplacementMode();
    }

    /**
     * Generates annotated copies for given block identifiers.
     */
    public MigrationOperationResult generateAnnotatedCopies(List<String> blockIds, String annotationTemplate) {
        if (CollectionUtils.isEmpty(blockIds)) {
            return new MigrationOperationResult(0, 0, 0, Collections.<String>emptyList());
        }
        int succeeded = 0;
        int skipped = 0;
        List<String> failures = new ArrayList<String>();
        for (String blockId : blockIds) {
            if (!StringUtils.hasText(blockId)) {
                skipped++;
                continue;
            }
            try {
                Optional<CodeBlockDetailDTO> detailOptional = loadDetail(blockId);
                if (!detailOptional.isPresent()) {
                    failures.add(blockId + ": 未找到对应的代码块详细信息");
                    continue;
                }
                CodeBlockDetailDTO detail = detailOptional.get();
                if (!StringUtils.hasText(detail.getOldCode())) {
                    skipped++;
                    log.info("浠ｇ爜鍧楃己灏戞簮浠ｇ爜锛岃烦杩囩敓鎴愭敞閲奲lockId={}", blockId);
                    continue;
                }
                Optional<BlockDecisionSnapshot> snapshotOptional = loadSnapshot(detail);
                if (!snapshotOptional.isPresent()) {
                    failures.add(blockId + ": 缺少差异快照数据");
                    continue;
                }
                BlockDecisionSnapshot snapshot = snapshotOptional.get();
                BlockDecisionRecord currentRecord = blockDecisionMutationService.findBlockRecord(snapshot, blockId);
                if (currentRecord == null || currentRecord.getDiff() == null) {
                    failures.add(blockId + ": 缺少差异片段信息");
                    continue;
                }
                RenderedAnnotation rendered = annotationRenderingService.render(detail, currentRecord.getDiff(), blockId, annotationTemplate);
                String annotated = rendered.getContent();
                if (annotated.equals(detail.getNewCode()) && LABEL_ANNOTATED.equalsIgnoreCase(detail.getStatus())) {
                    skipped++;
                    log.debug("浠ｇ爜鍧楀凡瀛樺湪娉ㄨВ鎷疯礉锛岃烦杩囬噸澶嶇敓鎴恇lockId={}", blockId);
                    continue;
                }
                BlockDecisionSnapshot updated = blockDecisionMutationService.updateSnapshot(snapshot, blockId,
                        new Function<BlockDecisionRecord, BlockDecisionRecord>() {
                            @Override
                            public BlockDecisionRecord apply(BlockDecisionRecord record) {
                                return blockDecisionMutationService.buildUpdatedRecord(
                                        record,
                                        annotated,
                                        LABEL_ANNOTATED,
                                        RISK_ANNOTATED,
                                        rendered.getTemplate(),
                                        rendered.getTemplateKey(),
                                        STAGE_ANNOTATED);
                            }
                        });
                blockDecisionRepository.save(updated);
                succeeded++;
                log.info("瀹屾垚娉ㄨВ鎷疯礉 blockId={} file={}", blockId, detail.getFilePath());
            } catch (Exception ex) {
                log.warn("娉ㄨВ鎷疯礉澶辫触 blockId={}", blockId, ex);
                failures.add(blockId + ": " + ex.getMessage());
            }
        }
        return new MigrationOperationResult(blockIds.size(), succeeded, skipped, failures);
    }

    public Optional<AnnotationMetadata> resolveAnnotationMetadata(String blockId) {
        if (!StringUtils.hasText(blockId)) {
            return Optional.empty();
        }
        Optional<CodeBlockDetailDTO> detailOptional = loadDetail(blockId);
        if (!detailOptional.isPresent()) {
            return Optional.empty();
        }
        CodeBlockDetailDTO detail = detailOptional.get();
        Optional<BlockDecisionSnapshot> snapshotOptional = loadSnapshot(detail);
        if (!snapshotOptional.isPresent()) {
            return Optional.empty();
        }
        BlockDecisionSnapshot snapshot = snapshotOptional.get();
        BlockDecisionRecord record = blockDecisionMutationService.findBlockRecord(snapshot, blockId);
        if (record == null || record.getMetadata() == null) {
            return Optional.empty();
        }
        Map<String, Object> metadata = record.getMetadata();
        String template = metadata.containsKey("annotationTemplate")
                ? Objects.toString(metadata.get("annotationTemplate"), null)
                : null;
        String templateKey = metadata.containsKey(METADATA_TEMPLATE_KEY)
                ? Objects.toString(metadata.get(METADATA_TEMPLATE_KEY), null)
                : null;
        if (!StringUtils.hasText(template) && !StringUtils.hasText(templateKey)) {
            return Optional.empty();
        }
        return Optional.of(new AnnotationMetadata(template, templateKey));
    }

    public MigrationOperationResult revertAnnotatedCopies(List<String> blockIds) {
        if (CollectionUtils.isEmpty(blockIds)) {
            return new MigrationOperationResult(0, 0, 0, Collections.<String>emptyList());
        }
        int skipped = 0;
        List<String> failures = new ArrayList<String>();
        List<UndoCandidate> candidates = new ArrayList<UndoCandidate>();
        for (String blockId : blockIds) {
            if (!StringUtils.hasText(blockId)) {
                skipped++;
                continue;
            }
            try {
                Optional<CodeBlockDetailDTO> detailOptional = loadDetail(blockId);
                if (!detailOptional.isPresent()) {
                    failures.add(blockId + ": 未找到对应的代码块详细信息");
                    continue;
                }
                CodeBlockDetailDTO detail = detailOptional.get();
                if (!StringUtils.hasText(detail.getTargetProjectCode()) || !StringUtils.hasText(detail.getFilePath())) {
                    failures.add(blockId + ": 缺少目标项目或文件路径信息");
                    continue;
                }
                Optional<BlockDecisionSnapshot> snapshotOptional = loadSnapshot(detail);
                if (!snapshotOptional.isPresent()) {
                    failures.add(blockId + ": 缺少差异快照数据");
                    continue;
                }
                BlockDecisionSnapshot snapshot = snapshotOptional.get();
                BlockDecisionRecord record = blockDecisionMutationService.findBlockRecord(snapshot, blockId);
                if (record == null || record.getDiff() == null) {
                    failures.add(blockId + ": 缺少差异片段信息");
                    continue;
                }
                if (!blockDecisionMutationService.isUndoAvailable(record)) {
                    log.info("代码块缺少撤销备份，跳过撤销操作 blockId={}", blockId);
                    skipped++;
                    continue;
                }
                int referenceLine = resolveReferenceLine(record.getDiff(), detail);
                candidates.add(new UndoCandidate(blockId, detail, snapshot, referenceLine));
            } catch (Exception ex) {
                log.warn("准备撤销代码块时发生异常 blockId={}", blockId, ex);
                failures.add(blockId + ": " + ex.getMessage());
            }
        }
        if (candidates.isEmpty()) {
            return new MigrationOperationResult(blockIds.size(), 0, skipped, failures);
        }
        int succeeded = 0;
        List<UndoFileGroup> groups = groupUndoCandidates(candidates);
        for (UndoFileGroup group : groups) {
            UndoCandidate primary = group.getPrimaryCandidate();
            if (primary == null) {
                continue;
            }
            String lockKey = buildFileLockKey(primary.getDetail());
            ReentrantLock fileLock = acquireFileLock(lockKey);
            fileLock.lock();
            try {
                BlockDecisionSnapshot workingSnapshot = diffSynchronizationService.refreshSnapshotForMigration(primary.getSnapshot());
                if (workingSnapshot == null) {
                    failures.add(primary.getBlockId() + ": 无法刷新差异数据");
                    continue;
                }
                List<UndoCandidate> ordered = new ArrayList<UndoCandidate>(group.getCandidates());
                ordered.sort(new java.util.Comparator<UndoCandidate>() {
                    @Override
                    public int compare(UndoCandidate left, UndoCandidate right) {
                        return Integer.compare(right.getReferenceLine(), left.getReferenceLine());
                    }
                });
                for (UndoCandidate candidate : ordered) {
                    try {
                        BlockDecisionRecord record = blockDecisionMutationService.findBlockRecord(workingSnapshot, candidate.getBlockId());
                        if (record == null || record.getDiff() == null) {
                            failures.add(candidate.getBlockId() + ": 缺少差异片段信息");
                            continue;
                        }
                        if (!blockDecisionMutationService.isUndoAvailable(record)) {
                            log.info("代码块缺少撤销备份，跳过撤销操作 blockId={}", candidate.getBlockId());
                            skipped++;
                            continue;
                        }
                        BlockDecisionRecord revertedRecord = blockDecisionMutationService.buildRevertedRecord(record);
                        InsertionResult revertResult = revertAnnotatedContent(
                                candidate.getBlockId(),
                                candidate.getDetail(),
                                record,
                                revertedRecord,
                                candidate.getReferenceLine());
                        if (revertResult.isSkipped()) {
                            failures.add(candidate.getBlockId() + ": 未定位到待撤销的迁移代码片段");
                            continue;
                        }
                        BlockDecisionSnapshot updated = blockDecisionMutationService.updateSnapshot(workingSnapshot, candidate.getBlockId(),
                                new Function<BlockDecisionRecord, BlockDecisionRecord>() {
                                    @Override
                                    public BlockDecisionRecord apply(BlockDecisionRecord original) {
                                        return blockDecisionMutationService.buildRevertedRecord(original);
                                    }
                                });
                        blockDecisionRepository.save(updated);
                        workingSnapshot = diffSynchronizationService.refreshSnapshotForMigration(updated);
                        if (workingSnapshot == null) {
                            workingSnapshot = updated;
                        }
                        succeeded++;
                        logRevertResult(candidate, revertResult);
                    } catch (Exception ex) {
                        log.warn("撤销代码块失败 blockId={}", candidate.getBlockId(), ex);
                        failures.add(candidate.getBlockId() + ": " + ex.getMessage());
                    }
                }
                if (workingSnapshot != null) {
                    BlockDecisionSnapshot refreshedSnapshot = diffSynchronizationService.refreshSiblingDiffs(primary.getDetail(), workingSnapshot);
                    if (refreshedSnapshot != workingSnapshot) {
                        blockDecisionRepository.save(refreshedSnapshot);
                    }
                }
            } finally {
                fileLock.unlock();
                releaseFileLock(lockKey, fileLock);
            }
        }
        return new MigrationOperationResult(blockIds.size(), succeeded, skipped, failures);
    }
    /**
     * Applies annotated copies into target project files.
     */
    public MigrationOperationResult applyAnnotatedCopies(List<String> blockIds) {
        if (CollectionUtils.isEmpty(blockIds)) {
            return new MigrationOperationResult(0, 0, 0, Collections.<String>emptyList());
        }
        int skipped = 0;
        List<String> failures = new ArrayList<String>();
        List<MigrationCandidate> candidates = new ArrayList<MigrationCandidate>();
        for (String blockId : blockIds) {
            if (!StringUtils.hasText(blockId)) {
                skipped++;
                continue;
            }
            try {
                Optional<CodeBlockDetailDTO> detailOptional = loadDetail(blockId);
                if (!detailOptional.isPresent()) {
                    failures.add(blockId + ": 未找到对应的代码块详细信息");
                    continue;
                }
                CodeBlockDetailDTO detail = detailOptional.get();
                if (!StringUtils.hasText(detail.getNewCode())) {
                    failures.add(blockId + ": 请先执行生成注解操作");
                    continue;
                }
                if (!StringUtils.hasText(detail.getTargetProjectCode()) || !StringUtils.hasText(detail.getFilePath())) {
                    failures.add(blockId + ": 缺少目标项目或文件路径信息");
                    continue;
                }
                Optional<BlockDecisionSnapshot> snapshotOptional = loadSnapshot(detail);
                if (!snapshotOptional.isPresent()) {
                    failures.add(blockId + ": 缺少差异快照数据");
                    continue;
                }
                BlockDecisionSnapshot snapshot = snapshotOptional.get();
                BlockDecisionRecord existingRecord = blockDecisionMutationService.findBlockRecord(snapshot, blockId);
                if (existingRecord == null || existingRecord.getDiff() == null) {
                    failures.add(blockId + ": 缺少差异片段信息");
                    continue;
                }
                BlockDiff diff = existingRecord.getDiff();
                boolean hasTargetBaseline = diff != null && StringUtils.hasText(diff.getTargetContent());
                String templateKeyToUse = hasTargetBaseline ? "migrate_adapt" : "default";
                RenderedAnnotation rendered = annotationRenderingService.renderWithTemplateKey(detail, diff, blockId, templateKeyToUse);
                String annotated = rendered != null
                        ? annotationRenderingService.normalizeLineEndings(rendered.getContent())
                        : annotationRenderingService.normalizeLineEndings(detail.getNewCode());
                List<String> expectedOriginal = Collections.<String>emptyList();
                if (hasTargetBaseline && diff != null && !CollectionUtils.isEmpty(diff.getTargetLines())) {
                    expectedOriginal = diff.getTargetLines();
                }
                String template = rendered != null ? rendered.getTemplate() : null;
                String templateKey = rendered != null ? rendered.getTemplateKey() : templateKeyToUse;
                candidates.add(new MigrationCandidate(blockId, detail, snapshot, existingRecord, annotated, diff, expectedOriginal, template, templateKey));
            } catch (Exception ex) {
                log.warn("搴旂敤杩佺Щ浠ｇ爜澶辫触 blockId={}", blockId, ex);
                failures.add(blockId + ": " + ex.getMessage());
            }
        }
        if (candidates.isEmpty()) {
            return new MigrationOperationResult(blockIds.size(), 0, skipped, failures);
        }
        int succeeded = 0;
        List<FileMigrationGroup> groups = migrationGroupingService.groupByTargetFile(candidates);
        for (FileMigrationGroup group : groups) {
            MigrationCandidate primary = group.getPrimaryCandidate();
            if (primary == null) {
                continue;
            }
            String lockKey = buildFileLockKey(group.getTargetProjectCode(), group.getFilePath());
            ReentrantLock fileLock = acquireFileLock(lockKey);
            fileLock.lock();
            try {
                BlockDecisionSnapshot workingSnapshot = diffSynchronizationService.refreshSnapshotForMigration(primary.getSnapshot());
                if (workingSnapshot == null) {
                    failures.add(primary.getBlockId() + ": 无法刷新差异数据");
                    continue;
                }
                List<MigrationCandidate> ordered = new ArrayList<MigrationCandidate>(group.getCandidates());
                ordered.sort(new java.util.Comparator<MigrationCandidate>() {
                    @Override
                    public int compare(MigrationCandidate left, MigrationCandidate right) {
                        return Integer.compare(resolveCandidateLine(right), resolveCandidateLine(left));
                    }
                });
                for (MigrationCandidate candidate : ordered) {
                    try {
                        BlockDecisionRecord record = blockDecisionMutationService.findBlockRecord(workingSnapshot, candidate.getBlockId());
                        if (record == null || record.getDiff() == null) {
                            failures.add(candidate.getBlockId() + ": 缺少差异片段信息");
                            continue;
                        }
                        BlockDiff baseDiff = record.getDiff();
                        int referenceStartLine = determineReferenceStartLine(baseDiff, candidate.getDetail());
                        if (log.isDebugEnabled()) {
                            log.debug("Resolved insertion baseline blockId={} file={} diffTargetLine={} detailStart={} referenceLine={}",
                                    candidate.getBlockId(),
                                    candidate.getDetail() == null ? null : candidate.getDetail().getFilePath(),
                                    baseDiff == null ? null : baseDiff.getTargetStartLine(),
                                    candidate.getDetail() == null ? null : candidate.getDetail().getStartLine(),
                                    referenceStartLine);
                        }
                        List<String> expectedOriginal = candidate.getExpectedOriginal();
                        if (CollectionUtils.isEmpty(expectedOriginal) && baseDiff != null && !CollectionUtils.isEmpty(baseDiff.getTargetLines())) {
                            expectedOriginal = baseDiff.getTargetLines();
                        }
                        BlockDiff diffForWrite = BlockDiff.from(baseDiff)
                                .targetStartLine(referenceStartLine > 0 ? referenceStartLine : baseDiff.getTargetStartLine())
                                .build();
                        InsertionResult insertionResult = applyAnnotatedContent(candidate, diffForWrite,
                                referenceStartLine, expectedOriginal);
                        List<String> appliedSnippet = insertionResult.getSnippet();
                        if (CollectionUtils.isEmpty(appliedSnippet)) {
                            String normalizedAnnotated = LineEndingNormalizer.normalize(candidate.getAnnotatedCode());
                            appliedSnippet = LineEndingNormalizer.splitLines(normalizedAnnotated);
                        }
                        String snippetContent = CollectionUtils.isEmpty(appliedSnippet)
                                ? LineEndingNormalizer.normalize(candidate.getAnnotatedCode())
                                : LineEndingNormalizer.joinLines(appliedSnippet);
                        BlockDiff.Builder appliedDiffBuilder = BlockDiff.from(diffForWrite)
                                .targetStartLine(insertionResult.getStartLine() > 0 ? insertionResult.getStartLine() : referenceStartLine)
                                .targetLines(appliedSnippet)
                                .targetContent(snippetContent);
                        if (!CollectionUtils.isEmpty(appliedSnippet)) {
                            int updatedChangedLines = Math.max(appliedSnippet.size(), diffForWrite.getChangedLineCount());
                            appliedDiffBuilder.changedLineCount(updatedChangedLines);
                        }
                        BlockDiff appliedDiff = appliedDiffBuilder.build();
                        BlockDecisionSnapshot updated = blockDecisionMutationService.updateSnapshot(workingSnapshot, candidate.getBlockId(),
                                new Function<BlockDecisionRecord, BlockDecisionRecord>() {
                                    @Override
                                    public BlockDecisionRecord apply(BlockDecisionRecord blockRecord) {
                                        String annotationTemplate = candidate.getTemplate();
                                        if (!StringUtils.hasText(annotationTemplate)
                                                && blockRecord != null
                                                && blockRecord.getMetadata() != null
                                                && blockRecord.getMetadata().get("annotationTemplate") instanceof String) {
                                            annotationTemplate = Objects.toString(blockRecord.getMetadata().get("annotationTemplate"), null);
                                        }
                                        return blockDecisionMutationService.buildUpdatedRecord(
                                                blockDecisionMutationService.withDiff(blockRecord, appliedDiff),
                                                candidate.getAnnotatedCode(),
                                                null,
                                                null,
                                                annotationTemplate,
                                                candidate.getTemplateKey(),
                                                STAGE_APPLIED);
                                    }
                                });
                        blockDecisionRepository.save(updated);
                        workingSnapshot = diffSynchronizationService.refreshSnapshotForMigration(updated);
                        if (workingSnapshot == null) {
                            workingSnapshot = updated;
                        }
                        succeeded++;
                        logInsertionResult(candidate, insertionResult);
                    } catch (Exception inner) {
                        log.warn("搴旂敤杩佺Щ浠ｇ爜澶辫触 blockId={}", candidate.getBlockId(), inner);
                        failures.add(candidate.getBlockId() + ": " + inner.getMessage());
                    }
                }
                if (workingSnapshot != null) {
                    BlockDecisionSnapshot refreshedSnapshot = diffSynchronizationService.refreshSiblingDiffs(primary.getDetail(), workingSnapshot);
                    if (refreshedSnapshot != workingSnapshot) {
                        blockDecisionRepository.save(refreshedSnapshot);
                    }
                }
            } finally {
                fileLock.unlock();
                releaseFileLock(lockKey, fileLock);
            }
        }
        return new MigrationOperationResult(blockIds.size(), succeeded, skipped, failures);
    }

    private Optional<CodeBlockDetailDTO> loadDetail(String blockId) {
        if (!StringUtils.hasText(blockId)) {
            return Optional.empty();
        }
        CodeBlockDetailDTO detail = blockStatsService.findDetail(null, blockId);
        if (detail != null) {
            return Optional.of(detail);
        }
        return Optional.empty();
    }

    private Optional<BlockDecisionSnapshot> loadSnapshot(CodeBlockDetailDTO detail) {
        String comparisonId = resolveComparisonId(detail);
        if (!StringUtils.hasText(comparisonId)) {
            return Optional.empty();
        }
        return blockDecisionRepository.findLatest(comparisonId, detail.getFilePath());
    }

    private String resolveComparisonId(CodeBlockDetailDTO detail) {
        if (detail == null) {
            return null;
        }
        if (StringUtils.hasText(detail.getComparisonId())) {
            return detail.getComparisonId();
        }
        if (StringUtils.hasText(detail.getSourceProjectCode())) {
            return detail.getSourceProjectCode();
        }
        if (StringUtils.hasText(detail.getTargetProjectCode())) {
            return detail.getTargetProjectCode();
        }
        return null;
    }

    private String buildFileLockKey(CodeBlockDetailDTO detail) {
        if (detail == null) {
            return buildFileLockKey(null, null, null);
        }
        return buildFileLockKey(detail.getTargetProjectCode(), detail.getFilePath(), detail.getId());
    }

    private String buildFileLockKey(String targetProject, String filePath) {
        return buildFileLockKey(targetProject, filePath, null);
    }

    private String buildFileLockKey(String targetProject, String filePath, String fallbackId) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(targetProject)) {
            builder.append(targetProject.trim());
        }
        builder.append("::");
        if (StringUtils.hasText(filePath)) {
            builder.append(filePath.trim());
        } else if (StringUtils.hasText(fallbackId)) {
            builder.append(fallbackId.trim());
        } else {
            builder.append("unknown");
        }
        return builder.toString();
    }

    private ReentrantLock acquireFileLock(String lockKey) {
        return fileLocks.computeIfAbsent(lockKey, key -> new ReentrantLock());
    }

    private void releaseFileLock(String lockKey, ReentrantLock lock) {
        if (!lock.hasQueuedThreads()) {
            fileLocks.remove(lockKey, lock);
        }
    }

    private int determineReferenceStartLine(BlockDiff diff, CodeBlockDetailDTO detail) {
        int candidate = diff != null ? diff.getTargetStartLine() : -1;
        if (detail != null) {
            int detailStart = detail.getStartLine();
            if (detailStart > 0 && (candidate <= 0 || detailStart < candidate)) {
                candidate = detailStart;
            }
            int detailEnd = detail.getEndLine();
            if (detailEnd > 0 && (candidate <= 0 || detailEnd < candidate)) {
                candidate = detailEnd;
            }
        }
        return candidate > 0 ? candidate : -1;
    }

    private void logInsertionResult(MigrationCandidate candidate, InsertionResult result) {
        if (candidate == null || result == null) {
            return;
        }
        String filePath = candidate.getDetail() != null ? candidate.getDetail().getFilePath() : null;
        String templateKey = candidate.getTemplateKey();
        if (result.isSkipped()) {
            log.info("Skip migration write blockId={} file={} template={} startLine={} preview={}",
                    candidate.getBlockId(), filePath, templateKey, result.getStartLine(), result.preview());
        } else {
            log.info("Migration write applied blockId={} file={} template={} startLine={} inserted={} replaced={} preview={}",
                    candidate.getBlockId(), filePath, templateKey, result.getStartLine(), result.getInsertedLines(),
                    result.getReplacedLines(), result.preview());
        }
    }

    private InsertionResult applyAnnotatedContent(MigrationCandidate candidate,
                                                  BlockDiff diffForWrite,
                                                  int referenceStartLine,
                                                  List<String> expectedOriginal) throws IOException {
        boolean useReplacement = replacementEnabled && hasNonBlankLine(expectedOriginal);
        InsertionResult insertionResult;
        if (useReplacement) {
            insertionResult = annotatedFileWriter.writeAnnotatedWithReplacement(
                    candidate.getDetail(),
                    candidate.getAnnotatedCode(),
                    diffForWrite,
                    referenceStartLine,
                    expectedOriginal);
            if (insertionResult.isSkipped()) {
                log.debug("替换模式未命中原始片段，回退为插入模式 blockId={} file={}",
                        candidate.getBlockId(),
                        candidate.getDetail() == null ? null : candidate.getDetail().getFilePath());
                insertionResult = annotatedFileWriter.writeAnnotatedToFile(
                        candidate.getDetail(),
                        candidate.getAnnotatedCode(),
                        diffForWrite,
                        referenceStartLine);
            }
        } else {
            insertionResult = annotatedFileWriter.writeAnnotatedToFile(
                    candidate.getDetail(),
                    candidate.getAnnotatedCode(),
                    diffForWrite,
                    referenceStartLine);
        }
        return insertionResult;

    }

    private InsertionResult revertAnnotatedContent(String blockId,
                                                   CodeBlockDetailDTO detail,
                                                   BlockDecisionRecord currentRecord,
                                                   BlockDecisionRecord revertedRecord,
                                                   int referenceLineHint) throws IOException {
        BlockDiff currentDiff = currentRecord == null ? null : currentRecord.getDiff();
        BlockDiff revertDiff = revertedRecord == null ? null : revertedRecord.getDiff();
        if (currentDiff == null) {
            return InsertionResult.skipped(referenceLineHint > 0 ? referenceLineHint : 1, java.util.Collections.<String>emptyList());
        }
        int referenceStartLine = determineReferenceStartLine(currentDiff, detail);
        if (referenceStartLine <= 0) {
            referenceStartLine = referenceLineHint;
        }
        int overrideStartLine = referenceStartLine > 0 ? referenceStartLine : -1;
        BlockDiff diffForWrite = BlockDiff.from(currentDiff)
                .targetStartLine(overrideStartLine > 0 ? overrideStartLine : currentDiff.getTargetStartLine())
                .build();
        String restoredContent = revertDiff == null ? "" : revertDiff.getTargetContent();
        String normalizedRestore = LineEndingNormalizer.normalize(restoredContent);
        List<String> expectedAnnotated = LineEndingNormalizer.splitLines(
                LineEndingNormalizer.normalize(currentDiff.getTargetContent()));
        InsertionResult primary;
        if (CollectionUtils.isEmpty(expectedAnnotated)) {
            primary = annotatedFileWriter.writeAnnotatedToFile(detail, normalizedRestore, diffForWrite, overrideStartLine);
        } else {
            primary = annotatedFileWriter.writeAnnotatedWithReplacement(
                    detail,
                    normalizedRestore,
                    diffForWrite,
                    overrideStartLine,
                    expectedAnnotated);
        }
        if (!primary.isSkipped()) {
            return primary;
        }
        String templateKey = resolveTemplateKey(currentRecord);
        InsertionResult markerResult = annotatedFileWriter.removeAnnotatedSegment(
                detail,
                blockId,
                templateKey,
                normalizedRestore,
                overrideStartLine > 0 ? overrideStartLine : referenceLineHint);
        if (!markerResult.isSkipped()) {
            return markerResult;
        }
        return primary;
    }

    private void logRevertResult(UndoCandidate candidate, InsertionResult result) {
        if (candidate == null || result == null) {
            return;
        }
        String filePath = candidate.getDetail() != null ? candidate.getDetail().getFilePath() : null;
        if (result.isSkipped()) {
            log.info("Skipped undo write blockId={} file={} startLine={} preview={}",
                    candidate.getBlockId(), filePath, result.getStartLine(), result.preview());
        } else {
            log.info("Undo write applied blockId={} file={} startLine={} inserted={} replaced={}",
                    candidate.getBlockId(), filePath, result.getStartLine(), result.getInsertedLines(), result.getReplacedLines());
        }
    }

    private List<UndoFileGroup> groupUndoCandidates(List<UndoCandidate> candidates) {
        Map<String, UndoFileGroup> groups = new LinkedHashMap<String, UndoFileGroup>();
        if (CollectionUtils.isEmpty(candidates)) {
            return new ArrayList<UndoFileGroup>();
        }
        for (UndoCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            CodeBlockDetailDTO detail = candidate.getDetail();
            if (detail == null || !StringUtils.hasText(detail.getTargetProjectCode()) || !StringUtils.hasText(detail.getFilePath())) {
                continue;
            }
            String key = detail.getTargetProjectCode() + "::" + detail.getFilePath();
            UndoFileGroup group = groups.get(key);
            if (group == null) {
                group = new UndoFileGroup(detail.getTargetProjectCode(), detail.getFilePath());
                groups.put(key, group);
            }
            group.addCandidate(candidate);
        }
        return new ArrayList<UndoFileGroup>(groups.values());
    }

    private boolean hasNonBlankLine(List<String> lines) {
        if (CollectionUtils.isEmpty(lines)) {
            return false;
        }
        for (String line : lines) {
            if (StringUtils.hasText(line)) {
                return true;
            }
        }
        return false;
    }

    private int resolveCandidateLine(MigrationCandidate candidate) {
        if (candidate == null) {
            return Integer.MAX_VALUE;
        }
        return resolveReferenceLine(candidate.getDiff(), candidate.getDetail());
    }

    private int resolveReferenceLine(BlockDiff diff, CodeBlockDetailDTO detail) {
        if (diff != null && diff.getTargetStartLine() > 0) {
            return diff.getTargetStartLine();
        }
        if (detail != null && detail.getStartLine() > 0) {
            return detail.getStartLine();
        }
        if (detail != null && detail.getEndLine() > 0) {
            return detail.getEndLine();
        }
        return Integer.MAX_VALUE;
    }

    private String resolveTemplateKey(BlockDecisionRecord record) {
        if (record == null) {
            return null;
        }
        Map<String, Object> metadata = record.getMetadata();
        if (metadata != null && metadata.containsKey(METADATA_TEMPLATE_KEY)) {
            String templateKey = Objects.toString(metadata.get(METADATA_TEMPLATE_KEY), null);
            if (StringUtils.hasText(templateKey)) {
                return templateKey.trim();
            }
        }
        BlockDiff diff = record.getDiff();
        if (diff != null && StringUtils.hasText(diff.getTargetContent())) {
            String targetContent = diff.getTargetContent();
            if (targetContent.contains("迁移适配段结束")) {
                return "migrate_adapt";
            }
            if (targetContent.contains("迁移生成的代码片段结束")) {
                return "default";
            }
        }
        return null;
    }

    private static final class UndoCandidate {
        private final String blockId;
        private final CodeBlockDetailDTO detail;
        private final BlockDecisionSnapshot snapshot;
        private final int referenceLine;

        private UndoCandidate(String blockId,
                              CodeBlockDetailDTO detail,
                              BlockDecisionSnapshot snapshot,
                              int referenceLine) {
            this.blockId = blockId;
            this.detail = detail;
            this.snapshot = snapshot;
            this.referenceLine = referenceLine > 0 ? referenceLine : Integer.MIN_VALUE;
        }

        private String getBlockId() {
            return blockId;
        }

        private CodeBlockDetailDTO getDetail() {
            return detail;
        }

        private BlockDecisionSnapshot getSnapshot() {
            return snapshot;
        }

        private int getReferenceLine() {
            return referenceLine;
        }
    }

    private static final class UndoFileGroup {
        private final String targetProjectCode;
        private final String filePath;
        private final List<UndoCandidate> candidates;
        private UndoCandidate primaryCandidate;

        private UndoFileGroup(String targetProjectCode, String filePath) {
            this.targetProjectCode = targetProjectCode;
            this.filePath = filePath;
            this.candidates = new ArrayList<UndoCandidate>();
        }

        private void addCandidate(UndoCandidate candidate) {
            if (candidate == null) {
                return;
            }
            if (primaryCandidate == null) {
                primaryCandidate = candidate;
            }
            candidates.add(candidate);
        }

        private UndoCandidate getPrimaryCandidate() {
            return primaryCandidate;
        }

        private List<UndoCandidate> getCandidates() {
            return candidates;
        }

        private String getTargetProjectCode() {
            return targetProjectCode;
        }

        private String getFilePath() {
            return filePath;
        }
    }

    public static final class AnnotationMetadata {
        private final String template;
        private final String templateKey;

        private AnnotationMetadata(String template, String templateKey) {
            this.template = template;
            this.templateKey = templateKey;
        }

        public String getTemplate() {
            return template;
        }

        public String getTemplateKey() {
            return templateKey;
        }
    }

    public static final class MigrationOperationResult {
        private final int requested;
        private final int succeeded;
        private final int skipped;
        private final List<String> failures;

        public MigrationOperationResult(int requested, int succeeded, int skipped, List<String> failures) {
            this.requested = requested;
            this.succeeded = succeeded;
            this.skipped = skipped;
            this.failures = failures == null ? Collections.<String>emptyList() : failures;
        }

        public int requested() {
            return requested;
        }

        public int succeeded() {
            return succeeded;
        }

        public int skipped() {
            return skipped;
        }

        public List<String> failures() {
            return failures;
        }

        public boolean hasFailures() {
            return !failures.isEmpty();
        }
    }
}
