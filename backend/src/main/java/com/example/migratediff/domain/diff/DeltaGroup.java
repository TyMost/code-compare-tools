package com.example.migratediff.domain.diff;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeltaGroup {

    private DiffFile deltaO;
    private DiffFile deltaG;
    @Builder.Default
    private List<DiffBlock> intersectBlocks = new ArrayList<>();
    @Builder.Default
    private List<DiffBlock> conflictBlocks = new ArrayList<>();
    @Builder.Default
    private List<DiffBlock> uniqueBlocks = new ArrayList<>();
}
