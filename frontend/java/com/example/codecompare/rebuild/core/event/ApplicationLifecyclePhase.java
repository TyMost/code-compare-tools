package com.example.codecompare.rebuild.core.event;

/**
 * 应用生命周期阶段枚举，统一标识核心模块发布的事件类型。
 */
public enum ApplicationLifecyclePhase {

    /**
     * 应用启动完成，核心依赖已经就绪。
     */
    STARTUP,

    /**
     * 应用准备关闭，用于收尾清理。
     */
    SHUTDOWN,

    /**
     * 扫描流程结束，供统计与 Agent 监听。
     */
    SCAN_COMPLETED,

    /**
     * 迁移流程结束。
     */
    MIGRATION_COMPLETED
}
