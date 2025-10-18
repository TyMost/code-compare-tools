package com.example.codecompare.rebuild.rules.strategy;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.model.DiffMetrics;
import com.example.codecompare.rebuild.rules.RuleDefinition;
import com.example.codecompare.rebuild.rules.RuleHit;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 相似度规则：依据差异指标的相似度百分比打标签。
 */
public class SimilarityRuleStrategy extends AbstractRuleStrategy {

    public SimilarityRuleStrategy() {
        super("similarity");
    }

    @Override
    public Optional<RuleHit> evaluate(BlockDiff diff, RuleDefinition definition) {
        DiffMetrics metrics = diff != null && diff.getDiffMetrics() != null ? diff.getDiffMetrics() : DiffMetrics.empty();
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
                // 未知比较符，默认按 "below" 处理
                matched = similarity < threshold;
        }

        if (!matched) {
            return empty();
        }
        if (!StringUtils.hasText(labelId)) {
            labelId = definition.getId();
        }
        RuleHit hit = toHit(definition, labelId, labelName);
        return Optional.of(hit);
    }
}
