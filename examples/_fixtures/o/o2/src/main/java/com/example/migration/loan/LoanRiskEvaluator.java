package com.example.migration.loan;

public class LoanRiskEvaluator {
    public boolean requiresManualReview(double exposure, double collateral) {
        return exposure > collateral * 1.2;
    }
}
