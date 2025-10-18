package com.example.codecompare.rebuild.rules;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.core.properties.ApplicationProperties;
import com.example.codecompare.rebuild.rules.strategy.RuleStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 默认规则注册器，实现规则加载与执行。
 */
public class DefaultRuleRegistry implements RuleRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultRuleRegistry.class);

    private final RuleLoader ruleLoader;
    private final Map<String, RuleStrategy> strategies;
    private final boolean reloadOnChange;

    public DefaultRuleRegistry(RuleLoader ruleLoader,
                               List<RuleStrategy> strategies,
                               ApplicationProperties.RuleRegistryProperties rulesProperties) {
        this.ruleLoader = ruleLoader;
        this.strategies = new LinkedHashMap<>();
        for (RuleStrategy strategy : strategies) {
            this.strategies.put(strategy.type().toLowerCase(Locale.ROOT), strategy);
        }
        this.reloadOnChange = rulesProperties != null && rulesProperties.isReloadOnChange();
    }

    @Override
    public RuleSet currentRuleSet() {
        return ruleLoader.currentRules();
    }

    @Override
    public RuleEvaluationReport evaluate(BlockDiff diff) {
        if (reloadOnChange) {
            ruleLoader.reload();
        }
        RuleSet ruleSet = ruleLoader.currentRules();
        List<RuleHit> hits = new ArrayList<>();
        for (RuleDefinition definition : ruleSet.getDefinitions()) {
            Optional<RuleStrategy> strategy = findStrategy(definition);
            if (!strategy.isPresent()) {
                log.warn("未找到类型为 {} 的规则实现，跳过规则 {}。", definition.getType(), definition.getId());
                continue;
            }
            strategy.get().evaluate(diff, definition).ifPresent(hits::add);
        }
        return new RuleEvaluationReport(hits);
    }

    private Optional<RuleStrategy> findStrategy(RuleDefinition definition) {
        RuleStrategy strategy = strategies.get(definition.getType());
        if (strategy != null) {
            return Optional.of(strategy);
        }
        // 再尝试忽略大小写匹配
        return strategies.values().stream()
                .filter(item -> item.type().equalsIgnoreCase(definition.getType()))
                .findFirst();
    }
}
