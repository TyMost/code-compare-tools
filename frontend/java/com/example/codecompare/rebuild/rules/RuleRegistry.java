package com.example.codecompare.rebuild.rules;

import com.example.codecompare.rebuild.block.model.BlockDiff;

/**
 * 规则执行入口。
 */
public interface RuleRegistry {

    RuleSet currentRuleSet();

    RuleEvaluationReport evaluate(BlockDiff diff);
}
