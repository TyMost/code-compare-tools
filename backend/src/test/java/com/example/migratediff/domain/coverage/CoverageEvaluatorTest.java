package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageEvaluatorTest {

    private final CoverageEvaluator evaluator = new CoverageEvaluator();

    @Test
    void evaluateFile_shouldReturnFullCoverageWhenBlocksMatch() {
        DiffBlock oracleBlock = DiffBlock.builder()
                .contentFrom("int value = 1;")
                .build();
        DiffBlock gaussBlock = DiffBlock.builder()
                .contentTo("int value = 1;")
                .build();
        DiffFile oracleFile = DiffFile.builder()
                .relativePath("demo/Match.java")
                .blocks(Collections.singletonList(oracleBlock))
                .build();
        DiffFile gaussFile = DiffFile.builder()
                .relativePath("demo/Match.java")
                .blocks(Collections.singletonList(gaussBlock))
                .build();

        CoverageDetail detail = evaluator.evaluateFile(oracleFile, gaussFile);

        assertEquals(1.0D, detail.getCoverage(), 1e-6, "完全相同的块覆盖率应为 1");
        assertEquals(1.0D, detail.getMatchedLines(), 1e-6, "加权匹配行数等于总行数");
        assertEquals(1, detail.getTotalLines(), "单行代码的总行数应为 1");
        assertTrue(detail.getMatchedBlocks().contains(oracleBlock), "匹配块列表应包含原始块");
    }

    @Test
    void evaluateFile_shouldReturnWeightedCoverageWithoutThreshold() {
        DiffBlock oracleBlock = DiffBlock.builder()
                .contentFrom("alpha beta")
                .build();
        DiffBlock gaussBlock = DiffBlock.builder()
                .contentTo("alpha")
                .build();
        DiffFile oracleFile = DiffFile.builder()
                .relativePath("demo/Partial.java")
                .blocks(Collections.singletonList(oracleBlock))
                .build();
        DiffFile gaussFile = DiffFile.builder()
                .relativePath("demo/Partial.java")
                .blocks(Collections.singletonList(gaussBlock))
                .build();

        CoverageDetail detail = evaluator.evaluateFile(oracleFile, gaussFile);

        double expectedSimilarity = 0.5D; // Jaccard(alpha, beta) 与 alpha 的交集为 1 / 2
        assertEquals(expectedSimilarity, detail.getCoverage(), 1e-6, "覆盖率应等于相似度加权结果");
        assertEquals(expectedSimilarity, detail.getMatchedLines(), 1e-6, "加权匹配行数应等于相似度 × 行数");
        assertEquals(1, detail.getTotalLines(), "单行文本的总行数应为 1");
        assertTrue(detail.getUnmatchedBlocks().contains(oracleBlock), "未达展示阈值的块应记录在 unmatched 列表中");
    }

    @Test
    void evaluateFiles_shouldAggregateWeightedCoverage() {
        DiffBlock oracleExact = DiffBlock.builder()
                .contentFrom("lineA")
                .build();
        DiffBlock gaussExact = DiffBlock.builder()
                .contentTo("lineA")
                .build();
        DiffBlock oraclePartial = DiffBlock.builder()
                .contentFrom("one two")
                .build();
        DiffBlock gaussPartial = DiffBlock.builder()
                .contentTo("one")
                .build();

        DiffFile oracleFile1 = DiffFile.builder()
                .relativePath("demo/File1.java")
                .blocks(Collections.singletonList(oracleExact))
                .build();
        DiffFile gaussFile1 = DiffFile.builder()
                .relativePath("demo/File1.java")
                .blocks(Collections.singletonList(gaussExact))
                .build();

        DiffFile oracleFile2 = DiffFile.builder()
                .relativePath("demo/File2.java")
                .blocks(Collections.singletonList(oraclePartial))
                .build();
        DiffFile gaussFile2 = DiffFile.builder()
                .relativePath("demo/File2.java")
                .blocks(Collections.singletonList(gaussPartial))
                .build();

        CoverageSummary summary = evaluator.evaluateFiles(
                java.util.Arrays.asList(oracleFile1, oracleFile2),
                java.util.Arrays.asList(gaussFile1, gaussFile2)
        );

        double expectedMatched = 1.0D + 0.5D; // 第一文件完全匹配，第二文件相似度 0.5
        double expectedTotal = 2.0D;          // 两个文件各 1 行，合计 2 行

        assertEquals(expectedMatched, summary.getTotalMatchedLines(), 1e-6, "总加权匹配行数应为两文件加总");
        assertEquals(expectedTotal, summary.getTotalLines(), 1e-6, "总行数应为两文件行数之和");
        assertEquals(expectedMatched / expectedTotal, summary.getOverallCoverage(), 1e-6, "总体覆盖率应为加权平均");
    }
}
