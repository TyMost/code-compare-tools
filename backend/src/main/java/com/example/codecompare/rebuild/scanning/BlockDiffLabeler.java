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
        Map<String, Object> ruleMetadata = new LinkedHashMap<String, Object>();
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
            String mapKey = normalizedStatusKey.toLowerCase();
            BlockDiff.LabelDescriptor existing = descriptorByStatus.get(mapKey);
            if (existing == null || descriptor.getPriority() >= existing.getPriority()) {
                descriptorByStatus.put(mapKey, descriptor);
            }
            if (!filteredOut && StringUtils.hasText(hit.getFilterAction())) {
                filteredOut = "drop".equalsIgnoreCase(hit.getFilterAction().trim());
            }
            if (!CollectionUtils.isEmpty(hit.getMetadata())) {
                ruleMetadata.put(normalizedStatusKey, hit.getMetadata());
            }
        }

        if (descriptorByStatus.isEmpty()) {
            BlockDiff.LabelDescriptor fallback = new BlockDiff.LabelDescriptor(
                    "fallback-no-rules",
                    "\u65e0\u89c4\u5219\u547d\u4e2d",
                    "\u65e0\u89c4\u5219\u547d\u4e2d",
                    BlockLabelConstants.STATUS_NO_RULES,
                    0,
                    "#909399",
                    "fallback",
                    null
            );
            descriptorByStatus.put(BlockLabelConstants.STATUS_NO_RULES, fallback);
        }

        List<String> labelIds = new ArrayList<String>();
        List<String> labelNames = new ArrayList<String>();
        List<BlockDiff.LabelDescriptor> descriptors = new ArrayList<BlockDiff.LabelDescriptor>();
        for (BlockDiff.LabelDescriptor descriptor : descriptorByStatus.values()) {
            labelIds.add(descriptor.getStatusKey());
            labelNames.add(descriptor.getLabelName());
            descriptors.add(descriptor);
        }

        Map<String, Object> metadata = diff.getMetadata() == null
                ? new LinkedHashMap<String, Object>()
                : new LinkedHashMap<String, Object>(diff.getMetadata());
        if (!ruleMetadata.isEmpty()) {
            Map<String, Object> existing = new LinkedHashMap<String, Object>();
            Object existingValue = metadata.get("ruleMetadata");
            if (existingValue instanceof Map) {
                Map<?, ?> rawMap = (Map<?, ?>) existingValue;
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    Object key = entry.getKey();
                    if (key != null) {
                        existing.put(key.toString(), entry.getValue());
                    }
                }
            }
            existing.putAll(ruleMetadata);
            metadata.put("ruleMetadata", existing);
        }

        return BlockDiff.from(diff)
                .labelIds(labelIds)
                .labels(labelNames)
                .labelDescriptors(descriptors)
                .metadata(metadata)
                .filteredOut(filteredOut)
                .build();
    }

    private RuleHit createFallbackHit() {
        return new RuleHit("fallback-no-rules",
                BlockLabelConstants.STATUS_NO_RULES,
                "\u65e0\u89c4\u5219\u547d\u4e2d",
                0,
                BlockLabelConstants.STATUS_NO_RULES,
                "#909399",
                "fallback",
                null);
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

