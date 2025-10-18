package com.example.codecompare.rebuild.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loads and renders annotation templates for migration code generation.
 */
public class AnnotationTemplateProvider {

    private static final Logger log = LoggerFactory.getLogger(AnnotationTemplateProvider.class);

    private static final String DEFAULT_TEMPLATE_FALLBACK =
            "/** 迁移生成的代码片段开始 (blockId=${blockId}) */" + System.lineSeparator()
                    + "${code}" + System.lineSeparator()
                    + "/** 迁移生成的代码片段结束 */";

    private final ResourceLoader resourceLoader;
    private final String templateLocation;
    private final AtomicReference<TemplateSet> cache = new AtomicReference<TemplateSet>();
    private final Object reloadLock = new Object();

    public AnnotationTemplateProvider(ResourceLoader resourceLoader, String templateLocation) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
        this.templateLocation = templateLocation;
        TemplateSet initial = loadTemplates(templateLocation);
        this.cache.set(initial);
    }

    public AnnotationRenderResult render(String blockId, String sourceCode, String originalCode, boolean useAdapt) {
        TemplateSet current = cache.get();
        boolean useAdaptTemplate = useAdapt && StringUtils.hasText(current.getAdaptTemplate());
        String templateKey = useAdaptTemplate ? "migrate_adapt" : "default";
        String template = useAdaptTemplate ? current.getAdaptTemplate() : current.getDefaultTemplate();
        if (!StringUtils.hasText(template)) {
            template = current.getDefaultTemplate();
            templateKey = "default";
        }
        String safeBlockId = blockId == null ? "" : blockId;
        String safeSource = normalizeContent(sourceCode);
        String safeOriginal = normalizeContent(originalCode);
        String rendered = template.replace("${blockId}", safeBlockId)
                .replace("${code}", safeSource)
                .replace("${original}", safeOriginal);
        return new AnnotationRenderResult(rendered, template, templateKey,
                current.getLoadedAt(), current.getSourceDescription());
    }

    /**
     * Reloads template content from the configured resource.
     *
     * @return report describing reload outcome
     */
    public TemplateReloadReport refresh() {
        synchronized (reloadLock) {
            TemplateSet previous = cache.get();
            TemplateSet reloaded = loadTemplates(templateLocation);
            cache.set(reloaded);
            boolean updated = !reloaded.contentEquals(previous);
            String message;
            if (!reloaded.isLoadSuccessful()) {
                message = "模板加载失败，已回退至默认模板";
            } else if (updated) {
                message = "模板已刷新";
            } else {
                message = "模板无变化";
            }
            return new TemplateReloadReport(
                    reloaded.isLoadSuccessful(),
                    updated,
                    reloaded.getLoadedAt(),
                    reloaded.getSourceDescription(),
                    message
            );
        }
    }

    private TemplateSet loadTemplates(String location) {
        String defaultTpl = DEFAULT_TEMPLATE_FALLBACK;
        String adaptTpl = null;
        boolean loadSuccessful = false;
        boolean fallbackUsed = false;
        String description = "annotation-template:default";
        if (StringUtils.hasText(location)) {
            Resource resource = resourceLoader.getResource(location);
            description = resource.getDescription();
            if (resource.exists()) {
                try {
                    YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
                    factory.setResources(resource);
                    Properties props = factory.getObject();
                    if (props != null) {
                        String candidateDefault = props.getProperty("annotation.default");
                        if (StringUtils.hasText(candidateDefault)) {
                            defaultTpl = candidateDefault;
                        } else {
                            fallbackUsed = true;
                        }
                        adaptTpl = props.getProperty("annotation.migrate_adapt");
                        loadSuccessful = true;
                        log.info("已加载注解模板配置，location={}，包含默认模板={}，适配模板={}",
                                location,
                                StringUtils.hasText(defaultTpl),
                                StringUtils.hasText(adaptTpl));
                    } else {
                        fallbackUsed = true;
                        log.warn("注解模板配置文件为空，location={}，使用默认模板", location);
                    }
                } catch (IllegalStateException ex) {
                    fallbackUsed = true;
                    log.warn("无法解析注解模板配置，使用默认模板: {}", ex.getMessage());
                }
            } else {
                fallbackUsed = true;
                log.warn("未找到注解模板资源: {}", location);
            }
        } else {
            fallbackUsed = true;
            description = "annotation-template:fallback";
            log.info("未配置迁移注解模板路径，使用内置默认模板");
        }

        String normalizedDefault = normalizeTemplate(defaultTpl);
        if (!StringUtils.hasText(normalizedDefault)) {
            normalizedDefault = DEFAULT_TEMPLATE_FALLBACK;
            fallbackUsed = true;
        }
        String normalizedAdapt = normalizeTemplate(adaptTpl);

        return new TemplateSet(
                normalizedDefault,
                normalizedAdapt,
                Instant.now(),
                description,
                fallbackUsed,
                loadSuccessful
        );
    }

    private String normalizeTemplate(String template) {
        if (!StringUtils.hasText(template)) {
            return null;
        }
        String normalized = template.replace("\r\n", "\n").replace('\r', '\n');
        if (!System.lineSeparator().equals("\n")) {
            normalized = normalized.replace("\n", System.lineSeparator());
        }
        return normalized;
    }

    private String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
        if (!System.lineSeparator().equals("\n")) {
            normalized = normalized.replace("\n", System.lineSeparator());
        }
        return normalized;
    }

    private static final class TemplateSet {
        private final String defaultTemplate;
        private final String adaptTemplate;
        private final Instant loadedAt;
        private final String sourceDescription;
        private final boolean fallbackUsed;
        private final boolean loadSuccessful;

        private TemplateSet(String defaultTemplate,
                            String adaptTemplate,
                            Instant loadedAt,
                            String sourceDescription,
                            boolean fallbackUsed,
                            boolean loadSuccessful) {
            this.defaultTemplate = defaultTemplate == null
                    ? DEFAULT_TEMPLATE_FALLBACK
                    : defaultTemplate;
            this.adaptTemplate = adaptTemplate;
            this.loadedAt = loadedAt == null ? Instant.now() : loadedAt;
            this.sourceDescription = sourceDescription;
            this.fallbackUsed = fallbackUsed;
            this.loadSuccessful = loadSuccessful;
        }

        private boolean contentEquals(TemplateSet other) {
            if (this == other) {
                return true;
            }
            if (other == null) {
                return false;
            }
            return Objects.equals(defaultTemplate, other.defaultTemplate)
                    && Objects.equals(adaptTemplate, other.adaptTemplate);
        }

        private String getDefaultTemplate() {
            return defaultTemplate;
        }

        private String getAdaptTemplate() {
            return adaptTemplate;
        }

        private Instant getLoadedAt() {
            return loadedAt;
        }

        private String getSourceDescription() {
            return sourceDescription;
        }

        private boolean isFallbackUsed() {
            return fallbackUsed;
        }

        private boolean isLoadSuccessful() {
            return loadSuccessful;
        }
    }

    public static final class TemplateReloadReport {
        private final boolean success;
        private final boolean updated;
        private final Instant loadedAt;
        private final String sourceDescription;
        private final String message;

        private TemplateReloadReport(boolean success,
                                     boolean updated,
                                     Instant loadedAt,
                                     String sourceDescription,
                                     String message) {
            this.success = success;
            this.updated = updated;
            this.loadedAt = loadedAt;
            this.sourceDescription = sourceDescription;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isUpdated() {
            return updated;
        }

        public Instant getLoadedAt() {
            return loadedAt;
        }

        public String getSourceDescription() {
            return sourceDescription;
        }

        public String getMessage() {
            return message;
        }
    }

    public static final class AnnotationRenderResult {
        private final String content;
        private final String template;
        private final String templateKey;
        private final Instant templateLoadedAt;
        private final String templateSourceDescription;

        private AnnotationRenderResult(String content,
                                       String template,
                                       String templateKey,
                                       Instant templateLoadedAt,
                                       String templateSourceDescription) {
            this.content = content;
            this.template = template;
            this.templateKey = templateKey;
            this.templateLoadedAt = templateLoadedAt;
            this.templateSourceDescription = templateSourceDescription;
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

        public Instant getTemplateLoadedAt() {
            return templateLoadedAt;
        }

        public String getTemplateSourceDescription() {
            return templateSourceDescription;
        }
    }
}

