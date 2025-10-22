package com.example.codecompare.rebuild.scanning;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时项目注册表，用于批量导出等一次性任务在运行期动态声明项目代码与路径的映射。
 * 不会写入持久化配置，导出完成后可清空。
 */
@Component
public class TransientProjectRegistry {

    private final Map<String, TransientProjectDescriptor> descriptors = new ConcurrentHashMap<>();

    public void register(String code, String rawPath) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(rawPath)) {
            return;
        }
        Path normalized = Paths.get(rawPath).toAbsolutePath().normalize();
        descriptors.put(code.trim().toLowerCase(), new TransientProjectDescriptor(code.trim(), normalized));
    }

    public Optional<TransientProjectDescriptor> findByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        return Optional.ofNullable(descriptors.get(code.trim().toLowerCase()));
    }

    public Collection<TransientProjectDescriptor> getAllDescriptors() {
        return Collections.unmodifiableCollection(descriptors.values());
    }

    public void clear() {
        descriptors.clear();
    }

    public static final class TransientProjectDescriptor {
        private final String code;
        private final Path path;

        private TransientProjectDescriptor(String code, Path path) {
            this.code = code;
            this.path = path;
        }

        public String getCode() {
            return code;
        }

        public Path getPath() {
            return path;
        }
    }
}
