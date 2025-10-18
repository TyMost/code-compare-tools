package com.example.codecompare.rebuild.scanning;

/**
 * Git 增量扫描接口，预留后续真实实现的扩展点。
 */
public interface GitChangeScanner {

    /**
     * 执行增量扫描，返回增量结果。当前实现返回空对象以兼容主流程。
     */
    GitIncrementalResult scanIncremental(ProjectScanRequest request);
}
