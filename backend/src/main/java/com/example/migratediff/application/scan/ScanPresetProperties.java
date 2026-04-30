package com.example.migratediff.application.scan;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Holds configured scan presets loaded from application properties.
 */
@Data
@Component
@ConfigurationProperties(prefix = "migratediff")
public class ScanPresetProperties {

    private List<ScanPreset> presets = new ArrayList<>();

    public Optional<ScanPreset> findPreset(String name) {
        if (!StringUtils.hasText(name)) {
            return Optional.empty();
        }
        return presets.stream()
                .filter(preset -> name.equalsIgnoreCase(preset.getName()))
                .findFirst();
    }

    @Data
    public static class ScanPreset {
        private String name;
        private RepoPreset source = new RepoPreset();
        private RepoPreset target = new RepoPreset();
    }

    @Data
    public static class RepoPreset {
        private String code;
        private String path;
        private String branchFrom;
        private String branchTo;
        private String timeFrom;
        private String timeTo;
        private String refHint;
        private String deltaType;
        private boolean includeWorkingTree;
        private boolean fetchIfMissing = true;
        private String remoteName = "origin";
        private String scanStrategy;
        private boolean snapshotIncludeRemoteRefs;
        private boolean snapshotIncludeTags;
        private Integer snapshotMaxRefs;
        
        // 添加兼容性方法
        public boolean isSnapshotIncludeRemoteRefs() {
            return snapshotIncludeRemoteRefs;
        }
        
        public boolean isSnapshotIncludeTags() {
            return snapshotIncludeTags;
        }
        
        public Integer getSnapshotMaxRefs() {
            return snapshotMaxRefs;
        }
    }
}
