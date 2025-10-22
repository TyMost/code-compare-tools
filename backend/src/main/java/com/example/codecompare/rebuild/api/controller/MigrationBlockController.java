package com.example.codecompare.rebuild.api.controller;

import com.example.codecompare.rebuild.agent.CodeBlockMigrationService;
import com.example.codecompare.rebuild.agent.LocalAgentOrchestrator;
import com.example.codecompare.rebuild.api.dto.CodeBlockDetailView;
import com.example.codecompare.rebuild.api.dto.CodeBlockListView;
import com.example.codecompare.rebuild.api.mapper.MigrationViewMapper;
import com.example.codecompare.rebuild.api.request.BatchOperationRequest;
import com.example.codecompare.rebuild.api.response.ApiResponse;
import com.example.codecompare.rebuild.api.response.ApiResponseFactory;
import com.example.codecompare.rebuild.stats.BlockStatsService;
import com.example.codecompare.rebuild.stats.CodeBlockQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * Code block list and detail endpoints.
 */
@RestController
@RequestMapping("/api/v1/migration")
public class MigrationBlockController {

    private static final Logger log = LoggerFactory.getLogger(MigrationBlockController.class);
    private final BlockStatsService blockStatsService;
    private final MigrationViewMapper viewMapper;
    private final LocalAgentOrchestrator agentOrchestrator;

    public MigrationBlockController(BlockStatsService blockStatsService,
                                    MigrationViewMapper viewMapper,
                                    LocalAgentOrchestrator agentOrchestrator) {
        this.blockStatsService = blockStatsService;
        this.viewMapper = viewMapper;
        this.agentOrchestrator = agentOrchestrator;
    }

    @GetMapping("/code-blocks")
    public ApiResponse<CodeBlockListView> listBlocks(
            @RequestParam(value = "projectKey", required = false) String projectKey,
            @RequestParam(value = "categories", required = false) List<String> categories,
            @RequestParam(value = "categories[]", required = false) List<String> categoriesAlt,
            @RequestParam(value = "excludeCategories", required = false) List<String> excludeCategories,
            @RequestParam(value = "excludeCategories[]", required = false) List<String> excludeCategoriesAlt,
            @RequestParam(value = "filePath", required = false) String filePath,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size) {
        String normalizedProjectKey = normalizeProjectKey(projectKey);
        List<String> effectiveCategories = CollectionUtils.isEmpty(categories) ? categoriesAlt : categories;
        List<String> effectiveExclude = CollectionUtils.isEmpty(excludeCategories)
                ? excludeCategoriesAlt
                : excludeCategories;
        String normalizedFileName = StringUtils.hasText(fileName) ? fileName.trim() : null;
        CodeBlockQuery.Builder builder = CodeBlockQuery.builder()
                .projectKey(normalizedProjectKey)
                .comparisonId(normalizedProjectKey)
                .filePath(StringUtils.hasText(filePath) ? filePath : null)
                .fileName(normalizedFileName)
                .page(page == null ? 1 : page)
                .size(size == null ? 20 : size);
        if (!CollectionUtils.isEmpty(effectiveCategories)) {
            builder.categories(effectiveCategories);
        }
        if (!CollectionUtils.isEmpty(effectiveExclude)) {
            builder.excludeCategories(effectiveExclude);
        }
        CodeBlockQuery query = builder.build();
        CodeBlockListView view = viewMapper.toBlockListView(blockStatsService.queryBlocks(query));
        if (view.getData().isEmpty()) {
            log.info("代码块列表为空，projectKey={}，返回重新加载提示", normalizedProjectKey);
            return ApiResponseFactory.ok("暂无代码块数据，请执行重新加载以生成最新内容", view);
        }
        return ApiResponseFactory.ok(view);
    }

    @GetMapping("/code-blocks/{id}")
    public ApiResponse<CodeBlockDetailView> blockDetail(
            @PathVariable("id") String blockId,
            @RequestParam(value = "projectKey", required = false) String projectKey,
            @RequestParam(value = "categories", required = false) List<String> categories,
            @RequestParam(value = "categories[]", required = false) List<String> categoriesAlt,
            @RequestParam(value = "excludeCategories", required = false) List<String> excludeCategories,
            @RequestParam(value = "excludeCategories[]", required = false) List<String> excludeCategoriesAlt,
            @RequestParam(value = "filePath", required = false) String filePath,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        String normalizedProjectKey = normalizeProjectKey(projectKey);
        List<String> effectiveCategories = CollectionUtils.isEmpty(categories) ? categoriesAlt : categories;
        List<String> effectiveExclude = CollectionUtils.isEmpty(excludeCategories)
                ? excludeCategoriesAlt
                : excludeCategories;
        String normalizedFileName = StringUtils.hasText(fileName) ? fileName.trim() : null;
        CodeBlockQuery.Builder builder = CodeBlockQuery.builder()
                .projectKey(normalizedProjectKey)
                .comparisonId(normalizedProjectKey)
                .filePath(StringUtils.hasText(filePath) ? filePath : null)
                .fileName(normalizedFileName);
        if (page != null) {
            builder.page(page);
        }
        if (size != null) {
            builder.size(size);
        }
        if (!CollectionUtils.isEmpty(effectiveCategories)) {
            builder.categories(effectiveCategories);
        }
        if (!CollectionUtils.isEmpty(effectiveExclude)) {
            builder.excludeCategories(effectiveExclude);
        }
        CodeBlockDetailView view = viewMapper.toDetailView(
                blockStatsService.findDetail(normalizedProjectKey, blockId, builder.build()));
        if (view == null) {
            log.info("未找到代码块，提示用户重新加载后再试。blockId={}，projectKey={}", blockId, normalizedProjectKey);
            return ApiResponseFactory.error("未找到指定的代码块，请执行重新加载后重试");
        }
        return ApiResponseFactory.ok(view);
    }

