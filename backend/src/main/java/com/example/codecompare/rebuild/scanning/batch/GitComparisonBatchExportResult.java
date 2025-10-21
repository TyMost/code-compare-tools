package com.example.codecompare.rebuild.scanning.batch;

import java.util.Arrays;
import java.util.Objects;

/**
 * Value object that represents a generated Excel export for batch Git comparisons.
 */
public final class GitComparisonBatchExportResult {

    private final byte[] content;
    private final String filename;

    public GitComparisonBatchExportResult(byte[] content, String filename) {
        this.content = Objects.requireNonNull(content, "content must not be null");
        this.filename = Objects.requireNonNull(filename, "filename must not be null");
    }

    public byte[] getContent() {
        return content;
    }

    public String getFilename() {
        return filename;
    }

    @Override
    public String toString() {
        return "GitComparisonBatchExportResult{" +
                "content=" + content.length + " bytes" +
                ", filename='" + filename + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GitComparisonBatchExportResult)) {
            return false;
        }
        GitComparisonBatchExportResult that = (GitComparisonBatchExportResult) o;
        return Arrays.equals(content, that.content) && filename.equals(that.filename);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(content);
        result = 31 * result + filename.hashCode();
        return result;
    }
}

