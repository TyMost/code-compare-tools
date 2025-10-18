package com.example.codecompare.rebuild.stats;

import com.example.codecompare.rebuild.rules.RuleDefinition;
import com.example.codecompare.rebuild.rules.RuleRegistry;
import com.example.codecompare.rebuild.rules.RuleSet;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Resolves human friendly labels for category identifiers based on the active rule configuration.
 */
@Component
public class CategoryLabelResolver {

    private final RuleRegistry ruleRegistry;

    public CategoryLabelResolver(RuleRegistry ruleRegistry) {
        this.ruleRegistry = ruleRegistry;
    }

    public String resolve(String key) {
        if (!StringUtils.hasText(key)) {
            return "未分类";
        }
        String normalizedKey = key.trim();
        RuleDefinition matched = findDefinition(normalizedKey);
        if (matched != null) {
            return matched.getResolvedDisplayName();
        }
        return toTitleCase(normalizedKey);
    }

    public String resolveColor(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        RuleDefinition matched = findDefinition(key.trim());
        if (matched == null) {
            return null;
        }
        return matched.getResolvedColor();
    }

    private RuleDefinition findDefinition(String normalizedKey) {
        RuleSet ruleSet = ruleRegistry.currentRuleSet();
        if (ruleSet == null) {
            return null;
        }
        for (RuleDefinition definition : ruleSet.getDefinitions()) {
            if (matches(normalizedKey, definition.getResolvedStatusKey())
                    || matches(normalizedKey, definition.getResolvedLabelId())
                    || matches(normalizedKey, definition.getId())) {
                return definition;
            }
        }
        return null;
    }

    private boolean matches(String key, String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        return key.equalsIgnoreCase(candidate.trim());
    }

    private String toTitleCase(String key) {
        String[] parts = key
                .replace('-', ' ')
                .replace('_', ' ')
                .split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            String lower = part.toLowerCase(Locale.ROOT);
            builder.append(Character.toUpperCase(lower.charAt(0)));
            if (lower.length() > 1) {
                builder.append(lower.substring(1));
            }
        }
        if (builder.length() == 0) {
            return key;
        }
        return builder.toString();
    }
}
