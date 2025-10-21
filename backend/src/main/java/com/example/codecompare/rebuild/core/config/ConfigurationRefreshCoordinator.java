package com.example.codecompare.rebuild.core.config;

import com.example.codecompare.rebuild.agent.AnnotationTemplateProvider;
import com.example.codecompare.rebuild.rules.RuleLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 协调不同配置载体的刷新逻辑，保证一次操作中各项配置保持一致。
 */
@Component
public class ConfigurationRefreshCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationRefreshCoordinator.class);

    private final RuleLoader ruleLoader;
    private final AnnotationTemplateProvider annotationTemplateProvider;

    public ConfigurationRefreshCoordinator(RuleLoader ruleLoader,
                                           AnnotationTemplateProvider annotationTemplateProvider) {
        this.ruleLoader = ruleLoader;
        this.annotationTemplateProvider = annotationTemplateProvider;
    }

    public ConfigurationReloadReport reloadAll() {
        Instant startedAt = Instant.now();
        List<ConfigurationReloadReport.Item> items = new ArrayList<ConfigurationReloadReport.Item>();
        boolean success = true;

        try {
            RuleLoader.RuleReloadReport report = ruleLoader.reloadWithReport();
            items.add(ConfigurationReloadReport.Item.success(
                    "rules",
                    report.isUpdated(),
                    report.getMessage(),
                    report.getRuleSet().getLoadedAt(),
                    report.getRuleSet().getSourceDescription(),
                    Integer.valueOf(report.getRuleSet().getDefinitions().size())
            ));
            if (!report.isSuccess()) {
                success = false;
            }
        } catch (Exception ex) {
            success = false;
            log.error("刷新规则配置失败", ex);
            items.add(ConfigurationReloadReport.Item.failure("rules", safeMessage(ex)));
        }

        try {
            AnnotationTemplateProvider.TemplateReloadReport templateReport = annotationTemplateProvider.refresh();
            items.add(ConfigurationReloadReport.Item.success(
                    "annotationTemplate",
                    templateReport.isUpdated(),
                    templateReport.getMessage(),
                    templateReport.getLoadedAt(),
                    templateReport.getSourceDescription(),
                    null
            ));
            if (!templateReport.isSuccess()) {
                success = false;
            }
        } catch (Exception ex) {
            success = false;
            log.error("刷新注解模板失败", ex);
            items.add(ConfigurationReloadReport.Item.failure("annotationTemplate", safeMessage(ex)));
        }

        Instant completedAt = Instant.now();
        return new ConfigurationReloadReport(startedAt, completedAt, success, items);
    }

    private String safeMessage(Exception ex) {
        if (ex == null) {
            return "";
        }
        String message = ex.getMessage();
        return message != null ? message : ex.toString();
    }
}

