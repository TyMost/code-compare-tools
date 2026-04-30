package com.example.migratediff.infrastructure.git;

import lombok.Builder;
import lombok.Data;

/**
 * Options for snapshot time-range scan.
 */
@Data
@Builder
public class SnapshotLocatorOptions {

    @Builder.Default
    private boolean includeRemoteRefs = false;

    @Builder.Default
    private int maxRefs = 256;

    @Builder.Default
    private boolean includeTags = false;
}
