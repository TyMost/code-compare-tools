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
        Set<String> fileExtensions = CollectionUtils.isEmpty(criteria.getFileExtensions())
                ? Collections.emptySet()
                : criteria.getFileExtensions().stream()
                .filter(ext -> ext != null && !ext.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.toSet());
        boolean excludeTestFiles = criteria.isExcludeTestFiles();
        List<String> excludePatterns = CollectionUtils.isEmpty(criteria.getExcludePatterns())
                ? Collections.emptyList()
                : criteria.getExcludePatterns().stream()
                .filter(pattern -> pattern != null && !pattern.trim().isEmpty())
                .map(String::trim)
                .collect(Collectors.toList());
        
        return rows.stream()
                .filter(row -> matchesStatus(row, statuses))
                .filter(row -> matchesCoverage(row, min, max, includeEmpty))
                .filter(row -> matchesFileExtension(row, fileExtensions))
                .filter(row -> !excludeTestFiles || !isTestFile(row))
                .filter(row -> !matchesExcludePatterns(row, excludePatterns))
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

    private boolean matchesFileExtension(DiffMatrixRow row, Set<String> fileExtensions) {
        if (CollectionUtils.isEmpty(fileExtensions)) {
            return true;
        }
        String filePath = row.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }
        // 提取文件扩展名
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filePath.length() - 1) {
            return false; // 没有扩展名
        }
        String extension = "." + filePath.substring(lastDotIndex + 1).toLowerCase();
        return fileExtensions.contains(extension);
    }

    private boolean isTestFile(DiffMatrixRow row) {
        String filePath = row.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        String lowerFileName = fileName.toLowerCase();
        
        // 检查常见的测试文件模式
        return lowerFileName.contains("test") 
            || lowerFileName.contains("spec")
            || lowerFileName.endsWith("test.java")
            || lowerFileName.endsWith("spec.java")
            || lowerFileName.endsWith("_test.java")
            || lowerFileName.endsWith("_spec.java")
            || lowerFileName.startsWith("test")
            || lowerFileName.startsWith("spec");
    }

    private boolean matchesExcludePatterns(DiffMatrixRow row, List<String> excludePatterns) {
        if (CollectionUtils.isEmpty(excludePatterns)) {
            return false;
        }
        String filePath = row.getFilePath();
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }
        
        String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        
        for (String pattern : excludePatterns) {
            if (matchesPattern(fileName, pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(String fileName, String pattern) {
        if (fileName == null || pattern == null) {
            return false;
        }
        
        // 检查是否为正则表达式（用 / 包裹）
        if (pattern.startsWith("/") && pattern.endsWith("/") && pattern.length() > 2) {
            try {
                String regexPattern = pattern.substring(1, pattern.length() - 1);
                return fileName.matches(regexPattern);
            } catch (Exception e) {
                // 正则表达式无效，忽略此模式
                return false;
            }
        }
        
        // 转换通配符为正则表达式
        String regexPattern = pattern
            .replace(".", "\\.")  // 转义点号
            .replace("*", ".*")   // * 转换为 .*
            .replace("?", ".");    // ? 转换为 .
        
        return fileName.matches("^" + regexPattern + "$");
    }
}
