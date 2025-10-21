package com.example.codecompare.rebuild.scanning.compare;

/**
 * High level classification for a file's incremental change derived from Git diff.
 */
public enum IncrementalChangeType {
    ADD,
    MODIFY,
    DELETE,
    RENAME,
    NONE,
    UNKNOWN
}
