package com.example.migratediff.application.scan;

import com.example.migratediff.domain.coverage.CoverageDetail;
import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 将 {@link ScanReport} 统一转换为矩阵视图，供接口响应及报表导出复用。
 */
@Component
public class DiffMatrixAssembler {

    public List<DiffMatrixRow> assemble(ScanReport report) {
        if (report == null) {
            return java.util.Collections.emptyList();
        }
        return report.filePaths().stream()
                .flatMap(path -> buildRow(report, path).map(Stream::of).orElseGet(Stream::empty))
                .sorted(Comparator.comparing(DiffMatrixRow::getFilePath))
                .collect(Collectors.toList());
    }

    private Optional<DiffMatrixRow> buildRow(ScanReport report, String path) {
        DiffFile oracleFile = report.findOracle(path).orElse(null);
        DiffFile gaussFile = report.findGauss(path).orElse(null);
        CoverageDetail coverageDetail = report.findCoverage(path).orElse(null);
        Double coverage = coverageDetail != null ? coverageDetail.getCoverage() : null;
        DiffMatrixRow row = DiffMatrixRow.builder()
                .filePath(path)
                .oracleAdded(countAdded(oracleFile))
                .oracleRemoved(countRemoved(oracleFile))
                .gaussAdded(countAdded(gaussFile))
                .gaussRemoved(countRemoved(gaussFile))
                .coverage(coverage)
                .status(resolveStatus(oracleFile, gaussFile, coverage != null ? coverage : 0D))
                .build();
        return Optional.of(row);
    }

    private String resolveStatus(DiffFile oracleFile, DiffFile gaussFile, double coverage) {
        boolean hasOracle = hasBlocks(oracleFile);
        boolean hasGauss = hasBlocks(gaussFile);
        if (hasOracle && hasGauss) {
            if (coverage >= 0.999D) {
                return "matched";
            }
            if (coverage <= 0.01D) {
                return "pending";
            }
            return "partial";
        }
        if (hasOracle) {
            return "oracle-only";
        }
        if (hasGauss) {
            return "gauss-only";
        }
        return "pending";
    }

    private boolean hasBlocks(DiffFile file) {
        return file != null && !CollectionUtils.isEmpty(file.getBlocks());
    }

    private int countAdded(DiffFile file) {
        if (file == null || CollectionUtils.isEmpty(file.getBlocks())) {
            return 0;
        }
        return file.getBlocks().stream()
                .mapToInt(this::addedLines)
                .sum();
    }

    private int countRemoved(DiffFile file) {
        if (file == null || CollectionUtils.isEmpty(file.getBlocks())) {
            return 0;
        }
        return file.getBlocks().stream()
                .mapToInt(this::removedLines)
                .sum();
    }

    private int addedLines(DiffBlock block) {
        if (block == null) {
            return 0;
        }
        DiffType type = block.getType();
        if (type == null) {
            type = DiffType.MODIFY;
        }
        switch (type) {
            case ADD:
                return safeCount(block.getStartLineTo(), block.getEndLineTo());
            case MODIFY:
                return Math.max(
                        safeCount(block.getStartLineTo(), block.getEndLineTo()),
                        safeCount(block.getStartLineFrom(), block.getEndLineFrom()));
            default:
                return safeCount(block.getStartLineTo(), block.getEndLineTo());
        }
    }

    private int removedLines(DiffBlock block) {
        if (block == null) {
            return 0;
        }
        DiffType type = block.getType();
        if (type == null) {
            type = DiffType.MODIFY;
        }
        switch (type) {
            case DELETE:
                return safeCount(block.getStartLineFrom(), block.getEndLineFrom());
            case MODIFY:
                return Math.max(
                        safeCount(block.getStartLineFrom(), block.getEndLineFrom()),
                        safeCount(block.getStartLineTo(), block.getEndLineTo()));
            default:
                return safeCount(block.getStartLineFrom(), block.getEndLineFrom());
        }
    }

    private int safeCount(int start, int end) {
        if (start <= 0 || end < start) {
            return 0;
        }
        return end - start + 1;
    }
}
