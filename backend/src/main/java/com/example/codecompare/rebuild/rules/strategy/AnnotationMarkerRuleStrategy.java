package com.example.codecompare.rebuild.rules.strategy;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.rules.RuleDefinition;
import com.example.codecompare.rebuild.rules.RuleHit;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

/**
 * Rule strategy that detects migration annotation markers within a diff block.
 */
public class AnnotationMarkerRuleStrategy extends AbstractRuleStrategy {

    private static final String DEFAULT_MODE = "both";
    private static final String DEFAULT_START_MARKER = "/** 迁移生成的代码片段开始 */";
    private static final String DEFAULT_END_MARKER = "/** 迁移生成的代码片段结束 */";

    public AnnotationMarkerRuleStrategy() {
        super("annotation-marker");
    }

    @Override
    public Optional<RuleHit> evaluate(BlockDiff diff, RuleDefinition definition) {
        if (diff == null) {
            return empty();
        }
        String normalizedTarget = collapseWhitespace(diff.getTargetContent());
        if (!StringUtils.hasText(normalizedTarget)) {
            return empty();
        }
        String startMarker = collapseWhitespace(definition.getStringParam("startMarker", DEFAULT_START_MARKER));
        String endMarker = collapseWhitespace(definition.getStringParam("endMarker", DEFAULT_END_MARKER));
        String mode = definition.getStringParam("mode", DEFAULT_MODE).trim().toLowerCase(Locale.ROOT);

        boolean hasStart = matchesStart(normalizedTarget, startMarker);
        boolean hasEnd = matchesEnd(normalizedTarget, endMarker);

        boolean matched;
        if ("start".equals(mode)) {
            matched = hasStart;
        } else if ("end".equals(mode)) {
            matched = hasEnd;
        } else if ("any".equals(mode)) {
            matched = hasStart || hasEnd;
        } else {
            matched = hasStart && hasEnd;
        }

        if (!matched) {
            return empty();
        }

        String labelId = definition.getStringParam("label").orElse(definition.getId());
        String labelName = definition.getDisplayName();
        RuleHit hit = toHit(definition, labelId, labelName);
        return Optional.of(hit);
    }

    private boolean matchesStart(String content, String marker) {
        return StringUtils.hasText(marker) && content.startsWith(marker);
    }

    private boolean matchesEnd(String content, String marker) {
        return StringUtils.hasText(marker) && content.endsWith(marker);
    }

    private String collapseWhitespace(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        boolean previousSpace = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isWhitespace(ch)) {
                if (!previousSpace) {
                    builder.append(' ');
                    previousSpace = true;
                }
            } else {
                builder.append(ch);
                previousSpace = false;
            }
        }
        return builder.toString().trim();
    }
}
