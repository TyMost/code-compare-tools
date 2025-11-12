package com.example.migratediff.infrastructure.git;

import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.repo.RepoConfig;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class GitDiffParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitDiffParser.class);
    private final GitDiffAdapter gitDiffAdapter;

    public GitDiffParser(GitDiffAdapter gitDiffAdapter) {
        this.gitDiffAdapter = gitDiffAdapter;
    }

    /**
     * 解析 DiffEntry 列表，转换为领域层可用的 DiffFile 集合。
     */
    public List<DiffFile> parse(List<DiffEntry> entries, Repository repository, DiffFormatter formatter, RepoConfig repoConfig) {
        List<DiffFile> files = new ArrayList<DiffFile>();
        if (entries == null || entries.isEmpty()) {
            return files;
        }
        for (DiffEntry entry : entries) {
            try {
                DiffFile diffFile = gitDiffAdapter.adapt(repository, formatter, entry, repoConfig);
                if (diffFile != null) {
                    files.add(diffFile);
                }
            } catch (IOException ex) {
                LOGGER.warn("解析文件差异失败 [{} -> {}]: {}", entry.getOldPath(), entry.getNewPath(), ex.getMessage(), ex);
            }
        }
        return files;
    }
}
