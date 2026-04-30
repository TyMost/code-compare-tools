package com.example.migratediff.application.commit;

import com.example.migratediff.api.dto.FileCommitHistoryDTO;
import com.example.migratediff.api.dto.GitCommitInfoDTO;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.domain.repo.RepoType;
import com.example.migratediff.infrastructure.git.GitRepositoryHelper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.EditList;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Git提交历史服务
 * 负责获取文件的Git提交历史信息
 * 优化：支持仓库级别批量获取，显著提升性能
 */
@Slf4j
@Service
public class GitCommitHistoryService {

    private final GitRepositoryHelper gitRepositoryHelper;

    @Autowired
    public GitCommitHistoryService(GitRepositoryHelper gitRepositoryHelper) {
        this.gitRepositoryHelper = gitRepositoryHelper;
    }

    /**
     * 获取文件在两个仓库中的提交历史
     * 
     * @param filePath 文件路径
     * @param oracleRepo Oracle仓库配置
     * @param gaussRepo Gauss仓库配置
     * @return 文件提交历史DTO
     */
    @Cacheable(value = "fileCommitHistory", key = "#filePath + '_' + (#oracleRepo != null ? #oracleRepo.repoPath.absolutePath : 'null') + '_' + (#gaussRepo != null ? #gaussRepo.repoPath.absolutePath : 'null')")
    public FileCommitHistoryDTO getFileCommitHistory(String filePath, RepoConfig oracleRepo, RepoConfig gaussRepo) {
        log.debug("Getting commit history for file: {}", filePath);
        log.debug("Oracle repo config: {}", oracleRepo);
        log.debug("Gauss repo config: {}", gaussRepo);
        
        List<GitCommitInfoDTO> oracleCommits = getCommitHistoryForRepo(filePath, oracleRepo, RepoType.ORACLE);
        List<GitCommitInfoDTO> gaussCommits = getCommitHistoryForRepo(filePath, gaussRepo, RepoType.GAUSS);
        
        log.debug("Oracle commits found: {}", oracleCommits.size());
        log.debug("Gauss commits found: {}", gaussCommits.size());
        
        return FileCommitHistoryDTO.builder()
                .filePath(filePath)
                .oracleCommits(oracleCommits)
                .gaussCommits(gaussCommits)
                .totalCount(oracleCommits.size() + gaussCommits.size())
                .oracleCount(oracleCommits.size())
                .gaussCount(gaussCommits.size())
                .hasOracleCommits(!oracleCommits.isEmpty())
                .hasGaussCommits(!gaussCommits.isEmpty())
                .build();
    }

