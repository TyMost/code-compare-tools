package com.example.migratediff.application.scan;

import com.example.migratediff.domain.coverage.CoverageDetail;
import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class DiffDetail {

    private final String taskId;
    private final String filePath;
    private final DiffFile oracleFile;
    private final DiffFile gaussFile;
    private final CoverageDetail coverageDetail;
    private final List<DiffBlock> referenceBlocks;

    public DiffDetail(String taskId,
                      String filePath,
                      DiffFile oracleFile,
                      DiffFile gaussFile,
                      CoverageDetail coverageDetail) {
        this.taskId = taskId;
        this.filePath = filePath;
        this.oracleFile = oracleFile;
        this.gaussFile = gaussFile;
        this.coverageDetail = coverageDetail;
        this.referenceBlocks = oracleFile != null ? oracleFile.getBlocks()
                : gaussFile != null ? gaussFile.getBlocks() : Collections.emptyList();
    }
}
