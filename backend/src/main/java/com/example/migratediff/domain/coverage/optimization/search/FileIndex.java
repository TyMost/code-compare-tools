package com.example.migratediff.domain.coverage.optimization.search;

import com.example.migratediff.domain.coverage.optimization.utils.BloomFilterUtil;
import com.example.migratediff.domain.coverage.optimization.utils.FeatureExtractor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.util.*;

/**
 * 文件索引
 * 存储文件的结构特征和语义特征，用于快速搜索
 */
@Slf4j
@Data
@Builder
public class FileIndex {
    
    private String fileHash;                    // 文件哈希值
    private Map<String, List<Integer>> methodSignatures;  // 方法签名 -> 位置列表
    private Map<String, List<Integer>> classSignatures;    // 类签名 -> 位置列表
    private Set<String> uniqueIdentifiers;         // 独特标识符
    private List<FeatureExtractor.CodeBlock> semanticBlocks;  // 语义块
    private BloomFilterUtil tokenBloomFilter;      // Token布隆过滤器
    private List<String> allTokens;               // 所有Token列表
    private int totalLines;                       // 总行数
    private long indexTime;                        // 索引构建时间
    private int blockSize;                        // 语义块数量
    
    /**
     * 构建文件索引
     */
    public static FileIndex build(String content) {
        long startTime = System.currentTimeMillis();
        
        log.debug("开始构建文件索引，内容长度: {}", content.length());
        
        // 计算文件哈希
        String fileHash = calculateHash(content);
        
        // 提取各种特征
        Map<String, List<Integer>> methodSignatures = FeatureExtractor.extractMethodSignatures(content);
        Map<String, List<Integer>> classSignatures = FeatureExtractor.extractClassSignatures(content);
        Set<String> uniqueIdentifiers = FeatureExtractor.extractUniqueIdentifiers(content);
        List<FeatureExtractor.CodeBlock> semanticBlocks = FeatureExtractor.segmentSemanticBlocks(content);
        
        // 构建Token布隆过滤器
        List<String> allTokens = new ArrayList<>();
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            Set<String> lineTokens = com.example.migratediff.domain.coverage.optimization.utils.CodeTokenizer.tokenize(line);
            allTokens.addAll(lineTokens);
        }
        
        BloomFilterUtil tokenBloomFilter = BloomFilterUtil.createWithElements(allTokens, 0.01);
        
        long indexTime = System.currentTimeMillis() - startTime;
        
        FileIndex index = FileIndex.builder()
            .fileHash(fileHash)
            .methodSignatures(methodSignatures)
            .classSignatures(classSignatures)
            .uniqueIdentifiers(uniqueIdentifiers)
            .semanticBlocks(semanticBlocks)
            .tokenBloomFilter(tokenBloomFilter)
            .allTokens(allTokens)
            .totalLines(lines.length)
            .indexTime(indexTime)
            .blockSize(semanticBlocks.size())
            .build();
        
        log.info("文件索引构建完成: 文件哈希={}, 方法数={}, 类数={}, 标识符数={}, 块数={}, 耗时{}ms",
                fileHash.substring(0, 8), methodSignatures.size(), classSignatures.size(),
                uniqueIdentifiers.size(), semanticBlocks.size(), indexTime);
        
