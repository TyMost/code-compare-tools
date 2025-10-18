package com.example.codecompare.rebuild.scanning;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 扫描请求对象，封装项目编码、扫描根目录以及忽略规则等上下文信息。
 */
public final class ProjectScanRequest {

    private final String projectCode;
    private final List<Path> projectRoots;
    private final List<String> ignoreGlobs;
    private final boolean skipHidden;
    private final Instant requestedAt;

    private ProjectScanRequest(Builder builder) {
        this.projectCode = Objects.requireNonNull(builder.projectCode, "projectCode must not be null");
        this.projectRoots = Collections.unmodifiableList(new ArrayList<>(builder.projectRoots));
        this.ignoreGlobs = Collections.unmodifiableList(new ArrayList<>(builder.ignoreGlobs));
        this.skipHidden = builder.skipHidden;
        this.requestedAt = builder.requestedAt == null ? Instant.now() : builder.requestedAt;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public List<Path> getProjectRoots() {
        return projectRoots;
    }

    public List<String> getIgnoreGlobs() {
        return ignoreGlobs;
    }

    public boolean isSkipHidden() {
        return skipHidden;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder().from(this);
    }

    public static final class Builder {

        private String projectCode;
        private final List<Path> projectRoots = new ArrayList<>();
        private final List<String> ignoreGlobs = new ArrayList<>();
        private boolean skipHidden = true;
        private Instant requestedAt;

        private Builder() {
        }

        private Builder from(ProjectScanRequest request) {
            this.projectCode = request.projectCode;
            this.projectRoots.addAll(request.projectRoots);
            this.ignoreGlobs.addAll(request.ignoreGlobs);
            this.skipHidden = request.skipHidden;
            this.requestedAt = request.requestedAt;
            return this;
        }

        public Builder projectCode(String projectCode) {
            this.projectCode = projectCode;
            return this;
        }

        public Builder addRoot(Path root) {
            if (root != null) {
                this.projectRoots.add(root.normalize().toAbsolutePath());
            }
            return this;
        }

        public Builder addRoot(String root) {
            if (root != null) {
                addRoot(Paths.get(root));
            }
            return this;
        }

        public Builder roots(List<Path> roots) {
            this.projectRoots.clear();
            if (!CollectionUtils.isEmpty(roots)) {
                roots.forEach(this::addRoot);
            }
            return this;
        }

        public Builder ignoreGlobs(List<String> globs) {
            this.ignoreGlobs.clear();
            if (!CollectionUtils.isEmpty(globs)) {
                this.ignoreGlobs.addAll(globs);
            }
            return this;
        }

        public Builder addIgnoreGlob(String glob) {
            if (glob != null && !glob.trim().isEmpty()) {
                this.ignoreGlobs.add(glob);
            }
            return this;
        }

        public Builder skipHidden(boolean skipHidden) {
            this.skipHidden = skipHidden;
            return this;
        }

        public Builder requestedAt(Instant requestedAt) {
            this.requestedAt = requestedAt;
            return this;
        }

        public ProjectScanRequest build() {
            Assert.hasText(projectCode, "projectCode must not be empty");
            Assert.state(!projectRoots.isEmpty(), "projectRoots must not be empty");
            return new ProjectScanRequest(this);
        }
    }
}
