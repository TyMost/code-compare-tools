package com.example.codecompare.rebuild.scanning.compare;

import com.example.codecompare.rebuild.api.dto.IncrementalDiffBlockView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffFileDetailView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffGitDiffView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffGitHunkView;
import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.model.DiffMetrics;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Prepares incremental change metadata from stored diff details.
 */
@Component
public class IncrementalChangeExtractor {

    public IncrementalChange extract(IncrementalDiffFileDetailView detail) {
        if (detail == null) {
            return IncrementalChange.empty();
        }
        IncrementalDiffGitDiffView gitDiff = detail.getGitDiff();
        IncrementalGitStats stats = new IncrementalGitStats(
                gitDiff == null ? 0 : gitDiff.getSameLineCount(),
                gitDiff == null ? 0 : gitDiff.getChangedLineCount());

        List<IncrementalCodeBlock> blocks = toBlocks(detail.getBlocks());
        if (blocks.isEmpty()) {
            blocks = fallbackBlocks(gitDiff);
        }

        IncrementalChangeType changeType = resolveChangeType(gitDiff, blocks);
        return new IncrementalChange(changeType, blocks, stats);
    }

    private List<IncrementalCodeBlock> toBlocks(List<IncrementalDiffBlockView> views) {
        if (CollectionUtils.isEmpty(views)) {
            return Collections.emptyList();
        }
        List<IncrementalCodeBlock> blocks = new ArrayList<IncrementalCodeBlock>();
        for (IncrementalDiffBlockView view : views) {
            if (view == null) {
                continue;
            }
            BlockDiff diff = view.getDiff();
            if (diff == null) {
                continue;
            }
            IncrementalBlockType blockType = resolveBlockType(diff);
            List<String> lines = extractLines(diff, blockType);
            if (lines.isEmpty()) {
                continue;
            }
            int changedLines = resolveChangedLines(diff, lines.size());
            blocks.add(new IncrementalCodeBlock(blockType, lines, changedLines, diff));
        }
        return blocks;
    }

    private List<IncrementalCodeBlock> fallbackBlocks(IncrementalDiffGitDiffView gitDiff) {
        if (gitDiff == null || CollectionUtils.isEmpty(gitDiff.getHunks())) {
            return Collections.emptyList();
        }
        List<String> addedLines = new ArrayList<String>();
        List<String> removedLines = new ArrayList<String>();
        for (IncrementalDiffGitHunkView hunk : gitDiff.getHunks()) {
            if (hunk == null || CollectionUtils.isEmpty(hunk.getLines())) {
                continue;
            }
            for (String rawLine : hunk.getLines()) {
                if (!StringUtils.hasText(rawLine)) {
                    continue;
                }
                char marker = rawLine.charAt(0);
                if (marker == '+') {
                    String payload = rawLine.substring(1).trim();
                    if (StringUtils.hasText(payload)) {
                        addedLines.add(payload);
                    }
                } else if (marker == '-') {
                    String payload = rawLine.substring(1).trim();
                    if (StringUtils.hasText(payload)) {
                        removedLines.add(payload);
                    }
                }
            }
        }
        List<IncrementalCodeBlock> blocks = new ArrayList<IncrementalCodeBlock>();
        if (!addedLines.isEmpty()) {
            int changedLines = Math.max(gitDiff.getChangedLineCount(), addedLines.size());
            blocks.add(new IncrementalCodeBlock(IncrementalBlockType.ADD, addedLines, changedLines, null));
        }
        if (!removedLines.isEmpty()) {
            int changedLines = Math.max(gitDiff.getChangedLineCount(), removedLines.size());
            blocks.add(new IncrementalCodeBlock(IncrementalBlockType.DELETE, removedLines, changedLines, null));
        }
        if (blocks.isEmpty()) {
            return Collections.emptyList();
        }
        return blocks;
    }

    private int resolveChangedLines(BlockDiff diff, int fallback) {
        int changed = diff.getChangedLineCount();
        if (changed > 0) {
            return changed;
        }
        DiffMetrics metrics = diff.getDiffMetrics();
        if (metrics != null && metrics.getChangedLines() > 0) {
            return metrics.getChangedLines();
        }
        return Math.max(1, fallback);
    }

