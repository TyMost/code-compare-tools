package com.example.codecompare.rebuild.agent.migration;

import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility methods for normalising line endings and working with multi-line text.
 */
public final class LineEndingNormalizer {

    private LineEndingNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String separator = System.lineSeparator();
        if (!"\n".equals(separator)) {
            normalized = normalized.replace("\n", separator);
        }
        return normalized;
    }

    public static List<String> splitLines(String content) {
        if (!StringUtils.hasText(content)) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<String>();
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("无法拆分迁移代码片段", ex);
        }
        return lines;
    }

    public static String joinLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return System.lineSeparator();
        }
        String joined = String.join(System.lineSeparator(), lines);
        if (!joined.endsWith(System.lineSeparator())) {
            joined = joined + System.lineSeparator();
        }
        return joined;
    }
}
