package com.example.codecompare.rebuild.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 核心属性绑定入口，对应 {@code migration} 前缀的配置项。
 * <p>该结构聚合项目根目录、Git 开关、规则加载与输出目录等设置，便于跨模块统一读取。</p>
 */
@ConfigurationProperties(prefix = "migration")
public class ApplicationProperties {

    private final ProjectRootsProperties project = new ProjectRootsProperties();
    private final GitToggleProperties git = new GitToggleProperties();
    private final RuleRegistryProperties rules = new RuleRegistryProperties();
    private final OutputProperties output = new OutputProperties();

    /**
     * 获取项目根目录配置，兼容旧版 {@code migration.project-roots} 数组。
     */
    public ProjectRootsProperties getProject() {
        return project;
    }

    /**
     * 便捷方法：直接返回当前声明的项目根目录列表。
     */
    public List<String> getProjectRoots() {
        return project.getRoots();
    }

    public GitToggleProperties getGit() {
        return git;
    }

    public RuleRegistryProperties getRules() {
        return rules;
    }

    public OutputProperties getOutput() {
        return output;
    }

    /**
     * 项目根目录配置，用于限制扫描、迁移的路径边界。
     */
    public static class ProjectRootsProperties {
        private final List<String> roots = new ArrayList<>();
        private final List<ProjectRootConfig> sources = new ArrayList<>();
        private final List<ProjectRootConfig> targets = new ArrayList<>();

        public List<String> getRoots() {
            return Collections.unmodifiableList(roots);
        }

        public void setRoots(List<String> values) {
            roots.clear();
            if (CollectionUtils.isEmpty(values)) {
                return;
            }
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    roots.add(value.trim());
                }
            }
        }

        public List<ProjectRootConfig> getSources() {
            return Collections.unmodifiableList(sources);
        }

        public void setSources(List<ProjectRootConfig> values) {
            sources.clear();
            if (CollectionUtils.isEmpty(values)) {
                return;
            }
            for (ProjectRootConfig value : values) {
                if (value != null) {
                    sources.add(value);
                }
            }
        }

        public List<ProjectRootConfig> getTargets() {
            return Collections.unmodifiableList(targets);
        }

        public void setTargets(List<ProjectRootConfig> values) {
            targets.clear();
            if (CollectionUtils.isEmpty(values)) {
                return;
            }
            for (ProjectRootConfig value : values) {
                if (value != null) {
                    targets.add(value);
                }
            }
        }

        public boolean hasStructuredRoots() {
            return !sources.isEmpty() || !targets.isEmpty();
        }

        public boolean isEmpty() {
            return roots.isEmpty() && sources.isEmpty() && targets.isEmpty();
        }
    }

    public static class ProjectRootConfig {
        private String code;
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
    }

    /**
     * Git 比较开关配置，用于控制增量扫描策略。
     */
    public static class GitToggleProperties {
        private boolean enabled = true;
        private String baseBranch = "main";
        private String compareBranch = "feature/migration";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseBranch() {
            return baseBranch;
        }

        public void setBaseBranch(String baseBranch) {
            this.baseBranch = baseBranch;
        }

        public String getCompareBranch() {
            return compareBranch;
        }

        public void setCompareBranch(String compareBranch) {
            this.compareBranch = compareBranch;
        }
    }

    /**
     * 规则加载配置，统一管理规则文件路径与是否实时刷新。
     */
    public static class RuleRegistryProperties {
        private String path = "config/rules.yaml";
        private boolean reloadOnChange = false;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isReloadOnChange() {
            return reloadOnChange;
        }

        public void setReloadOnChange(boolean reloadOnChange) {
            this.reloadOnChange = reloadOnChange;
        }
    }

    /**
     * 输出目录配置，用于存放报表或导出结果。
     */
    public static class OutputProperties {
        private String reportDirectory = "build/reports/migration";

        public String getReportDirectory() {
            return reportDirectory;
        }

        public void setReportDirectory(String reportDirectory) {
            this.reportDirectory = reportDirectory;
        }
    }
}
