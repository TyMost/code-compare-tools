package com.example.migratediff.infrastructure.persistence.filesystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * 文件操作公共方法，负责创建目录、原子写入等细节。
 */
public class FileStorageSupport {

    private static final Logger log = LoggerFactory.getLogger(FileStorageSupport.class);

    private final Path rootPath;
    private final ObjectMapper objectMapper;

    public FileStorageSupport(Path rootPath, ObjectMapper objectMapper) {
        this.rootPath = rootPath;
        this.objectMapper = objectMapper;
        initRootDirectory();
    }

    private void initRootDirectory() {
        try {
            Files.createDirectories(rootPath);
        } catch (IOException e) {
            throw new FilePersistenceException("初始化文件存储根目录失败: " + rootPath, e);
        }
    }

    public Path resolve(String first, String... more) {
        Path relative = more == null || more.length == 0 ? Paths.get(first) : Paths.get(first, more);
        return rootPath.resolve(relative).normalize();
    }

    public void ensureDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new FilePersistenceException("创建目录失败: " + directory, e);
        }
    }

    public void writeJson(Path filePath, Object value) {
        ensureDirectory(filePath.getParent());
        Path tempFile = filePath.getParent().resolve(filePath.getFileName().toString() + ".tmp");
        try (OutputStream outputStream = Files.newOutputStream(tempFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            objectMapper.writeValue(outputStream, value);
        } catch (IOException e) {
            throw new FilePersistenceException("写入临时文件失败: " + tempFile, e);
        }

        try {
            Files.move(tempFile, filePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicError) {
            // Windows 某些文件系统不支持 ATOMIC_MOVE，此时回退到普通替换。
            log.warn("原子移动失败，回退到普通替换: {}", filePath);
            try {
                Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new FilePersistenceException("替换文件失败: " + filePath, e);
            }
        }
    }

    public <T> Optional<T> readJson(Path filePath, Class<T> type) {
        if (!Files.exists(filePath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(filePath.toFile(), type));
        } catch (IOException e) {
            throw new FilePersistenceException("读取文件失败: " + filePath, e);
        }
    }

    public void deleteIfExists(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            throw new FilePersistenceException("删除文件失败: " + filePath, e);
        }
    }
}
