package com.example.codecompare.rebuild.agent;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * Agent 执行上下文，封装项目与代码块的定位信息。
 */
public final class AgentTaskContext {

    private final String projectKey;
    private final String blockId;
    private final String filePath;

    private AgentTaskContext(Builder builder) {
        this.projectKey = builder.projectKey;
        this.blockId = builder.blockId;
        this.filePath = builder.filePath;
    }

    public String getProjectKey() {
        return projectKey;
    }

    public String getBlockId() {
        return blockId;
    }

    public String getFilePath() {
        return filePath;
    }

    /**
     * 便捷构造入口，保证必要字段有效。
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AgentTaskContext)) {
            return false;
        }
        AgentTaskContext that = (AgentTaskContext) o;
        return Objects.equals(projectKey, that.projectKey)
                && Objects.equals(blockId, that.blockId)
                && Objects.equals(filePath, that.filePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectKey, blockId, filePath);
    }

    @Override
    public String toString() {
        return "AgentTaskContext{" +
                "projectKey='" + projectKey + '\'' +
                ", blockId='" + blockId + '\'' +
                ", filePath='" + filePath + '\'' +
                '}';
    }

    public static final class Builder {
        private String projectKey;
        private String blockId;
        private String filePath;

        private Builder() {
        }

        public Builder projectKey(String projectKey) {
            this.projectKey = projectKey;
            return this;
        }

        public Builder blockId(String blockId) {
            this.blockId = blockId;
            return this;
        }

        public Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        public AgentTaskContext build() {
            Assert.isTrue(StringUtils.hasText(blockId), "blockId 不能为空");
            return new AgentTaskContext(this);
        }
    }
}
