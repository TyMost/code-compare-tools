package com.example.codecompare.rebuild.api.controller;

import com.example.codecompare.rebuild.api.dto.AgentSuggestionView;
import com.example.codecompare.rebuild.api.mapper.MigrationViewMapper;
import com.example.codecompare.rebuild.api.response.ApiResponse;
import com.example.codecompare.rebuild.api.response.ApiResponseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 接口占位，当前环境使用本地两步式处理，因此仅返回建设提示。
 */
@RestController
@RequestMapping("/api/v1/migration")
public class MigrationAgentController {

    private static final Logger log = LoggerFactory.getLogger(MigrationAgentController.class);
    private final MigrationViewMapper viewMapper;

    public MigrationAgentController(MigrationViewMapper viewMapper) {
        this.viewMapper = viewMapper;
    }

    @GetMapping("/code-blocks/{id}/ai-suggestion")
    public ApiResponse<AgentSuggestionView> fetchSuggestion(@PathVariable("id") String blockId) {
        log.info("查询代码块 {} 的 AI 建议，占位实现返回提示", blockId);
        AgentSuggestionView view = viewMapper.toAgentSuggestionView(blockId, "等待后续建设");
        return ApiResponseFactory.ok(view);
    }

    @PostMapping("/agent/execute")
    public ApiResponse<Void> triggerAgent(@RequestBody(required = false) Object payload) {
        log.info("收到本地 Agent 执行请求，参数：{}", payload);
        return ApiResponseFactory.okMessage("本地 Agent 功能等待后续建设");
    }
}
