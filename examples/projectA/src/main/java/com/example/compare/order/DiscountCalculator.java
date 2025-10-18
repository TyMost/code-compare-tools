package com.example.compare.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Original discount calculation that relies on legacy tenant markers.
 */
public class DiscountCalculator {

    public BigDecimal calculate(String tenantCode, BigDecimal baseAmount) {
        if (tenantCode == null || baseAmount == null) {
            return BigDecimal.ZERO;
        }
        String normalizedTenant = tenantCode.startsWith("LEGACY::")
                ? tenantCode
                : "LEGACY::" + tenantCode;
        BigDecimal factor = normalizedTenant.contains("VIP")
                ? new BigDecimal("0.85")
                : new BigDecimal("0.95");
        return baseAmount.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    public String resolveTier(String tenantCode) {
        String normalizedTenant = tenantCode == null || tenantCode.isBlank()
                ? "LEGACY::STANDARD"
                : (tenantCode.startsWith("LEGACY::") ? tenantCode : "LEGACY::" + tenantCode);
        return normalizedTenant.contains("VIP") ? "LEGACY::VIP" : "LEGACY::STANDARD";
    }
}
