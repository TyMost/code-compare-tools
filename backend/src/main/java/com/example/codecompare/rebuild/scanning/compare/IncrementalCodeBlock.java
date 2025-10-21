package com.example.codecompare.rebuild.scanning.compare;

import com.example.codecompare.rebuild.block.model.BlockDiff;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Representation of a single incremental diff block prepared for comparison.
 */
public final class IncrementalCodeBlock {

    private final IncrementalBlockType blockType;
    private final List<String> lines;
    private final int changedLines;
    private final BlockDiff diff;

    public IncrementalCodeBlock(IncrementalBlockType blockType,
                                List<String> lines,
                                int changedLines,
                                BlockDiff diff) {
        this.blockType = blockType == null ? IncrementalBlockType.UNKNOWN : blockType;
        if (lines == null) {
            this.lines = Collections.emptyList();
        } else {
            this.lines = Collections.unmodifiableList(new ArrayList<String>(lines));
        }
        this.changedLines = Math.max(1, changedLines);
        this.diff = diff;
    }

    public IncrementalBlockType getBlockType() {
        return blockType;
    }

    public List<String> getLines() {
        return lines;
    }

    public int getChangedLines() {
        return changedLines;
    }

    public BlockDiff getDiff() {
        return diff;
    }
}
