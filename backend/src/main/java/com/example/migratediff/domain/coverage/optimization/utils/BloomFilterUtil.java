package com.example.migratediff.domain.coverage.optimization.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.BitSet;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * 布隆过滤器工具类
 * 用于快速判断一个元素是否可能存在于集合中
 */
@Slf4j
public class BloomFilterUtil {
    
    private final BitSet bitSet;
    private final int bitSetSize;
    private final int numHashFunctions;
    private final MessageDigest[] hashFunctions;
    
    /**
     * 创建布隆过滤器
     * @param expectedElements 预期元素数量
     * @param falsePositiveRate 期望的假阳性率
     */
    public BloomFilterUtil(int expectedElements, double falsePositiveRate) {
        // 计算最优的位数组大小和哈希函数数量
        this.bitSetSize = calculateOptimalSize(expectedElements, falsePositiveRate);
        this.numHashFunctions = calculateOptimalHashFunctions(bitSetSize, expectedElements);
        this.bitSet = new BitSet(bitSetSize);
        this.hashFunctions = createHashFunctions(numHashFunctions);
        
        log.debug("创建布隆过滤器: 位数组大小={}, 哈希函数数量={}", 
                 bitSetSize, numHashFunctions);
    }
    
    /**
     * 添加元素到布隆过滤器
     */
    public void add(String element) {
        if (element == null) return;
        
        int[] hashes = getHashes(element);
        for (int hash : hashes) {
            bitSet.set(Math.abs(hash % bitSetSize));
        }
    }
    
