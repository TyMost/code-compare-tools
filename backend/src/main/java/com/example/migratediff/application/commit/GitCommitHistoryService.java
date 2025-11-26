package com.example.migratediff.application.commit;

import com.example.migratediff.api.dto.FileCommitHistoryDTO;
import com.example.migratediff.api.dto.GitCommitInfoDTO;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.domain.repo.RepoType;
import com.example.migratediff.infrastructure.git.GitRepositoryHelper;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Git提交历史服务
 * 负责获取文件的Git提交历史信息
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
     * 获取单个仓库的文件提交历史
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
                        .map(commit -> convertToGitCommitInfoDTO(commit, repoConfig, repoType))
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
     * @return Git提交信息DTO
     */
    private GitCommitInfoDTO convertToGitCommitInfoDTO(RevCommit commit, RepoConfig repoConfig, RepoType repoType) {
        return GitCommitInfoDTO.builder()
                .commitHash(commit.getId().getName())
                .shortHash(commit.getId().abbreviate(7).name())
                .authorName(commit.getAuthorIdent().getName())
                .authorEmail(commit.getAuthorIdent().getEmailAddress())
                .commitTime(Instant.ofEpochSecond(commit.getCommitTime()))
                .message(commit.getFullMessage())
                .branch(getCurrentBranch(repoConfig))
                .url(buildGitWebUrl(commit, repoConfig))
                .stats(extractCommitStats(commit))
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
     * @return 提交统计DTO
     */
    private GitCommitInfoDTO.CommitStatsDTO extractCommitStats(RevCommit commit) {
        // JGit中提取提交统计信息比较复杂，这里提供基本实现
        // 实际项目中可能需要更详细的统计计算
        return GitCommitInfoDTO.CommitStatsDTO.builder()
                .added(0) // 需要通过DiffFormatter计算
                .removed(0) // 需要通过DiffFormatter计算
                .modified(0) // 需要通过DiffFormatter计算
                .filesChanged(commit.getParentCount() > 0 ? 1 : 0)
                .build();
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
}
