package com.example.codecompare.rebuild.scanning;

/**
 * Shared label constants used by diff labeling and metrics pipelines.
 */
public final class BlockLabelConstants {

    private BlockLabelConstants() {
    }

    /** Status used when a block仍需人工确认。 */
    public static final String STATUS_REVIEW = "review";
    /** Status used when源侧存在而目标缺失。 */
    public static final String STATUS_UNMIGRATED = "unmigrated";
    /** Status used when源目标已完成迁移。 */
    public static final String STATUS_MIGRATED = "migrated";
    /** Status used when目标新增了代码。 */
    public static final String STATUS_NEW_CODE = "adapt_migration";
    /** Status used when无其他规则命中。 */
    public static final String STATUS_OTHER = "other";
}

