package com.example.migratediff.domain.repo;

import com.example.migratediff.domain.diff.DeltaType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoConfig {

    private RepoPath repoPath;
    private RepoBranch branchFrom;
    private RepoBranch branchTo;
    private DeltaType deltaType;
    @Builder.Default
    private boolean fetchIfMissing = true;
    @Builder.Default
    private boolean includeWorkingTree = false;
    @Builder.Default
    private String remoteName = "origin";
    @Builder.Default
    private CommitLocatorMode locatorMode = CommitLocatorMode.BRANCH;
}
