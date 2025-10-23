package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.core.properties.ApplicationProperties;
import com.example.codecompare.rebuild.diff.model.DiffSegmentType;
import com.example.codecompare.rebuild.rules.DefaultRuleRegistry;
import com.example.codecompare.rebuild.rules.RuleEvaluationFacade;
import com.example.codecompare.rebuild.rules.RuleEvaluationReport;
import com.example.codecompare.rebuild.rules.RuleHit;
import com.example.codecompare.rebuild.rules.RuleLoader;
import com.example.codecompare.rebuild.rules.RuleRegistry;
import com.example.codecompare.rebuild.rules.RuleSet;
import com.example.codecompare.rebuild.rules.strategy.BlockFilterRuleStrategy;
import com.example.codecompare.rebuild.rules.strategy.ContentMaskRuleStrategy;
import com.example.codecompare.rebuild.rules.strategy.FieldReplaceRuleStrategy;
import com.example.codecompare.rebuild.rules.strategy.PresenceRuleStrategy;
import com.example.codecompare.rebuild.rules.strategy.SimilarityRuleStrategy;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BlockDiffLabelerTest {

    @Test
    void mergesRuleMetadataIntoDiffMetadata() {
        BlockDiff diff = BlockDiff.builder()
                .sourceStartLine(1)
                .targetStartLine(1)
                .sourceLines(Collections.singletonList("return 1;"))
                .targetLines(Collections.singletonList("return 1;"))
                .metadata(Collections.<String, Object>singletonMap("existingKey", "existing"))
                .build();

        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("similarity", Collections.singletonMap("similarityPercent", 100d));
        RuleHit hit = new RuleHit("similarity-high", "migrated", "migrated",
                10, "migrated", "#67C23A", "similarity", null, metadata);
        RuleEvaluationReport report = new RuleEvaluationReport(Collections.singletonList(hit));
        RuleEvaluationFacade facade = new RuleEvaluationFacade(new StubRuleRegistry(report));
        BlockDiffLabeler labeler = new BlockDiffLabeler(facade);

        BlockDiff labeled = labeler.label(diff);

        assertThat(labeled.getMetadata()).containsKey("ruleMetadata");
        Object ruleMetadata = labeled.getMetadata().get("ruleMetadata");
        assertThat(ruleMetadata).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, ?> ruleMetadataMap = (Map<String, ?>) ruleMetadata;
        assertThat(ruleMetadataMap).containsKey("migrated");
        assertThat(ruleMetadataMap.get("migrated")).isEqualTo(metadata);
        assertThat(labeled.getMetadata()).containsEntry("existingKey", "existing");
    }

    @Test
    void identicalBlockMatchesSimilarityRule() {
        ApplicationProperties properties = new ApplicationProperties();
        RuleLoader loader = new RuleLoader(properties);
        RuleRegistry registry = new DefaultRuleRegistry(
                loader,
                Arrays.asList(
                        new FieldReplaceRuleStrategy(),
                        new ContentMaskRuleStrategy(),
                        new BlockFilterRuleStrategy(),
                        new PresenceRuleStrategy(),
                        new SimilarityRuleStrategy()
                ),
                properties.getRules());
        BlockDiffLabeler labeler = new BlockDiffLabeler(new RuleEvaluationFacade(registry));

        BlockDiff diff = BlockDiff.builder()
                .type(DiffSegmentType.CHANGE)
                .sourceStartLine(10)
                .targetStartLine(10)
                .sourceLines(Arrays.asList("int value = 1;", "return value;"))
                .targetLines(Arrays.asList("int value = 1;", "return value;"))
                .similarityScore(100d)
                .build();

        BlockDiff labeled = labeler.label(diff);

        assertThat(labeled.getLabelIds()).contains("migrated");
        assertThat(labeled.getLabels()).isNotEmpty();
        assertThat(labeled.getPrimaryLabel()).isNotNull();
    }

    private static final class StubRuleRegistry implements RuleRegistry {
        private final RuleEvaluationReport report;

        private StubRuleRegistry(RuleEvaluationReport report) {
            this.report = report;
        }

        @Override
        public RuleSet currentRuleSet() {
            return null;
        }

        @Override
        public RuleEvaluationReport evaluate(BlockDiff diff) {
            return report;
        }
    }
}
