package com.example.codecompare.rebuild.agent.migration.snapshot;

import com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants;
import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.repository.model.BlockDecisionRecord;
import com.example.codecompare.rebuild.repository.model.BlockDecisionSnapshot;
import com.example.codecompare.rebuild.scanning.BlockLabelConstants;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.LABEL_ANNOTATED;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.METADATA_TEMPLATE_KEY;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.METADATA_UNDO_LABELS;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.METADATA_UNDO_LABEL_IDS;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.METADATA_UNDO_RISK;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.METADATA_UNDO_STATUS;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.METADATA_UNDO_TARGET;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.METADATA_UNDO_TIMESTAMP;
import static com.example.codecompare.rebuild.agent.migration.CodeBlockMigrationConstants.RISK_ANNOTATED;

/**
 * Handles BlockDecisionSnapshot mutations and related metadata adjustments.
 */
@Component
public class BlockDecisionMutationService {

    private final Clock clock;

    public BlockDecisionMutationService(Clock clock) {
        this.clock = clock;
    }

    public BlockDecisionSnapshot updateSnapshot(BlockDecisionSnapshot snapshot,
                                                String blockId,
                                                Function<BlockDecisionRecord, BlockDecisionRecord> transformer) {
        if (snapshot == null || !StringUtils.hasText(blockId) || transformer == null) {
            throw new IllegalArgumentException("Snapshot, blockId and transformer are required");
        }
        List<BlockDecisionRecord> updatedRecords = new ArrayList<BlockDecisionRecord>(snapshot.getRecords().size());
        boolean changed = false;
        for (BlockDecisionRecord record : snapshot.getRecords()) {
            if (Objects.equals(record.getId(), blockId)) {
                BlockDecisionRecord updated = transformer.apply(record);
                updatedRecords.add(updated);
                changed = true;
            } else {
                updatedRecords.add(record);
            }
        }
        if (!changed) {
            throw new IllegalStateException("Block record not found in snapshot");
        }
        Instant now = Instant.now(clock);
        return BlockDecisionSnapshot.builder()
                .id(snapshot.getId())
                .comparisonId(snapshot.getComparisonId())
                .filePath(snapshot.getFilePath())
                .sourceProjectCode(snapshot.getSourceProjectCode())
                .targetProjectCode(snapshot.getTargetProjectCode())
                .diffMode(snapshot.getDiffMode())
                .records(updatedRecords)
                .analyzedAt(now)
                .build();
    }

    public BlockDecisionRecord buildUpdatedRecord(BlockDecisionRecord original,
                                                  String annotatedCode,
                                                  String status,
                                                  String riskLabel,
                                                  String annotationTemplate,
                                                  String annotationTemplateKey,
                                                  String stage) {
        Instant now = Instant.now(clock);
        Map<String, Object> metadata = new LinkedHashMap<String, Object>(original.getMetadata());
        metadata.put("migrationStage", stage);
        metadata.put("migrationUpdatedAt", now.toString());
        metadata.remove(METADATA_UNDO_TIMESTAMP);
        if (StringUtils.hasText(annotationTemplate)) {
            metadata.put("annotationTemplate", annotationTemplate);
        } else {
            metadata.remove("annotationTemplate");
        }
        if (StringUtils.hasText(annotationTemplateKey)) {
            metadata.put(METADATA_TEMPLATE_KEY, annotationTemplateKey);
        } else {
            metadata.remove(METADATA_TEMPLATE_KEY);
        }
        BlockDiff baseDiff = original.getDiff();
        if (!hasUndoBackup(metadata)) {
            backupUndoMetadata(metadata, original, baseDiff);
        }
        boolean cleanLabels = shouldCleanLabels(stage);
        List<String> labelIds = prepareLabelList(baseDiff == null ? null : baseDiff.getLabelIds(), cleanLabels);
        List<String> labels = prepareLabelList(baseDiff == null ? null : baseDiff.getLabels(), cleanLabels);
        appendLabel(labelIds, status);
        appendLabel(labels, riskLabel);
        BlockDiff.Builder diffBuilder = baseDiff == null ? BlockDiff.builder() : BlockDiff.from(baseDiff);
        BlockDiff updatedDiff = diffBuilder
                .labelIds(labelIds)
                .labels(labels)
                .targetContent(annotatedCode)
                .build();
        String fallbackStatus = StringUtils.hasText(status)
                ? status
                : (cleanLabels ? BlockLabelConstants.STATUS_NO_RULES : original.getStatus());
        String fallbackRisk = StringUtils.hasText(riskLabel)
                ? riskLabel
                : (cleanLabels ? null : original.getRiskLevel());
        String resolvedStatus = resolveStatusFromDiff(updatedDiff, fallbackStatus);
        if (!StringUtils.hasText(resolvedStatus)) {
            resolvedStatus = BlockLabelConstants.STATUS_NO_RULES;
        }
        String resolvedRisk = resolveLabelFromDiff(updatedDiff, fallbackRisk);
        if (!StringUtils.hasText(resolvedRisk)) {
            resolvedRisk = resolvedStatus;
        }
        return BlockDecisionRecord.builder()
                .id(original.getId())
                .comparisonId(original.getComparisonId())
                .filePath(original.getFilePath())
                .blockIdentifier(original.getBlockIdentifier())
                .status(resolvedStatus)
                .riskLevel(resolvedRisk)
                .action(original.getAction())
                .metadata(metadata)
                .diff(updatedDiff)
                .analyzedAt(now)
                .build();
    }

