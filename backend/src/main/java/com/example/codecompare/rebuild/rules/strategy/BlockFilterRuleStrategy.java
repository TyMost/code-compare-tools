package com.example.codecompare.rebuild.rules.strategy;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.rules.RuleDefinition;
import com.example.codecompare.rebuild.rules.RuleHit;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则类型 {@code block-filter}：用于基于内容特征忽略整个差异块，例如空行、注释块等。
 */
public class BlockFilterRuleStrategy extends AbstractRuleStrategy {

    public BlockFilterRuleStrategy() {
        super("block-filter");
    }

    @Override
    public Optional<RuleHit> evaluate(BlockDiff diff, RuleDefinition definition) {
        if (diff == null) {
            return empty();
        }
        String filterAction = definition.getStringParam("filterAction").orElse(null);
        if (!StringUtils.hasText(filterAction)) {
            return empty();
        }
        String mode = definition.getStringParam("applyTo").orElse("either").trim().toLowerCase(Locale.ROOT);
        String patternText = definition.getStringParam("pattern").orElse(null);
        int flags = resolveFlags(definition.getStringParam("flags").orElse(null));

        String source = normalize(diff.getSourceContent());
        String target = normalize(diff.getTargetContent());

        boolean matched;
        if (StringUtils.hasText(patternText)) {
            Pattern pattern = Pattern.compile(patternText, flags);
            boolean sourceMatched = matches(pattern, source);
            boolean targetMatched = matches(pattern, target);
            matched = resolveMatchByMode(mode, sourceMatched, targetMatched);
        } else {
            // 默认检测空白块
            boolean sourceBlank = isBlank(source);
            boolean targetBlank = isBlank(target);
            matched = resolveMatchByMode(mode, sourceBlank, targetBlank);
        }

        if (!matched) {
            return empty();
        }
        String labelId = definition.getStringParam("label").orElse(definition.getId());
        String labelName = definition.getDisplayName();
        RuleHit hit = toHit(definition, labelId, labelName);
        return Optional.of(hit);
    }

    private boolean resolveMatchByMode(String mode, boolean source, boolean target) {
        if ("both".equals(mode) || "and".equals(mode) || "all".equals(mode)) {
            return source && target;
        }
        if ("source".equals(mode)) {
            return source;
        }
        if ("target".equals(mode)) {
            return target;
        }
        // 默认 either
        return source || target;
    }

    private boolean matches(Pattern pattern, String content) {
        if (pattern == null || content == null) {
            return false;
        }
        Matcher matcher = pattern.matcher(content);
        return matcher.find();
    }

    private boolean isBlank(String value) {
        return !StringUtils.hasText(value) || value.trim().isEmpty();
    }

    private String normalize(String value) {
        return value == null ? "" : value;
    }

    private int resolveFlags(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 0;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("DOTALL".equals(normalized)) {
            return Pattern.DOTALL;
        }
        if ("MULTILINE".equals(normalized)) {
            return Pattern.MULTILINE;
        }
        if ("CASE_INSENSITIVE".equals(normalized) || "CI".equals(normalized)) {
            return Pattern.CASE_INSENSITIVE;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
