package com.example.migratediff.infrastructure.git;

import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.infrastructure.git.GitBranchFetcher.BranchPair;
import com.example.migratediff.infrastructure.persistence.DiffRepository;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class GitRepoScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitRepoScanner.class);

    private final GitBranchFetcher gitBranchFetcher;
    private final GitDiffParser gitDiffParser;
    private final DiffRepository diffRepository;

    public GitRepoScanner(GitBranchFetcher gitBranchFetcher, GitDiffParser gitDiffParser, @Nullable DiffRepository diffRepository) {
        this.gitBranchFetcher = gitBranchFetcher;
        this.gitDiffParser = gitDiffParser;
        this.diffRepository = diffRepository;
    }

    /**
     * Incremental scan entry that assembles a {@link DiffSummary}.
     */
    public DiffSummary scan(RepoConfig repoConfig) {
        DiffSummary emptySummary = buildEmptySummary(repoConfig);
        if (repoConfig == null || repoConfig.getRepoPath() == null) {
            return emptySummary;
        }
        Repository repository = null;
        DiffFormatter formatter = null;
        try {
            repository = openRepository(repoConfig);
            BranchPair branchPair = gitBranchFetcher.resolveBranchPair(repository, repoConfig);
            AbstractTreeIterator baseTree = prepareTreeIterator(repository, branchPair.getBaseId());
            AbstractTreeIterator targetTree = resolveTargetIterator(repository, branchPair.getTargetId(), repoConfig.isIncludeWorkingTree());
            formatter = createDiffFormatter(repository);
            List<DiffEntry> entries = formatter.scan(baseTree, targetTree);
            LOGGER.debug("Scanned DiffEntry count: {}", entries != null ? entries.size() : 0);
            List<DiffFile> diffFiles = gitDiffParser.parse(entries, repository, formatter, repoConfig);
            DiffSummary summary = DiffSummary.builder()
                    .repoConfig(repoConfig)
                    .repoPath(repoConfig.getRepoPath() != null ? repoConfig.getRepoPath().getAbsolutePath() : null)
                    .branchFrom(repoConfig.getBranchFrom() != null ? repoConfig.getBranchFrom().getName() : null)
                    .branchTo(repoConfig.getBranchTo() != null ? repoConfig.getBranchTo().getName() : null)
                    .baseCommitId(branchPair.getBaseId() != null ? branchPair.getBaseId().name() : null)
                    .targetCommitId(branchPair.getTargetId() != null ? branchPair.getTargetId().name() : null)
                    .deltaType(repoConfig.getDeltaType())
                    .scanTime(Instant.now())
                    .diffFiles(diffFiles != null ? diffFiles : new ArrayList<>())
                    .build();
            persistSummary(summary);
            return summary;
        } catch (RepositoryNotFoundException ex) {
            String repoPath = repoConfig.getRepoPath() != null ? repoConfig.getRepoPath().getAbsolutePath() : null;
            LOGGER.warn("Invalid repository path; missing .git directory: {}", repoPath);
            return emptySummary;
        } catch (IOException | GitAPIException ex) {
            LOGGER.warn("Failed to scan repository diffs: {}", ex.getMessage(), ex);
            return emptySummary;
        } finally {
            if (formatter != null) {
                formatter.close();
            }
            if (repository != null) {
                repository.close();
            }
        }
    }

    /**
     * Legacy entry that currently returns an empty list.
     */
    public List<RepoConfig> scanAvailableRepos() {
        return new ArrayList<>();
    }

    protected DiffFormatter createDiffFormatter(Repository repository) {
        DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
        formatter.setRepository(repository);
        formatter.setDetectRenames(true);
        formatter.setContext(3);
        return formatter;
    }

    private Repository openRepository(RepoConfig repoConfig) throws IOException {
        File repoDirectory = new File(repoConfig.getRepoPath().getAbsolutePath());
        FileRepositoryBuilder builder = new FileRepositoryBuilder()
                .readEnvironment()
                .setMustExist(true)
                .findGitDir(repoDirectory);
        if (builder.getGitDir() == null) {
            File gitDirCandidate = new File(repoDirectory, Constants.DOT_GIT);
            if (gitDirCandidate.isDirectory()) {
                builder.setGitDir(gitDirCandidate);
            } else {
                builder.setGitDir(repoDirectory);
            }
        }
        return builder.build();
    }

    private AbstractTreeIterator resolveTargetIterator(Repository repository, @Nullable ObjectId targetId, boolean includeWorkingTree) throws IOException {
        if (includeWorkingTree) {
            // includeWorkingTree mode: use the working tree snapshot as the target iterator.
            return new FileTreeIterator(repository);
        }
        return prepareTreeIterator(repository, targetId);
    }

    private AbstractTreeIterator prepareTreeIterator(Repository repository, @Nullable ObjectId commitId) throws IOException {
        if (commitId == null) {
            return new EmptyTreeIterator();
        }
        ObjectId treeId = resolveTreeId(repository, commitId);
        if (treeId == null) {
            return new EmptyTreeIterator();
        }
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        org.eclipse.jgit.lib.ObjectReader reader = repository.newObjectReader();
        try {
            treeParser.reset(reader, treeId);
        } finally {
            reader.close();
        }
        return treeParser;
    }

    private ObjectId resolveTreeId(Repository repository, ObjectId objectId) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevObject revObject = revWalk.parseAny(objectId);
            if (revObject instanceof RevTree) {
                return revObject.getId();
            }
            if (revObject instanceof RevCommit) {
                RevCommit commit = (RevCommit) revObject;
                RevTree tree = commit.getTree();
                return tree != null ? tree.getId() : null;
            }
            return objectId;
        }
    }

    private DiffSummary buildEmptySummary(RepoConfig repoConfig) {
        return DiffSummary.builder()
                .repoConfig(repoConfig)
                .repoPath(repoConfig != null && repoConfig.getRepoPath() != null ? repoConfig.getRepoPath().getAbsolutePath() : null)
                .branchFrom(repoConfig != null && repoConfig.getBranchFrom() != null ? repoConfig.getBranchFrom().getName() : null)
                .branchTo(repoConfig != null && repoConfig.getBranchTo() != null ? repoConfig.getBranchTo().getName() : null)
                .deltaType(repoConfig != null ? repoConfig.getDeltaType() : null)
                .diffFiles(new ArrayList<>())
                .build();
    }

    private void persistSummary(DiffSummary summary) {
        if (diffRepository == null || summary == null) {
            return;
        }
        try {
            diffRepository.save(summary);
        } catch (RuntimeException ex) {
            LOGGER.warn("Persisting DiffSummary failed: {}", ex.getMessage(), ex);
        }
    }
}
