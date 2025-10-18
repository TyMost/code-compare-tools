package com.example.codecompare.rebuild.agent.migration;

/**
 * Shared constants used across the code block migration workflow.
 */
public final class CodeBlockMigrationConstants {

    private CodeBlockMigrationConstants() {
    }

    public static final String LABEL_ANNOTATED = "migrated_with_annotation";
    public static final String LABEL_MIGRATED = "migrated";
    public static final String RISK_ANNOTATED = "Annotated";
    public static final String RISK_MIGRATED = "Migrated";
    public static final String STAGE_ANNOTATED = "annotated_copy";
    public static final String STAGE_APPLIED = "applied";
    public static final String STAGE_UNDO = "undo";

    public static final String METADATA_UNDO_STATUS = "undoBackupStatus";
    public static final String METADATA_UNDO_RISK = "undoBackupRiskLevel";
    public static final String METADATA_UNDO_TARGET = "undoBackupTargetCode";
    public static final String METADATA_UNDO_LABEL_IDS = "undoBackupLabelIds";
    public static final String METADATA_UNDO_LABELS = "undoBackupLabels";
    public static final String METADATA_UNDO_TIMESTAMP = "undoPerformedAt";
    public static final String METADATA_TEMPLATE_KEY = "annotationTemplateKey";
}

