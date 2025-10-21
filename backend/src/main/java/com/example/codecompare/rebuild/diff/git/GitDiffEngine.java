package com.example.codecompare.rebuild.diff.git;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.DiffResult;
import com.example.codecompare.rebuild.diff.model.DiffSummary;
import com.example.codecompare.rebuild.diff.support.DiffSummaryCalculator;
import com.example.codecompare.rebuild.scanning.git.GitDiffFile;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Git-based diff engine that transforms Git hunk payloads into {@link DiffResult}.
 */
public class GitDiffEngine {

    private final GitDiffResultAssembler assembler;

    public GitDiffEngine() {
        this(new GitDiffResultAssembler());
    }

    public GitDiffEngine(GitDiffResultAssembler assembler) {
        this.assembler = assembler == null ? new GitDiffResultAssembler() : assembler;
    }

    public DiffResult analyze(GitDiffFile diffFile) {
        return assembler.assemble(diffFile);
    }

    public DiffResult analyze(List<GitDiffFile> diffFiles) {
        if (CollectionUtils.isEmpty(diffFiles)) {
            return DiffResult.empty();
        }
        List<BlockDiff> aggregated = new ArrayList<>();
        for (GitDiffFile file : diffFiles) {
            DiffResult perFile = assembler.assemble(file);
            if (perFile == null || CollectionUtils.isEmpty(perFile.getSegments())) {
                continue;
            }
            aggregated.addAll(perFile.getSegments());
        }
        if (aggregated.isEmpty()) {
            return DiffResult.empty();
        }
        DiffSummary summary = DiffSummaryCalculator.fromSegments(aggregated);
        return new DiffResult(aggregated, summary);
    }

    public List<BlockDiff> toBlocks(GitDiffFile diffFile) {
        DiffResult result = analyze(diffFile);
        if (result == null || CollectionUtils.isEmpty(result.getSegments())) {
            return Collections.emptyList();
        }
        return result.getSegments();
    }
}
