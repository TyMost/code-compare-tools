package com.example.codecompare.rebuild.rules.similarity;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class LineSimilarityAnalyzerTest {

    private final LineSimilarityAnalyzer analyzer = new LineSimilarityAnalyzer();

    @Test
    void identicalBlocksProducePerfectSimilarity() {
        BlockDiff diff = BlockDiff.builder()
                .sourceStartLine(10)
                .targetStartLine(20)
                .sourceLines(Arrays.asList("int value = 1;", "return value;"))
                .targetLines(Arrays.asList("int value = 1;", "return value;"))
                .similarityScore(100d)
                .build();

        LineSimilaritySnapshot snapshot = analyzer.analyze(diff);

        assertThat(snapshot.getSimilarityPercent()).isEqualTo(100d);
        assertThat(snapshot.getComparisons()).hasSize(2);
        assertThat(snapshot.getComparisons())
                .allSatisfy(item -> assertThat(item.getType()).isEqualTo(LineComparison.ComparisonType.MATCH));
    }

    @Test
    void changedLineProducesPartialSimilarity() {
        BlockDiff diff = BlockDiff.builder()
                .sourceStartLine(5)
                .targetStartLine(5)
                .sourceLines(Collections.singletonList("return calculateTotal(amount, tax);"))
                .targetLines(Collections.singletonList("return calculateTotal(amount, tax + surcharge);"))
                .similarityScore(60d)
                .build();

        LineSimilaritySnapshot snapshot = analyzer.analyze(diff);

        assertThat(snapshot.getSimilarityPercent()).isLessThan(100d).isGreaterThan(0d);
        assertThat(snapshot.getComparisons()).hasSize(1);
        LineComparison comparison = snapshot.getComparisons().get(0);
        assertThat(comparison.getType()).isEqualTo(LineComparison.ComparisonType.CHANGE);
        assertThat(comparison.getSourceLine()).isEqualTo(5);
        assertThat(comparison.getTargetLine()).isEqualTo(5);
    }

    @Test
    void insertAndDeleteAreReportedWithZeroSimilarity() {
        BlockDiff diff = BlockDiff.builder()
                .sourceStartLine(1)
                .targetStartLine(1)
                .sourceLines(Arrays.asList("int a = 1;", "int b = 2;"))
                .targetLines(Collections.singletonList("int a = 1;"))
                .similarityScore(0d)
                .build();

        LineSimilaritySnapshot snapshot = analyzer.analyze(diff);

        assertThat(snapshot.getComparisons()).hasSize(2);
        assertThat(snapshot.getComparisons().get(0).getType()).isEqualTo(LineComparison.ComparisonType.MATCH);
        assertThat(snapshot.getComparisons().get(1).getType()).isEqualTo(LineComparison.ComparisonType.DELETE);
        assertThat(snapshot.getComparisons().get(1).getSimilarity()).isEqualTo(0d);
    }
}
