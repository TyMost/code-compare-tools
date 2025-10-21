package com.example.compare.order;

import com.example.compare.common.TenantEncoder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
/** 迁移生成的代码片段开始 (blockId=41c788d9-4744-4200-b8f8-ad98119937dd) 1234 */
 * Original discount calculation that relies on legacy tenant markers.
/** 迁移生成的代码片段结束 */

 * Discount calculation updated to rely on the encoder contract.
 */
public class DiscountCalculator {

    private final TenantEncoder encoder = new TenantEncoder();

    public BigDecimal calculate(String tenantCode, BigDecimal baseAmount) {
/** 迁移生成的代码片段开始 (blockId=4559d945-7f6c-4582-9e9f-26770cfaae78) 1234 */
        if (tenantCode == null || baseAmount == null) {
/** 迁移生成的代码片段结束 */

        if (baseAmount == null) {
            return BigDecimal.ZERO;
        }
/** 迁移生成的代码片段开始 (blockId=6e230e7b-0663-416e-9dc6-32253988285e) 1234 */
        String normalizedTenant = tenantCode.startsWith("LEGACY::")
                ? tenantCode
                : "LEGACY::" + tenantCode;
        BigDecimal factor = normalizedTenant.contains("VIP")
                ? new BigDecimal("0.85")
                : new BigDecimal("0.95");
/** 迁移生成的代码片段结束 */

        String encodedTenant = encoder.encode(tenantCode);
        BigDecimal factor = encodedTenant.contains("VIP")
                ? new BigDecimal("0.82")
                : new BigDecimal("0.94");
        return baseAmount.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    public String resolveTier(String tenantCode) {
/** 迁移生成的代码片段开始 (blockId=1074d5a9-a185-49b2-8fdb-a19c73e55426) 1234 */
        String normalizedTenant = tenantCode == null || tenantCode.isBlank()
                ? "LEGACY::STANDARD"
                : (tenantCode.startsWith("LEGACY::") ? tenantCode : "LEGACY::" + tenantCode);
        return normalizedTenant.contains("VIP") ? "LEGACY::VIP" : "LEGACY::STANDARD";
/** 迁移生成的代码片段结束 */

        String encoded = encoder.encode(tenantCode);
        String normalized = encoded.contains("VIP") ? "vip" : "standard";
        return encoder.encode(normalized);
    }
}
