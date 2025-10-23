package com.example.demo.analytics;

import java.util.Map;

public class TrendAnalyzer {

    public double averageThroughput(Map<String, Integer> samples) {
        if (samples.isEmpty()) {
            return 0d;
        }
        int total = samples.values().stream().mapToInt(Integer::intValue).sum();
        return total / (double) samples.size();
    }
}
