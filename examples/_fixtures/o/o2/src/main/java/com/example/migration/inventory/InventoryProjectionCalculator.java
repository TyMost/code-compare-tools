package com.example.migration.inventory;

public class InventoryProjectionCalculator {
    public int project(int storeCount, int demand) {
        return Math.max(storeCount - demand, 0);
    }
}
