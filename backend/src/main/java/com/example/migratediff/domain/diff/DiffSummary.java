package com.example.migratediff.domain.diff;

import com.example.migratediff.domain.repo.RepoConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiffSummary {

    private RepoConfig repoConfig;
    private String repoPath;
    private String branchFrom;
    private String branchTo;
    private String baseCommitId;
    private String targetCommitId;
    private DeltaType deltaType;
    private Instant scanTime;
    @Builder.Default
    private List<DiffFile> diffFiles = new ArrayList<>();
}
