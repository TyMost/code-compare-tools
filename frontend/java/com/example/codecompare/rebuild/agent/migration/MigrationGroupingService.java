package com.example.codecompare.rebuild.agent.migration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups migration candidates by their target file and ensures the candidates
 * inside each group are ordered for back-to-front insertion.
 */
public final class MigrationGroupingService {

    public List<FileMigrationGroup> groupByTargetFile(List<MigrationCandidate> candidates) {
        Map<String, FileMigrationGroup> groups = new LinkedHashMap<String, FileMigrationGroup>();
        if (candidates == null || candidates.isEmpty()) {
            return new ArrayList<FileMigrationGroup>();
        }
        for (MigrationCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String projectCode = candidate.getTargetProjectCode();
            String filePath = candidate.getFilePath();
            if (projectCode == null || filePath == null) {
                continue;
            }
            String key = projectCode + "::" + filePath;
            FileMigrationGroup group = groups.get(key);
            if (group == null) {
                group = new FileMigrationGroup(projectCode, filePath);
                groups.put(key, group);
            }
            group.addCandidate(candidate);
        }
        List<FileMigrationGroup> orderedGroups = new ArrayList<FileMigrationGroup>(groups.values());
        for (FileMigrationGroup group : orderedGroups) {
            group.sortDescending();
        }
        return orderedGroups;
    }
}
