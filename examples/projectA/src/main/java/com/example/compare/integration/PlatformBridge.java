package com.example.compare.integration;

/**
 * Helper that remains mostly unchanged between migrations.
 */
public class PlatformBridge {

    public String bridge(String payload) {
        return payload == null ? "offline" : payload.trim();
    }

    public boolean isHealthy() {
        return true;
    }
}
