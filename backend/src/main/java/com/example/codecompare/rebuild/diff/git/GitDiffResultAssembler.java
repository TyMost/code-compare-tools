package com.example.codecompare.rebuild.diff.git;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.DiffResult;
import com.example.codecompare.rebuild.diff.model.DiffSegmentType;
import com.example.codecompare.rebuild.diff.model.DiffSummary;
import com.example.codecompare.rebuild.diff.support.DiffSimilarityCalculator;
import com.example.codecompare.rebuild.diff.support.DiffSummaryCalculator;
import com.example.codecompare.rebuild.repository.model.FileChangeType;
import com.example.codecompare.rebuild.scanning.git.GitDiffFile;
import com.example.codecompare.rebuild.scanning.git.GitDiffHunk;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles Git diff hunks into {@link BlockDiff} segments without invoking difflib.
 */
public class GitDiffResultAssembler {

    public DiffResult assemble(GitDiffFile diffFile) {
        if (diffFile == null || CollectionUtils.isEmpty(diffFile.getHunks())) {
            return DiffResult.empty();
        }
        List<BlockDiff> segments = new ArrayList<>();
        List<GitDiffHunk> hunks = diffFile.getHunks();
        for (int index = 0; index < hunks.size(); index++) {
            GitDiffHunk hunk = hunks.get(index);
            BlockDiff segment = toBlockDiff(diffFile, hunk, index + 1);
            if (segment != null) {
                segments.add(segment);
            }
        }
        if (segments.isEmpty()) {
            return DiffResult.empty();
        }
        DiffSummary summary = DiffSummaryCalculator.fromSegments(segments);
        return new DiffResult(segments, summary);
    }

    private BlockDiff toBlockDiff(GitDiffFile diffFile, GitDiffHunk hunk, int sequence) {
        if (hunk == null) {
            return null;
        }
        List<String> removed = new ArrayList<>();
        List<String> added = new ArrayList<>();
        int oldCursor = Math.max(1, hunk.getOldRange().getStartLine());
        int newCursor = Math.max(1, hunk.getNewRange().getStartLine());
        Integer sourceStart = null;
        Integer targetStart = null;
        boolean noNewlineMarker = false;

        for (String rawLine : hunk.getLines()) {
            if (rawLine == null || rawLine.isEmpty()) {
                continue;
            }
            char marker = rawLine.charAt(0);
            String content = rawLine.length() > 1 ? rawLine.substring(1) : "";
            switch (marker) {
                case ' ':
                    oldCursor++;
                    newCursor++;
                    break;
                case '-':
                    if (sourceStart == null) {
                        sourceStart = oldCursor;
                    }
                    removed.add(content);
                    oldCursor++;
                    break;
                case '+':
                    if (targetStart == null) {
                        targetStart = newCursor;
                    }
                    added.add(content);
                    newCursor++;
                    break;
                case '\\':
                    // "\ No newline at end of file" marker
                    noNewlineMarker = true;
                    break;
                default:
                    // treat as context when marker is unexpected
                    oldCursor++;
                    newCursor++;
                    break;
            }
        }

        if (removed.isEmpty() && added.isEmpty()) {
            if (noNewlineMarker) {
                Map<String, Object> metadata = baseMetadata(diffFile, hunk, sequence);
                metadata.put("gitNoNewlineAtEndOfFile", true);
                return BlockDiff.builder()
                        .type(DiffSegmentType.CHANGE)
                        .sourceStartLine(Math.max(1, hunk.getOldRange().getStartLine()))
                        .targetStartLine(Math.max(1, hunk.getNewRange().getStartLine()))
                        .changedLineCount(0)
                        .replacements(0)
                        .similarityScore(100d)
                        .metadata(metadata)
                        .build();
            }
            return null;
        }

        DiffSegmentType type = resolveSegmentType(diffFile.getChangeType(), removed, added);
        int sourceStartLine = resolveStartLine(sourceStart, hunk.getOldRange().getStartLine());
        int targetStartLine = resolveStartLine(targetStart, hunk.getNewRange().getStartLine());
        int changed = Math.max(removed.size(), added.size());
        int replacements = DiffSimilarityCalculator.computeReplacements(removed, added);
        double similarity = DiffSimilarityCalculator.computeSimilarity(removed, added);
        Map<String, Object> metadata = baseMetadata(diffFile, hunk, sequence);
        if (noNewlineMarker) {
            metadata.put("gitNoNewlineAtEndOfFile", true);
        }

        return BlockDiff.builder()
                .type(type)
                .sourceStartLine(sourceStartLine)
                .targetStartLine(targetStartLine)
                .changedLineCount(changed)
                .replacements(replacements)
                .similarityScore(similarity)
                .sourceLines(removed)
                .targetLines(added)
                .metadata(metadata)
                .build();
    }

    private DiffSegmentType resolveSegmentType(FileChangeType changeType,
                                               List<String> removed,
                                               List<String> added) {
        if (removed == null || removed.isEmpty()) {
            if (added == null || added.isEmpty()) {
                return DiffSegmentType.CHANGE;
            }
            return DiffSegmentType.INSERT;
        }
        if (added == null || added.isEmpty()) {
            return DiffSegmentType.DELETE;
        }
        if (changeType == FileChangeType.DELETED) {
            return DiffSegmentType.DELETE;
        }
        if (changeType == FileChangeType.NEW) {
            return DiffSegmentType.INSERT;
        }
        return DiffSegmentType.CHANGE;
    }

    private int resolveStartLine(Integer calculated, int fallback) {
        if (calculated != null && calculated > 0) {
            return calculated;
        }
        return Math.max(1, fallback);
    }

    private Map<String, Object> baseMetadata(GitDiffFile diffFile, GitDiffHunk hunk, int sequence) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("gitPath", diffFile.getPath());
        metadata.put("gitChangeType", diffFile.getChangeType() == null
                ? FileChangeType.MODIFIED.name()
                : diffFile.getChangeType().name());
        metadata.put("gitHunkIndex", sequence);
        metadata.put("gitHunkByteSize", hunk.getByteSize());
        metadata.put("gitOldStartLine", hunk.getOldRange().getStartLine());
        metadata.put("gitOldLineCount", hunk.getOldRange().getLineCount());
        metadata.put("gitNewStartLine", hunk.getNewRange().getStartLine());
        metadata.put("gitNewLineCount", hunk.getNewRange().getLineCount());
        metadata.put("gitDiffTruncated", diffFile.isTruncated());
        metadata.put("gitDiffTotalBytes", diffFile.getTotalBytes());
        if (StringUtils.hasText(diffFile.getPreviousPath())) {
            metadata.put("gitPreviousPath", diffFile.getPreviousPath());
        }
        if (diffFile.getChangeType() == FileChangeType.RENAMED && StringUtils.hasText(diffFile.getPreviousPath())) {
            metadata.put("gitRenamedFrom", diffFile.getPreviousPath());
        }
        return metadata;
    }
}
