package com.example.migratediff.domain.repo;

/**
 * 增量扫描策略。
 * SNAPSHOT 将使用全仓库起止快照模式，BRANCH 走现有分支差异模式。
 */
public enum ScanStrategy {
    BRANCH,
    SNAPSHOT
}
