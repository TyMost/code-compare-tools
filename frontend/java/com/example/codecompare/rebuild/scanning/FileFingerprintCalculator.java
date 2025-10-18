package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.repository.FileMetadataRepository;
import com.example.codecompare.rebuild.repository.model.FileChangeType;
import com.example.codecompare.rebuild.repository.model.FileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 文件指纹计算器，负责读取文件属性、计算哈希并构造 {@link FileRecord}。
 */
public class FileFingerprintCalculator implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(FileFingerprintCalculator.class);

    private final FileMetadataRepository fileMetadataRepository;
    private final ScanProperties properties;
    private final Clock clock;
    private final ExecutorService executor;

    public FileFingerprintCalculator(FileMetadataRepository fileMetadataRepository,
                                     ScanProperties properties,
                                     Clock clock) {
        this.fileMetadataRepository = fileMetadataRepository;
        this.properties = properties;
        this.clock = clock;
        this.executor = Executors.newFixedThreadPool(
                Math.max(1, properties.getFingerprintThreads()),
                new NamedThreadFactory("scan-fingerprint"));
    }

    public List<FileRecord> calculate(String projectCode,
                                      Path root,
                                      List<Path> files,
                                      Instant scanTime) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }
        List<CompletableFuture<FileRecord>> tasks = new ArrayList<>(files.size());
        for (Path file : files) {
            tasks.add(CompletableFuture.supplyAsync(
                    () -> computeFingerprint(projectCode, root, file, scanTime),
                    executor));
        }
        return tasks.stream()
                .map(future -> {
                    try {
                        return future.join();
                    } catch (Exception ex) {
                        log.warn("文件指纹计算失败，将忽略该文件: {}", ex.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private FileRecord computeFingerprint(String projectCode,
                                          Path root,
                                          Path file,
                                          Instant scanTime) {
        String relativePath = relativePath(root, file);
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            long size = attrs.size();
            Instant lastModified = attrs.lastModifiedTime().toInstant();

            Optional<FileRecord> previous = properties.isEnableHashCache()
                    ? fileMetadataRepository.findLatest(projectCode, relativePath)
                    : Optional.empty();

            String previousHash = previous.map(FileRecord::getContentHash).orElse(null);
            String contentHash;
            FileChangeType changeType;

            if (previous.isPresent()
                    && previous.get().getSizeInBytes() == size
                    && Objects.equals(previous.get().getLastModified(), lastModified)) {
                contentHash = previousHash;
                changeType = FileChangeType.UNCHANGED;
            } else {
                contentHash = calculateHash(file);
                changeType = previous.isPresent() ? FileChangeType.MODIFIED : FileChangeType.NEW;
            }

            return FileRecord.builder()
                    .projectCode(projectCode)
                    .path(relativePath)
                    .language(detectLanguage(relativePath))
                    .contentHash(contentHash)
                    .previousHash(previousHash)
                    .sizeInBytes(size)
                    .lastModified(lastModified)
                    .scannedAt(scanTime == null ? clock.instant() : scanTime)
                    .changeType(changeType)
                    .build();
        } catch (IOException ex) {
            log.warn("读取文件属性失败，跳过扫描：{}，错误：{}", relativePath, ex.getMessage());
            return null;
        }
    }

    private String calculateHash(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = Files.newInputStream(file);
             DigestInputStream dis = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
                // 仅用于推进流
            }
        }
        return toHex(digest.digest());
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("平台不支持 SHA-256 哈希算法", ex);
        }
    }

    private String relativePath(Path root, Path file) {
        Path relative = root.relativize(file);
        return relative.toString().replace('\\', '/');
    }

    private String detectLanguage(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return "plain";
        }
        String lower = relativePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) {
            return "java";
        }
        if (lower.endsWith(".js")) {
            return "javascript";
        }
        if (lower.endsWith(".ts")) {
            return "typescript";
        }
        if (lower.endsWith(".tsx")) {
            return "tsx";
        }
        if (lower.endsWith(".jsx")) {
            return "jsx";
        }
        if (lower.endsWith(".py")) {
            return "python";
        }
        if (lower.endsWith(".go")) {
            return "go";
        }
        if (lower.endsWith(".vue")) {
            return "vue";
        }
        if (lower.endsWith(".css")) {
            return "css";
        }
        if (lower.endsWith(".scss")) {
            return "scss";
        }
        if (lower.endsWith(".md")) {
            return "markdown";
        }
        return "plain";
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            String hex = Integer.toHexString(value & 0xFF);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }

    private static final class NamedThreadFactory implements ThreadFactory {

        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
