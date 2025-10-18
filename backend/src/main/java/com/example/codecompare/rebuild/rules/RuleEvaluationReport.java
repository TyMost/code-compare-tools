package com.example.codecompare.rebuild.rules;

import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 规则执行的聚合结果。
 */
public class RuleEvaluationReport {

    private final List<RuleHit> hits;

    public RuleEvaluationReport(List<RuleHit> hits) {
        this.hits = hits == null ? Collections.emptyList() : Collections.unmodifiableList(hits);
    }

    public List<RuleHit> getHits() {
        return hits;
    }

    public List<RuleHit> getHitsSortedByPriority() {
        return hits.stream()
                .sorted((left, right) -> {
                    int priorityCompare = Integer.compare(right.getPriority(), left.getPriority());
                    if (priorityCompare != 0) {
                        return priorityCompare;
                    }
                    return left.getRuleId().compareToIgnoreCase(right.getRuleId());
                })
                .collect(Collectors.toList());
    }

    public RuleHit getTopHit() {
        return hits.stream()
                .max((left, right) -> {
                    int priorityCompare = Integer.compare(left.getPriority(), right.getPriority());
                    if (priorityCompare != 0) {
                        return priorityCompare;
                    }
                    return right.getRuleId().compareToIgnoreCase(left.getRuleId());
                })
                .orElse(null);
    }

    public List<String> getLabelIds() {
        return hits.stream().map(RuleHit::getLabelId).collect(Collectors.toList());
    }

    public boolean hasFilterAction(String action) {
        if (!StringUtils.hasText(action)) {
            return false;
        }
        String normalized = action.trim().toLowerCase();
        return hits.stream()
                .map(RuleHit::getFilterAction)
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toLowerCase())
                .anyMatch(normalized::equals);
    }

    public boolean isEmpty() {
        return hits.isEmpty();
    }
}
