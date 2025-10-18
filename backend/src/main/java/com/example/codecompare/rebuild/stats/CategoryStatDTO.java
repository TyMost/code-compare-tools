package com.example.codecompare.rebuild.stats;

/**
 * 分类占比 DTO。
 */
public final class CategoryStatDTO {

    private final String key;
    private final String label;
    private final long lineCount;
    private final double ratio;
    private final String color;

    public CategoryStatDTO(String key, String label, long lineCount, double ratio, String color) {
        this.key = key;
        this.label = label;
        this.lineCount = lineCount;
        this.ratio = ratio;
        this.color = color;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public long getLineCount() {
        return lineCount;
    }

    public double getRatio() {
        return ratio;
    }

    public String getColor() {
        return color;
    }
}
