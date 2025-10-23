package com.example.codecompare.rebuild.agent.migration.io;

import com.example.codecompare.rebuild.agent.migration.LineEndingNormalizer;
import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.stats.CodeBlockDetailDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Writes annotated code snippets into target project files.
 */
@Component
public class AnnotatedFileWriter {

    private static final Logger log = LoggerFactory.getLogger(AnnotatedFileWriter.class);

    private final ProjectFileResolver projectFileResolver;

    private enum WriteMode {
        INSERT,
        REPLACE
    }

    public AnnotatedFileWriter(ProjectFileResolver projectFileResolver) {
        this.projectFileResolver = projectFileResolver;
    }

    public InsertionResult writeAnnotatedToFile(CodeBlockDetailDTO detail,
                                                String annotatedCode,
                                                BlockDiff diff) throws IOException {
        return writeAnnotatedToFile(detail, annotatedCode, diff, -1);
    }

    public InsertionResult writeAnnotatedToFile(CodeBlockDetailDTO detail,
                                                String annotatedCode,
                                                BlockDiff diff,
                                                int overrideStartLine) throws IOException {
        return writeAnnotated(detail, annotatedCode, diff, overrideStartLine, WriteMode.INSERT, Collections.<String>emptyList());
    }

    public InsertionResult writeAnnotatedWithReplacement(CodeBlockDetailDTO detail,
                                                         String annotatedCode,
                                                         BlockDiff diff,
                                                         List<String> expectedOriginal) throws IOException {
        return writeAnnotatedWithReplacement(detail, annotatedCode, diff, -1, expectedOriginal);
    }

    public InsertionResult writeAnnotatedWithReplacement(CodeBlockDetailDTO detail,
                                                         String annotatedCode,
                                                         BlockDiff diff,
                                                         int overrideStartLine,
                                                         List<String> expectedOriginal) throws IOException {
        return writeAnnotated(detail, annotatedCode, diff, overrideStartLine, WriteMode.REPLACE, expectedOriginal);
    }

