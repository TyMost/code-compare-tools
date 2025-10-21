package com.example.codecompare.rebuild.scanning.compare;

/**
 * Classification for a diff block within a file's incremental change.
 */
public enum IncrementalBlockType {
    ADD,
    MODIFY,
    DELETE,
    UNKNOWN
}
