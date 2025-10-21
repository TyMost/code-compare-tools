package com.example.codecompare.rebuild.scanning.compare;

/**
 * Lightweight holder for Git diff statistics needed during incremental comparison.
 */
public final class IncrementalGitStats {

    private final int sameLineCount;
    private final int changedLineCount;

    public IncrementalGitStats(int sameLineCount, int changedLineCount) {
        this.sameLineCount = Math.max(0, sameLineCount);
        this.changedLineCount = Math.max(0, changedLineCount);
    }

    public int getSameLineCount() {
        return sameLineCount;
    }

    public int getChangedLineCount() {
        return changedLineCount;
    }

    public boolean isEmpty() {
        return sameLineCount == 0 && changedLineCount == 0;
    }
}
