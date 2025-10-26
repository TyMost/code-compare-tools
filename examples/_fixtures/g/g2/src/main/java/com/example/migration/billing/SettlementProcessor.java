package com.example.migration.billing;

import com.example.migration.common.RequestContext;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class SettlementProcessor {
    private final List<String> auditTrail = new ArrayList<>();

    public String settle(RequestContext ctx, String windowId) {
        String result = "GAUSS_SETTLED:" + windowId + ":" + ctx.getOperator();
        auditTrail.add(result + ":" + OffsetDateTime.now());
        return result;
    }

    public void splitWindow(RequestContext ctx, String windowId) {
        auditTrail.add("SPLIT_GAUSS:" + windowId + ":" + ctx.getOperator());
    }

    public List<String> getAuditTrail() {
        return auditTrail;
    }
}
