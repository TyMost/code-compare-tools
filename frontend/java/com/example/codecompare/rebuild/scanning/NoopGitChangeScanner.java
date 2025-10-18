package com.example.codecompare.rebuild.scanning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 默认的 Git 增量扫描占位实现，直接返回空结果以保证主流程可用。
 */
@Component
public class NoopGitChangeScanner implements GitChangeScanner {

    private static final Logger log = LoggerFactory.getLogger(NoopGitChangeScanner.class);

    @Override
    public GitIncrementalResult scanIncremental(ProjectScanRequest request) {
        log.info("Git 增量扫描暂未实现，返回空结果，项目：{}", request == null ? "" : request.getProjectCode());
        if (request == null) {
            return GitIncrementalResult.empty("default");
        }
        return GitIncrementalResult.empty(request.getProjectCode());
    }
}
