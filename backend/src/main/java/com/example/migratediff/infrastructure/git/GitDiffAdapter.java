package com.example.migratediff.infrastructure.git;

import com.example.migratediff.domain.diff.DeltaType;
import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffType;
import com.example.migratediff.domain.repo.RepoConfig;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.patch.HunkHeader.OldImage;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GitDiffAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(GitDiffAdapter.class);
    private static final Pattern INVALID_HEADER = Pattern.compile("^(diff --git|index|---|\\+\\+\\+)\\b");
    private static final Pattern HUNK_HEADER_PATTERN = Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

    /**
     * Convert JGit DiffEntry/FileHeader into domain DiffFile/DiffBlock structures.
     */
    public DiffFile adapt(Repository repository, DiffFormatter formatter, DiffEntry entry, RepoConfig config) throws IOException {
        FileHeader fileHeader = formatter.toFileHeader(entry);
        List<DiffBlock> blocks = buildBlocks(repository, entry, fileHeader);
        DeltaType deltaType = config != null && config.getDeltaType() != null ? config.getDeltaType() : DeltaType.DELTA_O;
        DiffType diffType = mapFileDiffType(entry);
        String relativePath = resolveRelativePath(entry);
        return DiffFile.builder()
                .relativePath(relativePath)
                .deltaType(deltaType)
                .diffType(diffType)
                .blocks(blocks)
                .build();
    }

    private String resolveRelativePath(DiffEntry entry) {
        DiffEntry.ChangeType changeType = entry.getChangeType();
        if (changeType == DiffEntry.ChangeType.DELETE) {
            return entry.getOldPath();
        }
        if (changeType == DiffEntry.ChangeType.RENAME) {
            return entry.getNewPath();
        }
        return DiffEntry.DEV_NULL.equals(entry.getNewPath()) ? entry.getOldPath() : entry.getNewPath();
    }

    private DiffType mapFileDiffType(DiffEntry entry) {
        DiffEntry.ChangeType changeType = entry.getChangeType();
        if (changeType == null) {
            return DiffType.MODIFY;
        }
        switch (changeType) {
            case ADD:
                return DiffType.ADD;
            case DELETE:
                return DiffType.DELETE;
            case RENAME:
                return DiffType.RENAME;
            default:
                return DiffType.MODIFY;
        }
    }

    private List<DiffBlock> buildBlocks(Repository repository, DiffEntry entry, FileHeader fileHeader) throws IOException {
        List<DiffBlock> blocks = new ArrayList<>();
        String diffText = formatDiffWithFormatter(repository, entry);
        List<String> fallbackHunks = splitFallbackHunks(diffText);
        if (fallbackHunks != null && !fallbackHunks.isEmpty()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Fallback diff for {}:\n{}", entry.getNewPath(), diffText);
            }
            for (String hunkText : fallbackHunks) {
                HunkRange range = parseRange(hunkText);
                if (range == null) {
                    continue;
                }
                DiffBlock block = convertToDiffBlock(range, hunkText);
                if (block != null) {
                    blocks.add(block);
                }
            }
        }
        if (!blocks.isEmpty()) {
            return blocks;
        }
        for (HunkHeader hunkHeader : fileHeader.getHunks()) {
            String hunkText = extractHunkText(fileHeader, hunkHeader);
            if (isBlank(hunkText)) {
                continue;
            }
            DiffBlock block = convertToDiffBlock(hunkHeader, hunkText);
            if (block != null) {
                blocks.add(block);
            }
        }
        return blocks;
    }

    private String extractHunkText(FileHeader fileHeader, HunkHeader hunkHeader) {
        byte[] buffer = fileHeader.getBuffer();
        if (buffer == null || buffer.length == 0) {
            return "";
        }
        int startOffset = hunkHeader.getStartOffset();
        int endOffset = hunkHeader.getEndOffset();
        if (startOffset < 0 || endOffset < 0 || endOffset <= startOffset || endOffset > buffer.length) {
            return "";
        }
        return new String(buffer, startOffset, endOffset - startOffset, resolveCharset(fileHeader));
    }

    private Charset resolveCharset(FileHeader fileHeader) {
        // JGit does not expose FileHeader charset; default to UTF-8.
        return StandardCharsets.UTF_8;
    }

    private String fallbackFromHeaderOrFormat(Repository repository, DiffEntry entry, FileHeader fileHeader) throws IOException {
        String diff = decodeFullBuffer(fileHeader);
        if (!isBlank(diff)) {
            return diff;
        }
        return formatDiffWithFormatter(repository, entry);
    }

    private String decodeFullBuffer(FileHeader fileHeader) {
        byte[] buffer = fileHeader.getBuffer();
        if (buffer == null || buffer.length == 0) {
            return "";
        }
        return new String(buffer, resolveCharset(fileHeader));
    }

    private String formatDiffWithFormatter(Repository repository, DiffEntry entry) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DiffFormatter diffFormatter = new DiffFormatter(outputStream);
        try {
            diffFormatter.setRepository(repository);
            diffFormatter.setDetectRenames(true);
            diffFormatter.setContext(3);
            diffFormatter.format(entry);
            diffFormatter.flush();
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            diffFormatter.close();
        }
    }

    private String extractHunkFromFallback(String fallback, HunkHeader header) {
        if (isBlank(fallback)) {
            return "";
        }
        String marker = buildHunkHeaderLine(header);
        int start = fallback.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int next = fallback.indexOf("\n@@", start + marker.length());
        if (next < 0) {
            next = fallback.length();
        }
        return fallback.substring(start, next);
    }

    private HunkRange parseRange(String hunkText) {
        if (isBlank(hunkText)) {
            return null;
        }
        int lineBreak = hunkText.indexOf('\n');
        String headerLine = lineBreak >= 0 ? hunkText.substring(0, lineBreak) : hunkText;
        Matcher matcher = HUNK_HEADER_PATTERN.matcher(headerLine.trim());
        if (!matcher.matches()) {
            return null;
        }
        int oldStart = parseInt(matcher.group(1));
        int oldCount = parseIntWithDefault(matcher.group(2), 1);
        int newStart = parseInt(matcher.group(3));
        int newCount = parseIntWithDefault(matcher.group(4), 1);
        return new HunkRange(oldStart, oldCount, newStart, newCount);
    }

    private int parseInt(String value) {
        return parseIntWithDefault(value, 0);
    }

    private int parseIntWithDefault(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String buildHunkHeaderLine(HunkHeader header) {
        StringBuilder builder = new StringBuilder();
        builder.append("@@ -")
                .append(formatRange(resolveOldStartLine(header), resolveOldLineCount(header)))
                .append(" +")
                .append(formatRange(header.getNewStartLine(), header.getNewLineCount()))
                .append(" @@");
        return builder.toString();
    }

    private String formatRange(int start, int count) {
        int normalizedStart = Math.max(start, 0);
        if (count <= 1) {
            return String.valueOf(normalizedStart);
        }
        return normalizedStart + "," + count;
    }

    private DiffBlock convertToDiffBlock(HunkHeader header, String hunkText) {
        List<String> lines = new ArrayList<>(Arrays.asList(hunkText.split("\\r?\\n")));
        List<String> cleaned = cleanDiffLines(lines);
        String contentFrom = extractContent(cleaned, true);
        String contentTo = extractContent(cleaned, false);
        contentFrom = trimTrailingBlankLines(contentFrom);
        contentTo = trimTrailingBlankLines(contentTo);
        int oldStart = resolveOldStartLine(header);
        int oldCount = resolveOldLineCount(header);
        DiffType type = determineBlockType(oldCount, header.getNewLineCount());
        return DiffBlock.builder()
                .startLineFrom(calcStartLine(oldStart, oldCount))
                .endLineFrom(calcEndLine(oldStart, oldCount))
                .startLineTo(calcStartLine(header.getNewStartLine(), header.getNewLineCount()))
                .endLineTo(calcEndLine(header.getNewStartLine(), header.getNewLineCount()))
                .contentFrom(contentFrom)
                .contentTo(contentTo)
                .type(type)
                .build();
    }

    private DiffBlock convertToDiffBlock(HunkRange range, String hunkText) {
        List<String> lines = new ArrayList<>(Arrays.asList(hunkText.split("\\r?\\n")));
        List<String> cleaned = cleanDiffLines(lines);
        String contentFrom = extractContent(cleaned, true);
        String contentTo = extractContent(cleaned, false);
        contentFrom = trimTrailingBlankLines(contentFrom);
        contentTo = trimTrailingBlankLines(contentTo);
        DiffType type = determineBlockType(range.oldCount, range.newCount);
        return DiffBlock.builder()
                .startLineFrom(calcStartLine(range.oldStart, range.oldCount))
                .endLineFrom(calcEndLine(range.oldStart, range.oldCount))
                .startLineTo(calcStartLine(range.newStart, range.newCount))
                .endLineTo(calcEndLine(range.newStart, range.newCount))
                .contentFrom(contentFrom)
                .contentTo(contentTo)
                .type(type)
                .build();
    }

    private List<String> cleanDiffLines(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                result.add("");
                continue;
            }
            if (INVALID_HEADER.matcher(trimmed).find()) {
                continue;
            }
            if (trimmed.startsWith("@@")) {
                result.add(trimmed);
                continue;
            }
            if (trimmed.startsWith("\\")) {
                // Skip "No newline at end of file" style notices.
                continue;
            }
            result.add(line);
        }
        return result;
    }

    private String extractContent(List<String> lines, boolean fromSide) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (line == null || line.isEmpty()) {
                continue;
            }
            char prefix = line.charAt(0);
            if (prefix == '@') {
                continue;
            }
            if (prefix == '+') {
                if (!fromSide) {
                    appendLine(builder, line.substring(1));
                }
            } else if (prefix == '-') {
                if (fromSide) {
                    appendLine(builder, line.substring(1));
                }
            } else if (prefix == ' ') {
                appendLine(builder, line.substring(1));
            } else {
                appendLine(builder, line);
            }
        }
        return builder.toString();
    }

    private void appendLine(StringBuilder builder, String line) {
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(line);
    }

    private String trimTrailingBlankLines(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        String[] split = content.split("\\r?\\n");
        int end = split.length - 1;
        while (end >= 0 && split[end].trim().isEmpty()) {
            end--;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i <= end; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(split[i]);
        }
        return builder.toString();
    }

    private DiffType determineBlockType(int oldCount, int newCount) {
        if (oldCount == 0 && newCount > 0) {
            return DiffType.ADD;
        }
        if (newCount == 0 && oldCount > 0) {
            return DiffType.DELETE;
        }
        return DiffType.MODIFY;
    }

    private int calcStartLine(int start, int count) {
        if (count <= 0) {
            return Math.max(0, start);
        }
        return Math.max(1, start);
    }

    private int calcEndLine(int start, int count) {
        if (count <= 0) {
            return Math.max(0, start);
        }
        int base = Math.max(1, start);
        return base + count - 1;
    }

    private List<String> splitFallbackHunks(String fallback) {
        if (isBlank(fallback)) {
            return null;
        }
        String normalized = fallback.replace("\r\n", "\n");
        String[] lines = normalized.split("\n");
        List<String> hunks = new ArrayList<>();
        StringBuilder current = null;
        for (String line : lines) {
            if (line.startsWith("@@")) {
                if (current != null && current.length() > 0) {
                    hunks.add(current.toString());
                }
                current = new StringBuilder();
            }
            if (current != null) {
                if (current.length() > 0) {
                    current.append('\n');
                }
                current.append(line);
            }
        }
        if (current != null && current.length() > 0) {
            hunks.add(current.toString());
        }
        return hunks;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int resolveOldStartLine(HunkHeader header) {
        OldImage oldImage = header.getOldImage();
        if (oldImage == null) {
            return 0;
        }
        return oldImage.getStartLine();
    }

    private int resolveOldLineCount(HunkHeader header) {
        OldImage oldImage = header.getOldImage();
        if (oldImage == null) {
            return 0;
        }
        return oldImage.getLineCount();
    }

    private static class HunkRange {
        final int oldStart;
        final int oldCount;
        final int newStart;
        final int newCount;

        HunkRange(int oldStart, int oldCount, int newStart, int newCount) {
            this.oldStart = oldStart;
            this.oldCount = oldCount;
            this.newStart = newStart;
            this.newCount = newCount;
        }
    }
}




