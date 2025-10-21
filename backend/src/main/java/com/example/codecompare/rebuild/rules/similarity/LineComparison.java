package com.example.codecompare.rebuild.rules.similarity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 记录单行对齐结果及其相似度。
 */
public final class LineComparison {

    public enum ComparisonType {
        MATCH(true),
        CHANGE(true),
        INSERT(false),
        DELETE(false);

        private final boolean paired;

        ComparisonType(boolean paired) {
            this.paired = paired;
        }

        public boolean isPaired() {
            return paired;
        }
    }

    private final ComparisonType type;
    private final int sourceLine;
    private final int targetLine;
    private final String sourceContent;
    private final String targetContent;
    private final double similarity;

    private LineComparison(ComparisonType type,
                           int sourceLine,
                           int targetLine,
                           String sourceContent,
                           String targetContent,
                           double similarity) {
        this.type = type;
        this.sourceLine = sourceLine;
        this.targetLine = targetLine;
        this.sourceContent = sourceContent;
        this.targetContent = targetContent;
        this.similarity = similarity;
    }

    public static LineComparison match(int sourceLine, int targetLine, double similarity) {
        return new LineComparison(ComparisonType.MATCH, sourceLine, targetLine, null, null, clamp(similarity));
    }

    public static LineComparison change(int sourceLine,
                                        String sourceContent,
                                        int targetLine,
                                        String targetContent,
                                        double similarity) {
        return new LineComparison(ComparisonType.CHANGE,
                sourceLine, targetLine, sourceContent, targetContent, clamp(similarity));
    }

    public static LineComparison delete(int sourceLine, String sourceContent) {
        return new LineComparison(ComparisonType.DELETE, sourceLine, -1, sourceContent, null, 0d);
    }

    public static LineComparison insert(int targetLine, String targetContent) {
        return new LineComparison(ComparisonType.INSERT, -1, targetLine, null, targetContent, 0d);
    }

    public ComparisonType getType() {
        return type;
    }

    public int getSourceLine() {
        return sourceLine;
    }

    public int getTargetLine() {
        return targetLine;
    }

    public String getSourceContent() {
        return sourceContent;
    }

    public String getTargetContent() {
        return targetContent;
    }

    public double getSimilarity() {
        return similarity;
    }

    public Map<String, Object> toView() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("type", type.name());
        if (sourceLine > 0) {
            map.put("sourceLine", sourceLine);
        }
        if (targetLine > 0) {
            map.put("targetLine", targetLine);
        }
        if (sourceContent != null) {
            map.put("sourceContent", sourceContent);
        }
        if (targetContent != null) {
            map.put("targetContent", targetContent);
        }
        map.put("similarity", similarity);
        return Collections.unmodifiableMap(map);
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0d;
        }
        if (value < 0d) {
            return 0d;
        }
        if (value > 100d) {
            return 100d;
        }
        return value;
    }
}
