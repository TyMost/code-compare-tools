package com.example.migratediff.application.scan;

import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 根据状态与覆盖率范围过滤矩阵数据，逻辑与前端保持一致。
 */
@Component
public class DiffMatrixFilter {

    public List<DiffMatrixRow> filter(List<DiffMatrixRow> rows, DiffMatrixFilterCriteria criteria) {
        if (CollectionUtils.isEmpty(rows)) {
            return Collections.emptyList();
        }
        if (criteria == null) {
            return rows;
        }
        Set<String> statuses = CollectionUtils.isEmpty(criteria.getStatuses())
                ? Collections.emptySet()
                : criteria.getStatuses().stream()
                .filter(status -> status != null && !status.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.toSet());
        double min = criteria.getCoverageMin() == null ? 0D : criteria.getCoverageMin();
        double max = criteria.getCoverageMax() == null ? 1D : criteria.getCoverageMax();
        boolean includeEmpty = criteria.isIncludeEmptyCoverage();
        return rows.stream()
                .filter(row -> matchesStatus(row, statuses))
                .filter(row -> matchesCoverage(row, min, max, includeEmpty))
                .collect(Collectors.toList());
    }

    private boolean matchesStatus(DiffMatrixRow row, Set<String> statuses) {
        if (CollectionUtils.isEmpty(statuses)) {
            return true;
        }
        return statuses.contains(row.getStatus());
    }

    private boolean matchesCoverage(DiffMatrixRow row, double min, double max, boolean includeEmpty) {
        Double coverage = row.getCoverage();
        if (coverage == null) {
            return includeEmpty;
        }
        return coverage >= min && coverage <= max;
    }
}
