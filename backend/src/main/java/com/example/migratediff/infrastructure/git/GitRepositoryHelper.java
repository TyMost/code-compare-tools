package com.example.migratediff.infrastructure.git;

import com.example.migratediff.domain.repo.RepoConfig;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
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
import org.eclipse.jgit.diff.DiffFormatter;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

/**
 * Common Git repository utilities shared by diff scanners.
 */
@Component
public class GitRepositoryHelper {

    public Repository openRepository(RepoConfig repoConfig) throws IOException {
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

    public DiffFormatter createDiffFormatter(Repository repository) {
        DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
        formatter.setRepository(repository);
        formatter.setDetectRenames(true);
        formatter.setContext(3);
        return formatter;
    }

    public AbstractTreeIterator resolveTargetIterator(Repository repository, @Nullable ObjectId targetId, boolean includeWorkingTree) throws IOException {
        if (includeWorkingTree) {
            return new FileTreeIterator(repository);
        }
        return prepareTreeIterator(repository, targetId);
    }

    public AbstractTreeIterator prepareTreeIterator(Repository repository, @Nullable ObjectId commitId) throws IOException {
        if (commitId == null) {
            return new EmptyTreeIterator();
        }
        ObjectId treeId = resolveTreeId(repository, commitId);
        if (treeId == null) {
            return new EmptyTreeIterator();
        }
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        try (ObjectReader reader = repository.newObjectReader()) {
            treeParser.reset(reader, treeId);
        }
        return treeParser;
    }

    @Nullable
    public ObjectId resolveTreeId(Repository repository, ObjectId objectId) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevObject revObject = revWalk.parseAny(objectId);
            if (revObject instanceof RevTree) {
                return revObject.getId();
            }
            if (revObject instanceof RevCommit) {
                RevTree tree = ((RevCommit) revObject).getTree();
                return tree != null ? tree.getId() : null;
            }
            return objectId;
        }
    }
}
