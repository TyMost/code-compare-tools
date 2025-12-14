package com.example.migratediff.domain.coverage.optimization.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 布隆过滤器工具类单元测试
 */
class BloomFilterUtilTest {
    
    private BloomFilterUtil bloomFilter;
    
    @BeforeEach
    void setUp() {
        bloomFilter = new BloomFilterUtil(1000, 0.01); // 1000个元素，1%假阳性率
    }
    
    @Test
    @DisplayName("测试基本添加和查询功能")
    void testBasicAddAndContain() {
        // 添加元素
        bloomFilter.add("hello");
        bloomFilter.add("world");
        bloomFilter.add("java");
        
        // 检查包含的元素
        assertTrue(bloomFilter.mightContain("hello"));
        assertTrue(bloomFilter.mightContain("world"));
        assertTrue(bloomFilter.mightContain("java"));
        
        // 检查未添加的元素
        assertFalse(bloomFilter.mightContain("python"));
        assertFalse(bloomFilter.mightContain("unknown"));
    }
    
    @Test
    @DisplayName("测试null和空字符串处理")
    void testNullAndEmptyString() {
        // null值应该被忽略
        bloomFilter.add(null);
        assertFalse(bloomFilter.mightContain(null));
        
        // 空字符串应该可以正常处理
        bloomFilter.add("");
        assertTrue(bloomFilter.mightContain(""));
    }
    
    @Test
    @DisplayName("测试重复添加")
    void testDuplicateAdds() {
        String element = "duplicate";
        
        // 重复添加同一元素
        bloomFilter.add(element);
        bloomFilter.add(element);
        bloomFilter.add(element);
        
        // 应该仍然返回true
        assertTrue(bloomFilter.mightContain(element));
    }
    
    @Test
    @DisplayName("测试容量和假阳性率")
    void testCapacityAndFalsePositiveRate() {
        // 创建一个小的布隆过滤器来测试假阳性
        BloomFilterUtil smallFilter = new BloomFilterUtil(10, 0.1); // 10个元素，10%假阳性率
        
        // 添加一些元素
        String[] elements = {"a", "b", "c", "d", "e"};
        for (String element : elements) {
            smallFilter.add(element);
        }
        
        // 检查添加的元素
        for (String element : elements) {
            assertTrue(smallFilter.mightContain(element));
        }
        
        // 测试一些未添加的元素，可能会有假阳性
        int falsePositives = 0;
        String[] notAdded = {"x", "y", "z", "unknown1", "unknown2"};
        for (String element : notAdded) {
            if (smallFilter.mightContain(element)) {
                falsePositives++;
            }
        }
        
        // 假阳性率应该在合理范围内
        double actualFalsePositiveRate = (double) falsePositives / notAdded.length;
        assertTrue(actualFalsePositiveRate <= 0.3, "假阳性率过高: " + actualFalsePositiveRate);
    }
    
    @Test
    @DisplayName("测试使用率")
    void testUsageRate() {
        // 初始使用率应该为0
        assertEquals(0.0, bloomFilter.getUsageRate(), 0.001);
        
        // 添加一些元素
        bloomFilter.add("element1");
        bloomFilter.add("element2");
        
        // 使用率应该大于0
        double usageRate = bloomFilter.getUsageRate();
        assertTrue(usageRate > 0.0);
        assertTrue(usageRate < 1.0);
    }
    
    @Test
    @DisplayName("试预定义的布隆过滤器")
    void testPredefinedBloomFilters() {
        // 测试默认配置
        BloomFilterUtil defaultFilter = BloomFilterUtil.createDefault();
        assertNotNull(defaultFilter);
        assertTrue(defaultFilter.getBitSetSize() > 0);
        assertTrue(defaultFilter.getNumHashFunctions() > 0);
        
        // 测试小规模配置
        BloomFilterUtil smallFilter = BloomFilterUtil.createSmall();
        assertNotNull(smallFilter);
        assertTrue(smallFilter.getBitSetSize() > 0);
        
        // 测试大规模配置
        BloomFilterUtil largeFilter = BloomFilterUtil.createLarge();
        assertNotNull(largeFilter);
        assertTrue(largeFilter.getBitSetSize() > 0);
        
        // 大规模过滤器应该比小规模过滤器有更大的位数组
        assertTrue(largeFilter.getBitSetSize() > smallFilter.getBitSetSize());
    }
    
