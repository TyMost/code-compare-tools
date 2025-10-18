package com.example.codecompare.rebuild.core.support;

import com.example.codecompare.rebuild.core.properties.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 项目根目录注册表，负责路径归一化与安全校验。
 */
public class ProjectRootRegistry {

    private static final Logger log = LoggerFactory.getLogger(ProjectRootRegistry.class);

    private final List<ProjectRootDescriptor> descriptors;
    private final List<ProjectRootDescriptor> sources;
    private final List<ProjectRootDescriptor> targets;
    private final List<Path> roots;
    private final Map<String, ProjectRootDescriptor> descriptorIndex;

    public ProjectRootRegistry(ApplicationProperties properties) {
        List<ProjectRootDescriptor> resolved = resolveDescriptors(properties);
        this.descriptors = Collections.unmodifiableList(resolved);
        this.sources = Collections.unmodifiableList(resolved.stream()
                .filter(descriptor -> descriptor.getType() == ProjectRootType.SOURCE)
                .collect(Collectors.toList()));
        this.targets = Collections.unmodifiableList(resolved.stream()
                .filter(descriptor -> descriptor.getType() == ProjectRootType.TARGET)
                .collect(Collectors.toList()));
        this.roots = Collections.unmodifiableList(resolved.stream()
                .map(ProjectRootDescriptor::getPath)
                .collect(Collectors.toList()));
        this.descriptorIndex = resolved.stream()
                .filter(descriptor -> StringUtils.hasText(descriptor.getCode()))
                .collect(Collectors.toMap(
                        descriptor -> descriptor.getCode().toLowerCase(Locale.ROOT),
                        descriptor -> descriptor,
                        (existing, duplicate) -> existing,
                        LinkedHashMap::new));
        log.info("载入项目根目录：{}", roots);
    }

    /**
     * 归一化后的根目录列表（兼容旧接口）。
     */
    public List<Path> getRoots() {
        return roots;
    }

    public List<ProjectRootDescriptor> getDescriptors() {
        return descriptors;
    }

    public List<ProjectRootDescriptor> getSources() {
        return sources;
    }

    public List<ProjectRootDescriptor> getTargets() {
        return targets;
    }

