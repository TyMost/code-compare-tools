package com.example.codecompare.rebuild.rules.strategy;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.rules.RuleDefinition;
import com.example.codecompare.rebuild.rules.RuleHit;
import com.example.codecompare.rebuild.rules.similarity.LineSimilarityAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SimilarityRuleStrategyTest {

    private final LineSimilarityAnalyzer analyzer = new LineSimilarityAnalyzer();
    private final SimilarityRuleStrategy strategy = new SimilarityRuleStrategy(analyzer);

    @Test
    void ruleHitContainsSimilarityMetadata() {
        BlockDiff diff = BlockDiff.builder()
                .sourceStartLine(1)
                .targetStartLine(1)
                .sourceLines(Collections.singletonList("int value = 1;"))
                .targetLines(Collections.singletonList("int value = 1;"))
                .similarityScore(100d)
                .build();

        RuleDefinition definition = definitionWithParams("similarity-high",
                mapOf("threshold", 100, "comparison", "at-least", "label", "match"));

        Optional<RuleHit> hitOptional = strategy.evaluate(diff, definition);

        assertThat(hitOptional).isPresent();
        RuleHit hit = hitOptional.get();
        assertThat(hit.getMetadata()).containsKey("similarity");
        Object payload = hit.getMetadata().get("similarity");
        assertThat(payload).isInstanceOf(Map.class);
        Map<?, ?> similarity = (Map<?, ?>) payload;
        assertThat(similarity.get("similarityPercent")).isEqualTo(100d);
    }

    @Test
    void ruleNotHitWhenSimilarityBelowThreshold() {
        BlockDiff diff = BlockDiff.builder()
                .sourceStartLine(1)
                .targetStartLine(1)
                .sourceLines(Collections.singletonList("return 1;"))
                .targetLines(Collections.singletonList("return 2;"))
                .similarityScore(0d)
                .build();

        RuleDefinition definition = definitionWithParams("similarity-high",
                mapOf("threshold", 80, "comparison", "at-least"));

        Optional<RuleHit> hitOptional = strategy.evaluate(diff, definition);

        assertThat(hitOptional).isNotPresent();
    }

    private RuleDefinition definitionWithParams(String id, Map<String, Object> params) {
        return new RuleDefinition(id, "similarity", id, params);
    }

    private Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new HashMap<String, Object>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i].toString(), keyValues[i + 1]);
        }
        return map;
    }
}