    @Test
    @DisplayName("测试从集合创建布隆过滤器")
    void testCreateWithElements() {
        java.util.List<String> elements = java.util.Arrays.asList(
            "java", "python", "javascript", "typescript", "go"
        );
        
        BloomFilterUtil filter = BloomFilterUtil.createWithElements(elements, 0.01);
        
        assertNotNull(filter);
        
        // 检查所有元素都被正确添加
        for (String element : elements) {
            assertTrue(filter.mightContain(element));
        }
        
        // 检查未添加的元素
        assertFalse(filter.mightContain("ruby"));
        assertFalse(filter.mightContain("php"));
    }
    
    @Test
    @DisplayName("测试布隆过滤器统计信息")
    void testBloomFilterStats() {
        // 添加一些元素
        bloomFilter.add("test1");
        bloomFilter.add("test2");
        bloomFilter.add("test3");
        
        BloomFilterUtil.BloomFilterStats stats = bloomFilter.getStats();
        
        assertNotNull(stats);
        assertTrue(stats.getBitSetSize() > 0);
        assertTrue(stats.getNumHashFunctions() > 0);
        assertTrue(stats.getBitsSet() > 0);
        assertTrue(stats.getUsageRate() > 0.0);
        assertTrue(stats.getEstimatedFalsePositiveRate() >= 0.0);
        
        // 验证toString方法
        String statsString = stats.toString();
        assertNotNull(statsString);
        assertTrue(statsString.contains("size="));
        assertTrue(statsString.contains("hashFunctions="));
        assertTrue(statsString.contains("bitsSet="));
        assertTrue(statsString.contains("usage="));
        assertTrue(statsString.contains("fpr="));
    }
    
    @Test
    @DisplayName("测试清空功能")
    void testClear() {
        // 添加一些元素
        bloomFilter.add("element1");
        bloomFilter.add("element2");
        
        // 确认元素存在
        assertTrue(bloomFilter.mightContain("element1"));
        assertTrue(bloomFilter.mightContain("element2"));
        assertTrue(bloomFilter.getUsageRate() > 0.0);
        
        // 清空过滤器
        bloomFilter.clear();
        
        // 确认使用率为0
        assertEquals(0.0, bloomFilter.getUsageRate(), 0.001);
        
        // 清空后，所有查询都应该返回false
        assertFalse(bloomFilter.mightContain("element1"));
        assertFalse(bloomFilter.mightContain("element2"));
    }
    
    @Test
    @DisplayName("测试布隆过滤器合并")
    void testBloomFilterUnion() {
        // 创建两个布隆过滤器
        BloomFilterUtil filter1 = new BloomFilterUtil(100, 0.01);
        BloomFilterUtil filter2 = new BloomFilterUtil(100, 0.01);
        
        // 向第一个过滤器添加元素
        filter1.add("apple");
        filter1.add("banana");
        
        // 向第二个过滤器添加元素
        filter2.add("cherry");
        filter2.add("date");
        
        // 合并过滤器
        BloomFilterUtil unionFilter = BloomFilterUtil.union(filter1, filter2);
        
        assertNotNull(unionFilter);
        
        // 合并后的过滤器应该包含所有元素
        assertTrue(unionFilter.mightContain("apple"));
        assertTrue(unionFilter.mightContain("banana"));
        assertTrue(unionFilter.mightContain("cherry"));
        assertTrue(unionFilter.mightContain("date"));
    }
    
