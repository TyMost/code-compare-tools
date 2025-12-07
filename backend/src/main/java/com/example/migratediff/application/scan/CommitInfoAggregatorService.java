package com.example.migratediff.application.scan;

import com.example.migratediff.api.dto.GitCommitInfoDTO;
import com.example.migratediff.application.commit.GitCommitHistoryService;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.domain.repo.RepoType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 提交信息聚合服务
 * 负责收集和聚合文件的提交信息，用于导出报表
 */
@Service
@Slf4j
public class CommitInfoAggregatorService {

    private final GitCommitHistoryService gitCommitHistoryService;

    public CommitInfoAggregatorService(GitCommitHistoryService gitCommitHistoryService) {
        this.gitCommitHistoryService = gitCommitHistoryService;
    }

    // 提交消息类型识别模式
    private static final Map<String, Pattern> COMMIT_TYPE_PATTERNS = new HashMap<>();
    
    static {
        COMMIT_TYPE_PATTERNS.put("feature", Pattern.compile("(?i)^(feat|feature)[\\[\\(\\s:]"));
        COMMIT_TYPE_PATTERNS.put("bugfix", Pattern.compile("(?i)^(fix|bugfix|hotfix)[\\[\\(\\s:]"));
        COMMIT_TYPE_PATTERNS.put("refactor", Pattern.compile("(?i)^(refactor|ref)[\\[\\(\\s:]"));
        COMMIT_TYPE_PATTERNS.put("test", Pattern.compile("(?i)^(test|testing)[\\[\\(\\s:]"));
        COMMIT_TYPE_PATTERNS.put("docs", Pattern.compile("(?i)^(docs|doc)[\\[\\(\\s:]"));
        COMMIT_TYPE_PATTERNS.put("chore", Pattern.compile("(?i)^(chore|build|ci)[\\[\\(\\s:]"));
        COMMIT_TYPE_PATTERNS.put("perf", Pattern.compile("(?i)^(perf|performance)[\\[\\(\\s:]"));
        COMMIT_TYPE_PATTERNS.put("style", Pattern.compile("(?i)^(style|fmt)[\\[\\(\\s:]"));
    }

    /**
     * 为文件行添加提交信息
     */
    public DiffMatrixRow enrichWithCommitInfo(DiffMatrixRow row, RepoConfig oracleRepo, RepoConfig gaussRepo) {
        if (row == null || row.getFilePath() == null) {
            return row;
        }

        try {
            // 获取Oracle提交信息
            List<GitCommitInfoDTO> oracleCommits = gitCommitHistoryService.getFileCommitHistory(
                row.getFilePath(), oracleRepo, null).getOracleCommits();
            
            // 获取Gauss提交信息
            List<GitCommitInfoDTO> gaussCommits = gitCommitHistoryService.getFileCommitHistory(
                row.getFilePath(), null, gaussRepo).getGaussCommits();

            // 处理Oracle提交信息
            if (oracleCommits != null && !oracleCommits.isEmpty()) {
                GitCommitInfoDTO lastOracleCommit = oracleCommits.get(0);
                row.setLastOracleAuthor(lastOracleCommit.getAuthorName());
                row.setLastOracleCommitTime(lastOracleCommit.getCommitTime());
                row.setLastOracleCommitMessage(truncateMessage(lastOracleCommit.getMessage()));
                row.setLastOracleCommitHash(lastOracleCommit.getShortHash());
                row.setOracleCommitCount(oracleCommits.size());
                row.setHasOracleCommits(true);
            } else {
                row.setHasOracleCommits(false);
                row.setOracleCommitCount(0);
            }

            // 处理Gauss提交信息
            if (gaussCommits != null && !gaussCommits.isEmpty()) {
                GitCommitInfoDTO lastGaussCommit = gaussCommits.get(0);
                row.setLastGaussAuthor(lastGaussCommit.getAuthorName());
                row.setLastGaussCommitTime(lastGaussCommit.getCommitTime());
                row.setLastGaussCommitMessage(truncateMessage(lastGaussCommit.getMessage()));
                row.setLastGaussCommitHash(lastGaussCommit.getShortHash());
                row.setGaussCommitCount(gaussCommits.size());
                row.setHasGaussCommits(true);
            } else {
                row.setHasGaussCommits(false);
                row.setGaussCommitCount(0);
            }

        } catch (Exception e) {
            log.warn("Failed to enrich commit info for file: {}", row.getFilePath(), e);
            // 设置默认值
            row.setHasOracleCommits(false);
            row.setHasGaussCommits(false);
            row.setOracleCommitCount(0);
            row.setGaussCommitCount(0);
        }

        return row;
    }

    /**
     * 聚合提交者统计信息
     */
    public List<CommitAuthorStats> aggregateAuthorStats(List<DiffMatrixRow> rows, 
                                                     RepoConfig oracleRepo, 
                                                     RepoConfig gaussRepo,
                                                     String repoName,
                                                     Instant timeFrom,
                                                     Instant timeTo) {
        
        Map<String, CommitAuthorStats> authorStatsMap = new HashMap<>();

        for (DiffMatrixRow row : rows) {
            if (row.getFilePath() == null) continue;

            try {
                // 获取时间范围内的Oracle提交
                List<GitCommitInfoDTO> oracleCommits = getTimeRangeCommits(row.getFilePath(), oracleRepo, timeFrom, timeTo, RepoType.ORACLE);
                // 获取时间范围内的Gauss提交
                List<GitCommitInfoDTO> gaussCommits = getTimeRangeCommits(row.getFilePath(), gaussRepo, timeFrom, timeTo, RepoType.GAUSS);

                // 处理Oracle提交者
                processCommits(oracleCommits, authorStatsMap, repoName, true, row.getFilePath());
                
                // 处理Gauss提交者
                processCommits(gaussCommits, authorStatsMap, repoName, false, row.getFilePath());

            } catch (Exception e) {
                log.warn("Failed to aggregate author stats for file: {}", row.getFilePath(), e);
            }
        }

        // 计算统计信息并排序
        return authorStatsMap.values().stream()
            .peek(this::calculateDerivedStats)
            .sorted(Comparator.comparing(CommitAuthorStats::getTotalCommitCount).reversed()
                .thenComparing(CommitAuthorStats::getAuthorName))
            .collect(Collectors.toList());
    }

