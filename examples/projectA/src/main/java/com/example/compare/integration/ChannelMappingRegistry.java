package com.example.compare.integration;

import java.util.HashMap;
import java.util.Map;

/**
 * Legacy channel mapping that builds a mutable map for downstream systems.
 */
public class ChannelMappingRegistry {

    public Map<String, String> registry() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("web", "LEGACY::WEB");
        mapping.put("pos", "LEGACY::POS");
        mapping.put("partner", "LEGACY::PARTNER");
        mapping.put("mobile", "LEGACY::MOBILE");
        return mapping;
    }
}
