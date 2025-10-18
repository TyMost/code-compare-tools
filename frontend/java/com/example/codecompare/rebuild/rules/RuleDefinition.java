package com.example.codecompare.rebuild.rules;

import org.springframework.util.StringUtils;

import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 单条规则定义，对应配置文件中的一项。
 */
public class RuleDefinition {

    private final String id;
    private final String type;
    private final String displayName;
    private final Map<String, Object> params;

    public RuleDefinition(String id, String type, String displayName, Map<String, Object> params) {
        this.id = id;
        this.type = type == null ? "" : type.toLowerCase(Locale.ROOT);
        this.displayName = StringUtils.hasText(displayName) ? displayName : id;
        this.params = params == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(params));
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    public Optional<String> getStringParam(String key) {
        Object value = params.get(key);
        if (value == null) {
            return Optional.empty();
        }
        String text = value.toString();
        return StringUtils.hasText(text) ? Optional.of(text) : Optional.empty();
    }

    public String getStringParam(String key, String defaultValue) {
        return getStringParam(key).orElse(defaultValue);
    }

    public Optional<Integer> getIntParam(String key) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return Optional.of(((Number) value).intValue());
        }
        if (value != null) {
            try {
                return Optional.of(Integer.parseInt(value.toString()));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public int getIntParam(String key, int defaultValue) {
        return getIntParam(key).orElse(defaultValue);
    }

    public Optional<Double> getDoubleParam(String key) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return Optional.of(((Number) value).doubleValue());
        }
        if (value != null) {
            try {
                return Optional.of(Double.parseDouble(value.toString()));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public double getDoubleParam(String key, double defaultValue) {
        return getDoubleParam(key).orElse(defaultValue);
    }

    public int getPriority() {
        return getIntParam("priority", 0);
    }

    public String getResolvedLabelId() {
        return getStringParam("label").orElse(id);
    }

    public String getResolvedDisplayName() {
        return StringUtils.hasText(displayName) ? displayName : getResolvedLabelId();
    }

    public String getResolvedStatusKey() {
        String statusKey = getStringParam("statusKey").orElse(null);
        if (StringUtils.hasText(statusKey)) {
            return statusKey.trim();
        }
        if (StringUtils.hasText(id)) {
            return id.trim();
        }
        String labelId = getResolvedLabelId();
        if (StringUtils.hasText(labelId)) {
            return labelId.trim();
        }
        return id;
    }

    public String getResolvedCategory() {
        String category = getStringParam("category").orElse(null);
        if (StringUtils.hasText(category)) {
            return category.trim();
        }
        return type;
    }

    public String getResolvedColor() {
        String color = getStringParam("color").orElse(null);
        if (StringUtils.hasText(color)) {
            return color.trim();
        }
        return null;
    }

    public String getResolvedFilterAction() {
        String filterAction = getStringParam("filterAction").orElse(null);
        if (StringUtils.hasText(filterAction)) {
            return filterAction.trim();
        }
        return null;
    }
}