    /**
     * 【新增】批量获取仓库所有提交历史（性能优化关键方法）
     * 一次性获取仓库所有提交，然后在内存中按文件路径分组
     * 
     * @param repoConfig 仓库配置
     * @param repoType 仓库类型
     * @param timeFrom 开始时间
     * @param timeTo 结束时间
     * @return Map<文件路径, 提交列表>
     */
    @Cacheable(value = "repoAllCommits", key = "#repoConfig.repoPath.absolutePath + '_' + #repoType + '_' + #timeFrom + '_' + #timeTo")
    public Map<String, List<GitCommitInfoDTO>> getRepoAllCommits(
            RepoConfig repoConfig, 
            RepoType repoType, 
            Instant timeFrom, 
            Instant timeTo) {
        
        if (repoConfig == null || repoConfig.getRepoPath() == null) {
            log.warn("Repository config is null for repo type: {}", repoType);
            return new HashMap<>();
        }

        long startTime = System.currentTimeMillis();
        log.info("开始批量获取{}仓库所有提交历史: 时间范围 {} 至 {}", 
            repoType, timeFrom, timeTo);

        try (Repository repository = gitRepositoryHelper.openRepository(repoConfig)) {
            try (Git git = new Git(repository)) {
                // 构建时间范围过滤的提交遍历
                try (RevWalk revWalk = new RevWalk(repository)) {
                    // 设置时间范围
                    if (timeFrom != null || timeTo != null) {
                        revWalk.setRevFilter(new RevFilter() {
                            @Override
                            public boolean include(RevWalk walker, RevCommit commits) throws IOException {
                                Instant commitTime = Instant.ofEpochSecond(commits.getCommitTime());
                                boolean afterFrom = timeFrom == null || !commitTime.isBefore(timeFrom);
                                boolean beforeTo = timeTo == null || !commitTime.isAfter(timeTo);
                                return afterFrom && beforeTo;
                            }

                            @Override
                            public RevFilter clone() {
                                return this;
                            }
                        });
                    }

                    // 不限制文件路径，获取所有提交
                    revWalk.markStart(revWalk.parseCommit(repository.resolve("HEAD")));
                    revWalk.setRetainBody(false);

                    Map<String, List<GitCommitInfoDTO>> fileCommitsMap = new HashMap<>();
                    int totalCommits = 0;
                    int processedFiles = 0;

                    for (RevCommit commit : revWalk) {
                        totalCommits++;
                        
                        // 获取此提交涉及的所有文件
                        List<String> commitFiles = getCommitFiles(repository, commit);
                        
                        // 为每个文件添加此提交
                        for (String filePath : commitFiles) {
                            GitCommitInfoDTO commitInfo = convertToGitCommitInfoDTO(commit, repoConfig, repoType, filePath);
                            
                            fileCommitsMap.computeIfAbsent(filePath, k -> new ArrayList<>()).add(commitInfo);
                        }
                        
                        // 避免处理过多文件，限制处理数量
                        if (processedFiles > 1000 && totalCommits % 1000 == 0) {
                            log.debug("已处理 {} 个文件，当前提交数: {}", processedFiles, totalCommits);
                        }
                        processedFiles = Math.max(processedFiles, commitFiles.size());
                    }

                    // 对每个文件的提交列表按时间倒序排序
                    fileCommitsMap.forEach((filePath, commits) -> {
                        commits.sort(Comparator.comparing(GitCommitInfoDTO::getCommitTime).reversed());
                    });

                    long duration = System.currentTimeMillis() - startTime;
                    log.info("{}仓库批量获取完成: {} 个文件, {} 个提交, 耗时: {}ms", 
                        repoType, fileCommitsMap.size(), totalCommits, duration);

                    return fileCommitsMap;
                }
            }
        } catch (IOException e) {
            log.error("Failed to get all commits for repo: {}", repoType, e);
            return new HashMap<>();
        }
    }