    public InsertionResult removeAnnotatedSegment(CodeBlockDetailDTO detail,
                                                  String blockId,
                                                  String templateKey,
                                                  String replacementContent,
                                                  int referenceStartLineHint) throws IOException {
        int referenceLine = referenceStartLineHint > 0 ? referenceStartLineHint : 1;
        if (detail == null || !StringUtils.hasText(detail.getTargetProjectCode()) || !StringUtils.hasText(detail.getFilePath())) {
            return InsertionResult.skipped(referenceLine, Collections.<String>emptyList());
        }
        Marker marker = resolveMarkers(templateKey, blockId);
        if (marker == null) {
            return InsertionResult.skipped(referenceLine, Collections.<String>emptyList());
        }
        Path targetFile = projectFileResolver.resolveTargetFile(detail.getTargetProjectCode(), detail.getFilePath());
        if (!Files.exists(targetFile)) {
            return InsertionResult.skipped(referenceLine, Collections.<String>emptyList());
        }
        List<String> existingLines = Files.readAllLines(targetFile, StandardCharsets.UTF_8);
        if (CollectionUtils.isEmpty(existingLines)) {
            return InsertionResult.skipped(referenceLine, Collections.<String>emptyList());
        }
        int preferredIndex = clamp(referenceLine - 1, 0, Math.max(existingLines.size() - 1, 0));
        int startIndex = findMarkerIndex(existingLines, marker.getStart(), preferredIndex);
        if (startIndex < 0) {
            startIndex = findMarkerIndex(existingLines, marker.getStart(), 0);
        }
        if (startIndex < 0) {
            return InsertionResult.skipped(referenceLine, Collections.<String>emptyList());
        }
        int endIndex = findMarkerIndex(existingLines, marker.getEnd(), startIndex);
        if (endIndex < startIndex) {
            return InsertionResult.skipped(referenceLine, Collections.<String>emptyList());
        }
        int removeCount = endIndex - startIndex + 1;
        for (int i = 0; i < removeCount && startIndex < existingLines.size(); i++) {
            existingLines.remove(startIndex);
        }
        String normalizedReplacement = LineEndingNormalizer.normalize(replacementContent);
        List<String> replacementLines = LineEndingNormalizer.splitLines(normalizedReplacement);
        if (!CollectionUtils.isEmpty(replacementLines)) {
            existingLines.addAll(startIndex, replacementLines);
        }
        Files.createDirectories(targetFile.getParent());
        String updatedContent = LineEndingNormalizer.joinLines(existingLines);
        Files.write(targetFile,
                updatedContent.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        int insertedLines = CollectionUtils.isEmpty(replacementLines) ? 0 : replacementLines.size();
        return InsertionResult.applied(startIndex + 1, insertedLines, removeCount, replacementLines);
    }

    private int determineTargetLine(CodeBlockDetailDTO detail, BlockDiff diff, int overrideStartLine) {
        if (overrideStartLine > 0) {
            return overrideStartLine;
        }
        if (diff != null && diff.getTargetStartLine() > 0) {
            return diff.getTargetStartLine();
        }
        if (detail != null && detail.getStartLine() > 0) {
            return detail.getStartLine();
        }
        if (detail != null && detail.getEndLine() > 0) {
            return detail.getEndLine();
        }
        return Integer.MAX_VALUE;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private InsertionResult writeAnnotated(CodeBlockDetailDTO detail,
                                           String annotatedCode,
                                           BlockDiff diff,
                                           int overrideStartLine,
                                           WriteMode mode,
                                           List<String> expectedOriginal) throws IOException {
        Path targetFile = projectFileResolver.resolveTargetFile(detail.getTargetProjectCode(), detail.getFilePath());
        Files.createDirectories(targetFile.getParent());

        String normalized = LineEndingNormalizer.normalize(annotatedCode);
        List<String> annotatedLines = LineEndingNormalizer.splitLines(normalized);
        List<String> existingLines = Files.exists(targetFile)
                ? Files.readAllLines(targetFile, StandardCharsets.UTF_8)
                : new ArrayList<String>();

        int targetLine = determineTargetLine(detail, diff, overrideStartLine);
        int insertIndex = clamp(targetLine - 1, 0, existingLines.size());

        ReplacementWindow window = null;
        if (mode == WriteMode.REPLACE) {
            List<String> expected = expectedOriginal == null ? Collections.<String>emptyList() : expectedOriginal;
            if (CollectionUtils.isEmpty(expected) && diff != null && !CollectionUtils.isEmpty(diff.getTargetLines())) {
                expected = diff.getTargetLines();
            }
            window = locateReplacementWindow(existingLines, insertIndex, expected);
            if (window != null && window.length > 0) {
                for (int i = 0; i < window.length && window.start < existingLines.size(); i++) {
                    existingLines.remove(window.start);
                }
                insertIndex = window.start;
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("未找到原始实现片段，跳过替换写入，file={} startIndex={} expectedSize={}",
                            detail == null ? null : detail.getFilePath(),
                            Integer.valueOf(insertIndex),
                            Integer.valueOf(expected == null ? 0 : expected.size()));
                }
                return InsertionResult.skipped(insertIndex + 1, annotatedLines);
            }
        }

        existingLines.addAll(insertIndex, annotatedLines);

        String updatedContent = LineEndingNormalizer.joinLines(existingLines);
        Files.write(targetFile,
                updatedContent.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);

        int replaced = window == null ? 0 : window.length;
        return InsertionResult.applied(insertIndex + 1, annotatedLines.size(), replaced, annotatedLines);
    }

    private ReplacementWindow locateReplacementWindow(List<String> existingLines,
                                                      int preferredIndex,
                                                      List<String> expectedOriginal) {
        List<String> sanitized = sanitizeExpected(expectedOriginal);
        if (CollectionUtils.isEmpty(sanitized)) {
            return null;
        }
        int matchIndex = matchAt(existingLines, preferredIndex, sanitized);
        if (matchIndex < 0) {
            matchIndex = findSegment(existingLines, sanitized);
        }
        if (matchIndex < 0) {
            if (log.isDebugEnabled()) {
                log.debug("未找到可替换的原始片段，保持追加模式 preferredIndex={} expectedSize={}",
                        Integer.valueOf(preferredIndex),
                        Integer.valueOf(sanitized.size()));
            }
            return null;
        }
        return ReplacementWindow.of(matchIndex, sanitized.size());
    }

    private int matchAt(List<String> existingLines, int startIndex, List<String> expected) {
        if (CollectionUtils.isEmpty(expected) || existingLines == null) {
            return -1;
        }
        if (startIndex < 0 || startIndex + expected.size() > existingLines.size()) {
            return -1;
        }
        for (int i = 0; i < expected.size(); i++) {
            if (!linesEqual(existingLines.get(startIndex + i), expected.get(i))) {
                return -1;
            }
        }
        return startIndex;
    }

    private int findSegment(List<String> existingLines, List<String> expected) {
        if (CollectionUtils.isEmpty(expected) || CollectionUtils.isEmpty(existingLines)) {
            return -1;
        }
        int maxStart = existingLines.size() - expected.size();
        for (int i = 0; i <= maxStart; i++) {
            boolean match = true;
            for (int j = 0; j < expected.size(); j++) {
                if (!linesEqual(existingLines.get(i + j), expected.get(j))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    private Marker resolveMarkers(String templateKey, String blockId) {
        if (!StringUtils.hasText(templateKey)) {
            return null;
        }
        String trimmedKey = templateKey.trim();
        if ("migrate_adapt".equalsIgnoreCase(trimmedKey)) {
            return new Marker("/** 迁移适配段*/", "/** 迁移适配段结束 */");
        }
        if ("default".equalsIgnoreCase(trimmedKey)) {
            String safeBlockId = blockId == null ? "" : blockId;
            return new Marker(
                    String.format("/** 迁移生成的代码片段开始（blockId=%s）*/", safeBlockId),
                    "/** 迁移生成的代码片段结束 */");
        }
        return null;
    }

    private int findMarkerIndex(List<String> existingLines, String marker, int startIndex) {
        if (!StringUtils.hasText(marker) || CollectionUtils.isEmpty(existingLines)) {
            return -1;
        }
        int begin = Math.max(0, startIndex);
        for (int i = begin; i < existingLines.size(); i++) {
            if (linesEqual(existingLines.get(i), marker)) {
                return i;
            }
        }
        return -1;
    }

    private boolean linesEqual(String left, String right) {
        return normalizeForComparison(left).equals(normalizeForComparison(right));
    }

    private String normalizeForComparison(String value) {
        return Objects.toString(value, "").trim();
    }

    private List<String> sanitizeExpected(List<String> expectedOriginal) {
        if (CollectionUtils.isEmpty(expectedOriginal)) {
            return Collections.emptyList();
        }
        int start = 0;
        int end = expectedOriginal.size() - 1;
        while (start <= end && normalizeForComparison(expectedOriginal.get(start)).isEmpty()) {
            start++;
        }
        while (end >= start && normalizeForComparison(expectedOriginal.get(end)).isEmpty()) {
            end--;
        }
        if (start > end) {
            return Collections.emptyList();
        }
        List<String> sanitized = new ArrayList<String>(end - start + 1);
        for (int i = start; i <= end; i++) {
            sanitized.add(expectedOriginal.get(i));
        }
        return sanitized;
    }

    private static final class ReplacementWindow {
        private final int start;
        private final int length;

        private ReplacementWindow(int start, int length) {
            this.start = Math.max(0, start);
            this.length = Math.max(0, length);
        }

        private static ReplacementWindow of(int start, int length) {
            return new ReplacementWindow(start, length);
        }

    }

    private static final class Marker {
        private final String start;
        private final String end;

        private Marker(String start, String end) {
            this.start = start;
            this.end = end;
        }

        private String getStart() {
            return start;
        }

        private String getEnd() {
            return end;
        }
    }

    public static final class InsertionResult {
        private final boolean skipped;
        private final int startLine;
        private final int insertedLines;
        private final int replacedLines;
        private final List<String> snippet;

        private InsertionResult(boolean skipped, int startLine, int insertedLines, int replacedLines, List<String> snippet) {
            this.skipped = skipped;
            this.startLine = startLine;
            this.insertedLines = insertedLines;
            this.replacedLines = replacedLines;
            if (CollectionUtils.isEmpty(snippet)) {
                this.snippet = Collections.emptyList();
            } else {
                this.snippet = Collections.unmodifiableList(new ArrayList<String>(snippet));
            }
        }

        public static InsertionResult skipped(int startLine, List<String> snippet) {
            return new InsertionResult(true, startLine, 0, 0, snippet);
        }

        public static InsertionResult applied(int startLine, int insertedLines, int replacedLines, List<String> snippet) {
            return new InsertionResult(false, startLine, insertedLines, replacedLines, snippet);
        }

        public boolean isSkipped() {
            return skipped;
        }

        public int getStartLine() {
            return startLine;
        }

        public int getInsertedLines() {
            return insertedLines;
        }

        public int getReplacedLines() {
            return replacedLines;
        }

        public List<String> getSnippet() {
            return snippet;
        }

        public String preview() {
            if (CollectionUtils.isEmpty(snippet)) {
                return "";
            }
            int limit = Math.min(3, snippet.size());
            List<String> fragments = new ArrayList<String>(limit);
            for (int i = 0; i < limit; i++) {
                fragments.add(Objects.toString(snippet.get(i), "").trim());
            }
            String joined = String.join(" | ", fragments);
            if (snippet.size() > limit) {
                joined = joined + " ...";
            }
            return joined;
        }
    }
}
