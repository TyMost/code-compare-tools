package com.example.codecompare.rebuild.agent.migration.diff;

import com.example.codecompare.rebuild.agent.migration.LineEndingNormalizer;
import com.example.codecompare.rebuild.agent.migration.io.ProjectFileResolver;
import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.block.model.CodeSnapshot;
import com.example.codecompare.rebuild.diff.DiffRequest;
import com.example.codecompare.rebuild.diff.DiffResult;
import com.example.codecompare.rebuild.diff.DiffService;
import com.example.codecompare.rebuild.repository.model.BlockDecisionRecord;
import com.example.codecompare.rebuild.repository.model.BlockDecisionSnapshot;
import com.example.codecompare.rebuild.scanning.BlockDiffLabeler;
import com.example.codecompare.rebuild.stats.CodeBlockDetailDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Synchronises diff data after annotated content has been applied.
 */
@Component
public class DiffSynchronizationService {

    private final DiffService diffService;
    private final BlockDiffLabeler blockDiffLabeler;
    private final ProjectFileResolver projectFileResolver;
    private final Clock clock;

    public DiffSynchronizationService(DiffService diffService,
                                      BlockDiffLabeler blockDiffLabeler,
                                      ProjectFileResolver projectFileResolver,
                                      Clock clock) {
        this.diffService = diffService;
        this.blockDiffLabeler = blockDiffLabeler;
        this.projectFileResolver = projectFileResolver;
        this.clock = clock;
    }

    public BlockDecisionSnapshot refreshSiblingDiffs(CodeBlockDetailDTO detail,
                                                     BlockDecisionSnapshot snapshot) {
        try {
            Map<String, BlockDiff> recalculated = recalculateDiffsForFile(snapshot);
            return applyDiffReplacements(snapshot, recalculated);
        } catch (IOException ex) {
            throw new IllegalStateException("刷新文件 diff 失败 file=" + detail.getFilePath(), ex);
        }
    }

    private Map<String, BlockDiff> recalculateDiffsForFile(BlockDecisionSnapshot snapshot) throws IOException {
        if (snapshot == null) {
            return Collections.emptyMap();
        }
        String sourceContent = projectFileResolver.readProjectFile(snapshot.getSourceProjectCode(), snapshot.getFilePath());
        String targetContent = projectFileResolver.readProjectFile(snapshot.getTargetProjectCode(), snapshot.getFilePath());
        if (!StringUtils.hasText(sourceContent) || !StringUtils.hasText(targetContent)) {
            return Collections.emptyMap();
        }
        DiffRequest request = DiffRequest.builder()
                .source(CodeSnapshot.of(null, snapshot.getFilePath(), LineEndingNormalizer.normalize(sourceContent)))
                .target(CodeSnapshot.of(null, snapshot.getFilePath(), LineEndingNormalizer.normalize(targetContent)))
                .build();
        DiffResult diffResult = diffService.analyze(request);
        List<BlockDiff> candidates = normalizeSegments(diffResult == null ? null : diffResult.getSegments());
        if (CollectionUtils.isEmpty(candidates)) {
            return Collections.emptyMap();
        }
        Map<String, BlockDiff> result = new LinkedHashMap<String, BlockDiff>();
        for (BlockDecisionRecord record : snapshot.getRecords()) {
            BlockDiff original = record.getDiff();
            if (original == null) {
                continue;
            }
            BlockDiff matched = findMatchingSegment(original, candidates);
            if (matched != null) {
                result.put(record.getId(), mergeDiff(original, matched));
            }
        }
        return result;
    }

