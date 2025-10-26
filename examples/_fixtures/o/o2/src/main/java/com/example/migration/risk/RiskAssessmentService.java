package com.example.migration.risk;

public class RiskAssessmentService {
    public double assess(String customerId) {
        return customerId.hashCode() % 1000 / 10.0;
    }
}
