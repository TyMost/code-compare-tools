package com.example.migratediff.infrastructure.persistence.filesystem;

import com.example.migratediff.domain.repo.RepoBranch;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.domain.repo.RepoPath;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 仓库配置标识生成器，将路径等信息编码成文件系统安全的 ID。
 */
public class FileRepoKeyResolver {

    public String resolveKey(RepoConfig config) {
        RepoPath path = config.getRepoPath();
        if (path == null || !StringUtils.hasText(path.getAbsolutePath())) {
            throw new MissingIdentifierException("RepoConfig 缺少仓库路径，无法生成 ID");
        }
        RepoBranch from = config.getBranchFrom();
        RepoBranch to = config.getBranchTo();
        StringBuilder builder = new StringBuilder();
        builder.append(path.getAbsolutePath());
        builder.append('|').append(from != null ? from.getName() : "");
        builder.append('|').append(to != null ? to.getName() : "");
        if (config.getDeltaType() != null) {
            builder.append('|').append(config.getDeltaType().name());
        }
        return encode(builder.toString());
    }

    private String encode(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new FilePersistenceException("生成仓库 ID 失败: SHA-256 不可用", e);
        }
    }
}
