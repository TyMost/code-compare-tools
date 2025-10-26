package com.example.migration.customer;

public class CustomerProfile {
    private String customerId;
    private String tier;

    public CustomerProfile(String customerId, String tier) {
        this.customerId = customerId;
        this.tier = tier;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }
}