    private List<String> extractLines(BlockDiff diff, IncrementalBlockType blockType) {
        String content;
        if (blockType == IncrementalBlockType.DELETE) {
            content = diff.getSourceContent();
            if (!StringUtils.hasText(content) && !CollectionUtils.isEmpty(diff.getSourceLines())) {
                content = joinLines(diff.getSourceLines());
            }
        } else {
            content = diff.getTargetContent();
            if (!StringUtils.hasText(content) && !CollectionUtils.isEmpty(diff.getTargetLines())) {
                content = joinLines(diff.getTargetLines());
            }
        }
        if (!StringUtils.hasText(content)) {
            return Collections.emptyList();
        }
        return splitLines(content);
    }

    private String joinLines(List<String> lines) {
        if (CollectionUtils.isEmpty(lines)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(i));
        }
        return builder.toString();
    }

    private List<String> splitLines(String content) {
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        String[] raw = normalized.split("\n");
        List<String> lines = new ArrayList<String>(raw.length);
        for (String value : raw) {
            String trimmed = value == null ? "" : value.trim();
            if (StringUtils.hasText(trimmed)) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    private IncrementalBlockType resolveBlockType(BlockDiff diff) {
        if (diff == null) {
            return IncrementalBlockType.UNKNOWN;
        }
        String source = diff.getSourceContent();
        String target = diff.getTargetContent();
        boolean hasSource = StringUtils.hasText(source);
        boolean hasTarget = StringUtils.hasText(target);
        if (!hasSource && hasTarget) {
            return IncrementalBlockType.ADD;
        }
        if (hasSource && !hasTarget) {
            return IncrementalBlockType.DELETE;
        }
        if (hasSource && hasTarget) {
            return IncrementalBlockType.MODIFY;
        }
        return IncrementalBlockType.UNKNOWN;
    }

    private IncrementalChangeType resolveChangeType(IncrementalDiffGitDiffView gitDiff,
                                                    List<IncrementalCodeBlock> blocks) {
        if (gitDiff != null && StringUtils.hasText(gitDiff.getChangeType())) {
            IncrementalChangeType mapped = mapChangeType(gitDiff.getChangeType());
            if (mapped != IncrementalChangeType.UNKNOWN) {
                return mapped;
            }
        }
        if (!CollectionUtils.isEmpty(blocks)) {
            boolean allAdd = true;
            boolean allDelete = true;
            for (IncrementalCodeBlock block : blocks) {
                IncrementalBlockType type = block.getBlockType();
                if (type != IncrementalBlockType.ADD) {
                    allAdd = false;
                }
                if (type != IncrementalBlockType.DELETE) {
                    allDelete = false;
                }
            }
            if (allAdd) {
                return IncrementalChangeType.ADD;
            }
            if (allDelete) {
                return IncrementalChangeType.DELETE;
            }
            return IncrementalChangeType.MODIFY;
        }
        if (gitDiff != null) {
            return mapChangeType(gitDiff.getChangeType());
        }
        return IncrementalChangeType.NONE;
    }

    private IncrementalChangeType mapChangeType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return IncrementalChangeType.UNKNOWN;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("ADD".equals(normalized) || "ADDED".equals(normalized) || "NEW".equals(normalized)) {
            return IncrementalChangeType.ADD;
        }
        if ("MODIFY".equals(normalized) || "MODIFIED".equals(normalized)) {
            return IncrementalChangeType.MODIFY;
        }
        if ("DELETE".equals(normalized) || "DELETED".equals(normalized) || "REMOVE".equals(normalized)) {
            return IncrementalChangeType.DELETE;
        }
        if ("RENAME".equals(normalized) || "RENAMED".equals(normalized)) {
            return IncrementalChangeType.RENAME;
        }
        if ("NONE".equals(normalized)) {
            return IncrementalChangeType.NONE;
        }
        return IncrementalChangeType.UNKNOWN;
    }
}
