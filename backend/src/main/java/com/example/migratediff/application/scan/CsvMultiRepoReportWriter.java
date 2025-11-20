package com.example.migratediff.application.scan;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 将聚合结果渲染为 CSV 文本，便于直接下载。
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
        appendSummary(builder, result);
        for (MultiRepoExportResult.RepoReport repo : result.getRepoReports()) {
            appendRepoSummary(builder, repo);
            appendRepoDetails(builder, repo);
            builder.append(System.lineSeparator());
        }
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
                .append(System.lineSeparator());
    }

    private void appendRepoSummary(StringBuilder builder, MultiRepoExportResult.RepoReport repo) {
        builder.append("仓库,任务ID,模式,预设,生成时间,文件总数,匹配数,ΔO独有,ΔG独有,覆盖率")
                .append(System.lineSeparator());
        MultiRepoExportResult.RepoStats stats = repo.getStats();
        builder.append(csv(repo.getDisplayName())).append(',')
                .append(csv(repo.getScanReport().getTaskId())).append(',')
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
        builder.append("仓库,文件路径,ΔO新增,ΔO删除,ΔG新增,ΔG删除,覆盖率,状态")
                .append(System.lineSeparator());
        for (DiffMatrixRow row : repo.getRows()) {
            builder.append(csv(repo.getDisplayName())).append(',')
                    .append(csv(row.getFilePath())).append(',')
                    .append(row.getOracleAdded()).append(',')
                    .append(row.getOracleRemoved()).append(',')
                    .append(row.getGaussAdded()).append(',')
                    .append(row.getGaussRemoved()).append(',')
                    .append(formatPercent(row.getCoverage()))
                    .append(',')
                    .append(csv(row.getStatus()))
                    .append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());
    }

    private String csv(String value) {
        String safe = nullToEmpty(value);
        if (safe.contains(",") || safe.contains("\"")) {
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
}
