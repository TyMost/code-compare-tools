package com.example.codecompare.rebuild.agent.config;

import com.example.codecompare.rebuild.agent.AnnotationTemplateProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.io.ResourceLoader;

/**
 * Agent module configuration.
 */
@Configuration
@EnableConfigurationProperties(MigrationAnnotationProperties.class)
public class AgentConfiguration {

    @Bean
    public AnnotationTemplateProvider annotationTemplateProvider(MigrationAnnotationProperties properties,
                                                                 ResourceLoader resourceLoader) {
        return new AnnotationTemplateProvider(resourceLoader, properties.getAnnotationTemplate());
    }
}
