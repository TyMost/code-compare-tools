package com.example.codecompare.rebuild.diff.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shared utilities for computing diff segment similarity metrics.
 */
public final class DiffSimilarityCalculator {

    private static final double LINE_SIMILARITY_WEIGHT = 0.6d;
    private static final double TOKEN_SIMILARITY_WEIGHT = 0.4d;
    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[^\\p{L}\\p{N}_]+");

    private DiffSimilarityCalculator() {
    }

    public static int computeReplacements(List<String> sourceLines, List<String> targetLines) {
        int min = Math.min(size(sourceLines), size(targetLines));
        int replacements = 0;
        for (int i = 0; i < min; i++) {
            String src = sourceLines.get(i);
            String tgt = targetLines.get(i);
            if (!equalsIgnoreLineEnding(src, tgt)) {
                replacements++;
            }
        }
        return replacements;
    }

    public static double computeSimilarity(List<String> sourceLines, List<String> targetLines) {
        double lineSimilarity = computeLineSimilarity(sourceLines, targetLines);
        double tokenSimilarity = computeTokenSimilarity(sourceLines, targetLines);
        double combined = (lineSimilarity * LINE_SIMILARITY_WEIGHT) + (tokenSimilarity * TOKEN_SIMILARITY_WEIGHT);
        return combined * 100d;
    }

    public static boolean equalsIgnoreLineEnding(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return normalize(left).equals(normalize(right));
    }

    private static double computeLineSimilarity(List<String> sourceLines, List<String> targetLines) {
        int max = Math.max(size(sourceLines), size(targetLines));
        if (max == 0) {
            return 1d;
        }
        int equal = 0;
        int min = Math.min(size(sourceLines), size(targetLines));
        for (int i = 0; i < min; i++) {
            if (equalsIgnoreLineEnding(sourceLines.get(i), targetLines.get(i))) {
                equal++;
            }
        }
        return (double) equal / (double) max;
    }

    private static double computeTokenSimilarity(List<String> sourceLines, List<String> targetLines) {
        TokenStats source = tokenize(sourceLines);
        TokenStats target = tokenize(targetLines);
        if (source.totalTokens == 0 && target.totalTokens == 0) {
            return 1d;
        }
        if (source.totalTokens == 0 || target.totalTokens == 0) {
            return 0d;
        }
        int intersection = 0;
        for (Map.Entry<String, Integer> entry : source.frequencies.entrySet()) {
            int overlap = Math.min(entry.getValue(), target.frequencies.getOrDefault(entry.getKey(), 0));
            if (overlap > 0) {
                intersection += overlap;
            }
        }
        return (2d * intersection) / (double) (source.totalTokens + target.totalTokens);
    }

    private static TokenStats tokenize(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return TokenStats.EMPTY;
        }
        Map<String, Integer> frequencies = new HashMap<>();
        int total = 0;
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            for (String token : TOKEN_SPLITTER.split(line)) {
                String normalized = normalizeToken(token);
                if (normalized.isEmpty()) {
                    continue;
                }
                frequencies.merge(normalized, 1, Integer::sum);
                total++;
            }
        }
        if (frequencies.isEmpty()) {
            return TokenStats.EMPTY;
        }
        return new TokenStats(frequencies, total);
    }

    private static String normalizeToken(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n");
    }

    private static int size(List<String> values) {
        return values == null ? 0 : values.size();
    }

    private static final class TokenStats {
        private static final TokenStats EMPTY = new TokenStats(Collections.emptyMap(), 0);
        private final Map<String, Integer> frequencies;
        private final int totalTokens;

        private TokenStats(Map<String, Integer> frequencies, int totalTokens) {
            this.frequencies = frequencies;
            this.totalTokens = totalTokens;
        }
    }
}
