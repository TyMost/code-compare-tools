package com.example.migration.common;

public class FeatureToggleService {
    public boolean isEnabled(String key) {
        return key.startsWith("migration");
    }
}