    public Optional<ProjectRootDescriptor> findByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return Optional.empty();
        }
        return Optional.ofNullable(descriptorIndex.get(code.toLowerCase(Locale.ROOT)));
    }

    /**
     * 判断给定路径是否在受信任根目录内。
     */
    public boolean isAllowed(Path candidate) {
        if (candidate == null) {
            return false;
        }
        Path normalized = candidate.toAbsolutePath().normalize();
        return findMatchedRoot(normalized).isPresent();
    }

    /**
     * 校验路径合法性，不合法时抛出异常。
     */
    public Path ensureInsideRoots(Path candidate) {
        if (!isAllowed(candidate)) {
            throw new IllegalArgumentException("路径不在受信任的项目根目录内: " + candidate);
        }
        return candidate.toAbsolutePath().normalize();
    }

    private Optional<Path> findMatchedRoot(Path normalized) {
        for (Path root : roots) {
            if (normalized.startsWith(root)) {
                return Optional.of(root);
            }
        }
        return Optional.empty();
    }

    private List<ProjectRootDescriptor> resolveDescriptors(ApplicationProperties properties) {
        ApplicationProperties.ProjectRootsProperties project = properties.getProject();
        if (project != null && project.hasStructuredRoots()) {
            return resolveStructured(project);
        }
        return resolveLegacy(project);
    }

    private List<ProjectRootDescriptor> resolveStructured(ApplicationProperties.ProjectRootsProperties project) {
        List<ProjectRootDescriptor> resolved = new ArrayList<>();
        List<ApplicationProperties.ProjectRootConfig> sourceConfigs = project.getSources();
        List<ApplicationProperties.ProjectRootConfig> targetConfigs = project.getTargets();
        if (CollectionUtils.isEmpty(sourceConfigs) || CollectionUtils.isEmpty(targetConfigs)) {
            throw new IllegalStateException("必须同时配置至少一个源项目根目录和一个目标项目根目录");
        }
        LinkedHashMap<String, ProjectRootDescriptor> seenCodes = new LinkedHashMap<>();
        List<Path> seenPaths = new ArrayList<>();
        sourceConfigs.forEach(config -> resolved.add(buildDescriptor(config, ProjectRootType.SOURCE, seenCodes, seenPaths)));
        targetConfigs.forEach(config -> resolved.add(buildDescriptor(config, ProjectRootType.TARGET, seenCodes, seenPaths)));
        return resolved;
    }

    private ProjectRootDescriptor buildDescriptor(ApplicationProperties.ProjectRootConfig config,
                                                  ProjectRootType type,
                                                  Map<String, ProjectRootDescriptor> seenCodes,
                                                  List<Path> seenPaths) {
        if (config == null) {
            throw new IllegalStateException("项目根目录配置项不能为空");
        }
        String code = config.getCode();
        String rawPath = config.getPath();
        if (!StringUtils.hasText(code)) {
            throw new IllegalStateException("项目根目录配置缺少 code");
        }
        if (!StringUtils.hasText(rawPath)) {
            throw new IllegalStateException("项目根目录配置缺少 path");
        }
        Path normalized = Paths.get(rawPath).toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            throw new IllegalStateException("项目根目录不存在: " + normalized);
        }
        String loweredCode = code.toLowerCase(Locale.ROOT);
        if (seenCodes.containsKey(loweredCode)) {
            throw new IllegalStateException("重复的项目 code: " + code);
        }
        for (Path existing : seenPaths) {
            if (normalized.equals(existing)) {
                throw new IllegalStateException("重复的项目路径: " + normalized);
            }
            if (normalized.startsWith(existing) || existing.startsWith(normalized)) {
                throw new IllegalStateException("项目路径存在包含关系: " + normalized + " 与 " + existing);
            }
        }
        ProjectRootDescriptor descriptor = new ProjectRootDescriptor(code.trim(), normalized, type);
        seenCodes.put(loweredCode, descriptor);
        seenPaths.add(normalized);
        return descriptor;
    }

    private List<ProjectRootDescriptor> resolveLegacy(ApplicationProperties.ProjectRootsProperties project) {
        List<ProjectRootDescriptor> resolved = new ArrayList<>();
        List<String> configured = project == null ? Collections.emptyList() : project.getRoots();
        if (!CollectionUtils.isEmpty(configured)) {
            for (String item : configured) {
                if (!StringUtils.hasText(item)) {
                    continue;
                }
                Path path = Paths.get(item).toAbsolutePath().normalize();
                resolved.add(new ProjectRootDescriptor(null, path, ProjectRootType.LEGACY));
            }
            log.warn("检测到 legacy 的 migration.project.roots 配置，请升级至 sources/targets 结构以获得更精确的迁移行为");
        }
        if (resolved.isEmpty()) {
            Path current = Paths.get(".").toAbsolutePath().normalize();
            resolved.add(new ProjectRootDescriptor(null, current, ProjectRootType.LEGACY));
        }
        return resolved;
    }

    public enum ProjectRootType {
        SOURCE,
        TARGET,
        LEGACY
    }

    public static final class ProjectRootDescriptor {
        private final String code;
        private final Path path;
        private final ProjectRootType type;

        private ProjectRootDescriptor(String code, Path path, ProjectRootType type) {
            this.code = code;
            this.path = path;
            this.type = type;
        }

        public String getCode() {
            return code;
        }

        public Path getPath() {
            return path;
        }

        public ProjectRootType getType() {
            return type;
        }

        @Override
        public String toString() {
            return "ProjectRootDescriptor{" +
                    "code='" + code + '\'' +
                    ", path=" + path +
                    ", type=" + type +
                    '}';
        }
    }
}
