package com.example.codecompare.rebuild.agent;

/**
 * 迁移 Agent 接口，定义本地或远程实现需要提供的建议能力。
 * <p>当前仅支持返回占位建议，后续可接入真实 AI 服务。</p>
 */
public interface MigrationAgent {

    /**
     * 基于给定上下文生成迁移建议。
     *
     * @param context 代码块与项目上下文
     * @return 迁移建议结果
     */
    AgentSuggestion generateSuggestion(AgentTaskContext context);
}
