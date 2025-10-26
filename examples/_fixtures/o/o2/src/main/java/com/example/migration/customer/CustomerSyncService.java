package com.example.migration.customer;

import com.example.migration.common.RequestContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class CustomerSyncService {
    private final List<String> operations = new CopyOnWriteArrayList<>();
    private final Map<String, String> annotations = new LinkedHashMap<>();

    public void syncCustomer(RequestContext ctx, String customerId) {
        operations.add("SYNC:" + customerId + ":" + ctx.getOperator());
        if (customerId.endsWith("VIP")) {
            annotations.put(customerId, "ANNIVERSARY");
        }
    }

    public void refreshAnnotations(RequestContext ctx, List<String> customerIds) {
        for (String id : customerIds) {
            annotations.put(id, "REFRESHED:" + ctx.getOperator());
        }
    }

    public List<String> getOperations() {
        return operations;
    }

    public Map<String, String> getAnnotations() {
        return annotations;
    }
}
