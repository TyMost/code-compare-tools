package com.example.migratediff.application.scan;

import lombok.Builder;
import lombok.Data;

/**
 * 扫描矩阵中单个文件的统计信息，用于前后端共用的报表导出逻辑。
 */
@Data
@Builder
public class DiffMatrixRow {

    private String filePath;
    private int oracleAdded;
    private int oracleRemoved;
    private int gaussAdded;
    private int gaussRemoved;
    /**
     * 覆盖率（0~1），允许为空表示该文件尚未计算覆盖情况。
     */
    private Double coverage;
    /**
     * 状态标签：matched/oracle-only/gauss-only/partial/pending/processing。
     */
    private String status;
}
