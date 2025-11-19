package com.example.migratediff.infrastructure.git;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class SnapshotDiffExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnapshotDiffExtractor.class);

    private final GitRepositoryHelper repositoryHelper;

    public SnapshotDiffExtractor(GitRepositoryHelper repositoryHelper) {
        this.repositoryHelper = repositoryHelper;
    }

    public List<DiffEntry> extract(Repository repository, SnapshotPair pair) throws IOException {
        if (repository == null || pair == null) {
            return Collections.emptyList();
        }
        DiffFormatter formatter = null;
        try {
            formatter = repositoryHelper.createDiffFormatter(repository);
            AbstractTreeIterator baseTree = repositoryHelper.prepareTreeIterator(repository, pair.getEarliestCommitId());
            AbstractTreeIterator targetTree = repositoryHelper.prepareTreeIterator(repository, pair.getLatestCommitId());
            return formatter.scan(baseTree, targetTree);
        } catch (MissingObjectException ex) {
            LOGGER.warn("Failed to load trees for snapshot diff: {}", ex.getMessage());
            throw ex;
        } finally {
            if (formatter != null) {
                formatter.close();
            }
        }
    }
}
