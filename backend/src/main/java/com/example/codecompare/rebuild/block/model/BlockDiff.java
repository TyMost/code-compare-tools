package com.example.codecompare.rebuild.block.model;

import com.example.codecompare.rebuild.diff.model.DiffMetrics;
import com.example.codecompare.rebuild.diff.model.DiffSegmentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行级差异片段模型，兼容历史 BlockDiff 命名，增加行号、操作类型等字段。
 */
@JsonDeserialize(builder = BlockDiff.Builder.class)
public final class BlockDiff {

    private final DiffSegmentType type;
    private final int sourceStartLine;
    private final int targetStartLine;
    private final int changedLineCount;
    private final int replacements;
    private final double similarityScore;
    private final List<String> labelIds;
    private final List<String> labels;
    private final List<LabelDescriptor> labelDescriptors;
    private final boolean filteredOut;
    private final DiffMetrics diffMetrics;
    private final List<String> sourceLines;
    private final List<String> targetLines;
    private final String sourceContent;
    private final String targetContent;
    private final Map<String, Object> metadata;

    private BlockDiff(Builder builder) {
        this.type = builder.type == null ? DiffSegmentType.CHANGE : builder.type;
        this.sourceStartLine = Math.max(1, builder.sourceStartLine);
        this.targetStartLine = Math.max(1, builder.targetStartLine);
        this.changedLineCount = builder.changedLineCount > 0
                ? builder.changedLineCount
                : Math.max(builder.sourceLines == null ? 0 : builder.sourceLines.size(),
                builder.targetLines == null ? 0 : builder.targetLines.size());
        this.replacements = Math.max(0, builder.replacements);
        this.similarityScore = builder.similarityScore;
        this.labelIds = toUnmodifiable(builder.labelIds);
        this.labels = toUnmodifiable(builder.labels);
        this.labelDescriptors = toUnmodifiableDescriptors(builder.labelDescriptors);
        this.filteredOut = builder.filteredOut;
        this.sourceLines = toUnmodifiable(builder.sourceLines);
        this.targetLines = toUnmodifiable(builder.targetLines);
        this.sourceContent = determineContent(builder.sourceContent, sourceLines);
        this.targetContent = determineContent(builder.targetContent, targetLines);
        this.diffMetrics = builder.diffMetrics != null
                ? builder.diffMetrics
                : DiffMetrics.of(sourceContent, targetContent, builder.similarityScore);
        this.metadata = toUnmodifiableMap(builder.metadata);
    }

    private static String determineContent(String provided, List<String> lines) {
        if (StringUtils.hasText(provided)) {
            return normalizeContent(provided);
        }
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        return normalizeContent(String.join("\n", lines));
    }

