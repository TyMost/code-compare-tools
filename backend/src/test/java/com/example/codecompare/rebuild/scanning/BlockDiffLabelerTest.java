package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.model.DiffMetrics;
import com.example.codecompare.rebuild.rules.RuleEvaluationFacade;
import com.example.codecompare.rebuild.rules.RuleEvaluationReport;
import com.example.codecompare.rebuild.rules.RuleHit;
import com.example.codecompare.rebuild.rules.RuleRegistry;
import com.example.codecompare.rebuild.rules.RuleSet;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static com.example.codecompare.rebuild.scanning.BlockLabelConstants.STATUS_MIGRATED;
import static com.example.codecompare.rebuild.scanning.BlockLabelConstants.STATUS_NEW_CODE;
import static com.example.codecompare.rebuild.scanning.BlockLabelConstants.STATUS_UNMIGRATED;
import static org.assertj.core.api.Assertions.assertThat;

class BlockDiffLabelerTest {

    @Test
    void labelsSourceOnlyBlockAsUnmigrated() {
        BlockDiff diff = BlockDiff.builder()
                .sourceContent("legacyCall();")
                .targetContent("")
                .diffMetrics(DiffMetrics.of("legacyCall();", "", 0d))
                .build();

        BlockDiffLabeler labeler = new BlockDiffLabeler(ruleFacade(diff2 -> emptyReport()));
        BlockDiff labeled = labeler.label(diff);

        assertThat(labeled.getLabelIds()).contains(STATUS_UNMIGRATED);
    }

    @Test
    void labelsTargetOnlyBlockAsNewCode() {
        BlockDiff diff = BlockDiff.builder()
                .sourceContent("")
                .targetContent("modernCall();")
                .diffMetrics(DiffMetrics.of("", "modernCall();", 0d))
                .build();

        BlockDiffLabeler labeler = new BlockDiffLabeler(ruleFacade(diff2 -> emptyReport()));
        BlockDiff labeled = labeler.label(diff);

        assertThat(labeled.getLabelIds()).contains(STATUS_NEW_CODE);
    }

    @Test
    void labelsIdenticalContentAsMigrated() {
        String content = "void handler() { }";
        BlockDiff diff = BlockDiff.builder()
                .sourceContent(content)
                .targetContent(content)
                .diffMetrics(DiffMetrics.of(content, content, 100d))
                .build();

        BlockDiffLabeler labeler = new BlockDiffLabeler(ruleFacade(diff2 -> emptyReport()));
        BlockDiff labeled = labeler.label(diff);

        assertThat(labeled.getLabelIds()).contains(STATUS_MIGRATED);
    }

    @Test
    void mergesRuleLabelsWithBaseLabels() {
        BlockDiff diff = BlockDiff.builder()
                .sourceContent("LEGACY::encode(value);")
                .targetContent("encoder.encode(value);")
                .diffMetrics(DiffMetrics.of("LEGACY::encode(value);", "encoder.encode(value);", 80d))
                .build();

        BlockDiffLabeler labeler = new BlockDiffLabeler(ruleFacade(d -> new RuleEvaluationReport(
                Collections.singletonList(ruleHit("replace-syntax", "语法重构", "语法重构", null)))));
        BlockDiff labeled = labeler.label(diff);

        assertThat(labeled.getLabelIds()).contains("语法重构");
        assertThat(labeled.getLabels()).contains("语法重构");
    }

    @Test
    void marksBlockAsFilteredWhenRuleRequestsDrop() {
        BlockDiff diff = BlockDiff.builder()
                .sourceContent("// comment only")
                .targetContent("")
                .diffMetrics(DiffMetrics.of("// comment only", "", 0d))
                .build();

        BlockDiffLabeler labeler = new BlockDiffLabeler(ruleFacade(d -> new RuleEvaluationReport(
                Collections.singletonList(ruleHit("drop-comment", "ignored_comments", "ignored_comments", "drop")))));
        BlockDiff labeled = labeler.label(diff);

        assertThat(labeled.isFilteredOut()).isTrue();
    }

    private RuleEvaluationFacade ruleFacade(Function<BlockDiff, RuleEvaluationReport> evaluator) {
        return new RuleEvaluationFacade(new StubRuleRegistry(evaluator));
    }

    private RuleEvaluationReport emptyReport() {
        return new RuleEvaluationReport(Collections.emptyList());
    }

    private RuleHit ruleHit(String ruleId, String labelId, String labelName, String filterAction) {
        return new RuleHit(ruleId, labelId, labelName, 500, labelId, "#409EFF", "test", filterAction);
    }

    private static final class StubRuleRegistry implements RuleRegistry {

        private final Function<BlockDiff, RuleEvaluationReport> evaluator;

        private StubRuleRegistry(Function<BlockDiff, RuleEvaluationReport> evaluator) {
            this.evaluator = evaluator;
        }

        @Override
        public RuleSet currentRuleSet() {
            return new RuleSet(Collections.emptyList(), Instant.now(), "stub");
        }

        @Override
        public RuleEvaluationReport evaluate(BlockDiff diff) {
            return evaluator.apply(diff);
        }
    }
}
