package com.example.codecompare.rebuild.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 本地 Agent 编排器，实现两步式迁移占位流程。
 */
@Component
public class LocalAgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(LocalAgentOrchestrator.class);
    private static final String PLACEHOLDER_EXECUTION_MESSAGE = "占位执行完成，等待后续建设";

    private final MigrationAgent migrationAgent;
    private final AgentSuggestionGateway suggestionGateway;
    private final AgentSuggestionCache suggestionCache;
    private final CodeBlockMigrationService codeBlockMigrationService;

    public LocalAgentOrchestrator(MigrationAgent migrationAgent,
                                  AgentSuggestionGateway suggestionGateway,
                                  AgentSuggestionCache suggestionCache,
                                  CodeBlockMigrationService codeBlockMigrationService) {
        this.migrationAgent = migrationAgent;
        this.suggestionGateway = suggestionGateway;
        this.suggestionCache = suggestionCache;
        this.codeBlockMigrationService = codeBlockMigrationService;
    }

    /**
     * 获取指定代码块的建议，优先命中缓存，其次尝试远程接口，最后使用本地占位实现。
     */
    public AgentSuggestion getSuggestion(AgentTaskContext context) {
        if (context == null || context.getBlockId() == null) {
            log.warn("获取 Agent 建议时缺少上下文，直接返回占位提示。");
            return migrationAgent.generateSuggestion(context);
        }
        Optional<AgentSuggestion> cached = suggestionCache.get(context.getBlockId());
        if (cached.isPresent()) {
            log.info("命中 Agent 建议缓存，blockId={}", context.getBlockId());
            AgentSuggestion enrichedCached = enrichWithTemplate(context.getBlockId(), cached.get());
            if (enrichedCached != cached.get()) {
                suggestionCache.put(enrichedCached);
                return enrichedCached;
            }
            return cached.get();
        }
        Optional<AgentSuggestion> remote = suggestionGateway.fetchRemoteSuggestion(context);
        AgentSuggestion suggestion = remote.orElseGet(() -> migrationAgent.generateSuggestion(context));
        AgentSuggestion enriched = enrichWithTemplate(context.getBlockId(), suggestion);
        suggestionCache.put(enriched);
        return enriched;
    }

    public CodeBlockMigrationService.MigrationOperationResult generateAnnotatedCopies(List<String> blockIds,
                                                                                       String annotationTemplate) {
        return codeBlockMigrationService.generateAnnotatedCopies(blockIds, annotationTemplate);
    }

    public CodeBlockMigrationService.MigrationOperationResult applyAnnotatedCopies(List<String> blockIds) {
        return codeBlockMigrationService.applyAnnotatedCopies(blockIds);
    }

    public CodeBlockMigrationService.MigrationOperationResult revertAnnotatedCopies(List<String> blockIds) {
        return codeBlockMigrationService.revertAnnotatedCopies(blockIds);
    }

    /**
     * 执行占位的两步式迁移流程，当前仅刷新缓存并输出日志。
     */
    public AgentExecutionResult execute(AgentExecutionRequest request) {
        if (request == null) {
            log.warn("批量执行请求为空，跳过处理。");
            return new AgentExecutionResult(0, "未提供执行内容", Instant.now());
        }
        List<String> blockIds = request.getBlockIds();
        if (blockIds == null || blockIds.isEmpty()) {
            log.warn("批量执行请求未包含块 ID，跳过处理。");
            return new AgentExecutionResult(0, "未指定迁移块", Instant.now());
        }
        log.info("开始执行占位两步式迁移，项目={}，块数量={}，强制刷新={}",
                request.getProjectKey(), blockIds.size(), request.isForceRefreshSuggestion());
        int processed = 0;
        for (String blockId : blockIds) {
            if (blockId == null) {
                continue;
            }
            if (request.isForceRefreshSuggestion()) {
                suggestionCache.evict(blockId);
            }
            AgentTaskContext context = AgentTaskContext.builder()
                    .projectKey(request.getProjectKey())
                    .blockId(blockId)
                    .build();
            AgentSuggestion suggestion = migrationAgent.generateSuggestion(context);
            AgentSuggestion enriched = enrichWithTemplate(blockId, suggestion);
            suggestionCache.put(enriched);
            processed++;
        }
        log.info("占位两步式迁移流程完成，共处理 {} 个块。", processed);
        return new AgentExecutionResult(processed, PLACEHOLDER_EXECUTION_MESSAGE, Instant.now());
    }

    private AgentSuggestion enrichWithTemplate(String blockId, AgentSuggestion suggestion) {
        if (suggestion == null || blockId == null) {
            return suggestion;
        }
        Optional<CodeBlockMigrationService.AnnotationMetadata> metadataOptional =
                codeBlockMigrationService.resolveAnnotationMetadata(blockId);
        if (!metadataOptional.isPresent()) {
            return suggestion;
        }
        CodeBlockMigrationService.AnnotationMetadata metadata = metadataOptional.get();
        Map<String, Object> merged = new LinkedHashMap<>(suggestion.getMetadata());
        boolean changed = false;
        if (metadata.getTemplate() != null && !metadata.getTemplate().isEmpty()) {
            Object existing = merged.get("annotationTemplate");
            if (!metadata.getTemplate().equals(existing)) {
                merged.put("annotationTemplate", metadata.getTemplate());
                changed = true;
            }
        }
        if (metadata.getTemplateKey() != null && !metadata.getTemplateKey().isEmpty()) {
            Object existing = merged.get("annotationTemplateKey");
            if (!metadata.getTemplateKey().equals(existing)) {
                merged.put("annotationTemplateKey", metadata.getTemplateKey());
                changed = true;
            }
        }
        if (!changed) {
            return suggestion;
        }
        log.info("附加注解模板信息，blockId={}，templateKey={}", blockId, metadata.getTemplateKey());
        return suggestion.withMetadata(merged);
    }
}
