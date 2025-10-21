package com.example.codecompare.rebuild.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Category summary for incremental diff file overview.
 */
@JsonDeserialize(builder = IncrementalDiffFileCategoryView.Builder.class)
public final class IncrementalDiffFileCategoryView {

    private final String key;
    private final String label;
    private final String color;
    private final int count;

    private IncrementalDiffFileCategoryView(Builder builder) {
        this.key = builder.key;
        this.label = builder.label;
        this.color = builder.color;
        this.count = builder.count;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    public int getCount() {
        return count;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPOJOBuilder(withPrefix = "")
    public static final class Builder {
        private String key;
        private String label;
        private String color;
        private int count;

        public Builder key(@JsonProperty("key") String key) {
            this.key = key;
            return this;
        }

        public Builder label(@JsonProperty("label") String label) {
            this.label = label;
            return this;
        }

        public Builder color(@JsonProperty("color") String color) {
            this.color = color;
            return this;
        }

        public Builder count(@JsonProperty("count") int count) {
            this.count = count;
            return this;
        }

        public IncrementalDiffFileCategoryView build() {
            return new IncrementalDiffFileCategoryView(this);
        }
    }
}
