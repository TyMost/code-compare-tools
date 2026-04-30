package com.example.migratediff.domain.coverage.optimization.utils;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 代码特征提取器
 * 提取代码的结构特征和语义特征，用于快速定位和匹配
 */
@Slf4j
public class FeatureExtractor {
    
    // 方法签名模式
    private static final Pattern METHOD_PATTERN = Pattern.compile(
        "\\s*(public|private|protected)?\\s*(static)?\\s*(\\w+)\\s+(\\w+)\\s*\\([^)]*\\)\\s*(throws\\s+[^{]*)?"
    );
    
    // 类/接口模式
    private static final Pattern CLASS_PATTERN = Pattern.compile(
        "\\s*(public|private)?\\s*(abstract|final)?\\s*(class|interface|enum)\\s+(\\w+)"
    );
    
    // 常量模式（大写字母+下划线）
    private static final Pattern CONSTANT_PATTERN = Pattern.compile(
        "\\b[A-Z][A-Z0-9_]*\\b"
    );
    
    // 变量声明模式
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
        "\\s*(final\\s+)?(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*(?:=|;)"
    );
    
    /**
     * 提取方法签名和位置
     */
    public static Map<String, List<Integer>> extractMethodSignatures(String content) {
        Map<String, List<Integer>> methodPositions = new HashMap<>();
        
        if (content == null || content.trim().isEmpty()) {
            return methodPositions;
        }
        
        String[] lines = content.split("\n");
        java.util.regex.Matcher matcher = METHOD_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String methodName = matcher.group(4); // 方法名
            String fullSignature = matcher.group().trim();
            
            // 计算行号
            int lineNumber = calculateLineNumber(content, matcher.start());
            
            methodPositions.computeIfAbsent(fullSignature, k -> new ArrayList<>()).add(lineNumber);
            methodPositions.computeIfAbsent(methodName, k -> new ArrayList<>()).add(lineNumber);
        }
        
        log.debug("提取到{}个方法签名", methodPositions.size());
        return methodPositions;
    }
    
    /**
     * 提取类/接口签名和位置
     */
    public static Map<String, List<Integer>> extractClassSignatures(String content) {
        Map<String, List<Integer>> classPositions = new HashMap<>();
        
        if (content == null || content.trim().isEmpty()) {
            return classPositions;
        }
        
        java.util.regex.Matcher matcher = CLASS_PATTERN.matcher(content);
        
        while (matcher.find()) {
            String className = matcher.group(4);
            String fullSignature = matcher.group().trim();
            
            int lineNumber = calculateLineNumber(content, matcher.start());
            
            classPositions.computeIfAbsent(fullSignature, k -> new ArrayList<>()).add(lineNumber);
            classPositions.computeIfAbsent(className, k -> new ArrayList<>()).add(lineNumber);
        }
        
        log.debug("提取到{}个类/接口", classPositions.size());
        return classPositions;
    }
    
    /**
     * 提取独特标识符（常量、特殊变量名等）
     */
    public static Set<String> extractUniqueIdentifiers(String content) {
        Set<String> uniqueIdentifiers = new HashSet<>();
        
        if (content == null || content.trim().isEmpty()) {
            return uniqueIdentifiers;
        }
        
        // 提取常量
        java.util.regex.Matcher constantMatcher = CONSTANT_PATTERN.matcher(content);
        while (constantMatcher.find()) {
            String constant = constantMatcher.group();
            // 过滤掉常见的单词
            if (isLikelyConstant(constant)) {
                uniqueIdentifiers.add(constant);
            }
        }
        
        // 提取变量名（过滤常见变量）
        java.util.regex.Matcher variableMatcher = VARIABLE_PATTERN.matcher(content);
        while (variableMatcher.find()) {
            String variableName = variableMatcher.group(3);
            if (isUniqueVariable(variableName)) {
                uniqueIdentifiers.add(variableName);
            }
        }
        
        // 提取长方法名（可能是业务相关的）
        Map<String, List<Integer>> methods = extractMethodSignatures(content);
        for (String methodName : methods.keySet()) {
            if (methodName.length() > 6 && !isCommonMethodName(methodName)) {
                uniqueIdentifiers.add(methodName);
            }
        }
        
        log.debug("提取到{}个独特标识符", uniqueIdentifiers.size());
        return uniqueIdentifiers;
    }
    
    /**
     * 将代码分割成语义块（类、方法等）
     */
    public static List<CodeBlock> segmentSemanticBlocks(String content) {
        List<CodeBlock> blocks = new ArrayList<>();
        
        if (content == null || content.trim().isEmpty()) {
            return blocks;
        }
        
        String[] lines = content.split("\n");
        
        // 按大括号层级识别代码块
        int currentLevel = 0;
        int blockStart = -1;
        String currentSignature = "";
        
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            
            // 检查是否是方法或类签名
            if (isMethodOrClassSignature(line)) {
                if (blockStart != -1 && currentLevel > 0) {
                    // 保存之前的块
                    blocks.add(CodeBlock.builder()
                        .signature(currentSignature)
                        .startLine(blockStart + 1)
                        .endLine(i)
                        .content(String.join("\n", Arrays.copyOfRange(lines, blockStart, i)))
                        .level(currentLevel)
                        .build());
                }
                
                blockStart = i;
                currentSignature = line;
            }
            
            // 跟踪大括号层级
            currentLevel += countBrackets(line, '{') - countBrackets(line, '}');
            
            // 如果层级回到0，保存当前块
            if (currentLevel == 0 && blockStart != -1) {
                blocks.add(CodeBlock.builder()
                    .signature(currentSignature)
                    .startLine(blockStart + 1)
                    .endLine(i + 1)
                    .content(String.join("\n", Arrays.copyOfRange(lines, blockStart, i + 1)))
                    .level(0)
                    .build());
                
                blockStart = -1;
                currentSignature = "";
            }
        }
        
        log.debug("分割出{}个语义块", blocks.size());
        return blocks;
    }
    
    /**
     * 提取查询的关键特征
     */
    public static QueryFeatures extractQueryFeatures(String query) {
        if (query == null || query.trim().isEmpty()) {
            return QueryFeatures.empty();
        }
        
        // 提取Token
        Set<String> tokens = CodeTokenizer.tokenize(query);
        
        // 提取方法名
        Map<String, List<Integer>> methods = extractMethodSignatures(query);
        Set<String> methodNames = new HashSet<>();
        for (String signature : methods.keySet()) {
            if (signature.contains("(")) {
                // 提取方法名
                String methodName = signature.substring(signature.lastIndexOf(' ') + 1, signature.indexOf('('));
                methodNames.add(methodName);
            }
        }
        
        // 提取常量
        Set<String> constants = new HashSet<>();
        java.util.regex.Matcher matcher = CONSTANT_PATTERN.matcher(query);
        while (matcher.find()) {
            String constant = matcher.group();
            if (isLikelyConstant(constant)) {
                constants.add(constant);
            }
        }
        
        return QueryFeatures.builder()
            .tokens(tokens)
            .methodNames(methodNames)
            .constants(constants)
            .queryLength(query.length())
            .build();
    }
    
    /**
     * 计算文本中某个位置的行号
     */
    private static int calculateLineNumber(String content, int position) {
        int lineNumber = 1;
        for (int i = 0; i < position; i++) {
            if (content.charAt(i) == '\n') {
                lineNumber++;
            }
        }
        return lineNumber;
    }
    
    /**
     * 统计括号数量
     */
    private static int countBrackets(String line, char bracket) {
        int count = 0;
        for (char c : line.toCharArray()) {
            if (c == bracket) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 判断是否是方法或类签名
     */
    private static boolean isMethodOrClassSignature(String line) {
        return (METHOD_PATTERN.matcher(line).find() || CLASS_PATTERN.matcher(line).find())
               && line.contains("{");
    }
    
    /**
     * 判断是否可能是常量
     */
    private static boolean isLikelyConstant(String word) {
        // 长度大于2且全部大写，排除一些常见词汇
        Set<String> excludedConstants = new HashSet<>(Arrays.asList(
            "NULL", "TRUE", "FALSE", "MAX", "MIN", "DEFAULT"
        ));
        return word.length() > 2 
               && word.equals(word.toUpperCase())
               && !excludedConstants.contains(word);
    }
    
    /**
     * 判断是否是独特变量
     */
    private static boolean isUniqueVariable(String variableName) {
        // 过滤掉常见的变量名
        Set<String> commonVariables = new HashSet<>(Arrays.asList(
            "i", "j", "k", "index", "count", "size", "length", "result", "temp",
            "str", "s", "obj", "o", "list", "map", "set", "array", "data", "value"
        ));
        
        return variableName.length() > 3 && !commonVariables.contains(variableName.toLowerCase());
    }
    
    /**
     * 判断是否是常见方法名
     */
    private static boolean isCommonMethodName(String methodName) {
        Set<String> commonMethods = new HashSet<>(Arrays.asList(
            "main", "toString", "hashCode", "equals", "clone", "finalize",
            "init", "setup", "teardown", "before", "after", "test",
            "get", "set", "is", "has", "can", "should", "will",
            "do", "run", "execute", "start", "stop", "pause", "resume"
        ));
        
        return commonMethods.contains(methodName.toLowerCase());
    }
    
    /**
     * 代码块数据类
     */
    @Data
    @Builder
    public static class CodeBlock {
        private String signature;      // 块签名
        private int startLine;         // 起始行号
        private int endLine;           // 结束行号
        private String content;         // 块内容
        private int level;             // 嵌套层级
        
        public int getLength() {
            return endLine - startLine + 1;
        }
    }
    
    /**
     * 查询特征数据类
     */
    @Data
    @Builder
    public static class QueryFeatures {
        private Set<String> tokens;        // Token集合
        private Set<String> methodNames;    // 方法名集合
        private Set<String> constants;      // 常量集合
        private int queryLength;            // 查询长度
        
        public boolean isEmpty() {
            return (tokens == null || tokens.isEmpty()) 
                   && (methodNames == null || methodNames.isEmpty())
                   && (constants == null || constants.isEmpty());
        }
        
        public static QueryFeatures empty() {
            return QueryFeatures.builder()
                .tokens(Collections.emptySet())
                .methodNames(Collections.emptySet())
                .constants(Collections.emptySet())
                .queryLength(0)
                .build();
        }
    }
}
