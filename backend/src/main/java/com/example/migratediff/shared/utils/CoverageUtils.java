package com.example.migratediff.shared.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CoverageUtils {

    private CoverageUtils() {
    }

    public static double jaccardSimilarity(List<String> sourceTokens, List<String> targetTokens) {
        if (sourceTokens == null || targetTokens == null) {
            return 0D;
        }
        Set<String> sourceSet = new HashSet<>(sourceTokens);
        Set<String> targetSet = new HashSet<>(targetTokens);
        if (sourceSet.isEmpty() && targetSet.isEmpty()) {
            return 1D;
        }
        Set<String> intersection = new HashSet<>(sourceSet);
        intersection.retainAll(targetSet);
        Set<String> union = new HashSet<>(sourceSet);
        union.addAll(targetSet);
        return union.isEmpty() ? 1D : (double) intersection.size() / union.size();
    }

    /**
     * Recall-style similarity: how much of Delta-O's tokens are covered by Delta-G (intersection / source).
     */
    public static double recallSimilarity(List<String> sourceTokens, List<String> targetTokens) {
        if (sourceTokens == null || sourceTokens.isEmpty()) {
            return 1D;
        }
        if (targetTokens == null || targetTokens.isEmpty()) {
            return 0D;
        }
        int matched = 0;
        int total = sourceTokens.size();

        Map<String, Integer> targetFreq = new HashMap<>();
        for (String token : targetTokens) {
            if (isBlank(token)) {
                continue;
            }
            targetFreq.merge(token, 1, Integer::sum);
        }
        for (String token : sourceTokens) {
            if (isBlank(token)) {
                continue;
            }
            Integer count = targetFreq.get(token);
            if (count != null && count > 0) {
                matched++;
                targetFreq.put(token, count - 1);
            }
        }
        return total == 0 ? 1D : (double) matched / total;
    }

    public static int levenshteinDistance(String source, String target) {
        String normalizedSource = source == null ? "" : source;
        String normalizedTarget = target == null ? "" : target;

        int sourceLength = normalizedSource.length();
        int targetLength = normalizedTarget.length();

        int[][] dp = new int[sourceLength + 1][targetLength + 1];

        for (int i = 0; i <= sourceLength; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= targetLength; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= sourceLength; i++) {
            for (int j = 1; j <= targetLength; j++) {
                int cost = normalizedSource.charAt(i - 1) == normalizedTarget.charAt(j - 1) ? 0 : 1;
                int deletion = dp[i - 1][j] + 1;
                int insertion = dp[i][j - 1] + 1;
                int substitution = dp[i - 1][j - 1] + cost;
                dp[i][j] = Math.min(Math.min(deletion, insertion), substitution);
            }
        }

        return dp[sourceLength][targetLength];
    }

    public static List<String> tokenize(String content) {
        return tokenize(content, false); // 默认不启用过滤
    }

    /**
     * Tokenize content with optional noise filtering.
     * 
     * @param content the content to tokenize
     * @param filterNoise whether to filter out import statements and comments
     * @return list of tokens
     */
    public static List<String> tokenize(String content, boolean filterNoise) {
        if (isBlank(content)) {
            return new ArrayList<>();
        }
        
        if (filterNoise) {
            content = filterCodeNoise(content);
        }
        
        String[] tokens = content.trim().split("\\s+");
        List<String> result = new ArrayList<>(tokens.length);
        for (String token : tokens) {
            if (!isBlank(token)) {
                result.add(token);
            }
        }
        return result;
    }

    /**
     * Filter out import statements and comments from code content.
     * This method safely removes common noise that affects coverage calculation.
     * 
     * @param content the code content
     * @return filtered content
     */
    public static String filterCodeNoise(String content) {
        String[] lines = content.split("\\r?\\n");
        StringBuilder filtered = new StringBuilder();
        boolean inMultiLineComment = false;
        
        for (String line : lines) {
            String trimmed = line.trim();
            
            // Handle multi-line comments
            if (trimmed.startsWith("/*")) {
                inMultiLineComment = true;
            }
            if (inMultiLineComment) {
                if (trimmed.endsWith("*/")) {
                    inMultiLineComment = false;
                }
                continue;
            }
            
            // Skip single-line comments, imports, and javadoc lines
            if (trimmed.startsWith("//") || 
                trimmed.startsWith("import ") || 
                trimmed.startsWith("package ") || 
                trimmed.startsWith("*") ||
                trimmed.startsWith("/**")) {
                continue;
            }
            
            filtered.append(line).append("\n");
        }
        
        return filtered.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 业务特征Tokenization - 仅保留业务强特征
     * 排除：标点、空格、Java关键字、SQL关键字、短词(len<4)
     * 保留：字面量(String/Number)、驼峰命名、下划线命名、方法调用
     * 
     * @param content 代码内容
     * @return 业务特征token集合
     */
    public static java.util.Set<String> tokenizeBusinessFeatures(String content) {
        if (isBlank(content)) {
            return new java.util.HashSet<>();
        }

        java.util.Set<String> tokens = new java.util.HashSet<>();
        
        // Java关键字集合（需要排除）
        java.util.Set<String> javaKeywords = new java.util.HashSet<>(java.util.Arrays.asList(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", 
            "class", "const", "continue", "default", "do", "double", "else", "enum", 
            "extends", "final", "finally", "float", "for", "goto", "if", "implements", 
            "import", "instanceof", "int", "interface", "long", "native", "new", "package", 
            "private", "protected", "public", "return", "short", "static", "strictfp", 
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", 
            "try", "void", "volatile", "while"
        ));
        
        // SQL关键字集合（需要排除）
        java.util.Set<String> sqlKeywords = new java.util.HashSet<>(java.util.Arrays.asList(
            "select", "from", "where", "insert", "update", "delete", "create", "drop", 
            "alter", "table", "index", "join", "left", "right", "inner", "outer", 
            "group", "by", "order", "having", "union", "distinct", "count", "sum", 
            "avg", "max", "min", "as", "and", "or", "not", "in", "exists", 
            "between", "like", "is", "null", "desc", "asc", "limit", "offset"
        ));
        
        // 按行处理
        String[] lines = content.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            
            // 1. 提取字符串字面量
            extractStringLiterals(trimmed, tokens);
            
            // 2. 提取数字字面量
            extractNumberLiterals(trimmed, tokens);
            
            // 3. 提取标识符（驼峰命名、下划线命名、方法调用）
            extractIdentifiers(trimmed, tokens, javaKeywords, sqlKeywords);
        }
        
        return tokens;
    }
    
    /**
     * 提取字符串字面量
     */
    private static void extractStringLiterals(String line, java.util.Set<String> tokens) {
        // 匹配双引号和单引号字符串
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"([^\"]*)\"|'([^']*)'");
        java.util.regex.Matcher matcher = pattern.matcher(line);
        
        while (matcher.find()) {
            String literal = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (literal != null && literal.length() >= 3) {
                tokens.add(literal);
            }
        }
    }
    
    /**
     * 提取数字字面量
     */
    private static void extractNumberLiterals(String line, java.util.Set<String> tokens) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b\\d+(\\.\\d+)?\\b");
        java.util.regex.Matcher matcher = pattern.matcher(line);
        
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
    }
    
    /**
     * 提取标识符（驼峰命名、下划线命名、方法调用）
     */
    private static void extractIdentifiers(String line, java.util.Set<String> tokens, 
                                   java.util.Set<String> javaKeywords, java.util.Set<String> sqlKeywords) {
        // 移除字符串字面量，避免干扰
        String cleanLine = line.replaceAll("\"[^\"]*\"", " ")
                              .replaceAll("'[^']*'", " ");
        
        // 匹配标识符：字母开头，包含字母、数字、下划线、$
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b[a-zA-Z_][a-zA-Z0-9_$.]*\\b");
        java.util.regex.Matcher matcher = pattern.matcher(cleanLine);
        
        while (matcher.find()) {
            String identifier = matcher.group();
            
            // 过滤条件
            if (identifier.length() < 4) {  // 短词过滤
                continue;
            }
            
            if (javaKeywords.contains(identifier.toLowerCase())) {  // Java关键字过滤
                continue;
            }
            
            if (sqlKeywords.contains(identifier.toLowerCase())) {  // SQL关键字过滤
                continue;
            }
            
            // 检查是否为业务特征（驼峰、下划线、方法调用）
            if (isBusinessIdentifier(identifier)) {
                tokens.add(identifier);
            }
        }
    }
    
    /**
     * 判断是否为业务标识符
     * 驼峰命名、下划线命名、方法调用模式
     */
    private static boolean isBusinessIdentifier(String identifier) {
        // 1. 下划线命名：user_name, order_id
        if (identifier.contains("_")) {
            return true;
        }
        
        // 2. 驼峰命名：userName, orderId
        if (hasCamelCase(identifier)) {
            return true;
        }
        
        // 3. 方法调用模式：getName(), setId()
        if (identifier.endsWith("()") || identifier.matches(".+\\(.*\\)")) {
            return identifier.length() >= 6;  // 方法名长度过滤
        }
        
        // 4. 常量模式：MAX_SIZE, DEFAULT_VALUE
        if (identifier.equals(identifier.toUpperCase()) && identifier.length() >= 5) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查是否为驼峰命名
     */
    private static boolean hasCamelCase(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        
        boolean hasUpper = false;
        boolean hasLower = false;
        
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            }
        }
        
        return hasUpper && hasLower;
    }
}
