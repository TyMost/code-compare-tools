package com.example.migratediff.infrastructure.git;

import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.repo.RepoConfig;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class IncrementalSnapshotAssembler {

    private final GitDiffParser gitDiffParser;
    private final GitRepositoryHelper repositoryHelper;

    public IncrementalSnapshotAssembler(GitDiffParser gitDiffParser, GitRepositoryHelper repositoryHelper) {
        this.gitDiffParser = gitDiffParser;
        this.repositoryHelper = repositoryHelper;
    }

    public List<DiffFile> assemble(Repository repository, List<DiffEntry> entries, RepoConfig repoConfig) throws IOException {
        if (repository == null || entries == null || entries.isEmpty()) {
            return Collections.emptyList();
        }
        DiffFormatter formatter = null;
        try {
            formatter = repositoryHelper.createDiffFormatter(repository);
            return gitDiffParser.parse(entries, repository, formatter, repoConfig);
        } finally {
            if (formatter != null) {
                formatter.close();
            }
        }
    }
}
