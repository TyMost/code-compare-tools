package com.example.codecompare.rebuild.diff;

import com.example.codecompare.rebuild.core.properties.ApplicationProperties;
import com.example.codecompare.rebuild.diff.DiffContentMasker.Scope;
import com.example.codecompare.rebuild.rules.RuleDefinition;
import com.example.codecompare.rebuild.rules.RuleLoader;
import com.example.codecompare.rebuild.rules.RuleSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies masking based on {@code content-mask} rules defined in {@code rules.yaml}.
 */
@Component
public class RuleBasedDiffContentMasker implements DiffContentMasker {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedDiffContentMasker.class);
    private static final String RULE_TYPE = "content-mask";

    private final RuleLoader ruleLoader;
    private final boolean reloadOnChange;

    private volatile Instant cachedAt = Instant.EPOCH;
    private volatile List<MaskRule> cachedRules = Collections.emptyList();

    public RuleBasedDiffContentMasker(RuleLoader ruleLoader, ApplicationProperties applicationProperties) {
        this.ruleLoader = Objects.requireNonNull(ruleLoader, "ruleLoader must not be null");
        this.reloadOnChange = applicationProperties != null
                && applicationProperties.getRules() != null
                && applicationProperties.getRules().isReloadOnChange();
    }

    @Override
    public String mask(String content, Scope scope) {
        String source = content == null ? "" : content;
        if (!StringUtils.hasText(source)) {
            return source;
        }
        List<MaskRule> rules = ensureRules();
        if (rules.isEmpty()) {
            return source;
        }
        String result = source;
        for (MaskRule rule : rules) {
            if (!rule.appliesTo(scope)) {
                continue;
            }
            result = rule.apply(result);
        }
        return result;
    }

    private List<MaskRule> ensureRules() {
        if (reloadOnChange) {
            ruleLoader.reload();
        }
        RuleSet ruleSet = ruleLoader.currentRules();
        Instant loadedAt = ruleSet.getLoadedAt();
        if (loadedAt.equals(cachedAt)) {
            return cachedRules;
        }
        synchronized (this) {
            if (loadedAt.equals(cachedAt)) {
                return cachedRules;
            }
            List<MaskRule> compiled = compileRules(ruleSet.getDefinitions());
            cachedRules = compiled;
            cachedAt = loadedAt;
            log.info("Diff content masker loaded {} rule(s) at {}", compiled.size(), cachedAt);
            return compiled;
        }
    }

    private List<MaskRule> compileRules(List<RuleDefinition> definitions) {
        if (CollectionUtils.isEmpty(definitions)) {
            return Collections.emptyList();
        }
        List<MaskRule> rules = new ArrayList<>();
        for (RuleDefinition definition : definitions) {
            if (!RULE_TYPE.equalsIgnoreCase(definition.getType())) {
                continue;
            }
            java.util.Optional<String> patternOptional = definition.getStringParam("pattern");
            if (!patternOptional.isPresent()) {
                log.warn("Skip content-mask rule {}: missing pattern", definition.getId());
                continue;
            }
            String pattern = patternOptional.get();
            try {
                String replacement = definition.getStringParam("replacement", "");
                EnumSet<Scope> scopes = resolveScopes(definition);
                int flags = resolveFlags(definition);
                Pattern compiledPattern = Pattern.compile(pattern, flags);
                rules.add(new MaskRule(definition.getId(), compiledPattern, replacement, scopes));
            } catch (Exception ex) {
                log.warn("Skip content-mask rule {}: {}", definition.getId(), ex.getMessage());
            }
        }
        return Collections.unmodifiableList(rules);
    }

    private EnumSet<Scope> resolveScopes(RuleDefinition definition) {
        Object applyTo = definition.getParams().get("applyTo");
        if (applyTo == null) {
            return EnumSet.allOf(Scope.class);
        }
        Set<String> tokens = new LinkedHashSet<>();
        if (applyTo instanceof Iterable) {
            for (Object item : (Iterable<?>) applyTo) {
                if (item != null) {
                    tokens.add(item.toString());
                }
            }
        } else {
            tokens.add(applyTo.toString());
        }
        EnumSet<Scope> scopes = EnumSet.noneOf(Scope.class);
        for (String raw : tokens) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            if ("BOTH".equals(normalized) || "ALL".equals(normalized) || "*".equals(normalized)) {
                return EnumSet.allOf(Scope.class);
            }
            try {
                scopes.add(Scope.valueOf(normalized));
            } catch (IllegalArgumentException ex) {
                log.warn("content-mask 规则 {} 包含未知 applyTo 值 {}，已忽略", definition.getId(), raw);
            }
        }
        return scopes.isEmpty() ? EnumSet.allOf(Scope.class) : scopes;
    }

    private int resolveFlags(RuleDefinition definition) {
        Object rawFlags = definition.getParams().get("flags");
        int flags = Pattern.MULTILINE;
        if (rawFlags == null) {
            return flags;
        }
        if (rawFlags instanceof Number) {
            return flags | ((Number) rawFlags).intValue();
        }
        Set<String> tokens = new LinkedHashSet<>();
        if (rawFlags instanceof Iterable) {
            for (Object item : (Iterable<?>) rawFlags) {
                if (item != null) {
                    tokens.add(item.toString());
                }
            }
        } else {
            tokens.add(rawFlags.toString());
        }
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            String normalized = token.trim().toUpperCase(Locale.ROOT);
            Integer flagValue = FLAG_MAPPINGS.get(normalized);
            if (flagValue == null) {
                log.warn("content-mask 规则使用了未知 Pattern flag {}，默认忽略", token);
                continue;
            }
            flags |= flagValue;
        }
        return flags;
    }

    private static final Map<String, Integer> FLAG_MAPPINGS;

    static {
        Map<String, Integer> map = new HashMap<>();
        map.put("CASE_INSENSITIVE", Pattern.CASE_INSENSITIVE);
        map.put("CI", Pattern.CASE_INSENSITIVE);
        map.put("MULTILINE", Pattern.MULTILINE);
        map.put("DOTALL", Pattern.DOTALL);
        map.put("UNICODE_CASE", Pattern.UNICODE_CASE);
        map.put("COMMENTS", Pattern.COMMENTS);
        map.put("LITERAL", Pattern.LITERAL);
        map.put("UNICODE_CHARACTER_CLASS", Pattern.UNICODE_CHARACTER_CLASS);
        map.put("UCC", Pattern.UNICODE_CHARACTER_CLASS);
        FLAG_MAPPINGS = Collections.unmodifiableMap(map);
    }

    private static final class MaskRule {
        private final String id;
        private final Pattern pattern;
        private final String replacement;
        private final EnumSet<Scope> scopes;

        private MaskRule(String id, Pattern pattern, String replacement, EnumSet<Scope> scopes) {
            this.id = id;
            this.pattern = pattern;
            this.replacement = replacement == null ? "" : replacement;
            this.scopes = scopes.isEmpty() ? EnumSet.allOf(Scope.class) : scopes;
        }

        boolean appliesTo(Scope scope) {
            return scopes.contains(scope);
        }

        String apply(String input) {
            Matcher matcher = pattern.matcher(input);
            String result = matcher.replaceAll(replacement);
            if (log.isDebugEnabled() && !result.equals(input)) {
                log.debug("content-mask 规则 {} 已应用", id);
            }
            return result;
        }
    }
}
