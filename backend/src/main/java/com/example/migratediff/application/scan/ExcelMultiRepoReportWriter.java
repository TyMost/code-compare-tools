package com.example.migratediff.application.scan;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 将聚合结果渲染为Excel格式，支持多Sheet导出提交信息
 */
@Service
@Slf4j
public class ExcelMultiRepoReportWriter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withLocale(Locale.CHINA)
            .withZone(java.time.ZoneId.systemDefault());

    public MediaType contentType() {
        return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    public String extension() {
        return "xlsx";
    }

    public byte[] write(MultiRepoExportResult result) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            
            // 创建第一个Sheet：文件汇总信息
            createFileSummarySheet(workbook, result);
            
            // 创建第二个Sheet：文件详细提交列表
            createFileCommitDetailSheet(workbook, result);
            
            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Excel report", e);
            throw new IOException("Failed to generate Excel report", e);
        }
    }

    /**
     * 创建文件汇总信息Sheet
     */
    private void createFileSummarySheet(Workbook workbook, MultiRepoExportResult result) {
        Sheet sheet = workbook.createSheet("文件汇总信息");
        
        // 创建标题行
        Row headerRow = sheet.createRow(0);
        String[] headers = {
            "仓库", "文件路径", "覆盖率", "状态", "代码变更量-ΔO新增", "代码变更量-ΔO删除", 
            "代码变更量-ΔG新增", "代码变更量-ΔG删除",
            "Oracle最后提交者", "Oracle最后提交时间", "Oracle提交数",
            "Gauss最后提交者", "Gauss最后提交时间", "Gauss提交数"
        };
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(createHeaderStyle(workbook));
        }

        // 填充数据行
        int rowNum = 1;
        for (MultiRepoExportResult.RepoReport repo : result.getRepoReports()) {
            for (DiffMatrixRow row : repo.getRows()) {
                Row dataRow = sheet.createRow(rowNum++);
                
                dataRow.createCell(0).setCellValue(repo.getDisplayName());
                dataRow.createCell(1).setCellValue(row.getFilePath());
                dataRow.createCell(2).setCellValue(formatPercent(row.getCoverage()));
                dataRow.createCell(3).setCellValue(row.getStatus());
                dataRow.createCell(4).setCellValue(row.getOracleAdded());
                dataRow.createCell(5).setCellValue(row.getOracleRemoved());
                dataRow.createCell(6).setCellValue(row.getGaussAdded());
                dataRow.createCell(7).setCellValue(row.getGaussRemoved());
                
                // Oracle提交信息
                dataRow.createCell(8).setCellValue(nullToEmpty(row.getLastOracleAuthor()));
                dataRow.createCell(9).setCellValue(formatInstant(row.getLastOracleCommitTime()));
                dataRow.createCell(10).setCellValue(row.getOracleCommitCount() != null ? row.getOracleCommitCount() : 0);
                
                // Gauss提交信息
                dataRow.createCell(11).setCellValue(nullToEmpty(row.getLastGaussAuthor()));
                dataRow.createCell(12).setCellValue(formatInstant(row.getLastGaussCommitTime()));
                dataRow.createCell(13).setCellValue(row.getGaussCommitCount() != null ? row.getGaussCommitCount() : 0);
            }
        }

        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * 创建文件详细提交列表Sheet
     */
    private void createFileCommitDetailSheet(Workbook workbook, MultiRepoExportResult result) {
        Sheet sheet = workbook.createSheet("文件详细提交列表");
        
        // 创建标题行
        Row headerRow = sheet.createRow(0);
        String[] headers = {
            "文件路径", "仓库来源", "提交哈希", "提交者姓名", "提交者邮箱", "提交时间", 
            "提交消息", "提交类型", "变更类型", "添加行数", "删除行数", "是否在时间范围内"
        };
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(createHeaderStyle(workbook));
        }

        // 填充数据行
        int rowNum = 1;
        if (result.getAllFileCommitDetails() != null) {
            for (FileCommitDetail detail : result.getAllFileCommitDetails()) {
                Row dataRow = sheet.createRow(rowNum++);
                
                dataRow.createCell(0).setCellValue(nullToEmpty(detail.getFilePath()));
                dataRow.createCell(1).setCellValue(nullToEmpty(detail.getRepoSource()));
                dataRow.createCell(2).setCellValue(nullToEmpty(detail.getCommitHash()));
                dataRow.createCell(3).setCellValue(nullToEmpty(detail.getAuthorName()));
                dataRow.createCell(4).setCellValue(nullToEmpty(detail.getAuthorEmail()));
                dataRow.createCell(5).setCellValue(formatInstant(detail.getCommitTime()));
                dataRow.createCell(6).setCellValue(nullToEmpty(detail.getCommitMessage()));
                dataRow.createCell(7).setCellValue(nullToEmpty(detail.getCommitType()));
                dataRow.createCell(8).setCellValue(nullToEmpty(detail.getChangeType()));
                dataRow.createCell(9).setCellValue(detail.getLinesAdded() != null ? detail.getLinesAdded() : 0);
                dataRow.createCell(10).setCellValue(detail.getLinesRemoved() != null ? detail.getLinesRemoved() : 0);
                dataRow.createCell(11).setCellValue(detail.getInTimeRange() != null ? detail.getInTimeRange() : false);
            }
        }

        // 自动调整列宽
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * 创建标题样式
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        
        return style;
    }

    /**
     * 格式化百分比
     */
    private String formatPercent(Double value) {
        if (value == null) {
            return "--";
        }
        return String.format(Locale.CHINA, "%.2f%%", value * 100);
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

    /**
     * 空值转换
     */
    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 空值转换
     */
    private String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }
}
