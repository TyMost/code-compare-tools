package com.example.migratediff.infrastructure.git;

import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Enable Apache MINA SSH provider so OpenSSH 格式的私钥能够被 JGit 识别.
 *
 * 具体私钥/known_hosts 可通过标准的 ~/.ssh/config 控制。
 */
@Configuration
@ConditionalOnProperty(prefix = "migratediff.ssh", name = "enabled", havingValue = "true")
public class SshSupportConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(SshSupportConfiguration.class);

    @PostConstruct
    public void init() {
        LOGGER.info("Enabling Apache SSHD session factory for migratediff");
        java.io.File homeDir = resolveHomeDirectory();
        SshdSessionFactoryBuilder builder = new SshdSessionFactoryBuilder()
                .setPreferredAuthentications("publickey,password")
                .setHomeDirectory(homeDir)
                .setSshDirectory(new java.io.File(homeDir, ".ssh"));
        SshdSessionFactory factory = builder.build(null);
        SshSessionFactory.setInstance(factory);
    }

    private java.io.File resolveHomeDirectory() {
        String rawHome = System.getProperty("user.home");
        if (rawHome == null || rawHome.trim().isEmpty()) {
            LOGGER.warn("System property user.home is not set, fallback to current directory");
            return new java.io.File(".").getAbsoluteFile();
        }
        return new java.io.File(rawHome);
    }
}
