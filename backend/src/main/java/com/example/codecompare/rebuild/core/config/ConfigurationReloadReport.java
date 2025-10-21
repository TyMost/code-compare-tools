package com.example.codecompare.rebuild.core.config;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 描述一次配置刷新流程的执行结果。
 */
public class ConfigurationReloadReport {

    private final Instant triggeredAt;
    private final Instant completedAt;
    private final boolean success;
    private final List<Item> items;

    public ConfigurationReloadReport(Instant triggeredAt,
                                     Instant completedAt,
                                     boolean success,
                                     List<Item> items) {
        this.triggeredAt = triggeredAt;
        this.completedAt = completedAt;
        this.success = success;
        List<Item> copy = items == null ? Collections.<Item>emptyList() : new ArrayList<Item>(items);
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

    public List<Item> getItems() {
        return items;
    }

    public static final class Item {
        private final String name;
        private final boolean success;
        private final boolean updated;
        private final String message;
        private final Instant loadedAt;
        private final String source;
        private final Integer count;

        private Item(String name,
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

        public static Item success(String name,
                                   boolean updated,
                                   String message,
                                   Instant loadedAt,
                                   String source,
                                   Integer count) {
            return new Item(
                    name,
                    true,
                    updated,
                    sanitizeMessage(message),
                    loadedAt,
                    source,
                    count
            );
        }

        public static Item failure(String name, String message) {
            return new Item(
                    name,
                    false,
                    false,
                    sanitizeMessage(message),
                    null,
                    null,
                    null
            );
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

        private static String sanitizeMessage(String message) {
            String value = message;
            if (!StringUtils.hasText(value)) {
                return "";
            }
            return value.trim();
        }
    }

    private static final class StringUtils {
        private static boolean hasText(String value) {
            if (value == null) {
                return false;
            }
            int length = value.length();
            for (int i = 0; i < length; i++) {
                if (!Character.isWhitespace(value.charAt(i))) {
                    return true;
                }
            }
            return false;
        }
    }
}
