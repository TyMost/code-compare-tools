package com.example.codecompare.rebuild.rules.similarity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 逐行相似度分析输出结果。
 */
public final class LineSimilaritySnapshot {

    private static final LineSimilaritySnapshot EMPTY = new LineSimilaritySnapshot(100d, 0, 0, Collections.<LineComparison>emptyList());

    private final double similarityPercent;
    private final int matchedLinePairs;
    private final int totalComparisons;
    private final List<LineComparison> comparisons;

    public LineSimilaritySnapshot(double similarityPercent,
                                  int matchedLinePairs,
                                  int totalComparisons,
                                  List<LineComparison> comparisons) {
        this.similarityPercent = clamp(similarityPercent);
        this.matchedLinePairs = Math.max(0, matchedLinePairs);
        this.totalComparisons = Math.max(0, totalComparisons);
        this.comparisons = comparisons == null
                ? Collections.<LineComparison>emptyList()
                : Collections.unmodifiableList(comparisons);
    }

    public static LineSimilaritySnapshot empty() {
        return EMPTY;
    }

    public double getSimilarityPercent() {
        return similarityPercent;
    }

    public int getMatchedLinePairs() {
        return matchedLinePairs;
    }

    public int getTotalComparisons() {
        return totalComparisons;
    }

    public List<LineComparison> getComparisons() {
        return comparisons;
    }

    public Map<String, Object> toView() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("similarityPercent", similarityPercent);
        map.put("matchedLinePairs", matchedLinePairs);
        map.put("totalComparisons", totalComparisons);
        map.put("comparisons", toComparisonViews());
        return Collections.unmodifiableMap(map);
    }

    private List<Map<String, Object>> toComparisonViews() {
        if (comparisons.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> views = new java.util.ArrayList<Map<String, Object>>(comparisons.size());
        for (LineComparison comparison : comparisons) {
            views.add(comparison.toView());
        }
        return Collections.unmodifiableList(views);
    }

    private double clamp(double value) {
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
