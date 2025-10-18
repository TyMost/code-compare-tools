package com.example.codecompare.rebuild.core.event;

import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 核心模块发布的生命周期事件，包含阶段、时间戳及自定义上下文。
 */
public class ApplicationLifecycleEvent extends ApplicationEvent {

    private final ApplicationLifecyclePhase phase;
    private final Instant occurredAt;
    private final Map<String, Object> payload;

    public ApplicationLifecycleEvent(Object source,
                                     ApplicationLifecyclePhase phase,
                                     Instant timestamp,
                                     Map<String, Object> payload) {
        super(source);
        this.phase = phase;
        this.occurredAt = timestamp;
        Map<String, Object> safePayload = payload == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(payload);
        this.payload = Collections.unmodifiableMap(safePayload);
    }

    public ApplicationLifecyclePhase getPhase() {
        return phase;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
