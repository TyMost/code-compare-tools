package com.example.codecompare.rebuild.agent.migration.io;

import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry.ProjectRootDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Resolves project-root related paths and reads project files within configured roots.
 */
@Component
public class ProjectFileResolver {

    private static final Logger log = LoggerFactory.getLogger(ProjectFileResolver.class);

    private final ProjectRootRegistry projectRootRegistry;

    public ProjectFileResolver(ProjectRootRegistry projectRootRegistry) {
        this.projectRootRegistry = projectRootRegistry;
    }

    public Path resolveProjectRoot(String projectCode) {
        if (!StringUtils.hasText(projectCode)) {
            throw new IllegalStateException("Target project code is required to resolve the destination path");
        }
        Optional<ProjectRootDescriptor> descriptor = projectRootRegistry.findByCode(projectCode);
        if (descriptor.isPresent()) {
            return descriptor.get().getPath();
        }
        Path fallback = projectRootRegistry.getRoots().stream()
                .filter(path -> path.getFileName() != null
                        && projectCode.equalsIgnoreCase(path.getFileName().toString()))
                .findFirst()
                .orElse(null);
        if (fallback != null) {
            log.warn("Falling back to directory-name lookup for projectCode={}, please configure a unique code", projectCode);
            return fallback;
        }
        throw new IllegalStateException("Project root not registered: " + projectCode);
    }

    public Path resolveTargetFile(String projectCode, String relativePath) {
        Path targetRoot = resolveProjectRoot(projectCode);
        Path relative = Paths.get(relativePath);
        Path targetFile = targetRoot.resolve(relative).normalize();
        projectRootRegistry.ensureInsideRoots(targetFile);
        return targetFile;
    }

    public String readProjectFile(String projectCode, String relativePath) throws IOException {
        if (!StringUtils.hasText(projectCode) || !StringUtils.hasText(relativePath)) {
            return null;
        }
        Path resolved = resolveTargetFile(projectCode, relativePath);
        if (!Files.exists(resolved)) {
            return null;
        }
        byte[] bytes = Files.readAllBytes(resolved);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

