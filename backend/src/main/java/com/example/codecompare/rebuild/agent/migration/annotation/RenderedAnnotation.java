package com.example.codecompare.rebuild.agent.migration.annotation;

/**
 * Holds the rendered annotation content together with template metadata.
 */
public final class RenderedAnnotation {

    private final String content;
    private final String template;
    private final String templateKey;

    public RenderedAnnotation(String content, String template, String templateKey) {
        this.content = content;
        this.template = template;
        this.templateKey = templateKey;
    }

    public String getContent() {
        return content;
    }

    public String getTemplate() {
        return template;
    }

    public String getTemplateKey() {
        return templateKey;
    }
}