    public BlockDecisionRecord withDiff(BlockDecisionRecord original, BlockDiff diff) {
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

    public BlockDecisionRecord buildRevertedRecord(BlockDecisionRecord original) {
        Instant now = Instant.now(clock);
        Map<String, Object> metadata = new LinkedHashMap<String, Object>(original.getMetadata());
        String previousStatus = Objects.toString(metadata.getOrDefault(METADATA_UNDO_STATUS, "unmigrated"));
        String previousRisk = Objects.toString(metadata.getOrDefault(METADATA_UNDO_RISK, original.getRiskLevel()));
        String targetContent = Objects.toString(metadata.get(METADATA_UNDO_TARGET), null);
        BlockDiff baseDiff = original.getDiff();
        if (targetContent == null) {
            targetContent = baseDiff == null ? "" : baseDiff.getSourceContent();
        }
        List<String> labelIds = extractStringList(metadata.get(METADATA_UNDO_LABEL_IDS));
        List<String> labels = extractStringList(metadata.get(METADATA_UNDO_LABELS));
        if (labelIds == null) {
            labelIds = baseDiff == null || baseDiff.getLabelIds() == null
                    ? new ArrayList<String>()
                    : new ArrayList<String>(baseDiff.getLabelIds());
            labelIds = removeLabel(labelIds, LABEL_ANNOTATED);
        }
        if (labels == null) {
            labels = baseDiff == null || baseDiff.getLabels() == null
                    ? new ArrayList<String>()
                    : new ArrayList<String>(baseDiff.getLabels());
            labels = removeLabel(labels, LABEL_ANNOTATED);
            labels = removeLabel(labels, RISK_ANNOTATED);
        }
        if (labelIds.isEmpty() && StringUtils.hasText(previousStatus)) {
            labelIds.add(previousStatus);
        }
        if (labels.isEmpty() && StringUtils.hasText(previousRisk)) {
            labels.add(previousRisk);
        }
        BlockDiff.Builder diffBuilder = baseDiff == null ? BlockDiff.builder() : BlockDiff.from(baseDiff);
        BlockDiff updatedDiff = diffBuilder
                .labelIds(labelIds)
                .labels(labels)
                .targetContent(targetContent)
                .build();
        metadata.put("migrationStage", CodeBlockMigrationConstants.STAGE_UNDO);
        metadata.put("migrationUpdatedAt", now.toString());
        metadata.put(METADATA_UNDO_TIMESTAMP, now.toString());
        metadata.remove("annotationTemplate");
        metadata.remove(METADATA_TEMPLATE_KEY);
        metadata.remove(METADATA_UNDO_STATUS);
        metadata.remove(METADATA_UNDO_RISK);
        metadata.remove(METADATA_UNDO_TARGET);
        metadata.remove(METADATA_UNDO_LABEL_IDS);
        metadata.remove(METADATA_UNDO_LABELS);
        return BlockDecisionRecord.builder()
                .id(original.getId())
                .comparisonId(original.getComparisonId())
                .filePath(original.getFilePath())
                .blockIdentifier(original.getBlockIdentifier())
                .status(previousStatus)
                .riskLevel(previousRisk)
                .action(original.getAction())
                .metadata(metadata)
                .diff(updatedDiff)
                .analyzedAt(now)
                .build();
    }

    public boolean isUndoAvailable(BlockDecisionRecord record) {
        if (record == null) {
            return false;
        }
        Map<String, Object> metadata = record.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return false;
        }
        return metadata.containsKey(METADATA_UNDO_STATUS)
                || metadata.containsKey(METADATA_UNDO_TARGET)
                || metadata.containsKey(METADATA_UNDO_LABEL_IDS)
                || metadata.containsKey(METADATA_UNDO_LABELS);
    }

