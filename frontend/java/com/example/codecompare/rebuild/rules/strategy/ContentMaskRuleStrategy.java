package com.example.codecompare.rebuild.rules.strategy;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.rules.RuleDefinition;
import com.example.codecompare.rebuild.rules.RuleHit;

import java.util.Optional;

/**
 * Placeholder strategy for {@code content-mask} rules so they can coexist with label rules.
 */
public class ContentMaskRuleStrategy extends AbstractRuleStrategy {

    public ContentMaskRuleStrategy() {
        super("content-mask");
    }

    @Override
    public Optional<RuleHit> evaluate(BlockDiff diff, RuleDefinition definition) {
        return empty();
    }
}
