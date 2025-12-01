package com.example.migratediff.infrastructure.git;

import com.example.migratediff.domain.repo.CommitLocatorMode;
import com.example.migratediff.domain.repo.RepoBranch;
import com.example.migratediff.domain.repo.RepoConfig;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
public class GitBranchFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitBranchFetcher.class);

    /**
     * 按照仓库配置解析 base / target 提交。若本地引用缺失且允许 fetch，会自动同步远端。
     */
    public BranchPair resolveBranchPair(RepoConfig repoConfig) {
        if (repoConfig == null || repoConfig.getRepoPath() == null) {
            LOGGER.warn("仓库配置缺失，无法解析分支/提交");
            return BranchPair.empty();
        }
        Repository repository = null;
        try {
            repository = openRepository(repoConfig);
            return resolveBranchPair(repository, repoConfig);
        } catch (IOException | GitAPIException ex) {
            LOGGER.warn("解析 Git 引用失败: {}", ex.getMessage(), ex);
            return BranchPair.empty();
        } finally {
            if (repository != null) {
                repository.close();
            }
        }
    }

    /**
     * 复用外部打开的 Repository，避免重复打开文件句柄。
     */
    public BranchPair resolveBranchPair(Repository repository, RepoConfig repoConfig) throws IOException, GitAPIException {
        if (repository == null || repoConfig == null) {
            return BranchPair.empty();
        }
        Git git = new Git(repository);
        try {
            ObjectId baseId = resolveCommitObjectId(git, repoConfig.getBranchFrom(), repoConfig, CommitSelection.EARLIEST);
            ObjectId targetId = resolveCommitObjectId(git, repoConfig.getBranchTo(), repoConfig, CommitSelection.LATEST);
            populateBranchCommit(repoConfig.getBranchFrom(), baseId);
            populateBranchCommit(repoConfig.getBranchTo(), targetId);
            return new BranchPair(baseId, targetId);
        } finally {
            git.close();
        }
    }

    /**
     * 列出仓库全部分支，供前端下拉使用。
     */
    public List<RepoBranch> fetchBranches(RepoConfig repoConfig) {
        if (repoConfig == null || repoConfig.getRepoPath() == null) {
            return Collections.emptyList();
        }
        Repository repository = null;
        Git git = null;
        try {
            repository = openRepository(repoConfig);
            git = new Git(repository);
            List<Ref> refs = git.branchList()
                    .setListMode(ListBranchCommand.ListMode.ALL)
                    .call();
            List<RepoBranch> branches = new ArrayList<>();
            for (Ref ref : refs) {
                if (ref == null || Objects.equals(ref.getName(), Constants.HEAD)) {
                    continue;
                }
                ObjectId objectId = ref.getObjectId();
                RepoBranch branch = RepoBranch.builder()
                        .name(Repository.shortenRefName(ref.getName()))
                        .commitId(objectId != null ? objectId.name() : null)
                        .build();
                branches.add(branch);
            }
            return branches;
        } catch (IOException | GitAPIException ex) {
            LOGGER.warn("列出分支失败: {}", ex.getMessage(), ex);
            return Collections.emptyList();
        } finally {
            if (git != null) {
                git.close();
            }
            if (repository != null) {
                repository.close();
            }
        }
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

    private ObjectId resolveCommitObjectId(Git git, RepoBranch branch, RepoConfig repoConfig, CommitSelection selection) throws IOException, GitAPIException {
        if (branch == null) {
            return null;
        }
        ObjectId objectId = null;
        if (StringUtils.hasText(branch.getName())) {
            objectId = resolveBranchObjectId(git, branch.getName(), repoConfig);
        }
        if (objectId != null || !supportsTimeRange(repoConfig) || !hasTimeRange(branch)) {
            return objectId;
        }
        if (repoConfig.isFetchIfMissing()) {
            fetchRemote(git, repoConfig.getRemoteName());
        }
        return resolveCommitByTime(git.getRepository(), branch, selection);
    }

    private ObjectId resolveBranchObjectId(Git git, String branchName, RepoConfig repoConfig) throws IOException, GitAPIException {
        if (branchName == null || branchName.trim().isEmpty()) {
            return null;
        }
        Repository repository = git.getRepository();
        String sanitized = branchName.trim();
        ObjectId objectId = tryResolve(repository, sanitized);
        if (objectId == null) {
            objectId = tryResolve(repository, Constants.R_HEADS + sanitized);
        }
        if (objectId == null && repoConfig.getRemoteName() != null) {
            objectId = tryResolve(repository, Constants.R_REMOTES + repoConfig.getRemoteName() + "/" + sanitized);
        }
        if (objectId == null && repoConfig.isFetchIfMissing()) {
            fetchRemote(git, repoConfig.getRemoteName());
            objectId = tryResolve(repository, sanitized);
            if (objectId == null) {
                objectId = tryResolve(repository, Constants.R_HEADS + sanitized);
            }
            if (objectId == null && repoConfig.getRemoteName() != null) {
                objectId = tryResolve(repository, Constants.R_REMOTES + repoConfig.getRemoteName() + "/" + sanitized);
            }
        }
        return objectId;
    }

    private void fetchRemote(Git git, String remoteName) {
        String finalRemote = remoteName != null && !remoteName.trim().isEmpty() ? remoteName : "origin";
        if (!hasRemoteConfigured(git, finalRemote)) {
            LOGGER.debug("仓库未配置远端 {}，跳过 fetch", finalRemote);
            return;
        }
        try {
            git.fetch()
                    .setRemote(finalRemote)
                    .setCheckFetchedObjects(true)
                    .call();
        } catch (GitAPIException ex) {
            LOGGER.warn("远端 {} fetch 失败，继续使用本地引用: {}", finalRemote, ex.getMessage());
        }
    }

    private boolean hasRemoteConfigured(Git git, String remoteName) {
        if (git == null || remoteName == null) {
            return false;
        }
        return git.getRepository()
                .getConfig()
                .getSubsections("remote")
                .contains(remoteName);
    }

    /**
     * 使用时间窗口定位提交：LATEST 返回窗口内最新提交，EARLIEST 返回窗口内最早提交。
     * 对于时间范围模式，EARLIEST 返回其父提交以实现真正的增量diff。
     */
    private ObjectId resolveCommitByTime(Repository repository, RepoBranch branch, CommitSelection selection) throws IOException {
        if (!hasTimeRange(branch)) {
            return null;
        }
        String ref = resolveRefHint(branch);
        ObjectId startId = tryResolve(repository, ref);
        if (startId == null) {
            LOGGER.warn("参考引用 {} 无法解析，跳过时间区间计算", ref);
            return null;
        }
        try (RevWalk walk = new RevWalk(repository)) {
            walk.sort(RevSort.COMMIT_TIME_DESC, true);
            walk.markStart(walk.parseCommit(startId));
            RevCommit earliest = null;
            for (RevCommit commit : walk) {
                Instant commitInstant = Instant.ofEpochSecond(commit.getCommitTime());
                if (branch.getTimeTo() != null && commitInstant.isAfter(branch.getTimeTo())) {
                    continue;
                }
                if (branch.getTimeFrom() != null && commitInstant.isBefore(branch.getTimeFrom())) {
                    if (selection == CommitSelection.EARLIEST && earliest != null) {
                        break;
                    }
                    continue;
                }
                if (selection == CommitSelection.LATEST) {
                    return commit.getId();
                }
                earliest = commit;
            }
            
            if (earliest != null) {
                ObjectId result = earliest.getId();
                
                // 🔧 关键修改：时间范围模式下，earliest返回其父提交
                if (selection == CommitSelection.EARLIEST) {
                    ObjectId parentId = getParentCommit(repository, result);
                    if (parentId != null) {
                        LOGGER.debug("时间范围增量diff: 使用earliest的父提交 {} 作为base", parentId.name());
                        return parentId;
                    }
                    LOGGER.debug("earliest为初始提交，使用自身作为base: {}", result.name());
                    return result;
                }
                return result;
            }
            return null;
        }
    }

    /**
     * 获取指定提交的父提交
     */
    private ObjectId getParentCommit(Repository repository, ObjectId commitId) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            if (commit.getParentCount() > 0) {
                return commit.getParent(0).getId();
            }
            return null; // 没有父提交
        }
    }

    private ObjectId tryResolve(Repository repository, String revision) throws IOException {
        if (revision == null || revision.trim().isEmpty()) {
            return null;
        }
        try {
            return repository.resolve(revision);
        } catch (RevisionSyntaxException ex) {
            LOGGER.debug("分支语法不合法，忽略 {}: {}", revision, ex.getMessage());
            return null;
        }
    }

    private void populateBranchCommit(RepoBranch branch, ObjectId objectId) {
        if (branch != null && objectId != null) {
            branch.setCommitId(objectId.name());
        }
    }

    private boolean supportsTimeRange(RepoConfig repoConfig) {
        CommitLocatorMode mode = repoConfig.getLocatorMode() != null ? repoConfig.getLocatorMode() : CommitLocatorMode.BRANCH;
        return mode == CommitLocatorMode.TIME_RANGE || mode == CommitLocatorMode.HYBRID;
    }

    private boolean hasTimeRange(RepoBranch branch) {
        return branch != null && (branch.getTimeFrom() != null || branch.getTimeTo() != null);
    }

    private String resolveRefHint(RepoBranch branch) {
        if (branch == null) {
            return Constants.HEAD;
        }
        if (StringUtils.hasText(branch.getRefHint())) {
            return branch.getRefHint().trim();
        }
        if (StringUtils.hasText(branch.getName())) {
            return branch.getName().trim();
        }
        return Constants.HEAD;
    }

    private enum CommitSelection {
        EARLIEST,
        LATEST
    }

    public static class BranchPair {
        private final ObjectId baseId;
        private final ObjectId targetId;

        public BranchPair(ObjectId baseId, ObjectId targetId) {
            this.baseId = baseId;
            this.targetId = targetId;
        }

        public ObjectId getBaseId() {
            return baseId;
        }

        public ObjectId getTargetId() {
            return targetId;
        }

        public static BranchPair empty() {
            return new BranchPair(null, null);
        }
    }
}
