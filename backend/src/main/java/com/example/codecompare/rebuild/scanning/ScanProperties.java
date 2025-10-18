package com.example.codecompare.rebuild.scanning;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 扫描相关配置项，覆盖默认忽略规则与线程池大小等参数。<br/>
 * 可通过 {@code migration.scan.*} 在外部进行重写。
 */
@ConfigurationProperties(prefix = "migration.scan")
public class ScanProperties {

    private int fingerprintThreads = Math.max(Runtime.getRuntime().availableProcessors(), 4);
    private List<String> ignoreGlobs = new ArrayList<>();
    private boolean enableHashCache = true;
    private int batchSize = 500;
    private boolean followSymlinks = false;

    public int getFingerprintThreads() {
        return fingerprintThreads;
    }

    public void setFingerprintThreads(int fingerprintThreads) {
        this.fingerprintThreads = fingerprintThreads;
    }

    public List<String> getIgnoreGlobs() {
        return Collections.unmodifiableList(ignoreGlobs);
    }

    public void setIgnoreGlobs(List<String> ignoreGlobs) {
        this.ignoreGlobs = ignoreGlobs == null
                ? new ArrayList<>()
                : new ArrayList<>(ignoreGlobs);
    }

    public boolean isEnableHashCache() {
        return enableHashCache;
    }

    public void setEnableHashCache(boolean enableHashCache) {
        this.enableHashCache = enableHashCache;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public boolean isFollowSymlinks() {
        return followSymlinks;
    }

    public void setFollowSymlinks(boolean followSymlinks) {
        this.followSymlinks = followSymlinks;
    }
}
