package com.example.codecompare.rebuild.block.model;

import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * 描述一次比对中的单侧代码快照，包含语言、路径与源码。
 */
public class CodeSnapshot {

    private final String language;
    private final String path;
    private final String content;

    private CodeSnapshot(Builder builder) {
        this.language = StringUtils.hasText(builder.language) ? builder.language : "plain";
        this.path = builder.path == null ? "" : builder.path;
        this.content = builder.content == null ? "" : builder.content;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getLanguage() {
        return language;
    }

    public String getPath() {
        return path;
    }

    public String getContent() {
        return content;
    }

    public static CodeSnapshot of(String language, String path, String content) {
        return builder().language(language).path(path).content(content).build();
    }

    @Override
    public String toString() {
        return "CodeSnapshot{" +
                "language='" + language + '\'' +
                ", path='" + path + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CodeSnapshot)) {
            return false;
        }
        CodeSnapshot that = (CodeSnapshot) o;
        return Objects.equals(language, that.language)
                && Objects.equals(path, that.path)
                && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(language, path, content);
    }

    public static final class Builder {
        private String language;
        private String path;
        private String content;

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder path(String path) {
            this.path = path;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public CodeSnapshot build() {
            return new CodeSnapshot(this);
        }
    }
}
