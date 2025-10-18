package com.example.codecompare.rebuild.agent.migration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Groups migration candidates that share the same target file.
 */
public final class FileMigrationGroup {

    private final String targetProjectCode;
    private final String filePath;
    private final List<MigrationCandidate> candidates;
    private MigrationCandidate primaryCandidate;

    public FileMigrationGroup(String targetProjectCode, String filePath) {
        this.targetProjectCode = targetProjectCode;
        this.filePath = filePath;
        this.candidates = new ArrayList<MigrationCandidate>();
    }

    public void addCandidate(MigrationCandidate candidate) {
        if (candidate == null) {
            return;
        }
        if (primaryCandidate == null) {
            primaryCandidate = candidate;
        }
        candidates.add(candidate);
    }

    public void sortDescending() {
        Collections.sort(candidates, new Comparator<MigrationCandidate>() {
            @Override
            public int compare(MigrationCandidate left, MigrationCandidate right) {
                return Integer.compare(right.getInsertionLine(), left.getInsertionLine());
            }
        });
    }

    public String getTargetProjectCode() {
        return targetProjectCode;
    }

    public String getFilePath() {
        return filePath;
    }

    public List<MigrationCandidate> getCandidates() {
        return candidates;
    }

    public MigrationCandidate getPrimaryCandidate() {
        return primaryCandidate;
    }
}
