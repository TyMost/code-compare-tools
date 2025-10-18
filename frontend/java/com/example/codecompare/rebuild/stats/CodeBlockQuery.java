package com.example.codecompare.rebuild.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 代码块列表查询参数封装。
 */
public final class CodeBlockQuery {

    private final String projectKey;
    private final String comparisonId;
    private final List<String> categories;
    private final List<String> excludedCategories;
    private final String filePath;
    private final String fileName;
    private final int page;
    private final int size;

    private CodeBlockQuery(Builder builder) {
        this.projectKey = builder.projectKey;
        this.comparisonId = builder.comparisonId;
        this.categories = Collections.unmodifiableList(new ArrayList<>(builder.categories));
        this.excludedCategories = Collections.unmodifiableList(new ArrayList<>(builder.excludedCategories));
        this.filePath = builder.filePath;
        this.fileName = builder.fileName;
        this.page = Math.max(1, builder.page);
        this.size = Math.max(1, builder.size);
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getComparisonId() {
        return comparisonId;
    }

    public List<String> getCategories() {
        return categories;
    }

    public List<String> getExcludedCategories() {
        return excludedCategories;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder().from(this);
    }

    public static final class Builder {
        private String projectKey;
        private String comparisonId;
        private final List<String> categories = new ArrayList<>();
        private final List<String> excludedCategories = new ArrayList<>();
        private String filePath;
        private String fileName;
        private int page = 1;
        private int size = 20;

        private Builder() {
        }

        private Builder from(CodeBlockQuery query) {
            Objects.requireNonNull(query, "query must not be null");
            this.projectKey = query.projectKey;
            this.comparisonId = query.comparisonId;
            this.categories.addAll(query.categories);
            this.excludedCategories.addAll(query.excludedCategories);
            this.filePath = query.filePath;
            this.fileName = query.fileName;
            this.page = query.page;
            this.size = query.size;
            return this;
        }

        public Builder projectKey(String projectKey) {
            this.projectKey = projectKey;
            return this;
        }

        public Builder comparisonId(String comparisonId) {
            this.comparisonId = comparisonId;
            return this;
        }

        public Builder categories(List<String> categories) {
            this.categories.clear();
            if (categories != null) {
                categories.stream()
                        .filter(item -> item != null && !item.trim().isEmpty())
                        .forEach(this.categories::add);
            }
            return this;
        }

        public Builder excludeCategories(List<String> categories) {
            this.excludedCategories.clear();
            if (categories != null) {
                categories.stream()
                        .filter(item -> item != null && !item.trim().isEmpty())
                        .forEach(this.excludedCategories::add);
            }
            return this;
        }

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public Builder fileName(String fileName) {
            this.fileName = fileName == null ? null : fileName.trim();
            return this;
        }

        public Builder page(int page) {
            this.page = page;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public CodeBlockQuery build() {
            return new CodeBlockQuery(this);
        }
    }
}
