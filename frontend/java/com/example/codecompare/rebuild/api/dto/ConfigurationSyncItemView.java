package com.example.codecompare.rebuild.api.dto;

import java.time.Instant;

/**
 * 配置刷新单项的视图模型。
 */
public class ConfigurationSyncItemView {

    private final String name;
    private final boolean success;
    private final boolean updated;
    private final String message;
    private final Instant loadedAt;
    private final String source;
    private final Integer count;

    public ConfigurationSyncItemView(String name,
                                     boolean success,
                                     boolean updated,
                                     String message,
                                     Instant loadedAt,
                                     String source,
                                     Integer count) {
        this.name = name;
        this.success = success;
        this.updated = updated;
        this.message = message;
        this.loadedAt = loadedAt;
        this.source = source;
        this.count = count;
    }

    public String getName() {
        return name;
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isUpdated() {
        return updated;
    }

    public String getMessage() {
        return message;
    }

    public Instant getLoadedAt() {
        return loadedAt;
    }

    public String getSource() {
        return source;
    }

    public Integer getCount() {
        return count;
    }
}

