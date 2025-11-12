package com.example.migration.customer;

import com.example.migration.common.RequestContext;
import java.util.HashMap;
import java.util.Map;

public class RetentionCampaignService {
    private final Map<String, String> decisions = new HashMap<>();

    public void launchCampaign(RequestContext ctx, String segment) {
        decisions.put(segment, "LAUNCHED:" + ctx.getOperator());
    }

    public Map<String, String> getDecisions() {
        return decisions;
    }
}
