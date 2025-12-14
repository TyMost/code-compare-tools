package com.example.migratediff.domain.coverage.optimization.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 代码分词器
 * 将代码文本转换为Token序列，支持相似度计算
 */
@Slf4j
public class CodeTokenizer {
    
    // Java关键字
    private static final Set<String> JAVA_KEYWORDS = new HashSet<>(Arrays.asList(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long",
        "native", "new", "package", "private", "protected", "public", "return", "short", "static",
        "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
        "void", "volatile", "while"
    ));
    
    // 标识符模式：字母开头，包含字母、数字、下划线
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");
    
    // 操作符模式
    private static final Pattern OPERATOR_PATTERN = Pattern.compile("[+\\-*/%=<>!&|^~?:]");
    
    // 分隔符模式
    private static final Pattern DELIMITER_PATTERN = Pattern.compile("[(){}\\[\\],.;]");
    
    // 数字字面量模式
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(\\.\\d+)?[lLdDfF]?\\b");
    
    // 字符串字面量模式
    private static final Pattern STRING_PATTERN = Pattern.compile("\"([^\"\\\\]|\\\\.)*\"");
    
    /**
     * 将代码文本转换为Token集合
     */
    public static Set<String> tokenize(String code) {
        if (code == null || code.trim().isEmpty()) {
            return Collections.emptySet();
        }
        
        Set<String> tokens = new HashSet<>();
        
        // 移除注释
        String cleanCode = removeComments(code);
        
        // 提取各种Token
        tokens.addAll(extractIdentifiers(cleanCode));
        tokens.addAll(extractKeywords(cleanCode));
        tokens.addAll(extractOperators(cleanCode));
        tokens.addAll(extractNumbers(cleanCode));
        tokens.addAll(extractStrings(cleanCode));
        
        return tokens;
    }
    
    /**
     * 将代码文本转换为有序Token列表（保留顺序）
     */
    public static List<String> tokenizeToList(String code) {
        if (code == null || code.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        List<String> tokens = new ArrayList<>();
        String cleanCode = removeComments(code);
        
        // 按照代码中出现的顺序提取Token
        extractTokensInOrder(cleanCode, tokens);
        
        return tokens;
    }
    
    /**
     * 按照代码中出现的顺序提取Token
     */
    private static void extractTokensInOrder(String code, List<String> tokens) {
        int pos = 0;
        int length = code.length();
        
        while (pos < length) {
            char c = code.charAt(pos);
            
            // 跳过空白字符
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }
            
            // 检查字符串字面量
            if (c == '"') {
                int end = findStringEnd(code, pos);
                if (end > pos) {
                    tokens.add(code.substring(pos, end + 1));
                    pos = end + 1;
                    continue;
                }
            }
            
            // 检查数字
            if (Character.isDigit(c)) {
                int end = findNumberEnd(code, pos);
                if (end > pos) {
                    tokens.add(code.substring(pos, end + 1));
                    pos = end + 1;
                    continue;
                }
            }
            
            // 检查标识符和关键字
            if (Character.isJavaIdentifierStart(c)) {
                int end = findIdentifierEnd(code, pos);
                String identifier = code.substring(pos, end + 1);
                tokens.add(identifier);
                pos = end + 1;
                continue;
            }
            
            // 检查操作符和分隔符
            if (isOperatorOrDelimiter(c)) {
                tokens.add(String.valueOf(c));
                pos++;
                continue;
            }
            
            // 其他字符
            pos++;
        }
    }
    
    /**
     * 查找字符串字面量的结束位置
     */
    private static int findStringEnd(String code, int start) {
        int pos = start + 1;
        while (pos < code.length()) {
            char c = code.charAt(pos);
            if (c == '\\') {
                pos += 2; // 跳过转义字符
            } else if (c == '"') {
                return pos;
            } else {
                pos++;
            }
        }
        return start; // 如果没找到结束引号，返回开始位置
    }
    
    /**
     * 查找数字的结束位置
     */
    private static int findNumberEnd(String code, int start) {
        int pos = start;
        while (pos < code.length()) {
            char c = code.charAt(pos);
            if (Character.isDigit(c) || c == '.' || c == 'L' || c == 'l' || c == 'D' || c == 'd' || c == 'F' || c == 'f') {
                pos++;
            } else {
                break;
            }
        }
        return pos - 1;
    }
    
    /**
     * 查找标识符的结束位置
     */
    private static int findIdentifierEnd(String code, int start) {
        int pos = start;
        while (pos < code.length() && Character.isJavaIdentifierPart(code.charAt(pos))) {
            pos++;
        }
        return pos - 1;
    }
    
    /**
     * 检查字符是否为操作符或分隔符
     */
    private static boolean isOperatorOrDelimiter(char c) {
        return "+-*/%=<>!&|^~?:(){}[],.;".indexOf(c) >= 0;
    }
    
