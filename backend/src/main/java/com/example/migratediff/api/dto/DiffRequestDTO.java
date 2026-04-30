package com.example.migratediff.api.dto;

import lombok.Data;

@Data
public class DiffRequestDTO {

    private String repoPath;

    private String branchFrom;

    private String branchTo;

    private String deltaType;

    /**
     * 时间范围下界（含），ISO-8601 字符串，例如：2025-11-02T00:00:00+08:00。
     */
    private String timeFrom;

    /**
     * 时间范围上界（含），ISO-8601 字符串。
     */
    private String timeTo;

    /**
     * 针对时间检索的参考引用（未设置时默认使用分支名或 HEAD）。
     */
    private String refHint;

    /**
     * Whether to include working tree changes during incremental scan.
     */
    private Boolean includeWorkingTree;

    /**
     * Attempt to fetch missing branches or commits automatically.
     */
    private Boolean fetchIfMissing;

    /**
     * Remote name used when fetching, defaults to origin.
     */
    private String remoteName;

    /**
     * Scan strategy: BRANCH or SNAPSHOT.
     */
    private String scanStrategy;

    /**
     * Include refs/remotes when running snapshot scan.
     */
    private Boolean snapshotIncludeRemoteRefs;

    /**
     * Include refs/tags when running snapshot scan.
     */
    private Boolean snapshotIncludeTags;

    /**
     * Maximum refs inspected by snapshot scan (<=0 means unlimited, defaults to 256).
     */
    private Integer snapshotMaxRefs;
}
