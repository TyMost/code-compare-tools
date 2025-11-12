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
public class DiffFile {

    private String relativePath;
    @Builder.Default
    private List<DiffBlock> blocks = new ArrayList<>();
    private DiffType diffType;
    private DeltaType deltaType;
}