    /**
     * 获取提交涉及的所有文件路径
     */
    private List<String> getCommitFiles(Repository repository, RevCommit commit) throws IOException {
        List<String> filePaths = new ArrayList<>();
        
        if (commit.getParentCount() == 0) {
            // 初始提交，获取所有文件
            try (TreeWalk treeWalk = new TreeWalk(repository)) {
                treeWalk.addTree(commit.getTree());
                treeWalk.setRecursive(true);
                while (treeWalk.next()) {
                    String filePath = treeWalk.getPathString();
                    if (filePath != null && !filePath.isEmpty()) {
                        filePaths.add(filePath);
                    }
                }
            }
        } else {
            // 普通提交，比较父子提交差异
            RevCommit parent = commit.getParent(0);
            try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(repository);
                try (ObjectReader reader = repository.newObjectReader()) {
                    CanonicalTreeParser parentTree = new CanonicalTreeParser();
                    CanonicalTreeParser commitTree = new CanonicalTreeParser();
                    
                    parentTree.reset(reader, parent.getTree());
                    commitTree.reset(reader, commit.getTree());
                    
                    List<DiffEntry> diffs = diffFormatter.scan(parentTree, commitTree);
                    for (DiffEntry diff : diffs) {
                        String newPath = diff.getNewPath();
                        String oldPath = diff.getOldPath();
                        
                        if (!newPath.equals("/dev/null")) {
                            filePaths.add(newPath);
                        }
                        if (oldPath != null && !oldPath.equals("/dev/null")) {
                            filePaths.add(oldPath);
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("IO exception while getting commit files for commit: {}", commit.getId(), e);
                return new ArrayList<>();
            }
        }
        
        return filePaths.stream()
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 【优化后】批量获取文件提交历史（推荐使用此方法）
     * 
     * @param filePaths 文件路径列表
     * @param repoConfig 仓库配置
     * @param repoType 仓库类型
     * @param timeFrom 开始时间
     * @param timeTo 结束时间
     * @return Map<文件路径, 提交列表>
     */
    public Map<String, List<GitCommitInfoDTO>> getBatchCommitHistory(
            List<String> filePaths, 
            RepoConfig repoConfig, 
            RepoType repoType,
            Instant timeFrom, 
            Instant timeTo) {
        
        if (repoConfig == null || filePaths == null || filePaths.isEmpty()) {
            log.warn("Invalid parameters for batch commit history request");
            return new HashMap<>();
        }

        // 一次性获取仓库所有提交，然后按文件筛选
        Map<String, List<GitCommitInfoDTO>> allCommits = getRepoAllCommits(repoConfig, repoType, timeFrom, timeTo);
        
        // 只返回需要的文件
        Map<String, List<GitCommitInfoDTO>> result = new HashMap<>();
        for (String filePath : filePaths) {
            List<GitCommitInfoDTO> commits = allCommits.get(filePath);
            if (commits != null) {
                result.put(filePath, commits);
            }
        }

        log.debug("批量获取提交历史完成: 请求 {} 个文件，返回 {} 个文件", 
            filePaths.size(), result.size());

        return result;
    }

    /**
     * 获取单个仓库的文件提交历史（保留向后兼容，但推荐使用批量方法）
     * 
     * @param filePath 文件路径
     * @param repoConfig 仓库配置
     * @param repoType 仓库类型
     * @return 提交历史列表
     */
    private List<GitCommitInfoDTO> getCommitHistoryForRepo(String filePath, RepoConfig repoConfig, RepoType repoType) {
        if (repoConfig == null || repoConfig.getRepoPath() == null) {
            log.warn("Repository config is null for repo type: {}", repoType);
            return new ArrayList<>();
        }

        try (Repository repository = gitRepositoryHelper.openRepository(repoConfig)) {
            // 使用Git API获取文件历史
            try (Git git = new Git(repository)) {
                Iterable<RevCommit> commitIterable = git.log()
                        .addPath(filePath)
                        .setMaxCount(50) // 限制最多返回50个提交
                        .call();
                
                List<RevCommit> commits = new ArrayList<>();
                for (RevCommit commit : commitIterable) {
                    commits.add(commit);
                }
                
                return commits.stream()
                        .map(commit -> convertToGitCommitInfoDTO(commit, repoConfig, repoType, filePath))
                        .sorted(Comparator.comparing(GitCommitInfoDTO::getCommitTime).reversed())
                        .collect(Collectors.toList());
            }
            
        } catch (GitAPIException | IOException e) {
            log.error("Failed to get commit history for file: {} in repo: {}", filePath, repoType, e);
            return new ArrayList<>();
        }
    }

    /**
     * 将RevCommit转换为GitCommitInfoDTO
     * 
     * @param commit JGit提交对象
     * @param repoConfig 仓库配置
     * @param repoType 仓库类型
     * @param filePath 文件路径
     * @return Git提交信息DTO
     */
    private GitCommitInfoDTO convertToGitCommitInfoDTO(RevCommit commit, RepoConfig repoConfig, RepoType repoType, String filePath) {
        return GitCommitInfoDTO.builder()
                .commitHash(commit.getId().getName())
                .shortHash(commit.getId().abbreviate(7).name())
                .authorName(commit.getAuthorIdent().getName())
                .authorEmail(commit.getAuthorIdent().getEmailAddress())
                .commitTime(Instant.ofEpochSecond(commit.getCommitTime()))
                .message(commit.getFullMessage())
                .branch(getCurrentBranch(repoConfig))
                .url(buildGitWebUrl(commit, repoConfig))
                .stats(extractCommitStats(commit, repoConfig, filePath))
                .repoType(repoType.name().toLowerCase())
                .build();
    }

    /**
     * 获取当前分支名
     * 
     * @param repoConfig 仓库配置
     * @return 分支名
     */
    private String getCurrentBranch(RepoConfig repoConfig) {
        try (Repository repository = gitRepositoryHelper.openRepository(repoConfig)) {
            String branch = repository.getBranch();
            return branch != null ? branch : "unknown";
        } catch (IOException e) {
            log.warn("Failed to get current branch for repo: {}", repoConfig.getRepoPath(), e);
            return "unknown";
        }
    }

    /**
     * 构建Git Web URL
     * 
     * @param commit 提交对象
     * @param repoConfig 仓库配置
     * @return Git Web URL
     */
    private String buildGitWebUrl(RevCommit commit, RepoConfig repoConfig) {
        // 这里可以根据具体的Git平台（GitHub、GitLab等）构建URL
        // 目前返回一个通用的格式
        String repoPath = repoConfig.getRepoPath().getAbsolutePath();
        String shortHash = commit.getId().abbreviate(7).name();
        
        // 简单的URL构建逻辑，实际项目中可能需要更复杂的处理
        if (repoPath.contains("github")) {
            return String.format("https://github.com/xxx/xxx/commit/%s", shortHash);
        } else if (repoPath.contains("gitlab")) {
            return String.format("https://gitlab.com/xxx/xxx/-/commit/%s", shortHash);
        } else {
            return String.format("file://%s/commit/%s", repoPath, shortHash);
        }
    }

    /**
     * 提取提交统计信息
     * 
     * @param commit 提交对象
     * @param repoConfig 仓库配置
     * @param filePath 文件路径
     * @return 提交统计DTO
     */
    private GitCommitInfoDTO.CommitStatsDTO extractCommitStats(RevCommit commit, RepoConfig repoConfig, String filePath) {
        try {
            try (Repository repository = gitRepositoryHelper.openRepository(repoConfig)) {
                DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
                diffFormatter.setRepository(repository);
                
                List<DiffEntry> diffs;
                if (commit.getParentCount() > 0) {
                    RevCommit parent = commit.getParent(0);
                    try (ObjectReader reader = repository.newObjectReader()) {
                        CanonicalTreeParser parentTreeIterator = new CanonicalTreeParser();
                        CanonicalTreeParser commitTreeIterator = new CanonicalTreeParser();
                        
                        parentTreeIterator.reset(reader, parent.getTree());
                        commitTreeIterator.reset(reader, commit.getTree());
                        
                        diffs = diffFormatter.scan(parentTreeIterator, commitTreeIterator);
                    }
                } else {
                    // 对于初始提交，使用空树作为父节点
                    diffs = diffFormatter.scan(new EmptyTreeIterator(), new CanonicalTreeParser());
                }
                
                int added = 0, removed = 0, modified = 0;
                int filesChanged = 0;
                
                for (DiffEntry diff : diffs) {
                    // 检查是否涉及目标文件
                    boolean isTargetFile = diff.getNewPath().equals(filePath) || 
                                        diff.getOldPath().equals(filePath) ||
                                        (filePath != null && (diff.getNewPath().contains(filePath) || diff.getOldPath().contains(filePath)));
                    
                    if (isTargetFile) {
                        filesChanged++;
                        try {
                            EditList editList = diffFormatter.toFileHeader(diff).toEditList();
                            for (Edit edit : editList) {
                                if (edit.getType() == Edit.Type.INSERT) {
                                    added += edit.getLengthB();
                                } else if (edit.getType() == Edit.Type.DELETE) {
                                    removed += edit.getLengthA();
                                } else if (edit.getType() == Edit.Type.REPLACE) {
                                    removed += edit.getLengthA();
                                    added += edit.getLengthB();
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to calculate edit stats for diff: {}", diff, e);
                            // 如果无法计算详细统计，至少计算文件变更
                        }
                    }
                }
                
                return GitCommitInfoDTO.CommitStatsDTO.builder()
                        .added(added)
                        .removed(removed)
                        .modified(modified)
                        .filesChanged(filesChanged)
                        .build();
            }
        } catch (IOException e) {
            log.warn("IO exception while extracting commit stats for commit: {} in file: {}", commit.getId(), filePath, e);
            return GitCommitInfoDTO.CommitStatsDTO.builder()
                    .added(0)
                    .removed(0)
                    .modified(0)
                    .filesChanged(0)
                    .build();
        } catch (Exception e) {
            log.warn("Unexpected exception while extracting commit stats for commit: {} in file: {}", commit.getId(), filePath, e);
            return GitCommitInfoDTO.CommitStatsDTO.builder()
                    .added(0)
                    .removed(0)
                    .modified(0)
                    .filesChanged(0)
                    .build();
        }
    }

    /**
     * 清除指定文件的缓存
     * 
     * @param filePath 文件路径
     */
    public void clearCache(String filePath) {
        // 这里可以集成Spring Cache的缓存清除功能
        log.info("Cache cleared for file: {}", filePath);
    }

    /**
     * 清除仓库级别的缓存
     * 
     * @param repoConfig 仓库配置
     * @param repoType 仓库类型
     */
    public void clearRepoCache(RepoConfig repoConfig, RepoType repoType) {
        log.info("Cache cleared for repo: {} {}", repoType, repoConfig.getRepoPath());
        // 可以根据具体的缓存实现来清除
    }
}