    public BlockDecisionRecord findBlockRecord(BlockDecisionSnapshot snapshot, String blockId) {
        if (snapshot == null || !StringUtils.hasText(blockId)) {
            return null;
        }
        List<BlockDecisionRecord> records = snapshot.getRecords();
        if (CollectionUtils.isEmpty(records)) {
            return null;
        }
        for (BlockDecisionRecord record : records) {
            if (record != null && Objects.equals(record.getId(), blockId)) {
                return record;
            }
        }
        return null;
    }

    private void backupUndoMetadata(Map<String, Object> metadata,
                                    BlockDecisionRecord original,
                                    BlockDiff baseDiff) {
        metadata.put(METADATA_UNDO_STATUS, original.getStatus());
        metadata.put(METADATA_UNDO_RISK, original.getRiskLevel());
        metadata.put(METADATA_UNDO_TARGET, baseDiff == null ? null : baseDiff.getTargetContent());
        metadata.put(METADATA_UNDO_LABEL_IDS,
                baseDiff == null || baseDiff.getLabelIds() == null ? Collections.emptyList() : new ArrayList<String>(baseDiff.getLabelIds()));
        metadata.put(METADATA_UNDO_LABELS,
                baseDiff == null || baseDiff.getLabels() == null ? Collections.emptyList() : new ArrayList<String>(baseDiff.getLabels()));
    }

    private boolean hasUndoBackup(Map<String, Object> metadata) {
        return metadata != null
                && (metadata.containsKey(METADATA_UNDO_STATUS)
                || metadata.containsKey(METADATA_UNDO_RISK)
                || metadata.containsKey(METADATA_UNDO_TARGET)
                || metadata.containsKey(METADATA_UNDO_LABEL_IDS)
                || metadata.containsKey(METADATA_UNDO_LABELS));
    }

    @SuppressWarnings("unchecked")
    private List<String> extractStringList(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            List<Object> source = (List<Object>) value;
            List<String> result = new ArrayList<String>(source.size());
            for (Object item : source) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        return null;
    }

    private List<String> removeLabel(List<String> source, String candidate) {
        if (source == null || source.isEmpty()) {
            return new ArrayList<String>();
        }
        List<String> result = new ArrayList<String>();
        for (String entry : source) {
            if (entry == null) {
                continue;
            }
            if (candidate != null && candidate.equalsIgnoreCase(entry)) {
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    private List<String> prepareLabelList(List<String> source, boolean clean) {
        List<String> result = new ArrayList<String>();
        if (!clean && !CollectionUtils.isEmpty(source)) {
            for (String item : source) {
                appendLabel(result, item);
            }
        }
        return result;
    }

    private void appendLabel(List<String> target, String candidate) {
        if (target == null || !StringUtils.hasText(candidate)) {
            return;
        }
        String normalized = candidate.trim();
        if (normalized.isEmpty()) {
            return;
        }
        for (String existing : target) {
            if (existing != null && normalized.equalsIgnoreCase(existing.trim())) {
                return;
            }
        }
        target.add(normalized);
    }

    private boolean shouldCleanLabels(String stage) {
        if (!StringUtils.hasText(stage)) {
            return false;
        }
        return CodeBlockMigrationConstants.STAGE_APPLIED.equalsIgnoreCase(stage);
    }

    private String resolveStatusFromDiff(BlockDiff diff, String fallback) {
        if (diff != null) {
            BlockDiff.LabelDescriptor primary = diff.getPrimaryLabel();
            if (primary != null && StringUtils.hasText(primary.getStatusKey())) {
                return primary.getStatusKey();
            }
            List<String> ids = diff.getLabelIds();
            if (!CollectionUtils.isEmpty(ids)) {
                return ids.get(0);
            }
        }
        return fallback;
    }

    private String resolveLabelFromDiff(BlockDiff diff, String fallback) {
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
        return fallback;
    }
}
