package com.example.codecompare.rebuild.core.support;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一的忽略规则匹配器，支持兼容性更强的路径匹配逻辑。
 */
public final class IgnorePatternMatcher {

    private final AntPathMatcher pathMatcher;
    private final List<String> wildcardPatterns;
    private final Set<String> literalTokens;

    private IgnorePatternMatcher(List<String> wildcardPatterns, Set<String> literalTokens) {
        this.pathMatcher = new AntPathMatcher("/");
        this.pathMatcher.setTrimTokens(true);
        this.wildcardPatterns = wildcardPatterns;
        this.literalTokens = literalTokens;
    }

    public static IgnorePatternMatcher from(Collection<String> rawPatterns) {
        if (CollectionUtils.isEmpty(rawPatterns)) {
            return new IgnorePatternMatcher(Collections.<String>emptyList(), Collections.<String>emptySet());
        }
        LinkedHashSet<String> wildcard = new LinkedHashSet<String>();
        LinkedHashSet<String> literals = new LinkedHashSet<String>();
        for (String raw : rawPatterns) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String normalized = normalizePattern(raw);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            if (containsWildcard(normalized)) {
                addWildcardPatterns(normalized, wildcard);
            } else {
                literals.add(normalized);
            }
        }
        return new IgnorePatternMatcher(new ArrayList<String>(wildcard), literals);
    }

    public boolean isEmpty() {
        return wildcardPatterns.isEmpty() && literalTokens.isEmpty();
    }

    public boolean matches(Path root, Path path) {
        if (path == null) {
            return false;
        }
        if (root != null) {
            Path relative = safeRelativize(root, path);
            if (relative != null && matches(relative.toString())) {
                return true;
            }
        }
        return matches(path.toString());
    }

    public boolean matches(Path path) {
        return path != null && matches(path.toString());
    }

    public boolean matches(String path) {
        if (!StringUtils.hasText(path)) {
            return false;
        }
        String normalizedPath = normalizePath(path);
        if (!StringUtils.hasText(normalizedPath)) {
            return false;
        }
        String fileName = extractFileName(normalizedPath);
        if (matchLiteral(normalizedPath, fileName)) {
            return true;
        }
        for (String pattern : wildcardPatterns) {
            if (pathMatcher.match(pattern, normalizedPath)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizePattern(String pattern) {
        String normalized = normalizePath(pattern);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        if (normalized.endsWith("/") && !normalized.endsWith("/*") && !normalized.endsWith("/**")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizePath(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean containsWildcard(String value) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '*' || ch == '?' || ch == '[') {
                return true;
            }
        }
        return false;
    }

    private static void addWildcardPatterns(String pattern, Set<String> accumulator) {
        accumulator.add(pattern);
        if (!pattern.startsWith("**/")) {
            accumulator.add("**/" + pattern);
        }
        if (pattern.contains("/") && !pattern.endsWith("/**") && !pattern.endsWith("**")) {
            accumulator.add(pattern + "/**");
            if (!pattern.startsWith("**/")) {
                accumulator.add("**/" + pattern + "/**");
            }
        }
    }

    private boolean matchLiteral(String normalizedPath, String fileName) {
        if (literalTokens.isEmpty()) {
            return false;
        }
        for (String literal : literalTokens) {
            if (!StringUtils.hasText(literal)) {
                continue;
            }
            if (literal.equals(normalizedPath) || literal.equals(fileName)) {
                return true;
            }
            if (normalizedPath.startsWith(literal + "/")) {
                return true;
            }
            if (normalizedPath.endsWith("/" + literal)) {
                return true;
            }
            if (normalizedPath.contains("/" + literal + "/")) {
                return true;
            }
        }
        return false;
    }

    private static String extractFileName(String normalizedPath) {
        int index = normalizedPath.lastIndexOf('/');
        if (index < 0 || index == normalizedPath.length() - 1) {
            return normalizedPath;
        }
        return normalizedPath.substring(index + 1);
    }

    private Path safeRelativize(Path root, Path path) {
        try {
            return root.relativize(path);
        } catch (IllegalArgumentException ex) {
            return path;
        }
    }
}
