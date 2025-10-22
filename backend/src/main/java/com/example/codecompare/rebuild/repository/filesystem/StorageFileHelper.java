package com.example.codecompare.rebuild.repository.filesystem;

import com.example.codecompare.rebuild.repository.config.StorageProperties;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Utility methods for resolving storage paths and filenames.
 */
public final class StorageFileHelper {

    private static final DateTimeFormatter DIRECTORY_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern TIMESTAMP_SUFFIX_PATTERN = Pattern.compile("\\d{14}");

    private StorageFileHelper() {
    }

    public static Path ensureDirectory(Path directory) {
        try {
            java.nio.file.Files.createDirectories(directory);
            return directory;
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to create storage directory: " + directory, ex);
        }
    }

    static String encodeFileName(String input) {
        Assert.hasText(input, "input must not be empty");
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(input.replace('\\', '/').getBytes(StandardCharsets.UTF_8));
    }

    public static String decodeFileName(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }
        String base = filename.endsWith(".json") ? filename.substring(0, filename.length() - 5) : filename;
        byte[] decoded = Base64.getUrlDecoder().decode(base);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    static Path resolveProjectFile(StorageProperties properties, String projectCode) {
        String safeProject = projectCode.replaceAll("[^a-zA-Z0-9._-]", "_");
        return ensureDirectory(properties.resolveFileMetadata()).resolve(safeProject + ".json");
    }

    static Path resolveComparisonFile(Path baseDir, String comparisonId, String filePath) {
        Path comparisonDir = resolveComparisonDirectory(baseDir, comparisonId);
        String filename = encodeFileName(filePath) + ".json";
        return comparisonDir.resolve(filename);
    }

    static Path resolveScanSummaryFile(StorageProperties properties, String projectCode) {
        String safeProject = safeSegment(projectCode);
        return ensureDirectory(properties.resolveScanSummaries()).resolve(safeProject + ".json");
    }

    public static Path resolveComparisonDirectory(Path baseDir, String comparisonId) {
        ensureDirectory(baseDir);
        String safeName = safeSegment(comparisonId);
        Path existing = findLatestComparisonDirectory(baseDir, safeName);
        if (existing != null) {
            return existing;
        }
        String timestamp = DIRECTORY_TIMESTAMP_FORMAT.format(LocalDateTime.now(ZoneId.systemDefault()));
        Path target = baseDir.resolve(safeName + "-" + timestamp);
        return ensureDirectory(target);
    }

    public static List<Path> findComparisonDirectories(Path baseDir, String comparisonId) {
        if (baseDir == null || comparisonId == null) {
            return Collections.emptyList();
        }
        String safeName = safeSegment(comparisonId);
        return findComparisonDirectoriesBySafeName(baseDir, safeName);
    }

    public static String normalizeComparisonDirectoryName(String directoryName) {
        if (directoryName == null || directoryName.isEmpty()) {
            return directoryName;
        }
        int dashIndex = directoryName.lastIndexOf('-');
        if (dashIndex <= 0 || dashIndex + 1 >= directoryName.length()) {
            return directoryName;
        }
        String suffix = directoryName.substring(dashIndex + 1);
        if (TIMESTAMP_SUFFIX_PATTERN.matcher(suffix).matches()) {
            return directoryName.substring(0, dashIndex);
        }
        return directoryName;
    }

    private static String safeSegment(String raw) {
        if (raw == null) {
            return "default";
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static Path findLatestComparisonDirectory(Path baseDir, String safeName) {
        List<Path> matches = findComparisonDirectoriesBySafeName(baseDir, safeName);
        if (matches.isEmpty()) {
            return null;
        }
        Path latest = null;
        long latestValue = Long.MIN_VALUE;
        for (Path candidate : matches) {
            String name = candidate.getFileName() == null ? "" : candidate.getFileName().toString();
            long value = extractTimestampValue(name, safeName);
            if (latest == null || value > latestValue) {
                latest = candidate;
                latestValue = value;
            }
        }
        return latest;
    }

    private static List<Path> findComparisonDirectoriesBySafeName(Path baseDir, String safeName) {
        if (baseDir == null || safeName == null) {
            return Collections.emptyList();
        }
        if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
            return Collections.emptyList();
        }
        List<Path> matches = new ArrayList<Path>();
        try (Stream<Path> stream = Files.list(baseDir)) {
            stream.filter(Files::isDirectory)
                    .filter(path -> matchesSafeName(path, safeName))
                    .forEach(matches::add);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to list comparison directories under: " + baseDir, ex);
        }
        matches.sort(Comparator.comparingLong(path -> extractTimestampValue(
                path.getFileName() == null ? "" : path.getFileName().toString(), safeName)));
        return matches;
    }

    private static boolean matchesSafeName(Path path, String safeName) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString();
        if (safeName.equals(name)) {
            return true;
        }
        if (!name.startsWith(safeName)) {
            return false;
        }
        if (name.length() <= safeName.length() || name.charAt(safeName.length()) != '-') {
            return false;
        }
        String suffix = name.substring(safeName.length() + 1);
        return TIMESTAMP_SUFFIX_PATTERN.matcher(suffix).matches();
    }

    private static long extractTimestampValue(String directoryName, String safeName) {
        if (directoryName == null) {
            return Long.MIN_VALUE;
        }
        if (safeName.equals(directoryName)) {
            return Long.MIN_VALUE;
        }
        if (!directoryName.startsWith(safeName)) {
            return Long.MIN_VALUE;
        }
        if (directoryName.length() <= safeName.length() || directoryName.charAt(safeName.length()) != '-') {
            return Long.MIN_VALUE;
        }
        String suffix = directoryName.substring(safeName.length() + 1);
        if (!TIMESTAMP_SUFFIX_PATTERN.matcher(suffix).matches()) {
            return Long.MIN_VALUE;
        }
        try {
            return Long.parseLong(suffix);
        } catch (NumberFormatException ex) {
            return Long.MIN_VALUE;
        }
    }
}