    /**
     * 检查元素是否可能存在
     */
    public boolean mightContain(String element) {
        if (element == null) return false;
        
        int[] hashes = getHashes(element);
        for (int hash : hashes) {
            if (!bitSet.get(Math.abs(hash % bitSetSize))) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 获取当前使用率
     */
    public double getUsageRate() {
        return (double) bitSet.cardinality() / bitSetSize;
    }
    
    /**
     * 获取位数组大小
     */
    public int getBitSetSize() {
        return bitSetSize;
    }
    
    /**
     * 获取哈希函数数量
     */
    public int getNumHashFunctions() {
        return numHashFunctions;
    }
    
    /**
     * 计算最优位数组大小
     */
    private static int calculateOptimalSize(int n, double p) {
        return (int) Math.ceil(-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }
    
    /**
     * 计算最优哈希函数数量
     */
    private static int calculateOptimalHashFunctions(int m, int n) {
        return (int) Math.max(1, Math.round((double) m / n * Math.log(2)));
    }
    
    /**
     * 创建哈希函数
     */
    private MessageDigest[] createHashFunctions(int count) {
        MessageDigest[] functions = new MessageDigest[count];
        
        try {
            // 使用不同的哈希算法
            String[] algorithms = {"MD5", "SHA-1", "SHA-256"};
            
            for (int i = 0; i < count; i++) {
                functions[i] = MessageDigest.getInstance(algorithms[i % algorithms.length]);
            }
            
            // 如果需要的哈希函数数量超过算法数量，使用种子变体
            for (int i = algorithms.length; i < count; i++) {
                functions[i] = MessageDigest.getInstance(algorithms[i % algorithms.length]);
            }
            
        } catch (NoSuchAlgorithmException e) {
            log.error("创建哈希函数失败", e);
            throw new RuntimeException("无法创建哈希函数", e);
        }
        
        return functions;
    }
    
    /**
     * 获取元素的多个哈希值
     */
    private int[] getHashes(String element) {
        int[] hashes = new int[numHashFunctions];
        byte[] bytes = element.getBytes();
        
        for (int i = 0; i < numHashFunctions; i++) {
            MessageDigest digest = hashFunctions[i];
            digest.reset();
            byte[] hashBytes = digest.digest(bytes);
            
            // 将字节数组转换为整数
            hashes[i] = bytesToInt(hashBytes);
            
            // 如果使用相同算法的变体，添加种子
            if (i >= 3) {
                hashes[i] ^= i; // 简单的种子变化
            }
        }
        
        return hashes;
    }
    
    /**
     * 将字节数组转换为整数
     */
    private int bytesToInt(byte[] bytes) {
        int result = 0;
        for (int i = 0; i < Math.min(4, bytes.length); i++) {
            result <<= 8;
            result |= bytes[i] & 0xFF;
        }
        return result;
    }
    
    /**
     * 创建默认配置的布隆过滤器
     */
    public static BloomFilterUtil createDefault() {
        return new BloomFilterUtil(10000, 0.01); // 1万元素，1%假阳性率
    }
    
    /**
     * 创建小规模布隆过滤器
     */
    public static BloomFilterUtil createSmall() {
        return new BloomFilterUtil(1000, 0.05); // 1千元素，5%假阳性率
    }
    
    /**
     * 创建大规模布隆过滤器
     */
    public static BloomFilterUtil createLarge() {
        return new BloomFilterUtil(100000, 0.001); // 10万元素，0.1%假阳性率
    }
    
    /**
     * 创建布隆过滤器并添加多个元素
     */
    public static BloomFilterUtil createWithElements(Iterable<String> elements, double falsePositiveRate) {
        // 先估算元素数量
        int estimatedSize = 0;
        for (String element : elements) {
            if (element != null) estimatedSize++;
        }
        
        BloomFilterUtil filter = new BloomFilterUtil(estimatedSize, falsePositiveRate);
        
        // 添加元素
        for (String element : elements) {
            filter.add(element);
        }
        
        return filter;
    }
    
    /**
     * 合并两个布隆过滤器（OR操作）
     */
    public static BloomFilterUtil union(BloomFilterUtil filter1, BloomFilterUtil filter2) {
        if (filter1.bitSetSize != filter2.bitSetSize || 
            filter1.numHashFunctions != filter2.numHashFunctions) {
            throw new IllegalArgumentException("布隆过滤器参数不匹配，无法合并");
        }
        
        BloomFilterUtil result = new BloomFilterUtil(
            filter1.bitSetSize, 
            filter1.numHashFunctions
        );
        
        // 执行OR操作
        result.bitSet.or(filter1.bitSet);
        result.bitSet.or(filter2.bitSet);
        
        return result;
    }
    
    /**
     * 计算两个布隆过滤器的交集（AND操作）
     */
    public static BloomFilterUtil intersection(BloomFilterUtil filter1, BloomFilterUtil filter2) {
        if (filter1.bitSetSize != filter2.bitSetSize || 
            filter1.numHashFunctions != filter2.numHashFunctions) {
            throw new IllegalArgumentException("布隆过滤器参数不匹配，无法计算交集");
        }
        
        BloomFilterUtil result = new BloomFilterUtil(
            filter1.bitSetSize, 
            filter1.numHashFunctions
        );
        
        // 执行AND操作
        result.bitSet.and(filter1.bitSet);
        result.bitSet.and(filter2.bitSet);
        
        return result;
    }
    
    /**
     * 估算当前假阳性率
     */
    public double estimateFalsePositiveRate() {
        int k = numHashFunctions;
        int n = bitSet.cardinality(); // 设置的位数
        int m = bitSetSize;
        
        return Math.pow(1.0 - Math.exp(-k * (double) n / m), k);
    }
    
    /**
     * 清空布隆过滤器
     */
    public void clear() {
        bitSet.clear();
    }
    
    /**
     * 获取布隆过滤器的统计信息
     */
    public BloomFilterStats getStats() {
        return BloomFilterStats.builder()
            .bitSetSize(bitSetSize)
            .numHashFunctions(numHashFunctions)
            .bitsSet(bitSet.cardinality())
            .usageRate(getUsageRate())
            .estimatedFalsePositiveRate(estimateFalsePositiveRate())
            .build();
    }
    
    /**
     * 布隆过滤器统计信息
     */
    public static class BloomFilterStats {
        private final int bitSetSize;
        private final int numHashFunctions;
        private final int bitsSet;
        private final double usageRate;
        private final double estimatedFalsePositiveRate;
        
        private BloomFilterStats(int bitSetSize, int numHashFunctions, int bitsSet, 
                              double usageRate, double estimatedFalsePositiveRate) {
            this.bitSetSize = bitSetSize;
            this.numHashFunctions = numHashFunctions;
            this.bitsSet = bitsSet;
            this.usageRate = usageRate;
            this.estimatedFalsePositiveRate = estimatedFalsePositiveRate;
        }
        
        public static BloomFilterStatsBuilder builder() {
            return new BloomFilterStatsBuilder();
        }
        
        // Getters
        public int getBitSetSize() { return bitSetSize; }
        public int getNumHashFunctions() { return numHashFunctions; }
        public int getBitsSet() { return bitsSet; }
        public double getUsageRate() { return usageRate; }
        public double getEstimatedFalsePositiveRate() { return estimatedFalsePositiveRate; }
        
        @Override
        public String toString() {
            return String.format(
                "BloomFilterStats{size=%d, hashFunctions=%d, bitsSet=%d, usage=%.2f%%, fpr=%.4f}",
                bitSetSize, numHashFunctions, bitsSet, usageRate * 100, estimatedFalsePositiveRate
            );
        }
        
        public static class BloomFilterStatsBuilder {
            private int bitSetSize;
            private int numHashFunctions;
            private int bitsSet;
            private double usageRate;
            private double estimatedFalsePositiveRate;
            
            public BloomFilterStatsBuilder bitSetSize(int bitSetSize) {
                this.bitSetSize = bitSetSize;
                return this;
            }
            
            public BloomFilterStatsBuilder numHashFunctions(int numHashFunctions) {
                this.numHashFunctions = numHashFunctions;
                return this;
            }
            
            public BloomFilterStatsBuilder bitsSet(int bitsSet) {
                this.bitsSet = bitsSet;
                return this;
            }
            
            public BloomFilterStatsBuilder usageRate(double usageRate) {
                this.usageRate = usageRate;
                return this;
            }
            
            public BloomFilterStatsBuilder estimatedFalsePositiveRate(double estimatedFalsePositiveRate) {
                this.estimatedFalsePositiveRate = estimatedFalsePositiveRate;
                return this;
            }
            
            public BloomFilterStats build() {
                return new BloomFilterStats(bitSetSize, numHashFunctions, bitsSet, 
                                       usageRate, estimatedFalsePositiveRate);
            }
        }
    }
    
    // 私有构造函数，用于内部复制
    private BloomFilterUtil(int bitSetSize, int numHashFunctions) {
        this.bitSetSize = bitSetSize;
        this.numHashFunctions = numHashFunctions;
        this.bitSet = new BitSet(bitSetSize);
        this.hashFunctions = createHashFunctions(numHashFunctions);
    }
}
