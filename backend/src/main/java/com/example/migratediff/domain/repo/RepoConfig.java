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

    /**
     * Stable identifier used when persisting or referencing the repository configuration.
     */
    private String id;

    /**
     * Optional friendly name shown on the UI.
     */
    private String name;

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
    @Builder.Default
    private ScanStrategy scanStrategy = ScanStrategy.BRANCH;
    @Builder.Default
    private boolean snapshotIncludeRemoteRefs = false;
    @Builder.Default
    private boolean snapshotIncludeTags = false;
    @Builder.Default
    private int snapshotMaxRefs = 256;
}
