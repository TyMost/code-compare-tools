package com.example.codecompare.rebuild.core.event;

import com.example.codecompare.rebuild.core.properties.ApplicationProperties;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.CollectionUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 应用生命周期事件发布器，负责统一输出启动、关闭等阶段事件。
 */
public class BootstrapEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BootstrapEventPublisher.class);

    private final ApplicationEventPublisher delegate;
    private final Clock clock;

    public BootstrapEventPublisher(ApplicationEventPublisher delegate, Clock clock) {
        this.delegate = delegate;
        this.clock = clock;
    }

    /**
     * 发布应用启动事件，附带关键配置上下文。
     */
    public void publishStartup(ApplicationProperties properties, ProjectRootRegistry projectRootRegistry) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectRoots", projectRootRegistry.getRoots());
        payload.put("gitEnabled", properties.getGit().isEnabled());
        publishPhase(ApplicationLifecyclePhase.STARTUP, payload);
    }

    /**
     * 发布统一的关机事件。
     */
    public void publishShutdown() {
        publishPhase(ApplicationLifecyclePhase.SHUTDOWN, new LinkedHashMap<>());
    }

    /**
     * 泛化的事件发布接口，供其他模块扩展调用。
     */
    public void publishPhase(ApplicationLifecyclePhase phase, Map<String, Object> payload) {
        Map<String, Object> safePayload = CollectionUtils.isEmpty(payload)
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(payload);
        ApplicationLifecycleEvent event =
                new ApplicationLifecycleEvent(this, phase, Instant.now(clock), safePayload);
        log.info("核心生命周期事件发布：{}，上下文：{}", phase, safePayload);
        delegate.publishEvent(event);
    }
}
