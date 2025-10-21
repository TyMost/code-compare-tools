package com.example.codecompare.rebuild.rules.config;

import com.example.codecompare.rebuild.core.properties.ApplicationProperties;
import com.example.codecompare.rebuild.rules.DefaultRuleRegistry;
import com.example.codecompare.rebuild.rules.RuleEvaluationFacade;
import com.example.codecompare.rebuild.rules.RuleLoader;
import com.example.codecompare.rebuild.rules.RuleRegistry;
import com.example.codecompare.rebuild.rules.strategy.BlockFilterRuleStrategy;
import com.example.codecompare.rebuild.rules.strategy.ContentMaskRuleStrategy;
import com.example.codecompare.rebuild.rules.strategy.FieldReplaceRuleStrategy;
import com.example.codecompare.rebuild.rules.strategy.PresenceRuleStrategy;
import com.example.codecompare.rebuild.rules.strategy.RuleStrategy;
import com.example.codecompare.rebuild.rules.strategy.SimilarityRuleStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 规则模块自动配置，注入策略与规则加载器。
 */
@Configuration(proxyBeanMethods = false)
public class RulesAutoConfiguration {

    @Bean
    public RuleLoader ruleLoader(ApplicationProperties migrationProperties) {
        return new RuleLoader(migrationProperties);
    }

    @Bean
    public RuleStrategy fieldReplaceRuleStrategy() {
        return new FieldReplaceRuleStrategy();
    }

    @Bean
    public RuleStrategy contentMaskRuleStrategy() {
        return new ContentMaskRuleStrategy();
    }

    @Bean
    public RuleStrategy blockFilterRuleStrategy() {
        return new BlockFilterRuleStrategy();
    }

    @Bean
    public RuleStrategy similarityRuleStrategy() {
        return new SimilarityRuleStrategy();
    }

    @Bean
    public RuleStrategy presenceRuleStrategy() {
        return new PresenceRuleStrategy();
    }

    @Bean
    public RuleRegistry ruleRegistry(RuleLoader ruleLoader,
                                     List<RuleStrategy> strategies,
                                     ApplicationProperties migrationProperties) {
        return new DefaultRuleRegistry(ruleLoader, strategies, migrationProperties.getRules());
    }

    @Bean
    public RuleEvaluationFacade ruleEvaluationFacade(RuleRegistry ruleRegistry) {
        return new RuleEvaluationFacade(ruleRegistry);
    }
}