    private static String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("\r\n", "\n");
    }

    private static List<String> toUnmodifiable(List<String> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static List<LabelDescriptor> toUnmodifiableDescriptors(List<LabelDescriptor> values) {
        if (values == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder from(BlockDiff diff) {
        return new Builder()
                .type(diff.type)
                .sourceStartLine(diff.sourceStartLine)
                .targetStartLine(diff.targetStartLine)
                .changedLineCount(diff.changedLineCount)
                .replacements(diff.replacements)
                .similarityScore(diff.similarityScore)
                .labelIds(diff.labelIds)
                .labels(diff.labels)
                .labelDescriptors(diff.labelDescriptors)
                .filteredOut(diff.filteredOut)
                .diffMetrics(diff.diffMetrics)
                .sourceLines(diff.sourceLines)
                .targetLines(diff.targetLines)
                .sourceContent(diff.sourceContent)
                .targetContent(diff.targetContent)
                .metadata(diff.metadata);
    }

    public DiffSegmentType getType() {
        return type;
    }

    public int getSourceStartLine() {
        return sourceStartLine;
    }

    public int getTargetStartLine() {
        return targetStartLine;
    }

    public int getChangedLineCount() {
        return changedLineCount;
    }

    public int getReplacements() {
        return replacements;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public List<String> getLabelIds() {
        return labelIds;
    }

    public List<String> getLabels() {
        return labels;
    }

    public List<LabelDescriptor> getLabelDescriptors() {
        return labelDescriptors;
    }

    public boolean isFilteredOut() {
        return filteredOut;
    }

    public LabelDescriptor getPrimaryLabel() {
        return labelDescriptors.isEmpty() ? null : labelDescriptors.get(0);
    }

    public DiffMetrics getDiffMetrics() {
        return diffMetrics;
    }

    public List<String> getSourceLines() {
        return sourceLines;
    }

    public List<String> getTargetLines() {
        return targetLines;
    }

    public String getSourceContent() {
        return sourceContent;
    }

    public String getTargetContent() {
        return targetContent;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public BlockDiff withLabels(List<String> newLabelIds, List<String> newLabels) {
        return BlockDiff.from(this)
                .labelIds(newLabelIds)
                .labels(newLabels)
                .labelDescriptors(labelDescriptors)
                .build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private DiffSegmentType type;
        private int sourceStartLine = 1;
        private int targetStartLine = 1;
        private int changedLineCount;
        private int replacements;
        private double similarityScore;
        private List<String> labelIds;
        private List<String> labels;
        private List<LabelDescriptor> labelDescriptors;
        private boolean filteredOut;
        private DiffMetrics diffMetrics;
        private List<String> sourceLines;
        private List<String> targetLines;
        private String sourceContent;
        private String targetContent;
        private Map<String, Object> metadata = Collections.emptyMap();

        public Builder() {
        }

        public Builder type(@JsonProperty("type") DiffSegmentType type) {
            this.type = type;
            return this;
        }

        public Builder sourceStartLine(@JsonProperty("sourceStartLine") int sourceStartLine) {
            this.sourceStartLine = sourceStartLine;
            return this;
        }

        public Builder targetStartLine(@JsonProperty("targetStartLine") int targetStartLine) {
            this.targetStartLine = targetStartLine;
            return this;
        }

        public Builder changedLineCount(@JsonProperty("changedLineCount") int changedLineCount) {
            this.changedLineCount = changedLineCount;
            return this;
        }

        public Builder replacements(@JsonProperty("replacements") int replacements) {
            this.replacements = replacements;
            return this;
        }

        public Builder similarityScore(@JsonProperty("similarityScore") double similarityScore) {
            this.similarityScore = similarityScore;
            return this;
        }

        public Builder labelIds(@JsonProperty("labelIds") List<String> labelIds) {
            this.labelIds = labelIds;
            return this;
        }

        public Builder labels(@JsonProperty("labels") List<String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder labelDescriptors(@JsonProperty("labelDescriptors") List<LabelDescriptor> labelDescriptors) {
            this.labelDescriptors = labelDescriptors;
            return this;
        }

        public Builder primaryLabel(@JsonProperty("primaryLabel") LabelDescriptor primaryLabel) {
            if (primaryLabel == null) {
                return this;
            }
            if (this.labelDescriptors == null || this.labelDescriptors.isEmpty()) {
                List<LabelDescriptor> descriptors = new ArrayList<>();
                descriptors.add(primaryLabel);
                this.labelDescriptors = descriptors;
            }
            return this;
        }

        public Builder filteredOut(@JsonProperty("filteredOut") boolean filteredOut) {
            this.filteredOut = filteredOut;
            return this;
        }

        public Builder diffMetrics(@JsonProperty("diffMetrics") DiffMetrics diffMetrics) {
            this.diffMetrics = diffMetrics;
            return this;
        }

        public Builder sourceLines(@JsonProperty("sourceLines") List<String> sourceLines) {
            this.sourceLines = sourceLines;
            return this;
        }

        public Builder targetLines(@JsonProperty("targetLines") List<String> targetLines) {
            this.targetLines = targetLines;
            return this;
        }

        public Builder sourceContent(@JsonProperty("sourceContent") String sourceContent) {
            this.sourceContent = sourceContent;
            return this;
        }

        public Builder targetContent(@JsonProperty("targetContent") String targetContent) {
            this.targetContent = targetContent;
            return this;
        }

        public Builder metadata(@JsonProperty("metadata") Map<String, ?> metadata) {
            if (metadata == null || metadata.isEmpty()) {
                this.metadata = Collections.emptyMap();
            } else {
                this.metadata = new LinkedHashMap<>(metadata);
            }
            return this;
        }

        public BlockDiff build() {
            return new BlockDiff(this);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class LabelDescriptor {
        private final String ruleId;
        private final String labelId;
        private final String labelName;
        private final String statusKey;
        private final int priority;
        private final String color;
        private final String category;
        private final String filterAction;

        public LabelDescriptor(String ruleId,
                               String labelId,
                               String labelName,
                               String statusKey,
                               int priority,
                               String color,
                               String category,
                               String filterAction) {
            this.ruleId = ruleId;
            this.labelId = labelId;
            this.labelName = labelName;
            this.statusKey = statusKey;
            this.priority = priority;
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

        public String getStatusKey() {
            return statusKey;
        }

        public int getPriority() {
            return priority;
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

    private Map<String, Object> toUnmodifiableMap(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
