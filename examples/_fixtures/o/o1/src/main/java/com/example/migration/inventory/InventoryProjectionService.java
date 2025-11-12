package com.example.migration.inventory;

public class InventoryProjectionService {
    public int project(int storeCount, int demand) {
        return Math.max(storeCount - demand, 0);
    }
}
