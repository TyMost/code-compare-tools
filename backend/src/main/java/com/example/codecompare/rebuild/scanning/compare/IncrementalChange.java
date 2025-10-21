package com.example.codecompare.rebuild.scanning.compare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Aggregated representation of all incremental changes for a single file.
 */
public final class IncrementalChange {

    private static final IncrementalChange EMPTY = new IncrementalChange(
            IncrementalChangeType.NONE,
            Collections.<IncrementalCodeBlock>emptyList(),
            new IncrementalGitStats(0, 0));

    private final IncrementalChangeType changeType;
    private final List<IncrementalCodeBlock> blocks;
    private final IncrementalGitStats gitStats;

    public IncrementalChange(IncrementalChangeType changeType,
                              List<IncrementalCodeBlock> blocks,
                              IncrementalGitStats gitStats) {
        this.changeType = changeType == null ? IncrementalChangeType.UNKNOWN : changeType;
        if (blocks == null || blocks.isEmpty()) {
            this.blocks = Collections.emptyList();
        } else {
            this.blocks = Collections.unmodifiableList(new ArrayList<IncrementalCodeBlock>(blocks));
        }
        this.gitStats = gitStats == null ? new IncrementalGitStats(0, 0) : gitStats;
    }

    public static IncrementalChange empty() {
        return EMPTY;
    }

    public IncrementalChangeType getChangeType() {
        return changeType;
    }

    public List<IncrementalCodeBlock> getBlocks() {
        return blocks;
    }

    public IncrementalGitStats getGitStats() {
        return gitStats;
    }

    public boolean isEmpty() {
        return blocks.isEmpty() && gitStats.isEmpty();
    }
}
