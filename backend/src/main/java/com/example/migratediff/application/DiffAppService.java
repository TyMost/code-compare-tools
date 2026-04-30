package com.example.migratediff.application;

import com.example.migratediff.domain.diff.DeltaGroup;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.infrastructure.git.GitRepoScanner;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class DiffAppService {

    private final GitRepoScanner gitRepoScanner;

    public DiffAppService(GitRepoScanner gitRepoScanner) {
        this.gitRepoScanner = gitRepoScanner;
    }

    public DiffSummary generateDiff(DiffSummary request) {
        if (request == null || request.getRepoConfig() == null) {
            return request;
        }
        return gitRepoScanner.scan(request.getRepoConfig());
    }

    public DeltaGroup mergeDelta(DiffSummary deltaO, DiffSummary deltaG) {
        DeltaGroup group = new DeltaGroup();
        group.setDeltaO(extractFirstFile(deltaO));
        group.setDeltaG(extractFirstFile(deltaG));
        return group;
    }

    private DiffFile extractFirstFile(DiffSummary summary) {
        return Optional.ofNullable(summary)
                .flatMap(s -> s.getDiffFiles().stream().findFirst())
                .orElse(null);
    }
}