    /**
     * 计算Token级别的Jaccard相似度
     */
    public static double jaccardSimilarity(String code1, String code2) {
        Set<String> tokens1 = tokenize(code1);
        Set<String> tokens2 = tokenize(code2);
        
        if (tokens1.isEmpty() && tokens2.isEmpty()) {
            return 1.0;
        }
        
        Set<String> intersection = new HashSet<>(tokens1);
        intersection.retainAll(tokens2);
        
        Set<String> union = new HashSet<>(tokens1);
        union.addAll(tokens2);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
    
    /**
     * 移除注释
     */
    private static String removeComments(String code) {
        // 移除单行注释
        code = code.replaceAll("//.*", "");
        // 移除多行注释（使用DOTALL模式匹配跨行注释）
        code = code.replaceAll("(?s)/\\*.*?\\*/", "");
        return code;
    }
    
    /**
     * 提取标识符
     */
    private static Set<String> extractIdentifiers(String code) {
        Set<String> identifiers = new HashSet<>();
        java.util.regex.Matcher matcher = IDENTIFIER_PATTERN.matcher(code);
        
        while (matcher.find()) {
            String identifier = matcher.group();
            // 过滤掉Java关键字
            if (!JAVA_KEYWORDS.contains(identifier)) {
                identifiers.add(identifier);
            }
        }
        
        return identifiers;
    }
    
    /**
     * 提取标识符（保留顺序）
     */
    private static List<String> extractIdentifiersList(String code) {
        List<String> identifiers = new ArrayList<>();
        java.util.regex.Matcher matcher = IDENTIFIER_PATTERN.matcher(code);
        
        while (matcher.find()) {
            String identifier = matcher.group();
            if (!JAVA_KEYWORDS.contains(identifier)) {
                identifiers.add(identifier);
            }
        }
        
        return identifiers;
    }
    
    /**
     * 提取关键字
     */
    private static Set<String> extractKeywords(String code) {
        Set<String> keywords = new HashSet<>();
        String[] words = code.split("\\W+");
        
        for (String word : words) {
            if (JAVA_KEYWORDS.contains(word)) {
                keywords.add(word);
            }
        }
        
        return keywords;
    }
    
    /**
     * 提取关键字（保留顺序）
     */
    private static List<String> extractKeywordsList(String code) {
        List<String> keywords = new ArrayList<>();
        String[] words = code.split("\\W+");
        
        for (String word : words) {
            if (JAVA_KEYWORDS.contains(word)) {
                keywords.add(word);
            }
        }
        
        return keywords;
    }
    
    /**
     * 提取操作符
     */
    private static Set<String> extractOperators(String code) {
        Set<String> operators = new HashSet<>();
        java.util.regex.Matcher matcher = OPERATOR_PATTERN.matcher(code);
        
        while (matcher.find()) {
            operators.add(matcher.group());
        }
        
        return operators;
    }
    
    /**
     * 提取操作符（保留顺序）
     */
    private static List<String> extractOperatorsList(String code) {
        List<String> operators = new ArrayList<>();
        java.util.regex.Matcher matcher = OPERATOR_PATTERN.matcher(code);
        
        while (matcher.find()) {
            operators.add(matcher.group());
        }
        
        return operators;
    }
    
    /**
     * 提取数字字面量
     */
    private static Set<String> extractNumbers(String code) {
        Set<String> numbers = new HashSet<>();
        java.util.regex.Matcher matcher = NUMBER_PATTERN.matcher(code);
        
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        
        return numbers;
    }
    
    /**
     * 提取数字字面量（保留顺序）
     */
    private static List<String> extractNumbersList(String code) {
        List<String> numbers = new ArrayList<>();
        java.util.regex.Matcher matcher = NUMBER_PATTERN.matcher(code);
        
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        
        return numbers;
    }
    
    /**
     * 提取字符串字面量
     */
    private static Set<String> extractStrings(String code) {
        Set<String> strings = new HashSet<>();
        java.util.regex.Matcher matcher = STRING_PATTERN.matcher(code);
        
        while (matcher.find()) {
            strings.add(matcher.group());
        }
        
        return strings;
    }
    
    /**
     * 计算两个Token序列的编辑距离相似度
     */
    public static double editDistanceSimilarity(List<String> tokens1, List<String> tokens2) {
        int editDistance = calculateEditDistance(tokens1, tokens2);
        int maxLength = Math.max(tokens1.size(), tokens2.size());
        
        return maxLength == 0 ? 1.0 : 1.0 - (double) editDistance / maxLength;
    }
    
    /**
     * 计算编辑距离
     */
    private static int calculateEditDistance(List<String> tokens1, List<String> tokens2) {
        int m = tokens1.size();
        int n = tokens2.size();
        
        int[][] dp = new int[m + 1][n + 1];
        
        // 初始化
        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }
        
        // 填充DP表
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (tokens1.get(i - 1).equals(tokens2.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j], dp[i][j - 1]) + 1,
                        dp[i - 1][j - 1] + 1
                    );
                }
            }
        }
        
        return dp[m][n];
    }
}
