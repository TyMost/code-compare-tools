package com.example.migration.inventory;

public class InventoryFacade {
    private final InventoryProjectionService projectionService = new InventoryProjectionService();

    public int projectForStore(String storeId, int demand) {
        int storeCount = Math.abs(storeId.hashCode() % 10) + 5;
        return projectionService.project(storeCount, demand);
    }
}
