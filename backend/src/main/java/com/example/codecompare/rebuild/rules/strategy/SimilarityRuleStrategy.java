package com.example.codecompare.rebuild.rules.strategy;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.model.DiffMetrics;
import com.example.codecompare.rebuild.rules.RuleDefinition;
import com.example.codecompare.rebuild.rules.RuleHit;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 相似度规则：依据 diff 模块给出的相似度指标判定标签。
 */
public class SimilarityRuleStrategy extends AbstractRuleStrategy {

    public SimilarityRuleStrategy() {
        super("similarity");
    }

    @Override
    public Optional<RuleHit> evaluate(BlockDiff diff, RuleDefinition definition) {
        if (diff == null) {
            return empty();
        }
        DiffMetrics metrics = diff.getDiffMetrics();
        if (metrics == null) {
            metrics = DiffMetrics.empty();
        }
        double similarity = metrics.getSimilarityPercent();
        double threshold = definition.getDoubleParam("threshold", 50D);
        String comparison = definition.getStringParam("comparison", "below").toLowerCase();
        String labelId = definition.getStringParam("label", definition.getId());
        String labelName = definition.getDisplayName();

        boolean matched;
        switch (comparison) {
            case "below":
                matched = similarity < threshold;
                break;
            case "at-most":
                matched = similarity <= threshold;
                break;
            case "above":
                matched = similarity > threshold;
                break;
            case "at-least":
                matched = similarity >= threshold;
                break;
            case "between":
                double minThreshold = definition.getDoubleParam("minThreshold", Double.NEGATIVE_INFINITY);
                double maxThreshold = definition.getDoubleParam("maxThreshold",
                        definition.getDoubleParam("threshold").orElse(Double.POSITIVE_INFINITY));
                if (maxThreshold < minThreshold) {
                    double temp = minThreshold;
                    minThreshold = maxThreshold;
                    maxThreshold = temp;
                }
                matched = similarity > minThreshold && similarity < maxThreshold;
                break;
            default:
                matched = similarity < threshold;
        }

        if (!matched) {
            return empty();
        }
        if (!StringUtils.hasText(labelId)) {
            labelId = definition.getId();
        }
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("similarityPercent", similarity);
        metadata.put("changedLines", metrics.getChangedLines());
        metadata.put("existsInSource", metrics.isExistsInA());
        metadata.put("existsInTarget", metrics.isExistsInB());
        RuleHit hit = toHit(definition, labelId, labelName, metadata);
        return Optional.of(hit);
    }
}
