package com.example.codecompare.rebuild.diff;

import com.example.codecompare.rebuild.block.model.CodeSnapshot;
import com.example.codecompare.rebuild.core.support.IgnorePatternMatcher;
import com.example.codecompare.rebuild.diff.DiffContentMasker.Scope;
import com.example.codecompare.rebuild.diff.config.DiffConfigurationProperties;
import com.example.codecompare.rebuild.diff.exception.DiffEngineException;
import com.example.codecompare.rebuild.diff.git.GitDiffEngine;
import com.example.codecompare.rebuild.scanning.git.GitDiffFile;
import com.github.difflib.patch.Patch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 行级差异服务，对外封装 diff 流程。
 */
@Service
public class DiffService {

    private static final Logger log = LoggerFactory.getLogger(DiffService.class);

    private final DiffEngine diffEngine;
    private final GitDiffEngine gitDiffEngine;
    private final DiffResultAssembler diffResultAssembler;
    private final DiffConfigurationProperties properties;
    private final DiffContentMasker contentMasker;

    public DiffService(DiffEngine diffEngine,
                       DiffResultAssembler diffResultAssembler,
                       DiffContentMasker contentMasker,
                       DiffConfigurationProperties properties,
                       GitDiffEngine gitDiffEngine) {
        this.diffEngine = Objects.requireNonNull(diffEngine, "diffEngine must not be null");
        this.gitDiffEngine = Objects.requireNonNull(gitDiffEngine, "gitDiffEngine must not be null");
        this.diffResultAssembler = Objects.requireNonNull(diffResultAssembler, "diffResultAssembler must not be null");
        this.contentMasker = contentMasker == null ? DiffContentMasker.noop() : contentMasker;
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public DiffResult analyze(DiffRequest request) {
        return analyze(request, null);
    }

    public DiffResult analyze(DiffRequest request, GitDiffFile gitDiffFile) {
        Assert.notNull(request, "diff 请求不能为空");
        CodeSnapshot source = request.getSource();
        CodeSnapshot target = request.getTarget();
        String sourcePath = source == null ? "" : normalizePath(source.getPath());
        String targetPath = target == null ? "" : normalizePath(target.getPath());
        if (shouldIgnore(sourcePath, targetPath)) {
            log.debug("diff 忽略匹配的路径，source={}, target={}", sourcePath, targetPath);
            return DiffResult.empty();
        }

        String sourceContent = source == null ? "" : normalizeContent(source.getContent());
        String targetContent = target == null ? "" : normalizeContent(target.getContent());
        sourceContent = maskContent(sourceContent, Scope.SOURCE);
        targetContent = maskContent(targetContent, Scope.TARGET);
        validateSize(sourceContent, sourcePath);
        validateSize(targetContent, targetPath);

        List<String> sourceLines = toLines(sourceContent);
        List<String> targetLines = toLines(targetContent);

        if (log.isDebugEnabled()) {
            log.debug("DiffService analyze start, engineType={} gitDiffFilePresent={} gitHunks={}",
                    properties.getEngineType(),
                    gitDiffFile != null,
                    gitDiffFile == null || gitDiffFile.getHunks() == null ? 0 : gitDiffFile.getHunks().size());
        }

        if (shouldUseGitEngine(gitDiffFile)) {
            DiffResult gitResult = gitDiffEngine.analyze(gitDiffFile);
            if (gitResult != null && !CollectionUtils.isEmpty(gitResult.getSegments())) {
                log.debug("DiffService returning Git diff result, segments={}", gitResult.getSegments().size());
                return gitResult;
            }
            String diffPath = gitDiffFile == null ? targetPath : gitDiffFile.getPath();
            log.debug("Git diff 返回空结果，改用默认引擎 path={}", diffPath);
        }

        Patch<String> patch = diffEngine.compare(sourceLines, targetLines);
        if (patch == null || patch.getDeltas().isEmpty()) {
            log.debug("Default diff engine produced empty patch for source={} target={}", sourcePath, targetPath);
            return DiffResult.empty();
        }
        return diffResultAssembler.assemble(sourceLines, targetLines, patch);
    }

    private boolean shouldUseGitEngine(GitDiffFile gitDiffFile) {
        if (!"git".equalsIgnoreCase(properties.getEngineType())) {
            return false;
        }
        return gitDiffFile != null && !CollectionUtils.isEmpty(gitDiffFile.getHunks());
    }

    private String maskContent(String content, Scope scope) {
        if (contentMasker == null) {
            return content;
        }
        try {
            String masked = contentMasker.mask(content, scope);
            if (masked == null) {
                return "";
            }
            String normalized = normalizeContent(masked);
            if (normalized.equals(content)) {
                return normalized;
            }
            return trimMaskedBoundaries(normalized);
        } catch (Exception ex) {
            log.warn("content-mask 处理失败，将继续使用原始内容，scope={}", scope, ex);
            return content;
        }
    }

    private void validateSize(String content, String path) {
        long maxSize = properties.getMaxFileSize();
        if (maxSize <= 0) {
            return;
        }
        int length = content.getBytes(StandardCharsets.UTF_8).length;
        if (length > maxSize) {
            throw new DiffEngineException("文件超过 diff 大小限制: " + path);
        }
    }

    private boolean shouldIgnore(String sourcePath, String targetPath) {
        List<String> patterns = properties.getIgnorePatterns();
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }
        IgnorePatternMatcher matcher = IgnorePatternMatcher.from(patterns);
        if (matcher.isEmpty()) {
            return false;
        }
        if (StringUtils.hasText(sourcePath) && matcher.matches(sourcePath)) {
            return true;
        }
        if (StringUtils.hasText(targetPath) && matcher.matches(targetPath)) {
            return true;
        }
        return false;
    }

    private String normalizePath(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return "";
        }
        return rawPath.replace('\\', '/');
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private List<String> toLines(String content) {
        if (!StringUtils.hasText(content)) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException ex) {
            throw new DiffEngineException("解析内容行失败", ex);
        }
        return lines;
    }

    private String trimMaskedBoundaries(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String withoutLeading = text.replaceFirst("^(\\s*\\n)+", "");
        return withoutLeading.replaceFirst("(\\n\\s*)+$", "");
    }
}
