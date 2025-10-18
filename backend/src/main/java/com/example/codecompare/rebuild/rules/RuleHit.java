package com.example.codecompare.rebuild.rules;

/**
 * 规则命中结果。
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

    public RuleHit(String ruleId,
                   String labelId,
                   String labelName,
                   int priority,
                   String statusKey,
                   String color,
                   String category,
                   String filterAction) {
        this.ruleId = ruleId;
        this.labelId = labelId;
        this.labelName = labelName;
        this.priority = priority;
        this.statusKey = statusKey;
        this.color = color;
        this.category = category;
        this.filterAction = filterAction;
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
}
