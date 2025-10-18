package com.example.codecompare.rebuild.rules;

import com.example.codecompare.rebuild.block.model.BlockDiff;

/**
 * 面向业务层的规则执行门面。
 */
public class RuleEvaluationFacade {

    private final RuleRegistry ruleRegistry;

    public RuleEvaluationFacade(RuleRegistry ruleRegistry) {
        this.ruleRegistry = ruleRegistry;
    }

    public RuleEvaluationReport evaluate(BlockDiff diff) {
        return ruleRegistry.evaluate(diff);
    }

    public RuleSet currentRuleSet() {
        return ruleRegistry.currentRuleSet();
    }
}
