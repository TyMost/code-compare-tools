package com.example.compare.legacy;

/**
 * Remains in the legacy system and has not been migrated.
 */
public class LegacyPromotionService {

    public String banner() {
        return "LEGACY::PROMO";
    }

    public boolean isLegacy() {
        return true;
    }

    public String ownerTeam() {
        return "LEGACY::CAMPAIGN";
    }
}
