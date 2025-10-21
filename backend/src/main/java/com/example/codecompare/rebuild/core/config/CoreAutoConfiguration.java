package com.example.codecompare.rebuild.core.config;

import com.example.codecompare.rebuild.core.event.BootstrapEventPublisher;
import com.example.codecompare.rebuild.core.properties.ApplicationProperties;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Clock;

/**
 * 核心自动配置，声明跨模块复用的基础 Bean。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApplicationProperties.class)
public class CoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CoreAutoConfiguration.class);

    @Bean
    public ObjectMapper objectMapper() {
        log.info("初始化全局 ObjectMapper，启用 Java 时间模块");
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    public TaskExecutor coreTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cores = Math.max(Runtime.getRuntime().availableProcessors(), 2);
        executor.setCorePoolSize(cores);
        executor.setMaxPoolSize(cores * 2);
        executor.setQueueCapacity(cores * 64);
        executor.setThreadNamePrefix("core-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        log.info("核心任务线程池已初始化，核心线程数：{}", cores);
        return executor;
    }

    @Bean
    public BootstrapEventPublisher bootstrapEventPublisher(ApplicationEventPublisher publisher, Clock clock) {
        return new BootstrapEventPublisher(publisher, clock);
    }

    @Bean
    public ProjectRootRegistry projectRootRegistry(ApplicationProperties applicationProperties) {
        return new ProjectRootRegistry(applicationProperties);
    }

    @Bean
    public ApplicationListener<ApplicationReadyEvent> coreStartupListener(
            BootstrapEventPublisher bootstrapEventPublisher,
            ApplicationProperties applicationProperties,
            ProjectRootRegistry projectRootRegistry) {
        return event -> bootstrapEventPublisher.publishStartup(applicationProperties, projectRootRegistry);
    }

    @Bean
    public ApplicationListener<ContextClosedEvent> coreShutdownListener(
            BootstrapEventPublisher bootstrapEventPublisher) {
        return event -> bootstrapEventPublisher.publishShutdown();
    }
}
