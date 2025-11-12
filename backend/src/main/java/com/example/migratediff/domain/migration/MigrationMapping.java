package com.example.migratediff.domain.migration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationMapping {

    private String sourcePath;
    private String targetPath;
    @Builder.Default
    private Map<Integer, Integer> lineMapping = new HashMap<>();
}
