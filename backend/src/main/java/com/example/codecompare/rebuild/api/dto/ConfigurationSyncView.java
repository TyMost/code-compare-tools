package com.example.codecompare.rebuild.api.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 描述一次配置刷新结果的视图模型。
 */
public class ConfigurationSyncView {

    private final Instant triggeredAt;
    private final Instant completedAt;
    private final boolean success;
    private final List<ConfigurationSyncItemView> items;

    public ConfigurationSyncView(Instant triggeredAt,
                                 Instant completedAt,
                                 boolean success,
                                 List<ConfigurationSyncItemView> items) {
        this.triggeredAt = triggeredAt;
        this.completedAt = completedAt;
        this.success = success;
        List<ConfigurationSyncItemView> copy = items == null
                ? Collections.<ConfigurationSyncItemView>emptyList()
                : new ArrayList<ConfigurationSyncItemView>(items);
        this.items = Collections.unmodifiableList(copy);
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public boolean isSuccess() {
        return success;
    }

    public List<ConfigurationSyncItemView> getItems() {
        return items;
    }
}

