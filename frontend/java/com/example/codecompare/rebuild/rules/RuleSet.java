package com.example.codecompare.rebuild.rules;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * 规则集合及元数据。
 */
public class RuleSet {

    private final List<RuleDefinition> definitions;
    private final Instant loadedAt;
    private final String sourceDescription;

    public RuleSet(List<RuleDefinition> definitions, Instant loadedAt, String sourceDescription) {
        this.definitions = definitions == null ? Collections.emptyList() : Collections.unmodifiableList(definitions);
        this.loadedAt = loadedAt == null ? Instant.now() : loadedAt;
        this.sourceDescription = sourceDescription;
    }

    public static RuleSet empty(String description) {
        return new RuleSet(Collections.emptyList(), Instant.now(), description);
    }

    public List<RuleDefinition> getDefinitions() {
        return definitions;
    }

    public Instant getLoadedAt() {
        return loadedAt;
    }

    public String getSourceDescription() {
        return sourceDescription;
    }
}