        return index;
    }
    
    /**
     * 检查是否可能包含指定的Token
     */
    public boolean mightContainToken(String token) {
        return tokenBloomFilter != null && tokenBloomFilter.mightContain(token);
    }
    
    /**
     * 检查是否可能包含所有指定的Token
     */
    public boolean mightContainAllTokens(Collection<String> tokens) {
        if (tokenBloomFilter == null || tokens == null || tokens.isEmpty()) {
            return true;
        }
        
        for (String token : tokens) {
            if (!tokenBloomFilter.mightContain(token)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 获取包含方法名的位置列表
     */
    public List<Integer> getMethodPositions(String methodName) {
        return methodSignatures.getOrDefault(methodName, Collections.emptyList());
    }
    
    /**
     * 获取包含类名的位置列表
     */
    public List<Integer> getClassPositions(String className) {
        return classSignatures.getOrDefault(className, Collections.emptyList());
    }
    
    /**
     * 根据行号获取语义块
     */
    public FeatureExtractor.CodeBlock getBlockByLineNumber(int lineNumber) {
        for (FeatureExtractor.CodeBlock block : semanticBlocks) {
            if (lineNumber >= block.getStartLine() && lineNumber <= block.getEndLine()) {
                return block;
            }
        }
        return null;
    }
    
    /**
     * 获取指定行范围的内容
     */
    public String getContentByLineRange(String fullContent, int startLine, int endLine) {
        if (fullContent == null || startLine < 1 || endLine > totalLines || startLine > endLine) {
            return "";
        }
        
        String[] lines = fullContent.split("\n");
        StringBuilder content = new StringBuilder();
        
        for (int i = startLine - 1; i < Math.min(endLine, lines.length); i++) {
            if (content.length() > 0) {
                content.append("\n");
            }
            content.append(lines[i]);
        }
        
        return content.toString();
    }
    
    /**
     * 根据方法位置获取扩展的上下文
     */
    public String getMethodContext(String fullContent, String methodName, int contextLines) {
        List<Integer> positions = getMethodPositions(methodName);
        if (positions.isEmpty()) {
            return "";
        }
        
        int methodLine = positions.get(0);
        int startLine = Math.max(1, methodLine - contextLines);
        int endLine = Math.min(totalLines, methodLine + contextLines);
        
        return getContentByLineRange(fullContent, startLine, endLine);
    }
    
    /**
     * 获取索引统计信息
     */
    public IndexStats getStats() {
        BloomFilterUtil.BloomFilterStats bloomStats = tokenBloomFilter != null ? 
            tokenBloomFilter.getStats() : null;
        
        return IndexStats.builder()
            .fileHash(fileHash)
            .methodCount(methodSignatures.size())
            .classCount(classSignatures.size())
            .identifierCount(uniqueIdentifiers.size())
            .blockCount(semanticBlocks.size())
            .tokenCount(allTokens.size())
            .totalLines(totalLines)
            .indexTime(indexTime)
            .bloomStats(bloomStats)
            .build();
    }
    
    /**
     * 计算文件哈希
     */
    private static String calculateHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes("UTF-8"));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            log.error("计算文件哈希失败", e);
            return String.valueOf(content.hashCode());
        }
    }
    
    /**
     * 合并两个文件索引（用于多文件场景）
     */
    public static FileIndex merge(FileIndex index1, FileIndex index2) {
        if (index1 == null) return index2;
        if (index2 == null) return index1;
        
        // 合并方法签名
        Map<String, List<Integer>> mergedMethods = new HashMap<>(index1.methodSignatures);
        for (Map.Entry<String, List<Integer>> entry : index2.methodSignatures.entrySet()) {
            mergedMethods.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                     .addAll(entry.getValue());
        }
        
        // 合并类签名
        Map<String, List<Integer>> mergedClasses = new HashMap<>(index1.classSignatures);
        for (Map.Entry<String, List<Integer>> entry : index2.classSignatures.entrySet()) {
            mergedClasses.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                     .addAll(entry.getValue());
        }
        
        // 合并标识符
        Set<String> mergedIdentifiers = new HashSet<>(index1.uniqueIdentifiers);
        mergedIdentifiers.addAll(index2.uniqueIdentifiers);
        
        // 合并语义块
        List<FeatureExtractor.CodeBlock> mergedBlocks = new ArrayList<>(index1.semanticBlocks);
        mergedBlocks.addAll(index2.semanticBlocks);
        
        // 合并Token
        List<String> mergedTokens = new ArrayList<>(index1.allTokens);
        mergedTokens.addAll(index2.allTokens);
        
        // 合并布隆过滤器
        BloomFilterUtil mergedBloom = index1.tokenBloomFilter != null && index2.tokenBloomFilter != null ?
            BloomFilterUtil.union(index1.tokenBloomFilter, index2.tokenBloomFilter) :
            index1.tokenBloomFilter != null ? index1.tokenBloomFilter : index2.tokenBloomFilter;
        
        String mergedHash = index1.fileHash + "_" + index2.fileHash;
        
        return FileIndex.builder()
            .fileHash(mergedHash)
            .methodSignatures(mergedMethods)
            .classSignatures(mergedClasses)
            .uniqueIdentifiers(mergedIdentifiers)
            .semanticBlocks(mergedBlocks)
            .tokenBloomFilter(mergedBloom)
            .allTokens(mergedTokens)
            .totalLines(Math.max(index1.totalLines, index2.totalLines))
            .indexTime(index1.indexTime + index2.indexTime)
            .blockSize(mergedBlocks.size())
            .build();
    }
    
    /**
     * 索引统计信息
     */
    @Data
    @Builder
    public static class IndexStats {
        private String fileHash;
        private int methodCount;
        private int classCount;
        private int identifierCount;
        private int blockCount;
        private int tokenCount;
        private int totalLines;
        private long indexTime;
        private BloomFilterUtil.BloomFilterStats bloomStats;
        
        @Override
        public String toString() {
            return String.format(
                "IndexStats{hash=%s..., methods=%d, classes=%d, identifiers=%d, blocks=%d, tokens=%d, lines=%d, time=%dms}",
                fileHash != null ? fileHash.substring(0, 8) : "null",
                methodCount, classCount, identifierCount, blockCount, tokenCount, totalLines, indexTime
            );
        }
    }
}
