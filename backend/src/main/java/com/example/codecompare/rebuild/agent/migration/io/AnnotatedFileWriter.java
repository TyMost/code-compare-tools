package com.example.codecompare.rebuild.agent.migration.io;

import com.example.codecompare.rebuild.agent.migration.LineEndingNormalizer;
import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.stats.CodeBlockDetailDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

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
        Path targetFile = projectFileResolver.resolveTargetFile(detail.getTargetProjectCode(), detail.getFilePath());
        Files.createDirectories(targetFile.getParent());

        String normalized = LineEndingNormalizer.normalize(annotatedCode);
        List<String> annotatedLines = LineEndingNormalizer.splitLines(normalized);
        List<String> existingLines = Files.exists(targetFile)
                ? Files.readAllLines(targetFile, StandardCharsets.UTF_8)
                : new ArrayList<String>();

        int targetLine = determineTargetLine(detail, diff, overrideStartLine);
        int insertIndex = clamp(targetLine - 1, 0, existingLines.size());

        int replaced = 0;
        existingLines.addAll(insertIndex, annotatedLines);

        String updatedContent = LineEndingNormalizer.joinLines(existingLines);
        Files.write(targetFile,
                updatedContent.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);

        return InsertionResult.applied(insertIndex + 1, annotatedLines.size(), replaced, annotatedLines);
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
