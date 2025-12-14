package com.example.migratediff.infrastructure.persistence.filesystem;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * File storage configuration bound from file.storage.* properties.
 */
@ConfigurationProperties(prefix = "file.storage")
public class StorageProperties {

    /**
     * 是否启用文件持久化，默认开启。
     */
    private boolean enabled = true;

    /**
     * 根目录，默认放在用户目录下的 .migratediff/storage。
     */
    private String rootPath = Paths.get(System.getProperty("user.home"), ".migratediff", "storage").toString();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRootPath() {
        return rootPath;
    }

    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }

    public Path resolveRootPath() {
        return Paths.get(rootPath).toAbsolutePath().normalize();
    }
}
