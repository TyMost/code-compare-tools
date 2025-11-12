package com.example.migratediff.api.dto;

import lombok.Data;

@Data
public class ScanPresetDTO {

    private String name;
    private RepoDTO source;
    private RepoDTO target;

    @Data
    public static class RepoDTO {
        private String code;
        private String path;
        private String branchFrom;
        private String branchTo;
        private String timeFrom;
        private String timeTo;
        private String refHint;
        private String deltaType;
        private boolean includeWorkingTree;
        private boolean fetchIfMissing;
        private String remoteName;
    }
}
