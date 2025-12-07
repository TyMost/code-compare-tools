package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.shared.utils.CoverageUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DebugSimilarityTest {

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
        DiffBlock block1 = DiffBlock.builder()
                .contentFrom("alpha beta")
                .build();
        DiffBlock block2 = DiffBlock.builder()
                .contentTo("alpha")
                .build();
        
        CoverageEvaluator evaluator = new CoverageEvaluator();
        
        // 使用反射访问私有方法来调试
        try {
            java.lang.reflect.Method method = CoverageEvaluator.class.getDeclaredMethod("findBestSimilarity", 
                    DiffBlock.class, List.class);
            method.setAccessible(true);
            
            double similarity = (Double) method.invoke(evaluator, block1, Arrays.asList(block2));
            System.out.println("Block similarity: " + similarity);
            
            assertEquals(0.5, similarity, 0.001, "Expected 0.5 similarity for blocks");
            
        } catch (Exception e) {
            fail("Failed to test block similarity: " + e.getMessage());
        }
    }
    
    @Test
    void debugExactMatch() {
        DiffBlock block1 = DiffBlock.builder()
                .contentFrom("int value = 1;")
                .build();
        DiffBlock block2 = DiffBlock.builder()
                .contentTo("int value = 1;")
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
}
