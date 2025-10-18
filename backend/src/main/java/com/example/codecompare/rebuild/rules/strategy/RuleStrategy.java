package com.example.codecompare.rebuild.rules.strategy;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.rules.RuleDefinition;
import com.example.codecompare.rebuild.rules.RuleHit;

import java.util.Optional;

/**
 * 规则策略接口，负责处理特定类型的规则。
 */
public interface RuleStrategy {

    /**
     * @return 该策略支持的规则类型（小写）。
     */
    String type();

    /**
     * 执行规则逻辑，返回命中标签。
     */
    Optional<RuleHit> evaluate(BlockDiff diff, RuleDefinition definition);
}
