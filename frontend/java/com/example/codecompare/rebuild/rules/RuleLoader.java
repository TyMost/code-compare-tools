package com.example.codecompare.rebuild.rules;

import com.example.codecompare.rebuild.core.properties.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 负责加载规则配置文件，并提供内存缓存。
 */
public class RuleLoader {

    private static final Logger log = LoggerFactory.getLogger(RuleLoader.class);

    private final ApplicationProperties migrationProperties;
    private final AtomicReference<RuleSet> cache = new AtomicReference<RuleSet>(RuleSet.empty("uninitialized"));

    public RuleLoader(ApplicationProperties migrationProperties) {
        this.migrationProperties = Objects.requireNonNull(migrationProperties, "migrationProperties must not be null");
        reload();
    }

    public RuleSet currentRules() {
        return cache.get();
    }

    public RuleSet reload() {
        RuleReloadReport report = reloadWithReport();
        return report.getRuleSet();
    }

    public RuleReloadReport reloadWithReport() {
        RuleSet previous = cache.get();
        LoadOutcome outcome = loadDetailed();
        cache.set(outcome.getRuleSet());
        boolean updated = !previous.getLoadedAt().equals(outcome.getRuleSet().getLoadedAt())
                || previous.getDefinitions().size() != outcome.getRuleSet().getDefinitions().size();
        log.info("规则缓存已更新，加载时间={}，规则数量={}，成功={}，描述={}",
                outcome.getRuleSet().getLoadedAt(),
                outcome.getRuleSet().getDefinitions().size(),
                outcome.isSuccess(),
                outcome.getMessage());
        return new RuleReloadReport(outcome.isSuccess(), updated, outcome.getRuleSet(), outcome.getMessage());
    }

    private LoadOutcome loadDetailed() {
        String location = migrationProperties.getRules().getPath();
        Resource resource = resolveLocation(location);
        String description = resource.getDescription();
        log.info("准备加载规则文件，配置路径={}，解析路径={}", location, description);
        if (!resource.exists()) {
            log.warn("规则文件不存在，配置路径={}，解析路径={}，返回空规则集", location, description);
            return new LoadOutcome(RuleSet.empty(description), false, "规则文件不存在，已使用空规则集");
        }

        try (InputStream inputStream = resource.getInputStream()) {
            Yaml yaml = new Yaml();
            Object document = yaml.load(inputStream);
            List<RuleDefinition> definitions = parse(document);
            log.info("规则文件加载成功，解析路径={}，规则数量={}", description, definitions.size());
            return new LoadOutcome(new RuleSet(definitions, Instant.now(), description), true, "规则加载成功");
        } catch (IOException ex) {
            log.error("读取规则文件失败，解析路径={}", description, ex);
            throw new IllegalStateException("无法读取规则文件: " + description, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<RuleDefinition> parse(Object document) {
        if (!(document instanceof Map)) {
            log.warn("规则文件结构异常，根节点不是对象，将返回空规则集");
            return Collections.emptyList();
        }
        Map<String, Object> root = (Map<String, Object>) document;
        Object rulesNode = root.get("rules");
        if (!(rulesNode instanceof List)) {
            log.warn("规则文件缺少 rules 列表，将返回空规则集");
            return Collections.emptyList();
        }

        List<Object> entries = (List<Object>) rulesNode;
        List<RuleDefinition> definitions = new ArrayList<RuleDefinition>();
        for (Object entry : entries) {
            if (!(entry instanceof Map)) {
                log.warn("忽略无法解析的规则项：{}", entry);
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<String, Object>((Map<String, Object>) entry);
            String id = asText(node.remove("id"));
            String type = asText(node.remove("type"));
            if (!StringUtils.hasText(id) || !StringUtils.hasText(type)) {
                log.warn("忽略无效规则：{}", entry);
                continue;
            }
            String displayName = asText(node.remove("displayName"));
            Map<String, Object> params = Collections.emptyMap();
            Object paramsNode = node.remove("params");
            if (paramsNode instanceof Map) {
                params = (Map<String, Object>) paramsNode;
            }
            definitions.add(new RuleDefinition(id, type, displayName, params));
        }
        return definitions;
    }

    private String asText(Object value) {
        return value == null ? null : value.toString();
    }

    private Resource resolveLocation(String location) {
        if (!StringUtils.hasText(location)) {
            return new ClassPathResource("config/rules.yaml");
        }
        String trimmed = location.trim();
        if (trimmed.startsWith("classpath:")) {
            String path = trimmed.substring("classpath:".length());
            return new ClassPathResource(path);
        }
        return new FileSystemResource(trimmed);
    }

    public static final class RuleReloadReport {
        private final boolean success;
        private final boolean updated;
        private final RuleSet ruleSet;
        private final String message;

        private RuleReloadReport(boolean success, boolean updated, RuleSet ruleSet, String message) {
            this.success = success;
            this.updated = updated;
            this.ruleSet = ruleSet;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isUpdated() {
            return updated;
        }

        public RuleSet getRuleSet() {
            return ruleSet;
        }

        public String getMessage() {
            return message;
        }
    }

    private static final class LoadOutcome {
        private final RuleSet ruleSet;
        private final boolean success;
        private final String message;

        private LoadOutcome(RuleSet ruleSet, boolean success, String message) {
            this.ruleSet = ruleSet;
            this.success = success;
            this.message = message;
        }

        private RuleSet getRuleSet() {
            return ruleSet;
        }

        private boolean isSuccess() {
            return success;
        }

        private String getMessage() {
            return message;
        }
    }
}

