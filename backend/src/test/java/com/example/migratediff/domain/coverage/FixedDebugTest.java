package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.shared.utils.CoverageUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FixedDebugTest {

    @Test
    void debugTokenizationAndSimilarity() {
        // 测试基本的tokenization和相似度计算
        String content1 = "alpha beta";
        String content2 = "alpha";
        
        List<String> tokens1 = CoverageUtils.tokenize(content1);
        List<String> tokens2 = CoverageUtils.tokenize(content2);
        
        System.out.println("Content1: " + content1);
        System.out.println("Tokens1: " + tokens1);
        System.out.println("Content2: " + content2);
        System.out.println("Tokens2: " + tokens2);
        
        double similarity = CoverageUtils.recallSimilarity(tokens1, tokens2);
        System.out.println("Recall Similarity: " + similarity);
        
        assertEquals(0.5, similarity, 0.001, "Expected 0.5 similarity for alpha beta vs alpha");
    }
    
    @Test
    void debugBlockSimilarity() {
        // 修复：为两个块都设置contentFrom和contentTo
        DiffBlock block1 = DiffBlock.builder()
                .contentFrom("alpha beta")
                .contentTo("alpha beta modified")
                .build();
        DiffBlock block2 = DiffBlock.builder()
                .contentFrom("alpha")
                .contentTo("alpha modified")
                .build();
        
        CoverageEvaluator evaluator = new CoverageEvaluator();
        
        // 使用反射访问私有方法来调试
        try {
            java.lang.reflect.Method method = CoverageEvaluator.class.getDeclaredMethod("findBestSimilarity", 
                    DiffBlock.class, List.class);
            method.setAccessible(true);
            
            double similarity = (Double) method.invoke(evaluator, block1, Arrays.asList(block2));
            System.out.println("Block similarity: " + similarity);
            
            // 由于block1的contentFrom ("alpha beta") 和 block2的contentFrom ("alpha") 的相似度是0.5
            // 这应该是deleteSimilarity，权重1.0，所以最终应该是0.5
            assertEquals(0.5, similarity, 0.001, "Expected 0.5 similarity for blocks");
            
        } catch (Exception e) {
            fail("Failed to test block similarity: " + e.getMessage());
        }
    }
    
    @Test
    void debugExactMatch() {
        // 修复：为两个块都设置完整的contentFrom和contentTo
        DiffBlock block1 = DiffBlock.builder()
                .contentFrom("int value = 1;")
                .contentTo("int value = 2;")
                .build();
        DiffBlock block2 = DiffBlock.builder()
                .contentFrom("int value = 1;")
                .contentTo("int value = 2;")
                .build();
        
        CoverageEvaluator evaluator = new CoverageEvaluator();
        
        try {
            java.lang.reflect.Method method = CoverageEvaluator.class.getDeclaredMethod("findBestSimilarity", 
                    DiffBlock.class, List.class);
            method.setAccessible(true);
            
            double similarity = (Double) method.invoke(evaluator, block1, Arrays.asList(block2));
            System.out.println("Exact match similarity: " + similarity);
            
            assertEquals(1.0, similarity, 0.001, "Expected 1.0 similarity for exact match");
            
        } catch (Exception e) {
            fail("Failed to test exact match: " + e.getMessage());
        }
    }
    
    @Test
    void debugSimpleBlockMatch() {
        // 最简单的块匹配测试
        DiffBlock block1 = DiffBlock.builder()
                .contentFrom("alpha")
                .contentTo("alpha")
                .build();
        DiffBlock block2 = DiffBlock.builder()
                .contentFrom("alpha")
                .contentTo("alpha")
                .build();
        
        CoverageEvaluator evaluator = new CoverageEvaluator();
        
        try {
            java.lang.reflect.Method method = CoverageEvaluator.class.getDeclaredMethod("findBestSimilarity", 
                    DiffBlock.class, List.class);
            method.setAccessible(true);
            
            double similarity = (Double) method.invoke(evaluator, block1, Arrays.asList(block2));
            System.out.println("Simple block match similarity: " + similarity);
            
            assertEquals(1.0, similarity, 0.001, "Expected 1.0 similarity for simple exact match");
            
        } catch (Exception e) {
            fail("Failed to test simple block match: " + e.getMessage());
        }
    }
}
