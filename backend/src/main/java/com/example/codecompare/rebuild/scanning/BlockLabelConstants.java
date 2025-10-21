package com.example.codecompare.rebuild.scanning;

/**
 * Shared label constants used by diff labeling and metrics pipelines.
 */
public final class BlockLabelConstants {

    private BlockLabelConstants() {
    }

    /** Status used when no rule hit is available. */
    public static final String STATUS_NO_RULES = "no_rules";

    /** Normalized status key for migrated segments (100% similarity). */
    public static final String STATUS_MIGRATED = "migrated";

    /** Display label for migrated segments. */
    public static final String LABEL_MIGRATED = "\u5df2\u8fc1\u79fb";

    /** Default color used for migrated labels (aligned with similarity-high rule). */
    public static final String COLOR_MIGRATED = "#67C23A";
}
