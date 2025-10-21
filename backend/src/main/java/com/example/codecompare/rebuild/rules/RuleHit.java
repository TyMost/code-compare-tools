package com.example.codecompare.rebuild.rules;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规则命中结果封装。
 */
public class RuleHit {

    private final String ruleId;
    private final String labelId;
    private final String labelName;
    private final int priority;
    private final String statusKey;
    private final String color;
    private final String category;
    private final String filterAction;
    private final Map<String, Object> metadata;

    public RuleHit(String ruleId,
                   String labelId,
                   String labelName,
                   int priority,
                   String statusKey,
                   String color,
                   String category,
                   String filterAction) {
        this(ruleId, labelId, labelName, priority, statusKey, color, category, filterAction, Collections.<String, Object>emptyMap());
    }

    public RuleHit(String ruleId,
                   String labelId,
                   String labelName,
                   int priority,
                   String statusKey,
                   String color,
                   String category,
                   String filterAction,
                   Map<String, Object> metadata) {
        this.ruleId = ruleId;
        this.labelId = labelId;
        this.labelName = labelName;
        this.priority = priority;
        this.statusKey = statusKey;
        this.color = color;
        this.category = category;
        this.filterAction = filterAction;
        this.metadata = metadata == null
                ? Collections.<String, Object>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, Object>(metadata));
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getLabelId() {
        return labelId;
    }

    public String getLabelName() {
        return labelName;
    }

    public int getPriority() {
        return priority;
    }

    public String getStatusKey() {
        return statusKey;
    }

    public String getColor() {
        return color;
    }

    public String getCategory() {
        return category;
    }

    public String getFilterAction() {
        return filterAction;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
