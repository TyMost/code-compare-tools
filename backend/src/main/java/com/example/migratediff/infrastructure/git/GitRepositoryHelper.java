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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

/**
 * Common Git repository utilities shared by diff scanners.
 * 集成了RepositoryPool和RevWalkPool以提升性能
 */
@Component
public class GitRepositoryHelper {

    private final RepositoryPool repositoryPool;
    private final RevWalkPool revWalkPool;

    @Autowired
    public GitRepositoryHelper(RepositoryPool repositoryPool, RevWalkPool revWalkPool) {
        this.repositoryPool = repositoryPool;
        this.revWalkPool = revWalkPool;
    }

    public Repository openRepository(RepoConfig repoConfig) throws IOException {
        if (repoConfig == null || repoConfig.getRepoPath() == null) {
            throw new IllegalArgumentException("RepoConfig and repo path cannot be null");
        }
        String repoPath = repoConfig.getRepoPath().getAbsolutePath();
        return repositoryPool.borrowRepository(repoPath);
    }

    /**
     * 归还Repository实例到池中
     */
    public void returnRepository(RepoConfig repoConfig, Repository repository) {
        if (repoConfig != null && repoConfig.getRepoPath() != null) {
            String repoPath = repoConfig.getRepoPath().getAbsolutePath();
            repositoryPool.returnRepository(repoPath, repository);
        } else {
            // 如果无法确定路径，直接关闭
            if (repository != null) {
                repository.close();
            }
        }
    }

    /**
     * 借用RevWalk实例
     */
    public RevWalk borrowRevWalk(Repository repository) {
        return revWalkPool.borrowRevWalk(repository);
    }

    /**
     * 归还RevWalk实例到池中
     */
    public void returnRevWalk(RevWalk revWalk) {
        revWalkPool.returnRevWalk(revWalk);
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
