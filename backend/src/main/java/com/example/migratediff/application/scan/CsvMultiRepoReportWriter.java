package com.example.migratediff.application.scan;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 将聚合结果渲染为 CSV 文本，支持提交信息导出。
 */
@Component
public class CsvMultiRepoReportWriter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withLocale(Locale.CHINA)
            .withZone(java.time.ZoneId.systemDefault());

    public MediaType contentType() {
        return MediaType.TEXT_PLAIN;
    }

    public String extension() {
        return "csv";
    }

    public byte[] write(MultiRepoExportResult result) {
        StringBuilder builder = new StringBuilder();
        
        // 添加UTF-8 BOM，确保Excel正确识别中文
        builder.append('\ufeff');
        
        appendSummary(builder, result);
        
        // 按仓库分组显示，提高多仓库布局协调性
        for (MultiRepoExportResult.RepoReport repo : result.getRepoReports()) {
            appendRepoSection(builder, repo);
        }
        
        // 提交者统计信息（跨仓库汇总）- 已移除
        
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendSummary(StringBuilder builder, MultiRepoExportResult result) {
        builder.append("导出时间,").append(FORMATTER.format(result.getGeneratedAt())).append(System.lineSeparator());
        DiffMatrixFilterCriteria filter = result.getFilterCriteria();
        builder.append("状态筛选,")
                .append(filter.getStatuses() == null || filter.getStatuses().isEmpty()
                        ? "全部"
                        : String.join("|", filter.getStatuses()))
                .append(System.lineSeparator());
        builder.append("覆盖率区间,")
                .append(formatRange(filter.getCoverageMin(), filter.getCoverageMax()))
                .append(",包含覆盖率为空,")
                .append(filter.isIncludeEmptyCoverage() ? "是" : "否")
                .append(System.lineSeparator())
                .append("是否包含提交信息,")
                .append(filter.isIncludeCommitInfo() ? "是" : "否")
                .append(System.lineSeparator());
        // 注意：由于简化了前端界面，时间范围现在是仓库级别的，不在此显示
        builder.append(System.lineSeparator());
    }

    /**
     * 添加仓库分组部分，提高多仓库布局协调性
     */
    private void appendRepoSection(StringBuilder builder, MultiRepoExportResult.RepoReport repo) {
        // 仓库分隔符，增强视觉效果
        builder.append("========================================").append(System.lineSeparator());
        builder.append("=== 仓库: ").append(repo.getDisplayName()).append(" ===").append(System.lineSeparator());
        builder.append("========================================").append(System.lineSeparator());
        builder.append(System.lineSeparator());
        
        // 仓库汇总信息
        appendRepoSummary(builder, repo);
        
        // 文件详情
        builder.append("--- 文件详情 ---").append(System.lineSeparator());
        appendRepoDetails(builder, repo);
        
        // 仓库结束分隔
        builder.append(System.lineSeparator());
        builder.append(System.lineSeparator());
    }

    private void appendRepoSummary(StringBuilder builder, MultiRepoExportResult.RepoReport repo) {
        builder.append("任务ID,模式,预设,生成时间,文件总数,匹配数,ΔO独有,ΔG独有,覆盖率")
                .append(System.lineSeparator());
        MultiRepoExportResult.RepoStats stats = repo.getStats();
        builder.append(csv(repo.getScanReport().getTaskId())).append(',')
                .append(repo.getScanReport().getMode()).append(',')
                .append(csv(nullToEmpty(repo.getScanReport().getPresetName()))).append(',')
                .append(FORMATTER.format(repo.getScanReport().getGeneratedAt())).append(',')
                .append(stats.getTotalFiles()).append(',')
                .append(stats.getMatched()).append(',')
                .append(stats.getOracleOnly()).append(',')
                .append(stats.getGaussOnly()).append(',')
                .append(formatPercent(stats.getOverallCoverage()))
                .append(System.lineSeparator())
                .append(System.lineSeparator());
    }

    private void appendRepoDetails(StringBuilder builder, MultiRepoExportResult.RepoReport repo) {
        // 重新排序列，重要信息前置，提高可读性
        builder.append("文件路径,覆盖率,状态,ΔO新增,ΔO删除,ΔG新增,ΔG删除,")
                .append("Oracle最后提交者,Oracle最后提交时间,Oracle提交数,")
                .append("Gauss最后提交者,Gauss最后提交时间,Gauss提交数,")
                .append("Oracle最后提交哈希,Gauss最后提交哈希,")
                .append("Oracle最后提交消息,Gauss最后提交消息,")
                .append("是否有Oracle提交,是否有Gauss提交")
                .append(System.lineSeparator());
        for (DiffMatrixRow row : repo.getRows()) {
            builder.append(csv(row.getFilePath())).append(',')
                    .append(formatPercent(row.getCoverage())).append(',')
                    .append(csv(row.getStatus())).append(',')
                    .append(row.getOracleAdded()).append(',')
                    .append(row.getOracleRemoved()).append(',')
                    .append(row.getGaussAdded()).append(',')
                    .append(row.getGaussRemoved()).append(',')
                    
                    // Oracle提交信息（核心字段）
                    .append(csv(nullToEmpty(row.getLastOracleAuthor()))).append(',')
                    .append(csv(formatInstant(row.getLastOracleCommitTime()))).append(',')
                    .append(row.getOracleCommitCount() != null ? row.getOracleCommitCount() : 0).append(',')
                    
                    // Gauss提交信息（核心字段）
                    .append(csv(nullToEmpty(row.getLastGaussAuthor()))).append(',')
                    .append(csv(formatInstant(row.getLastGaussCommitTime()))).append(',')
                    .append(row.getGaussCommitCount() != null ? row.getGaussCommitCount() : 0).append(',')
                    
                    // 详细信息（次要字段）
                    .append(csv(nullToEmpty(row.getLastOracleCommitHash()))).append(',')
                    .append(csv(nullToEmpty(row.getLastGaussCommitHash()))).append(',')
                    .append(csv(nullToEmpty(truncateCommitMessage(row.getLastOracleCommitMessage())))).append(',')
                    .append(csv(nullToEmpty(truncateCommitMessage(row.getLastGaussCommitMessage())))).append(',')
                    .append(row.getHasOracleCommits() != null ? row.getHasOracleCommits() : false).append(',')
                    .append(row.getHasGaussCommits() != null ? row.getHasGaussCommits() : false)
                    .append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());
    }

    /**
     * 截断提交消息，避免CSV格式混乱
     */
    private String truncateCommitMessage(String message) {
        if (message == null) {
            return "";
        }
        // 限制长度，避免过长的提交消息影响CSV格式
        if (message.length() > 100) {
            return message.substring(0, 97) + "...";
        }
        return message;
    }


    private String csv(String value) {
        String safe = nullToEmpty(value);
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            safe = safe.replace("\"", "\"\"");
            return '"' + safe + '"';
        }
        return safe;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String formatPercent(Double value) {
        if (value == null) {
            return "--";
        }
        return String.format(Locale.CHINA, "%.2f%%", value * 100);
    }

    private String formatRange(Double min, Double max) {
        double lower = min == null ? 0D : min;
        double upper = max == null ? 1D : max;
        return String.format(Locale.CHINA, "%.0f%%~%.0f%%", lower * 100, upper * 100);
    }

    /**
     * 格式化时间
     */
    private String formatInstant(java.time.Instant instant) {
        if (instant == null) {
            return "";
        }
        return FORMATTER.format(instant);
    }
}
