package com.example.codecompare.rebuild.stats;

/**
 * 分类筛选项 DTO。
 */
public final class CategoryFilterDTO {

    private final String value;
    private final String label;
    private final String color;

    public CategoryFilterDTO(String value, String label, String color) {
        this.value = value;
        this.label = label;
        this.color = color;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }
}
