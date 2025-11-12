package com.example.migratediff.domain.diff;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffBlock {

    private int startLineFrom;
    private int endLineFrom;
    private int startLineTo;
    private int endLineTo;
    private String contentFrom;
    private String contentTo;
    private DiffType type;
}
