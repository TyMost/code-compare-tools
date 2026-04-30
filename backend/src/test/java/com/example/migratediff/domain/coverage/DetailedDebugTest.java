package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.shared.utils.CoverageUtils;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DetailedDebugTest {

    @Test
    void debugExactMatchStepByStep() {
        DiffBlock block1 = DiffBlock.builder()
                .contentFrom("int value = 1;")
                .contentTo("int value = 2;")
                .build();
        DiffBlock block2 = DiffBlock.builder()
                .contentFrom("int value = 1;")
                .contentTo("int value = 2;")
                .build();
        
        // 手动计算4-way相似度
        String oFrom = block1.getContentFrom();
        String oTo = block1.getContentTo();
        String tFrom = block2.getContentFrom();
        String tTo = block2.getContentTo();
        
        System.out.println("=== 4-Way Similarity Debug ===");
        System.out.println("oFrom: " + oFrom);
        System.out.println("oTo: " + oTo);
        System.out.println("tFrom: " + tFrom);
        System.out.println("tTo: " + tTo);
        
        // 计算各种相似度
        List<String> oFromTokens = CoverageUtils.tokenize(oFrom);
        List<String> oToTokens = CoverageUtils.tokenize(oTo);
        List<String> tFromTokens = CoverageUtils.tokenize(tFrom);
        List<String> tToTokens = CoverageUtils.tokenize(tTo);
        
        System.out.println("oFromTokens: " + oFromTokens);
        System.out.println("oToTokens: " + oToTokens);
        System.out.println("tFromTokens: " + tFromTokens);
        System.out.println("tToTokens: " + tToTokens);
        
        double deleteSim = CoverageUtils.recallSimilarity(oFromTokens, tFromTokens) * 1.0;
        double addSim = CoverageUtils.recallSimilarity(oToTokens, tToTokens) * 1.0;
        double cross1Sim = CoverageUtils.recallSimilarity(oFromTokens, tToTokens) * 0.3;
        double cross2Sim = CoverageUtils.recallSimilarity(oToTokens, tFromTokens) * 0.3;
        
        System.out.println("Delete similarity: " + deleteSim);
        System.out.println("Add similarity: " + addSim);
        System.out.println("Cross1 similarity: " + cross1Sim);
        System.out.println("Cross2 similarity: " + cross2Sim);
        
        double maxSim = Math.max(Math.max(deleteSim, addSim), Math.max(cross1Sim, cross2Sim));
        System.out.println("Final similarity: " + maxSim);
        
        // 对于完全相同的块，应该期望1.0的相似度
        assertTrue(maxSim >= 0.85, "Exact match should have high similarity");
    }
    
    @Test
    void debugSimpleMatch() {
        // 更简单的测试
        DiffBlock block1 = DiffBlock.builder()
                .contentFrom("alpha")
                .build();
        DiffBlock block2 = DiffBlock.builder()
                .contentTo("alpha")
                .build();
        
        String oFrom = block1.getContentFrom();
        String tTo = block2.getContentTo();
        
        System.out.println("=== Simple Match Debug ===");
        System.out.println("oFrom: '" + oFrom + "'");
        System.out.println("tTo: '" + tTo + "'");
        
        List<String> oFromTokens = CoverageUtils.tokenize(oFrom);
        List<String> tToTokens = CoverageUtils.tokenize(tTo);
        
        System.out.println("oFromTokens: " + oFromTokens);
        System.out.println("tToTokens: " + tToTokens);
        
        double similarity = CoverageUtils.recallSimilarity(oFromTokens, tToTokens);
        System.out.println("Recall similarity: " + similarity);
        
        assertEquals(1.0, similarity, 0.001, "Exact string match should be 1.0");
    }
}
