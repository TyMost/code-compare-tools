package com.example.migratediff.shared.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CoverageUtils {

    private CoverageUtils() {
    }

    public static double jaccardSimilarity(List<String> sourceTokens, List<String> targetTokens) {
        if (sourceTokens == null || targetTokens == null) {
            return 0D;
        }
        Set<String> sourceSet = new HashSet<>(sourceTokens);
        Set<String> targetSet = new HashSet<>(targetTokens);
        if (sourceSet.isEmpty() && targetSet.isEmpty()) {
            return 1D;
        }
        Set<String> intersection = new HashSet<>(sourceSet);
        intersection.retainAll(targetSet);
        Set<String> union = new HashSet<>(sourceSet);
        union.addAll(targetSet);
        return union.isEmpty() ? 1D : (double) intersection.size() / union.size();
    }

    public static int levenshteinDistance(String source, String target) {
        String normalizedSource = source == null ? "" : source;
        String normalizedTarget = target == null ? "" : target;

        int sourceLength = normalizedSource.length();
        int targetLength = normalizedTarget.length();

        int[][] dp = new int[sourceLength + 1][targetLength + 1];

        for (int i = 0; i <= sourceLength; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= targetLength; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= sourceLength; i++) {
            for (int j = 1; j <= targetLength; j++) {
                int cost = normalizedSource.charAt(i - 1) == normalizedTarget.charAt(j - 1) ? 0 : 1;
                int deletion = dp[i - 1][j] + 1;
                int insertion = dp[i][j - 1] + 1;
                int substitution = dp[i - 1][j - 1] + cost;
                dp[i][j] = Math.min(Math.min(deletion, insertion), substitution);
            }
        }

        return dp[sourceLength][targetLength];
    }

    public static List<String> tokenize(String content) {
        if (isBlank(content)) {
            return new ArrayList<>();
        }
        String[] tokens = content.trim().split("\\s+");
        List<String> result = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            if (!isBlank(token)) {
                result.add(token);
            }
        }
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