    private BlockDecisionSnapshot applyDiffReplacements(BlockDecisionSnapshot snapshot,
                                                        Map<String, BlockDiff> replacements) {
        if (snapshot == null || replacements == null || replacements.isEmpty()) {
            return snapshot;
        }
        List<BlockDecisionRecord> updatedRecords = new ArrayList<BlockDecisionRecord>(snapshot.getRecords().size());
        boolean changed = false;
        for (BlockDecisionRecord record : snapshot.getRecords()) {
            BlockDiff updatedDiff = replacements.get(record.getId());
            if (updatedDiff != null) {
                updatedRecords.add(withDiff(record, updatedDiff));
                changed = true;
            } else {
                updatedRecords.add(record);
            }
        }
        if (!changed) {
            return snapshot;
        }
        Instant now = Instant.now(clock);
        return BlockDecisionSnapshot.builder()
                .id(snapshot.getId())
                .comparisonId(snapshot.getComparisonId())
                .filePath(snapshot.getFilePath())
                .sourceProjectCode(snapshot.getSourceProjectCode())
                .targetProjectCode(snapshot.getTargetProjectCode())
                .records(updatedRecords)
                .analyzedAt(now)
                .build();
    }

    private List<BlockDiff> normalizeSegments(List<BlockDiff> segments) {
        if (CollectionUtils.isEmpty(segments)) {
            return Collections.emptyList();
        }
        if (blockDiffLabeler == null) {
            return segments;
        }
        List<BlockDiff> normalized = new ArrayList<BlockDiff>(segments.size());
        for (BlockDiff segment : segments) {
            BlockDiff labeled = blockDiffLabeler.label(segment);
            if (labeled != null && !labeled.isFilteredOut()) {
                normalized.add(labeled);
            }
        }
        return normalized;
    }

    private BlockDiff mergeDiff(BlockDiff original, BlockDiff latest) {
        return BlockDiff.from(original)
                .type(latest.getType())
                .sourceStartLine(latest.getSourceStartLine())
                .targetStartLine(latest.getTargetStartLine())
                .changedLineCount(latest.getChangedLineCount())
                .replacements(latest.getReplacements())
                .similarityScore(latest.getSimilarityScore())
                .sourceLines(latest.getSourceLines())
                .targetLines(latest.getTargetLines())
                .sourceContent(latest.getSourceContent())
                .targetContent(latest.getTargetContent())
                .diffMetrics(latest.getDiffMetrics())
                .build();
    }

    private BlockDiff findMatchingSegment(BlockDiff original, List<BlockDiff> candidates) {
        if (CollectionUtils.isEmpty(candidates)) {
            return null;
        }
        for (BlockDiff candidate : candidates) {
            if (segmentsMatch(original, candidate)) {
                return candidate;
            }
        }
        if (!CollectionUtils.isEmpty(original.getSourceLines())) {
            for (BlockDiff candidate : candidates) {
                if (sameLines(original.getSourceLines(), candidate.getSourceLines())) {
                    return candidate;
                }
            }
        }
        if (!CollectionUtils.isEmpty(original.getTargetLines())) {
            for (BlockDiff candidate : candidates) {
                if (sameLines(original.getTargetLines(), candidate.getTargetLines())) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private boolean segmentsMatch(BlockDiff original, BlockDiff candidate) {
        return original.getType() == candidate.getType()
                && sameLines(original.getSourceLines(), candidate.getSourceLines())
                && sameLines(original.getTargetLines(), candidate.getTargetLines());
    }

    private boolean sameLines(List<String> left, List<String> right) {
        if (CollectionUtils.isEmpty(left) && CollectionUtils.isEmpty(right)) {
            return true;
        }
        if (CollectionUtils.isEmpty(left) || CollectionUtils.isEmpty(right)) {
            return false;
        }
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!Objects.toString(left.get(i), "").equals(Objects.toString(right.get(i), ""))) {
                return false;
            }
        }
        return true;
    }

    private BlockDecisionRecord withDiff(BlockDecisionRecord original, BlockDiff diff) {
        return BlockDecisionRecord.builder()
                .id(original.getId())
                .comparisonId(original.getComparisonId())
                .filePath(original.getFilePath())
                .blockIdentifier(original.getBlockIdentifier())
                .status(original.getStatus())
                .riskLevel(original.getRiskLevel())
                .action(original.getAction())
                .metadata(original.getMetadata())
                .diff(diff)
                .analyzedAt(original.getAnalyzedAt())
                .build();
    }
}
