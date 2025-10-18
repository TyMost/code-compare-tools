package com.example.codecompare.rebuild.rules.strategy;

import com.example.codecompare.rebuild.rules.RuleDefinition;
import com.example.codecompare.rebuild.rules.RuleHit;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 提供通用的工具方法，便于子类构建标签结果。
 */
public abstract class AbstractRuleStrategy implements RuleStrategy {

    private final String type;

    protected AbstractRuleStrategy(String type) {
        this.type = type.toLowerCase();
    }

    @Override
    public final String type() {
        return type;
    }

    protected RuleHit toHit(RuleDefinition definition, String labelId, String labelName) {
        String resolvedLabel = StringUtils.hasText(labelId) ? labelId : definition.getResolvedLabelId();
        String resolvedName = StringUtils.hasText(labelName) ? labelName : definition.getResolvedDisplayName();
        int priority = definition.getPriority();
        String statusKey = definition.getResolvedStatusKey();
        String color = definition.getResolvedColor();
        String category = definition.getResolvedCategory();
        String filterAction = definition.getResolvedFilterAction();
        return new RuleHit(definition.getId(), resolvedLabel, resolvedName, priority, statusKey, color, category, filterAction);
    }

    protected Optional<RuleHit> empty() {
        return Optional.empty();
    }
}
