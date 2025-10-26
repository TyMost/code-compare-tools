package com.example.migration.loan;

import com.example.migration.common.RequestContext;

public class LoanAdjustmentService {
    public double repriceLoan(RequestContext ctx, double principal, double rate) {
        double adjustedRate = rate - 0.01;
        ctx.withOperator(ctx.getOperator());
        return principal * adjustedRate;
    }
}
