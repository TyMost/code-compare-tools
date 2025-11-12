package com.example.migration.billing;

import com.example.migration.common.RequestContext;

public class SettlementProcessor {
    public String settle(RequestContext ctx, String windowId) {
        return "SETTLED:" + windowId + ":" + ctx.getOperator();
    }
}
