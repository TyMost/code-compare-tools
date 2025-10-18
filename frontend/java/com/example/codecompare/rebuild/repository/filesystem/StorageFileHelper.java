package com.example.codecompare.rebuild.repository.filesystem;

import com.example.codecompare.rebuild.repository.config.StorageProperties;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Utility methods for resolving storage paths and filenames.
 */
public final class StorageFileHelper {

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
        Path comparisonDir = ensureDirectory(baseDir.resolve(safeSegment(comparisonId)));
        String filename = encodeFileName(filePath) + ".json";
        return comparisonDir.resolve(filename);
    }

    static Path resolveScanSummaryFile(StorageProperties properties, String projectCode) {
        String safeProject = safeSegment(projectCode);
        return ensureDirectory(properties.resolveScanSummaries()).resolve(safeProject + ".json");
    }

    private static String safeSegment(String raw) {
        if (raw == null) {
            return "default";
        }
        return raw.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
