package com.example.codecompare.rebuild.diff.support;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.model.DiffSegmentType;
import com.example.codecompare.rebuild.diff.model.DiffSummary;

import java.util.List;

/**
 * Shared summary aggregation helpers for diff segments.
 */
public final class DiffSummaryCalculator {

    private DiffSummaryCalculator() {
    }

    public static DiffSummary fromSegments(List<BlockDiff> segments) {
        DiffSummary.Builder builder = DiffSummary.builder();
        int insertCount = 0;
        int deleteCount = 0;
        int changeCount = 0;
        int totalChangedLines = 0;
        int maxContinuous = 0;
        if (segments != null) {
            for (BlockDiff segment : segments) {
                if (segment == null) {
                    continue;
                }
                int changed = segment.getChangedLineCount();
                totalChangedLines = safeAdd(totalChangedLines, changed);
                maxContinuous = Math.max(maxContinuous, changed);
                DiffSegmentType type = segment.getType();
                if (type == null) {
                    changeCount++;
                    continue;
                }
                switch (type) {
                    case INSERT:
                        insertCount++;
                        break;
                    case DELETE:
                        deleteCount++;
                        break;
                    default:
                        changeCount++;
                        break;
                }
            }
        }
        return builder
                .insertSegments(insertCount)
                .deleteSegments(deleteCount)
                .changeSegments(changeCount)
                .totalChangedLines(totalChangedLines)
                .maxContinuousChangedLines(maxContinuous)
                .build();
    }

    private static int safeAdd(int left, int right) {
        long sum = (long) left + (long) right;
        if (sum > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (sum < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) sum;
    }
}