    @Test
    @DisplayName("测试布隆过滤器交集")
    void testBloomFilterIntersection() {
        // 创建两个布隆过滤器
        BloomFilterUtil filter1 = new BloomFilterUtil(100, 0.01);
        BloomFilterUtil filter2 = new BloomFilterUtil(100, 0.01);
        
        // 向两个过滤器都添加共同元素
        filter1.add("common");
        filter2.add("common");
        
        // 只向第一个过滤器添加独有元素
        filter1.add("only1");
        
        // 只向第二个过滤器添加独有元素
        filter2.add("only2");
        
        // 计算交集
        BloomFilterUtil intersectionFilter = BloomFilterUtil.intersection(filter1, filter2);
        
        assertNotNull(intersectionFilter);
        
        // 交集应该包含共同元素
        assertTrue(intersectionFilter.mightContain("common"));
        
        // 交集可能不包含独有元素（由于布隆过滤器的特性）
        // 注意：布隆过滤器的交集操作可能导致更多的假阴性
    }
    
    @Test
    @DisplayName("测试参数不匹配的异常")
    void testParameterMismatchException() {
        // 创建参数不匹配的布隆过滤器
        BloomFilterUtil filter1 = new BloomFilterUtil(100, 0.01);
        BloomFilterUtil filter2 = new BloomFilterUtil(200, 0.01); // 不同的预期元素数量
        
        // 尝试合并应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            BloomFilterUtil.union(filter1, filter2);
        });
        
        // 尝试计算交集也应该抛出异常
        assertThrows(IllegalArgumentException.class, () -> {
            BloomFilterUtil.intersection(filter1, filter2);
        });
    }
    
    @Test
    @DisplayName("测试假阳性率估算")
    void testFalsePositiveRateEstimation() {
        // 创建一个特定的布隆过滤器
        BloomFilterUtil filter = new BloomFilterUtil(100, 0.05); // 5%假阳性率
        
        // 添加一些元素，但不要填满
        for (int i = 0; i < 50; i++) {
            filter.add("element" + i);
        }
        
        double estimatedRate = filter.estimateFalsePositiveRate();
        
        // 估算的假阳性率应该是合理的
        assertTrue(estimatedRate >= 0.0);
        assertTrue(estimatedRate <= 1.0);
        
        // 随着元素增加，假阳性率应该增加
        for (int i = 50; i < 200; i++) {
            filter.add("element" + i);
        }
        
        double laterEstimatedRate = filter.estimateFalsePositiveRate();
        assertTrue(laterEstimatedRate >= estimatedRate);
    }
    
    @Test
    @DisplayName("测试大量数据处理")
    void testLargeDataSet() {
        // 创建一个较大的布隆过滤器
        BloomFilterUtil largeFilter = new BloomFilterUtil(10000, 0.01);
        
        // 添加大量元素
        for (int i = 0; i < 1000; i++) {
            largeFilter.add("large_element_" + i);
        }
        
        // 检查一些已添加的元素
        assertTrue(largeFilter.mightContain("large_element_0"));
        assertTrue(largeFilter.mightContain("large_element_500"));
        assertTrue(largeFilter.mightContain("large_element_999"));
        
        // 检查一些未添加的元素
        int falsePositives = 0;
        for (int i = 2000; i < 2010; i++) {
            if (largeFilter.mightContain("large_element_" + i)) {
                falsePositives++;
            }
        }
        
        // 假阳性数量应该较少
        assertTrue(falsePositives <= 2, "假阳性数量过多: " + falsePositives);
    }
    
    @Test
    @DisplayName("测试边界条件")
    void testBoundaryConditions() {
        // 测试极小的布隆过滤器
        BloomFilterUtil tinyFilter = new BloomFilterUtil(1, 0.5);
        tinyFilter.add("single");
        assertTrue(tinyFilter.mightContain("single"));
        
        // 测试极低的假阳性率要求
        BloomFilterUtil strictFilter = new BloomFilterUtil(100, 0.0001);
        strictFilter.add("strict");
        assertTrue(strictFilter.mightContain("strict"));
        
        // 验证参数
        assertTrue(tinyFilter.getNumHashFunctions() >= 1);
        assertTrue(strictFilter.getNumHashFunctions() >= 1);
        assertTrue(tinyFilter.getBitSetSize() >= 1);
        assertTrue(strictFilter.getBitSetSize() >= 1);
    }
}
