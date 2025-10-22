package com.example.codecompare.rebuild.diff.support;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.support.BlockDiffAlignment.Line;
import com.example.codecompare.rebuild.diff.support.BlockDiffAlignment.LineType;
import com.example.codecompare.rebuild.diff.model.DiffSegmentType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlockDiffAlignmentTest {

    @Test
    void alignShouldProduceChangeAndContextRows() {
        BlockDiff diff = BlockDiff.builder()
                .type(DiffSegmentType.CHANGE)
                .sourceStartLine(10)
                .targetStartLine(20)
                .sourceLines(Arrays.asList("int value = 1;", "return value;"))
                .targetLines(Arrays.asList("int value = 2;", "return value;"))
                .build();

        List<Line> rows = BlockDiffAlignment.align(diff);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getType()).isEqualTo(LineType.CHANGE);
        assertThat(rows.get(0).getSourceLine()).isEqualTo(10);
        assertThat(rows.get(0).getTargetLine()).isEqualTo(20);
        assertThat(rows.get(0).getSourceText()).isEqualTo("int value = 1;");
        assertThat(rows.get(0).getTargetText()).isEqualTo("int value = 2;");

        assertThat(rows.get(1).getType()).isEqualTo(LineType.CONTEXT);
        assertThat(rows.get(1).getSourceLine()).isEqualTo(11);
        assertThat(rows.get(1).getTargetLine()).isEqualTo(21);
        assertThat(rows.get(1).getSourceText()).isEqualTo("return value;");
        assertThat(rows.get(1).getTargetText()).isEqualTo("return value;");
    }

    @Test
    void alignShouldHandleInsertOnly() {
        BlockDiff diff = BlockDiff.builder()
                .type(DiffSegmentType.INSERT)
                .sourceStartLine(5)
                .targetStartLine(8)
                .sourceLines(Collections.emptyList())
                .targetLines(Collections.singletonList("new line"))
                .build();

        List<Line> rows = BlockDiffAlignment.align(diff);

        assertThat(rows).hasSize(1);
        Line row = rows.get(0);
        assertThat(row.getType()).isEqualTo(LineType.ADD);
        assertThat(row.getSourceLine()).isNull();
        assertThat(row.getTargetLine()).isEqualTo(8);
        assertThat(row.getTargetText()).isEqualTo("new line");
    }

    @Test
    void alignShouldHandleDeleteOnly() {
        BlockDiff diff = BlockDiff.builder()
                .type(DiffSegmentType.DELETE)
                .sourceStartLine(3)
                .targetStartLine(3)
                .sourceLines(Collections.singletonList("removed();"))
                .targetLines(Collections.emptyList())
                .build();

        List<Line> rows = BlockDiffAlignment.align(diff);

        assertThat(rows).hasSize(1);
        Line row = rows.get(0);
        assertThat(row.getType()).isEqualTo(LineType.REMOVE);
        assertThat(row.getSourceLine()).isEqualTo(3);
        assertThat(row.getSourceText()).isEqualTo("removed();");
        assertThat(row.getTargetLine()).isNull();
    }

    @Test
    void alignShouldDistributeMismatchedChangeLengths() {
        BlockDiff diff = BlockDiff.builder()
                .type(DiffSegmentType.CHANGE)
                .sourceStartLine(1)
                .targetStartLine(1)
                .sourceLines(Arrays.asList("line a", "line b", "line c"))
                .targetLines(Arrays.asList("line a modified", "line b", "line c", "line d"))
                .build();

        List<Line> rows = BlockDiffAlignment.align(diff);

        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).getType()).isEqualTo(LineType.CHANGE);
        assertThat(rows.get(1).getType()).isEqualTo(LineType.CONTEXT);
        assertThat(rows.get(2).getType()).isEqualTo(LineType.CONTEXT);
        assertThat(rows.get(3).getType()).isEqualTo(LineType.ADD);
    }
}
