package com.example.migratediff.domain.repo;

/**
 * 提交定位模式：
 * <ul>
 *     <li>BRANCH：仅依赖分支名解析提交。</li>
 *     <li>TIME_RANGE：仅根据时间窗口检索提交。</li>
 *     <li>HYBRID：先按照分支解析，失败时退回时间窗口。</li>
 * </ul>
 */
public enum CommitLocatorMode {
    BRANCH,
    TIME_RANGE,
    HYBRID
}
