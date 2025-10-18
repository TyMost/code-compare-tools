package com.example.codecompare.rebuild.rules.strategy;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.model.DiffMetrics;
import com.example.codecompare.rebuild.diff.model.DiffSegmentType;
import com.example.codecompare.rebuild.rules.RuleDefinition;
import com.example.codecompare.rebuild.rules.RuleHit;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 规则类型 {@code presence}：根据差异块在源/目标两侧是否存在来打标签，例如判断“目标新增”场景。
 */
public class PresenceRuleStrategy extends AbstractRuleStrategy {

    private static final String PARAM_REQUIRE_EXISTS_IN_A = "requireExistsInA";
    private static final String PARAM_REQUIRE_EXISTS_IN_B = "requireExistsInB";
    private static final String PARAM_MATCH_TYPES = "matchTypes";

    public PresenceRuleStrategy() {
        super("presence");
    }

    @Override
    public Optional<RuleHit> evaluate(BlockDiff diff, RuleDefinition definition) {
        if (diff == null) {
            return empty();
        }
        DiffMetrics metrics = diff.getDiffMetrics();
        if (metrics == null) {
            metrics = DiffMetrics.empty();
        }

        if (!matchesExistence(definition, PARAM_REQUIRE_EXISTS_IN_A, metrics.isExistsInA())) {
            return empty();
        }
        if (!matchesExistence(definition, PARAM_REQUIRE_EXISTS_IN_B, metrics.isExistsInB())) {
            return empty();
        }
        if (!matchesSegmentType(definition, diff.getType())) {
            return empty();
        }

        String labelId = definition.getStringParam("label").orElse(definition.getId());
        String labelName = definition.getDisplayName();
        RuleHit hit = toHit(definition, labelId, labelName);
        return Optional.of(hit);
    }

    private boolean matchesExistence(RuleDefinition definition, String key, boolean actual) {
        Object raw = definition.getParams().get(key);
        if (raw == null) {
            return true;
        }
        Boolean expected = convertToBoolean(raw);
        if (expected == null) {
            return true;
        }
        return actual == expected.booleanValue();
    }

    private Boolean convertToBoolean(Object raw) {
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        String text = raw.toString();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "yes".equals(normalized) || "1".equals(normalized)) {
            return Boolean.TRUE;
        }
        if ("false".equals(normalized) || "no".equals(normalized) || "0".equals(normalized)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private boolean matchesSegmentType(RuleDefinition definition, DiffSegmentType type) {
        if (type == null) {
            return false;
        }
        Object raw = definition.getParams().get(PARAM_MATCH_TYPES);
        if (raw == null) {
            return true;
        }
        List<String> values = normalizeToStringList(raw);
        if (CollectionUtils.isEmpty(values)) {
            return true;
        }
        String actual = type.name();
        for (String candidate : values) {
            if (StringUtils.hasText(candidate) && actual.equalsIgnoreCase(candidate.trim())) {
                return true;
            }
        }
        return false;
    }

    private List<String> normalizeToStringList(Object raw) {
        if (raw instanceof Collection) {
            Collection<?> collection = (Collection<?>) raw;
            List<String> result = new ArrayList<String>(collection.size());
            for (Object item : collection) {
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }
        String text = raw.toString();
        if (!StringUtils.hasText(text)) {
            return new ArrayList<String>(0);
        }
        String[] items = text.split(",");
        List<String> result = new ArrayList<String>(items.length);
        for (String item : items) {
            result.add(item);
        }
        return result;
    }
}
