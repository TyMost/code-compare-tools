package com.example.migratediff.infrastructure.git;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * SSH 相关配置，供 JGit 使用。
 */
@Data
@Component
@ConfigurationProperties(prefix = "migratediff.ssh")
public class SshSupportProperties {

    /**
     * 是否启用自定义 SSH Session 工厂。
     */
    private boolean enabled = false;

    /**
     * 私钥文件路径，例如 C:/Users/foo/.ssh/id_rsa。
     */
    private String privateKey;

    /**
     * 私钥口令（如有）。
     */
    private String passphrase;

    /**
     * known_hosts 文件路径。
     */
    private String knownHosts;

    /**
     * 是否跳过 host key 检查，默认 false。
     */
    private boolean strictHostKeyChecking = true;
}
