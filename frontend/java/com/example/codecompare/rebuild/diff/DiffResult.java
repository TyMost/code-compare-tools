package com.example.codecompare.rebuild.diff;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.model.DiffSummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 行级差异分析结果，包含片段列表与汇总指标。
 */
public final class DiffResult {

    private static final DiffResult EMPTY = new DiffResult(Collections.emptyList(), DiffSummary.builder().build());

    private final List<BlockDiff> segments;
    private final DiffSummary summary;

    public DiffResult(List<BlockDiff> segments, DiffSummary summary) {
        this.segments = segments == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(segments));
        this.summary = summary == null ? DiffSummary.builder().build() : summary;
    }

    public static DiffResult empty() {
        return EMPTY;
    }

    public List<BlockDiff> getSegments() {
        return segments;
    }

    /**
     * @deprecated 为兼容旧逻辑，建议改用 {@link #getSegments()}。
     */
    @Deprecated
    public List<BlockDiff> getBlocks() {
        return segments;
    }

    public DiffSummary getSummary() {
        return summary;
    }
}
