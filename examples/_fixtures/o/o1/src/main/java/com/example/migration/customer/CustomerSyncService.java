package com.example.migration.customer;

import com.example.migration.common.RequestContext;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CustomerSyncService {
    private final List<String> operations = new CopyOnWriteArrayList<>();

    public void syncCustomer(RequestContext ctx, String customerId) {
        operations.add("SYNC:" + customerId + ":" + ctx.getOperator());
    }

    public List<String> getOperations() {
        return operations;
    }
}
