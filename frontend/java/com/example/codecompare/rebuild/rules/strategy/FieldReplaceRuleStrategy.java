package com.example.codecompare.rebuild.rules.strategy;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.model.DiffMetrics;
import com.example.codecompare.rebuild.rules.RuleDefinition;
import com.example.codecompare.rebuild.rules.RuleHit;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 字段替换规则：判断源代码是否被目标代码替换为指定内容。
 */
public class FieldReplaceRuleStrategy extends AbstractRuleStrategy {

    public FieldReplaceRuleStrategy() {
        super("field-replace");
    }

    @Override
    public Optional<RuleHit> evaluate(BlockDiff diff, RuleDefinition definition) {
        String from = definition.getStringParam("from").orElse(null);
        String to = definition.getStringParam("to").orElse(null);
        if (!StringUtils.hasText(from) || !StringUtils.hasText(to)) {
            return empty();
        }
        String labelId = definition.getStringParam("label", definition.getId());
        String labelName = definition.getDisplayName();
        int minCount = definition.getIntParam("minCount", 1);

        DiffMetrics metrics = diff != null && diff.getDiffMetrics() != null ? diff.getDiffMetrics() : DiffMetrics.empty();
        int replaceCount = metrics.replace(from, to);
        if (replaceCount >= minCount) {
            RuleHit hit = toHit(definition, labelId, labelName);
            return Optional.of(hit);
        }

        // 备用逻辑：直接比较源/目标内容，避免指标缺失。
        String source = diff != null ? diff.getSourceContent() : null;
        String target = diff != null ? diff.getTargetContent() : null;
        if (StringUtils.hasText(source) && StringUtils.hasText(target)) {
            boolean sourceContains = source.contains(from);
            boolean targetContains = target.contains(to);
            boolean targetStillHasFrom = target.contains(from);
            if (sourceContains && targetContains && !targetStillHasFrom) {
                RuleHit hit = toHit(definition, labelId, labelName);
                return Optional.of(hit);
            }
        }

        return empty();
    }
}
