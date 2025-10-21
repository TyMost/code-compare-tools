package com.example.codecompare.rebuild.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Holds migration agent annotation template configuration.
 */
@ConfigurationProperties(prefix = "migration.agent")
public class MigrationAnnotationProperties {

    /**
     * Resource location of the annotation template yaml.
     */
    private String annotationTemplate;

    public String getAnnotationTemplate() {
        return annotationTemplate;
    }

    public void setAnnotationTemplate(String annotationTemplate) {
        this.annotationTemplate = annotationTemplate;
    }
}