    @PostMapping("/code-blocks/generate")
    public ApiResponse<Void> generate(@RequestBody(required = false) BatchOperationRequest request) {
        List<String> blockIds = request == null ? Collections.emptyList() : request.getBlockIds();
        if (CollectionUtils.isEmpty(blockIds)) {
            return ApiResponseFactory.error("\u8bf7\u5148\u9009\u62e9\u9700\u8981\u5904\u7406\u7684\u4ee3\u7801\u5757");
        }
        CodeBlockMigrationService.MigrationOperationResult result =
                agentOrchestrator.generateAnnotatedCopies(blockIds, request.getAnnotationTemplate());
        log.info("\u6279\u91cf\u751f\u6210\u6ce8\u89e3\u64cd\u4f5c\u5b8c\u6210: \u603b\u6570={}, \u6210\u529f={}, \u8df3\u8fc7={}, \u5931\u8d25={}",
                blockIds.size(), result.succeeded(), result.skipped(), result.failures().size());
        result.failures().forEach(message -> log.warn("\u751f\u6210\u5931\u8d25: {}", message));
        return ApiResponseFactory.okMessage(buildOperationMessage("\u751f\u6210", result));
    }

    @PostMapping("/code-blocks/apply")
    public ApiResponse<Void> apply(@RequestBody(required = false) BatchOperationRequest request) {
        List<String> blockIds = request == null ? Collections.emptyList() : request.getBlockIds();
        if (CollectionUtils.isEmpty(blockIds)) {
            return ApiResponseFactory.error("\u8bf7\u5148\u9009\u62e9\u9700\u8981\u5904\u7406\u7684\u4ee3\u7801\u5757");
        }
        CodeBlockMigrationService.MigrationOperationResult result =
                agentOrchestrator.applyAnnotatedCopies(blockIds);
        log.info("\u6279\u91cf\u5e94\u7528\u8fc1\u79fb\u4ee3\u7801\u5b8c\u6210: \u603b\u6570={}, \u6210\u529f={}, \u8df3\u8fc7={}, \u5931\u8d25={}",
                blockIds.size(), result.succeeded(), result.skipped(), result.failures().size());
        result.failures().forEach(message -> log.warn("\u5e94\u7528\u5931\u8d25: {}", message));
        return ApiResponseFactory.okMessage(buildOperationMessage("\u5e94\u7528", result));
    }

    @PostMapping("/code-blocks/undo")
    public ApiResponse<Void> undo(@RequestBody(required = false) BatchOperationRequest request) {
        List<String> blockIds = request == null ? Collections.emptyList() : request.getBlockIds();
        if (CollectionUtils.isEmpty(blockIds)) {
            return ApiResponseFactory.error("\u8bf7\u5148\u9009\u62e9\u9700\u8981\u5904\u7406\u7684\u4ee3\u7801\u5757");
        }
        CodeBlockMigrationService.MigrationOperationResult result =
                agentOrchestrator.revertAnnotatedCopies(blockIds);
        log.info("\u6279\u91cf\u64a4\u9500\u6ce8\u89e3\u64cd\u4f5c\u5b8c\u6210: \u603b\u6570={}, \u6210\u529f={}, \u8df3\u8fc7={}, \u5931\u8d25={}",
                blockIds.size(), result.succeeded(), result.skipped(), result.failures().size());
        result.failures().forEach(message -> log.warn("\u64a4\u9500\u5931\u8d25: {}", message));
        return ApiResponseFactory.okMessage(buildOperationMessage("\u64a4\u9500", result));
    }

    @PostMapping("/code-blocks/ignore")
    public ApiResponse<Void> ignore(@RequestBody(required = false) BatchOperationRequest request) {
        int size = request == null ? 0 : request.size();
        log.info("收到批量忽略请求，代码块数量={}", size);
        return ApiResponseFactory.okMessage("忽略请求已记录，等待后续建设");
    }

    private String normalizeProjectKey(String projectKey) {
        if (!StringUtils.hasText(projectKey)) {
            return null;
        }
        String trimmed = projectKey.trim();
        if ("null".equalsIgnoreCase(trimmed) || "undefined".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private String buildOperationMessage(String action,
                                         CodeBlockMigrationService.MigrationOperationResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append(action)
                .append("\u5b8c\u6210: ")
                .append(result.succeeded())
                .append("/")
                .append(result.requested());
        if (result.skipped() > 0) {
            builder.append(", \u8df3\u8fc7").append(result.skipped());
        }
        if (result.hasFailures()) {
            builder.append(", \u5931\u8d25").append(result.failures().size());
        }
        return builder.toString();
    }

}