    /**
     * 获取时间范围内的提交
     */
    private List<GitCommitInfoDTO> getTimeRangeCommits(String filePath, RepoConfig repoConfig, 
                                                      Instant timeFrom, Instant timeTo, RepoType repoType) {
        if (repoConfig == null) return Collections.emptyList();
        
        try {
            List<GitCommitInfoDTO> commits = repoType == RepoType.ORACLE 
                ? gitCommitHistoryService.getFileCommitHistory(filePath, repoConfig, null).getOracleCommits()
                : gitCommitHistoryService.getFileCommitHistory(filePath, null, repoConfig).getGaussCommits();
            
            if (commits == null) return Collections.emptyList();

            // 过滤时间范围
            return commits.stream()
                .filter(commit -> {
                    Instant commitTime = commit.getCommitTime();
                    if (timeFrom != null && commitTime.isBefore(timeFrom)) return false;
                    if (timeTo != null && commitTime.isAfter(timeTo)) return false;
                    return true;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to get time range commits for file: {}", filePath, e);
            return Collections.emptyList();
        }
    }

    /**
     * 处理提交列表，更新作者统计
     */
    private void processCommits(List<GitCommitInfoDTO> commits, Map<String, CommitAuthorStats> authorStatsMap, 
                               String repoName, boolean isOracle, String filePath) {
        for (GitCommitInfoDTO commit : commits) {
            if (commit.getAuthorName() == null) continue;

            String authorKey = commit.getAuthorName() + "|" + (commit.getAuthorEmail() != null ? commit.getAuthorEmail() : "");
            
            CommitAuthorStats stats = authorStatsMap.computeIfAbsent(authorKey, k -> 
                CommitAuthorStats.builder()
                    .repoName(repoName)
                    .authorName(commit.getAuthorName())
                    .authorEmail(commit.getAuthorEmail())
                    .oracleCommitCount(0)
                    .gaussCommitCount(0)
                    .totalCommitCount(0)
                    .affectedFiles(new HashSet<>())
                    .recentCommitMessages(new ArrayList<>())
                    .commonCommitTypes(new ArrayList<>())
                    .build());

            // 更新提交计数
            if (isOracle) {
                stats.setOracleCommitCount(stats.getOracleCommitCount() + 1);
            } else {
                stats.setGaussCommitCount(stats.getGaussCommitCount() + 1);
            }
            stats.setTotalCommitCount(stats.getTotalCommitCount() + 1);

            // 更新时间范围
            Instant commitTime = commit.getCommitTime();
            if (stats.getFirstCommitTime() == null || commitTime.isBefore(stats.getFirstCommitTime())) {
                stats.setFirstCommitTime(commitTime);
            }
            if (stats.getLastCommitTime() == null || commitTime.isAfter(stats.getLastCommitTime())) {
                stats.setLastCommitTime(commitTime);
            }

            // 添加影响的文件
            stats.getAffectedFiles().add(filePath);

            // 添加最近提交消息（限制数量）
            if (stats.getRecentCommitMessages().size() < 5) {
                stats.getRecentCommitMessages().add(truncateMessage(commit.getMessage()));
            }

            // 分析提交类型
            analyzeCommitType(commit.getMessage(), stats);
        }
    }

    /**
     * 分析提交消息类型
     */
    private void analyzeCommitType(String message, CommitAuthorStats stats) {
        if (message == null || message.trim().isEmpty()) return;

        String firstLine = message.split("\n")[0].trim();
        
        for (Map.Entry<String, Pattern> entry : COMMIT_TYPE_PATTERNS.entrySet()) {
            Matcher matcher = entry.getValue().matcher(firstLine);
            if (matcher.find()) {
                String type = entry.getKey();
                if (!stats.getCommonCommitTypes().contains(type)) {
                    stats.getCommonCommitTypes().add(type);
                }
                break;
            }
        }
    }

    /**
     * 计算派生统计信息
     */
    private void calculateDerivedStats(CommitAuthorStats stats) {
        // 计算提交者类型
        if (stats.getOracleCommitCount() > 0 && stats.getGaussCommitCount() > 0) {
            stats.setAuthorType("both");
        } else if (stats.getOracleCommitCount() > 0) {
            stats.setAuthorType("oracle-only");
        } else if (stats.getGaussCommitCount() > 0) {
            stats.setAuthorType("gauss-only");
        } else {
            stats.setAuthorType("unknown");
        }

        // 计算影响文件数
        stats.setAffectedFilesCount(stats.getAffectedFiles().size());

        // 计算活跃度评分（基于提交数和文件数的加权平均）
        double commitScore = Math.log1p(stats.getTotalCommitCount()) * 10;
        double fileScore = Math.log1p(stats.getAffectedFilesCount()) * 5;
        stats.setActivityScore((commitScore + fileScore) / 2);
    }

    /**
     * 截断提交消息
     */
    private String truncateMessage(String message) {
        if (message == null) return "";
        String trimmed = message.trim();
        if (trimmed.length() <= 50) return trimmed;
        return trimmed.substring(0, 47) + "...";
    }
}
