package com.example.migratediff.application.scan;

import com.example.migratediff.domain.repo.RepoType;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.infrastructure.git.GitRepositoryHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文件提交映射预构建服务 - 替换GitCommitHistoryService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileCommitMapService {
    
    private final GitRepositoryHelper gitRepositoryHelper;
    
    /**
     * 缓存映射结果
     */
    private final Map<String, FileCommitMapping.RepoMapping> mappingCache = new ConcurrentHashMap<>();
    
    /**
     * 预构建指定时间范围内所有仓库的文件提交映射
     */
    public Map<MultiRepoExportRequest.RepoSelection, FileCommitMapping> preBuildAllMappings(
            List<MultiRepoExportRequest.RepoSelection> repoSelections,
            Instant timeFrom,
            Instant timeTo) {
        
        log.info("开始预构建文件提交映射: 仓库数={}, 时间范围=[{}, {}]", 
                 repoSelections.size(), timeFrom, timeTo);
        
        // 并行预构建所有仓库的映射
        return repoSelections.parallelStream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        selection -> preBuildSingleMapping(selection, timeFrom, timeTo)
                ));
    }
    
    /**
     * 预构建单个仓库的文件提交映射
     */
    private FileCommitMapping preBuildSingleMapping(
            MultiRepoExportRequest.RepoSelection selection, Instant timeFrom, Instant timeTo) {
        
        try {
            // 获取扫描报告来提取仓库配置
            ScanReport scanReport = getScanReport(selection);
            if (scanReport == null) {
                log.warn("未找到扫描报告: {}", selection);
                return createEmptyMapping(selection, timeFrom, timeTo);
            }
            
            RepoConfig oracleRepo = extractOracleRepoConfig(scanReport);
            RepoConfig gaussRepo = extractGaussRepoConfig(scanReport);
            
            if (oracleRepo == null || gaussRepo == null) {
                log.warn("仓库配置为空: oracle={}, gauss={}", oracleRepo, gaussRepo);
                return createEmptyMapping(selection, timeFrom, timeTo);
            }
            
            // 并行获取Oracle和Gauss的提交映射
            FileCommitMapping.RepoMapping oracleMapping = preBuildRepoMapping(
                    oracleRepo, timeFrom, timeTo, RepoType.ORACLE);
            FileCommitMapping.RepoMapping gaussMapping = preBuildRepoMapping(
                    gaussRepo, timeFrom, timeTo, RepoType.GAUSS);
            
            return FileCommitMapping.builder()
                    .repoSelection(selection)
                    .timeFrom(timeFrom)
                    .timeTo(timeTo)
                    .oracleMapping(oracleMapping)
                    .gaussMapping(gaussMapping)
                    .buildAt(Instant.now())
                    .build();
                    
        } catch (Exception e) {
            log.error("预构建映射失败: {}", selection, e);
            return createEmptyMapping(selection, timeFrom, timeTo);
        }
    }
    
    /**
     * 预构建单个仓库的映射
     */
    private FileCommitMapping.RepoMapping preBuildRepoMapping(
            RepoConfig repoConfig, Instant timeFrom, Instant timeTo, RepoType repoType) {
        
        String cacheKey = buildCacheKey(repoConfig, timeFrom, timeTo, repoType);
        FileCommitMapping.RepoMapping cached = mappingCache.get(cacheKey);
        if (cached != null && !isExpired(cached)) {
            log.debug("使用缓存的映射: {}", cacheKey);
            return cached;
        }
        
        try {
            String repoPath = repoConfig.getRepoPath() != null ? repoConfig.getRepoPath().getAbsolutePath() : "unknown";
            log.info("预构建仓库映射: {} [{}]", repoType, repoPath);
            
            List<CommitWithFiles> commitsWithFiles = getAllCommitsInRange(
                    repoConfig, timeFrom, timeTo, repoType);
            
            // 构建文件→提交映射
            Map<String, List<CommitInfo>> fileToCommits = buildFileToCommitsMap(
                    commitsWithFiles);
            
            FileCommitMapping.RepoMapping mapping = FileCommitMapping.RepoMapping.builder()
                    .repoConfig(repoConfig)
                    .repoType(repoType)
                    .timeFrom(timeFrom)
                    .timeTo(timeTo)
                    .fileToCommits(fileToCommits)
                    .totalCommits(commitsWithFiles.size())
                    .totalFiles(fileToCommits.size())
                    .buildAt(Instant.now())
                    .build();
            
            // 缓存结果
            mappingCache.put(cacheKey, mapping);
            
            log.info("预构建完成: {} -> {} 文件, {} 提交", 
                     repoType, fileToCommits.size(), commitsWithFiles.size());
            
            return mapping;
            
        } catch (Exception e) {
            log.error("预构建仓库映射失败: {}", repoConfig, e);
            return createEmptyRepoMapping(repoConfig, timeFrom, timeTo, repoType);
        }
    }
    
    /**
     * 一次性获取时间范围内的所有提交
     */
    private List<CommitWithFiles> getAllCommitsInRange(
            RepoConfig repoConfig, Instant timeFrom, Instant timeTo, RepoType repoType) {
        
        try (Repository repository = gitRepositoryHelper.openRepository(repoConfig)) {
            try (Git git = new Git(repository)) {
                
                // 关键优化：一次性获取时间范围内的所有提交
                Iterable<RevCommit> commitIterable = git.log()
                        .addRange(
                            resolveTimeCommit(timeFrom, repository),
                            resolveTimeCommit(timeTo, repository)
                        )
                        .call();
                
                List<RevCommit> commits = new ArrayList<>();
                for (RevCommit commit : commitIterable) {
                    commits.add(commit);
                }
                
                log.debug("获取到 {} 个提交: {} [{}]", commits.size(), repoType, repoConfig.getRepoPath());
                
                // 并行处理每个提交，提取其涉及的文件
                return commits.parallelStream()
                        .map(commit -> new CommitWithFiles(
                                convertToCommitInfo(commit, repoType),
                                extractChangedFiles(commit, repository)
                        ))
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("获取提交范围失败: {} [{}]", repoType, repoConfig.getRepoPath(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 提取提交中变更的文件
     */
    private Set<String> extractChangedFiles(RevCommit commit, Repository repository) {
        try {
            DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
            diffFormatter.setRepository(repository);
            
            List<DiffEntry> diffs;
            if (commit.getParentCount() > 0) {
                RevCommit parent = commit.getParent(0);
                diffs = diffFormatter.scan(parent.getTree(), commit.getTree());
            } else {
                diffs = diffFormatter.scan(new EmptyTreeIterator(), 
                    new CanonicalTreeParser(null, repository.newObjectReader(), commit.getTree()));
            }
            
            return diffs.stream()
                    .map(diff -> {
                        String path = diff.getNewPath();
                        if (path == null || path.equals(DiffEntry.DEV_NULL)) {
                            path = diff.getOldPath();
                        }
                        return path;
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
                    
        } catch (Exception e) {
            log.warn("提取变更文件失败: {}", commit.getId(), e);
            return Collections.emptySet();
        }
    }
    
    /**
     * 转换RevCommit为CommitInfo
     */
    private CommitInfo convertToCommitInfo(RevCommit commit, RepoType repoType) {
        return CommitInfo.builder()
                .commitHash(commit.getId().getName())
                .shortHash(commit.getId().abbreviate(7).name())
                .authorName(commit.getAuthorIdent().getName())
                .authorEmail(commit.getAuthorIdent().getEmailAddress())
                .commitTime(Instant.ofEpochSecond(commit.getCommitTime()))
                .message(commit.getFullMessage())
                .repoType(repoType)
                .commitType(identifyCommitType(commit.getFullMessage()))
                .changedFiles(0) // 稍后在提取文件时设置
                .linesAdded(0)   // 稍后在提取文件时设置
                .linesRemoved(0) // 稍后在提取文件时设置
                .build();
    }
    
    /**
     * 识别提交类型
     */
    private String identifyCommitType(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "unknown";
        }
        
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.startsWith("fix") || lowerMessage.startsWith("bugfix")) {
            return "bugfix";
        } else if (lowerMessage.startsWith("feature") || lowerMessage.startsWith("feat")) {
            return "feature";
        } else if (lowerMessage.startsWith("refactor")) {
            return "refactor";
        } else if (lowerMessage.startsWith("test")) {
            return "test";
        } else if (lowerMessage.startsWith("doc") || lowerMessage.startsWith("chore")) {
            return "chore";
        } else {
            return "other";
        }
    }
    
    /**
     * 构建文件到提交的映射
     */
    private Map<String, List<CommitInfo>> buildFileToCommitsMap(
            List<CommitWithFiles> commitsWithFiles) {
        
        Map<String, List<CommitInfo>> fileToCommits = new HashMap<>();
        
        for (CommitWithFiles commitWithFiles : commitsWithFiles) {
            CommitInfo commit = commitWithFiles.getCommit();
            
            for (String filePath : commitWithFiles.getChangedFiles()) {
                fileToCommits.computeIfAbsent(filePath, k -> new ArrayList<>())
                                 .add(commit);
            }
        }
        
        // 按提交时间倒序排序
        fileToCommits.values().forEach(commits -> 
            commits.sort(Comparator.comparing(CommitInfo::getCommitTime).reversed()));
        
        return fileToCommits;
    }
    
    /**
     * 构建缓存键
     */
    private String buildCacheKey(RepoConfig repoConfig, Instant timeFrom, Instant timeTo, RepoType repoType) {
        String repoPath = repoConfig.getRepoPath() != null ? repoConfig.getRepoPath().getAbsolutePath() : "unknown";
        return String.format("%s_%s_%s_%s", 
                           repoPath, timeFrom, timeTo, repoType);
    }
    
    /**
     * 检查缓存是否过期
     */
    private boolean isExpired(FileCommitMapping.RepoMapping mapping) {
        // 缓存1小时过期
        return mapping.getBuildAt().isBefore(Instant.now().minusSeconds(3600));
    }
    
    /**
     * 创建空的映射
     */
    private FileCommitMapping createEmptyMapping(
            MultiRepoExportRequest.RepoSelection selection, Instant timeFrom, Instant timeTo) {
        return FileCommitMapping.builder()
                .repoSelection(selection)
                .timeFrom(timeFrom)
                .timeTo(timeTo)
                .oracleMapping(createEmptyRepoMapping(null, timeFrom, timeTo, RepoType.ORACLE))
                .gaussMapping(createEmptyRepoMapping(null, timeFrom, timeTo, RepoType.GAUSS))
                .buildAt(Instant.now())
                .build();
    }
    
    /**
     * 创建空的仓库映射
     */
    private FileCommitMapping.RepoMapping createEmptyRepoMapping(
            RepoConfig repoConfig, Instant timeFrom, Instant timeTo, RepoType repoType) {
        return FileCommitMapping.RepoMapping.builder()
                .repoConfig(repoConfig)
                .repoType(repoType)
                .timeFrom(timeFrom)
                .timeTo(timeTo)
                .fileToCommits(Collections.emptyMap())
                .totalCommits(0)
                .totalFiles(0)
                .buildAt(Instant.now())
                .build();
    }
    
    /**
     * 解析时间范围的提交
     */
    private RevCommit resolveTimeCommit(Instant time, Repository repository) {
        try {
            RevWalk walk = new RevWalk(repository);
            // 这里需要根据具体需求实现时间到commit的映射
            // 简化实现：返回HEAD或特定的commit
            return walk.parseCommit(repository.resolve("HEAD"));
        } catch (Exception e) {
            log.warn("解析时间提交失败: {}", time, e);
            return null;
        }
    }
    
    /**
     * 获取扫描报告
     */
    private ScanReport getScanReport(MultiRepoExportRequest.RepoSelection selection) {
        // 这里需要从ScanResultStore获取扫描报告
        // 简化实现，实际需要注入ScanResultStore
        return null;
    }
    
    /**
     * 提取Oracle仓库配置
     */
    private RepoConfig extractOracleRepoConfig(ScanReport scanReport) {
        // 简化实现，实际需要从扫描报告中提取
        return null;
    }
    
    /**
     * 提取Gauss仓库配置
     */
    private RepoConfig extractGaussRepoConfig(ScanReport scanReport) {
        // 简化实现，实际需要从扫描报告中提取
        return null;
    }
}
