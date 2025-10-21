package com.example.codecompare.rebuild.diff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * diff 模块配置。
 */
@ConfigurationProperties(prefix = "diff")
public class DiffConfigurationProperties {

    /**
     * 单个文件允许的最大字节数，默认为 1 MB。
     */
    private long maxFileSize = 1_048_576L;

    /**
     * （预留）装配时附加的上下文行数。
     */
    private int contextLines = 0;

    /**
     * 跳过 diff 的文件路径模式。
     */
    private List<String> ignorePatterns = new ArrayList<>();

    /**
     * diff 引擎类型：default / git。
     */
    private String engineType = "default";

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public int getContextLines() {
        return contextLines;
    }

    public void setContextLines(int contextLines) {
        this.contextLines = contextLines;
    }

    public List<String> getIgnorePatterns() {
        return ignorePatterns;
    }

    public void setIgnorePatterns(List<String> ignorePatterns) {
        this.ignorePatterns = ignorePatterns;
    }

    public String getEngineType() {
        return engineType;
    }

    public void setEngineType(String engineType) {
        this.engineType = engineType == null || engineType.trim().isEmpty()
                ? "default"
                : engineType.trim();
    }
}
