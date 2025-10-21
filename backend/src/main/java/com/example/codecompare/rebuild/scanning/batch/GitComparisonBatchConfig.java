package com.example.codecompare.rebuild.scanning.batch;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Configuration descriptor for batch Git comparison exports.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitComparisonBatchConfig {

    @JsonProperty("sources")
    private List<ProjectEntry> sources = new ArrayList<>();

    @JsonProperty("targets")
    private List<ProjectEntry> targets = new ArrayList<>();

    @JsonProperty("pairs")
    private List<PairEntry> pairs = new ArrayList<>();

    @JsonProperty("git-base-ref-source")
    private String gitBaseRefSource;

    @JsonProperty("git-target-ref-source")
    private String gitTargetRefSource;

    @JsonProperty("git-base-ref-target")
    private String gitBaseRefTarget;

    @JsonProperty("git-target-ref-target")
    private String gitTargetRefTarget;

    public List<ProjectEntry> getSources() {
        return sources == null ? Collections.emptyList() : Collections.unmodifiableList(sources);
    }

    public void setSources(List<ProjectEntry> sources) {
        this.sources = sources == null ? new ArrayList<>() : new ArrayList<>(sources);
    }

    public List<ProjectEntry> getTargets() {
        return targets == null ? Collections.emptyList() : Collections.unmodifiableList(targets);
    }

    public void setTargets(List<ProjectEntry> targets) {
        this.targets = targets == null ? new ArrayList<>() : new ArrayList<>(targets);
    }

    public List<PairEntry> getPairs() {
        return pairs == null ? Collections.emptyList() : Collections.unmodifiableList(pairs);
    }

    public void setPairs(List<PairEntry> pairs) {
        this.pairs = pairs == null ? new ArrayList<>() : new ArrayList<>(pairs);
    }

    public String getGitBaseRefSource() {
        return gitBaseRefSource;
    }

    public void setGitBaseRefSource(String gitBaseRefSource) {
        this.gitBaseRefSource = gitBaseRefSource;
    }

    public String getGitTargetRefSource() {
        return gitTargetRefSource;
    }

    public void setGitTargetRefSource(String gitTargetRefSource) {
        this.gitTargetRefSource = gitTargetRefSource;
    }

    public String getGitBaseRefTarget() {
        return gitBaseRefTarget;
    }

    public void setGitBaseRefTarget(String gitBaseRefTarget) {
        this.gitBaseRefTarget = gitBaseRefTarget;
    }

    public String getGitTargetRefTarget() {
        return gitTargetRefTarget;
    }

    public void setGitTargetRefTarget(String gitTargetRefTarget) {
        this.gitTargetRefTarget = gitTargetRefTarget;
    }

    public static final class ProjectEntry {
        @JsonProperty("code")
        private String code;

        @JsonProperty("path")
        private String path;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        @Override
        public String toString() {
            return "ProjectEntry{" +
                    "code='" + code + '\'' +
                    ", path='" + path + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ProjectEntry)) {
                return false;
            }
            ProjectEntry that = (ProjectEntry) o;
            return Objects.equals(code, that.code) && Objects.equals(path, that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(code, path);
        }
    }

    public static final class PairEntry {
        @JsonProperty("source")
        private ProjectEntry source;

        @JsonProperty("target")
        private ProjectEntry target;

        @JsonProperty("git-base-ref-source")
        private String gitBaseRefSource;

        @JsonProperty("git-target-ref-source")
        private String gitTargetRefSource;

        @JsonProperty("git-base-ref-target")
        private String gitBaseRefTarget;

        @JsonProperty("git-target-ref-target")
        private String gitTargetRefTarget;

        public ProjectEntry getSource() {
            return source;
        }

        public void setSource(ProjectEntry source) {
            this.source = source;
        }

        public ProjectEntry getTarget() {
            return target;
        }

        public void setTarget(ProjectEntry target) {
            this.target = target;
        }

        public String getGitBaseRefSource() {
            return gitBaseRefSource;
        }

        public void setGitBaseRefSource(String gitBaseRefSource) {
            this.gitBaseRefSource = gitBaseRefSource;
        }

        public String getGitTargetRefSource() {
            return gitTargetRefSource;
        }

        public void setGitTargetRefSource(String gitTargetRefSource) {
            this.gitTargetRefSource = gitTargetRefSource;
        }

        public String getGitBaseRefTarget() {
            return gitBaseRefTarget;
        }

        public void setGitBaseRefTarget(String gitBaseRefTarget) {
            this.gitBaseRefTarget = gitBaseRefTarget;
        }

        public String getGitTargetRefTarget() {
            return gitTargetRefTarget;
        }

        public void setGitTargetRefTarget(String gitTargetRefTarget) {
            this.gitTargetRefTarget = gitTargetRefTarget;
        }
    }
}
