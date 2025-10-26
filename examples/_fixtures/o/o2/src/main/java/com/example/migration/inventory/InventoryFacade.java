package com.example.migration.inventory;

public class InventoryFacade {
    private final InventoryProjectionCalculator projectionCalculator = new InventoryProjectionCalculator();

    public int projectForStore(String storeId, int demand) {
        int storeCount = Math.abs(storeId.hashCode() % 10) + 5;
        return projectionCalculator.project(storeCount, demand);
    }
}
