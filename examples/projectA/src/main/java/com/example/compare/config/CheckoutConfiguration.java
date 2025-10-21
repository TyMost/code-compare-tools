package com.example.compare.config;

import java.util.Map;

public class CheckoutConfiguration {

    public void apply(Map<String, Object> overrides) {
        setNewCheckout(true);
    }

    void setNewCheckout(boolean value) {
        // legacy toggling stub
    }
}
