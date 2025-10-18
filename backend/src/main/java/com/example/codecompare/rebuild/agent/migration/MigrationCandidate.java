package com.example.codecompare.rebuild.agent.migration;

import com.example.codecompare.rebuild.repository.model.BlockDecisionRecord;
import com.example.codecompare.rebuild.repository.model.BlockDecisionSnapshot;
import com.example.codecompare.rebuild.stats.CodeBlockDetailDTO;
import com.example.codecompare.rebuild.block.model.BlockDiff;

/**
 * Represents a code block that is ready to be migrated into the target file.
 */
public final class MigrationCandidate {

    private final String blockId;
    private final CodeBlockDetailDTO detail;
    private final BlockDecisionSnapshot snapshot;
    private final BlockDecisionRecord record;
    private final String annotatedCode;
    private final BlockDiff diff;

    public MigrationCandidate(String blockId,
                              CodeBlockDetailDTO detail,
                              BlockDecisionSnapshot snapshot,
                              BlockDecisionRecord record,
                              String annotatedCode,
                              BlockDiff diff) {
        this.blockId = blockId;
        this.detail = detail;
        this.snapshot = snapshot;
        this.record = record;
        this.annotatedCode = annotatedCode;
        this.diff = diff;
    }

    public String getBlockId() {
        return blockId;
    }

    public CodeBlockDetailDTO getDetail() {
        return detail;
    }

    public BlockDecisionSnapshot getSnapshot() {
        return snapshot;
    }

    public BlockDecisionRecord getRecord() {
        return record;
    }

    public String getAnnotatedCode() {
        return annotatedCode;
    }

    public BlockDiff getDiff() {
        return diff;
    }

    public String getTargetProjectCode() {
        return detail != null ? detail.getTargetProjectCode() : null;
    }

    public String getFilePath() {
        return detail != null ? detail.getFilePath() : null;
    }

    public int getInsertionLine() {
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
}
