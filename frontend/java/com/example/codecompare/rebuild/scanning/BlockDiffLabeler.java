package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.rules.RuleEvaluationFacade;
import com.example.codecompare.rebuild.rules.RuleEvaluationReport;
import com.example.codecompare.rebuild.rules.RuleHit;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies rule-driven labels onto line-level diff segments，基于规则优先级输出标签元数据。
 */
@Component
public class BlockDiffLabeler {

    private final RuleEvaluationFacade ruleEvaluationFacade;

    public BlockDiffLabeler(RuleEvaluationFacade ruleEvaluationFacade) {
        this.ruleEvaluationFacade = ruleEvaluationFacade;
    }

    public BlockDiff label(BlockDiff diff) {
        if (diff == null) {
            return null;
        }

        RuleEvaluationReport report = ruleEvaluationFacade == null ? null : ruleEvaluationFacade.evaluate(diff);
        List<RuleHit> sortedHits = report == null ? Collections.<RuleHit>emptyList() : report.getHitsSortedByPriority();

        if (CollectionUtils.isEmpty(sortedHits)) {
            sortedHits = Collections.singletonList(createFallbackHit());
        }

        int maxPriority = sortedHits.get(0) == null ? 0 : sortedHits.get(0).getPriority();
        Map<String, BlockDiff.LabelDescriptor> descriptorByStatus = new LinkedHashMap<String, BlockDiff.LabelDescriptor>();
        boolean filteredOut = false;
        for (RuleHit hit : sortedHits) {
            if (hit == null) {
                continue;
            }
            if (hit.getPriority() < maxPriority) {
                break;
            }
            String statusKey = resolveStatusKey(hit);
            String labelName = resolveLabelName(hit);
            if (!StringUtils.hasText(statusKey)) {
                continue;
            }
            String normalizedStatusKey = statusKey.trim();
            String normalizedLabelName = labelName;
            BlockDiff.LabelDescriptor descriptor = new BlockDiff.LabelDescriptor(
                    hit.getRuleId(),
                    hit.getLabelId(),
                    normalizedLabelName,
                    normalizedStatusKey,
                    hit.getPriority(),
                    hit.getColor(),
                    hit.getCategory(),
                    hit.getFilterAction()
            );
            descriptorByStatus.put(normalizedStatusKey.toLowerCase(), descriptor);
            if (!filteredOut && StringUtils.hasText(hit.getFilterAction())) {
                filteredOut = "drop".equalsIgnoreCase(hit.getFilterAction().trim());
            }
        }

        if (descriptorByStatus.isEmpty()) {
            BlockDiff.LabelDescriptor fallback = new BlockDiff.LabelDescriptor(
                    "fallback-other",
                    "其他",
                    "其他",
                    "other",
                    0,
                    "#909399",
                    "fallback",
                    null
            );
            descriptorByStatus.put("other", fallback);
        }

        List<String> labelIds = new ArrayList<String>();
        List<String> labelNames = new ArrayList<String>();
        List<BlockDiff.LabelDescriptor> descriptors = new ArrayList<BlockDiff.LabelDescriptor>();
        for (BlockDiff.LabelDescriptor descriptor : descriptorByStatus.values()) {
            labelIds.add(descriptor.getStatusKey());
            labelNames.add(descriptor.getLabelName());
            descriptors.add(descriptor);
        }

        return BlockDiff.from(diff)
                .labelIds(labelIds)
                .labels(labelNames)
                .labelDescriptors(descriptors)
                .filteredOut(filteredOut)
                .build();
    }

    private RuleHit createFallbackHit() {
        return new RuleHit("fallback-other", "其他", "其他", 0, "other", "#909399", "fallback", null);
    }

    private String resolveStatusKey(RuleHit hit) {
        if (hit == null) {
            return null;
        }
        if (StringUtils.hasText(hit.getStatusKey())) {
            return hit.getStatusKey();
        }
        if (StringUtils.hasText(hit.getLabelId())) {
            return hit.getLabelId();
        }
        return hit.getLabelName();
    }

    private String resolveLabelName(RuleHit hit) {
        if (hit == null) {
            return null;
        }
        if (StringUtils.hasText(hit.getLabelName())) {
            return hit.getLabelName();
        }
        if (StringUtils.hasText(hit.getLabelId())) {
            return hit.getLabelId();
        }
        return hit.getStatusKey();
    }
}

