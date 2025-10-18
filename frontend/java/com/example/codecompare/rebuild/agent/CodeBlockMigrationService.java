package com.example.codecompare.rebuild.agent;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.LABEL_ANNOTATED;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.LABEL_MIGRATED;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.METADATA_TEMPLATE_KEY;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.RISK_ANNOTATED;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.RISK_MIGRATED;
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

    public CodeBlockMigrationService(BlockStatsService blockStatsService,
                                     BlockDecisionRepository blockDecisionRepository,
                                     AnnotationRenderingService annotationRenderingService,
                                     BlockDecisionMutationService blockDecisionMutationService,
                                     AnnotatedFileWriter annotatedFileWriter,
                                     DiffSynchronizationService diffSynchronizationService) {
        this.blockStatsService = blockStatsService;
        this.blockDecisionRepository = blockDecisionRepository;
        this.annotationRenderingService = annotationRenderingService;
        this.blockDecisionMutationService = blockDecisionMutationService;
        this.annotatedFileWriter = annotatedFileWriter;
        this.diffSynchronizationService = diffSynchronizationService;
        this.migrationGroupingService = new MigrationGroupingService();
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
                    log.info("代码块缺少源代码，跳过生成注释blockId={}", blockId);
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
                    log.debug("代码块已存在注解拷贝，跳过重复生成blockId={}", blockId);
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
                log.info("完成注解拷贝 blockId={} file={}", blockId, detail.getFilePath());
            } catch (Exception ex) {
                log.warn("注解拷贝失败 blockId={}", blockId, ex);
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
                Optional<BlockDecisionSnapshot> snapshotOptional = loadSnapshot(detail);
                if (!snapshotOptional.isPresent()) {
                    failures.add(blockId + ": 缺少差异快照数据");
                    continue;
                }
                BlockDecisionSnapshot snapshot = snapshotOptional.get();
                BlockDecisionRecord record = blockDecisionMutationService.findBlockRecord(snapshot, blockId);
                if (record == null) {
                    failures.add(blockId + ": 未找到对应的代码块记录");
                    continue;
                }
                if (!blockDecisionMutationService.isUndoAvailable(record)) {
                    log.info("代码块尚未生成注解，跳过撤销操作 blockId={}", blockId);
                    skipped++;
                    continue;
                }
                BlockDecisionSnapshot updated = blockDecisionMutationService.updateSnapshot(snapshot, blockId,
                        new Function<BlockDecisionRecord, BlockDecisionRecord>() {
                            @Override
                            public BlockDecisionRecord apply(BlockDecisionRecord original) {
                                return blockDecisionMutationService.buildRevertedRecord(original);
                            }
                        });
                blockDecisionRepository.save(updated);
                succeeded++;
            } catch (Exception ex) {
                log.warn("撤销代码块时发生异常 blockId={}", blockId, ex);
                failures.add(blockId + ": " + ex.getMessage());
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
                    failures.add(blockId + ": 请先执行生成注解步骤");
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
                String annotated = annotationRenderingService.normalizeLineEndings(detail.getNewCode());
                candidates.add(new MigrationCandidate(blockId, detail, snapshot, existingRecord, annotated, existingRecord.getDiff()));
            } catch (Exception ex) {
                log.warn("应用迁移代码失败 blockId={}", blockId, ex);
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
                BlockDecisionSnapshot workingSnapshot = primary.getSnapshot();
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
                        BlockDiff diffForWrite = BlockDiff.from(baseDiff)
                                .targetStartLine(referenceStartLine > 0 ? referenceStartLine : baseDiff.getTargetStartLine())
                                .build();
                        InsertionResult insertionResult = annotatedFileWriter.writeAnnotatedToFile(
                                candidate.getDetail(), candidate.getAnnotatedCode(), diffForWrite, referenceStartLine);
                        BlockDiff appliedDiff = BlockDiff.from(diffForWrite)
                                .targetStartLine(insertionResult.getStartLine() > 0 ? insertionResult.getStartLine() : referenceStartLine)
                                .build();
                        BlockDecisionSnapshot updated = blockDecisionMutationService.updateSnapshot(workingSnapshot, candidate.getBlockId(),
                                new Function<BlockDecisionRecord, BlockDecisionRecord>() {
                                    @Override
                                    public BlockDecisionRecord apply(BlockDecisionRecord blockRecord) {
                                        return blockDecisionMutationService.buildUpdatedRecord(
                                                blockDecisionMutationService.withDiff(blockRecord, appliedDiff),
                                                candidate.getAnnotatedCode(),
                                                LABEL_MIGRATED,
                                                RISK_MIGRATED,
                                                null,
                                                null,
                                                STAGE_APPLIED);
                                    }
                                });
                        blockDecisionRepository.save(updated);
                        workingSnapshot = updated;
                        succeeded++;
                        logInsertionResult(candidate, insertionResult);
                    } catch (Exception inner) {
                        log.warn("应用迁移代码失败 blockId={}", candidate.getBlockId(), inner);
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
        if (diff != null && diff.getTargetStartLine() > 0) {
            return diff.getTargetStartLine();
        }
        if (detail != null && detail.getStartLine() > 0) {
            return detail.getStartLine();
        }
        if (detail != null && detail.getEndLine() > 0) {
            return detail.getEndLine();
        }
        return -1;
    }

    private void logInsertionResult(MigrationCandidate candidate, InsertionResult result) {
        if (candidate == null || result == null) {
            return;
        }
        String filePath = candidate.getDetail() != null ? candidate.getDetail().getFilePath() : null;
        if (result.isSkipped()) {
            log.info("目标文件已存在迁移片段，跳过写入 blockId={} file={} startLine={} preview={}",
                    candidate.getBlockId(), filePath, result.getStartLine(), result.preview());
        } else {
            log.info("已将迁移代码写入目标文件 blockId={} file={} startLine={} inserted={} replaced={} preview={}",
                    candidate.getBlockId(), filePath, result.getStartLine(), result.getInsertedLines(),
                    result.getReplacedLines(), result.preview());
        }
    }

    private int resolveCandidateLine(MigrationCandidate candidate) {
        if (candidate == null) {
            return Integer.MAX_VALUE;
        }
        BlockDiff diff = candidate.getDiff();
        if (diff != null && diff.getTargetStartLine() > 0) {
            return diff.getTargetStartLine();
        }
        CodeBlockDetailDTO detail = candidate.getDetail();
        if (detail != null && detail.getStartLine() > 0) {
            return detail.getStartLine();
        }
        if (detail != null && detail.getEndLine() > 0) {
            return detail.getEndLine();
        }
        return Integer.MAX_VALUE;
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
