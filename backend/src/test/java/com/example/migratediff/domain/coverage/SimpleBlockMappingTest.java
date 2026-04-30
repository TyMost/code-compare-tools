package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 简单测试，验证核心功能不依赖Spring
 */
class SimpleBlockMappingTest {

    @Test
    void testBlockMappingBasicFunctionality() {
        // 直接实例化，避免Spring注入
        BlockMapping mapping = BlockMapping.builder().build();
        
        // 测试基本属性
        assertNotNull(mapping);
        assertEquals(0, mapping.getTotalOracleCount());
        assertEquals(0, mapping.getTotalGaussCount());
        assertEquals(1.0, mapping.getCoverage()); // 空集合视为完全覆盖
    }

    @Test
    void testBlockMappingWithMatches() {
        DiffBlock oBlock = createTestBlock("int x = 1;", 1);
        DiffBlock gBlock = createTestBlock("int x = 1;", 1);
        
        BlockMapping mapping = BlockMapping.builder().build();
        mapping.addMatch(oBlock, gBlock, 1.0);
        
        assertEquals(1, mapping.getTotalOracleCount());
        assertEquals(1, mapping.getTotalGaussCount());
        assertEquals(1, mapping.getMatchedCount());
        assertEquals(1.0, mapping.getCoverage(), 0.01);
        assertEquals(gBlock, mapping.getGaussBlock(oBlock));
        assertEquals(1.0, mapping.getSimilarity(oBlock), 0.01);
    }

    @Test
    void testBlockMappingWithUnmatched() {
        DiffBlock oBlock = createTestBlock("int x = 1;", 1);
        DiffBlock gBlock = createTestBlock("int y = 2;", 2);
        
        BlockMapping mapping = BlockMapping.builder().build();
        mapping.addUnmatchedOracle(oBlock);
        mapping.addUnmatchedGauss(gBlock);
        
        assertEquals(1, mapping.getTotalOracleCount());
        assertEquals(1, mapping.getTotalGaussCount());
        assertEquals(0, mapping.getMatchedCount());
        assertEquals(0.0, mapping.getCoverage(), 0.01);
        assertEquals(1, mapping.getUnmatchedOracle().size());
        assertEquals(1, mapping.getUnmatchedGauss().size());
    }

    @Test
    void testDiffBlockCreation() {
        DiffBlock block = createTestBlock("int value = 1;", 1);
        
        assertNotNull(block);
        assertEquals("int value = 1;", block.getContentFrom());
        assertEquals("int value = 1;", block.getContentTo());
        assertEquals(1, block.getStartLineTo());
        assertEquals(1, block.getEndLineTo());
        assertEquals(DiffType.MODIFY, block.getType());
    }

    @Test
    void testDiffFileCreation() {
        DiffBlock block = createTestBlock("int value = 1;", 1);
        DiffFile file = DiffFile.builder()
                .relativePath("Test.java")
                .blocks(Arrays.asList(block))
                .diffType(DiffType.MODIFY)
                .build();
        
        assertNotNull(file);
        assertEquals("Test.java", file.getRelativePath());
        assertEquals(1, file.getBlocks().size());
        assertEquals(DiffType.MODIFY, file.getDiffType());
    }

    @Test
    void testCoverageEvaluatorBasicCreation() {
        CoverageEvaluator evaluator = new CoverageEvaluator();
        assertNotNull(evaluator);
    }

    /**
     * 创建测试用的DiffBlock
     */
    private DiffBlock createTestBlock(String content, int lineNumber) {
        return DiffBlock.builder()
                .contentFrom(content)
                .contentTo(content)
                .startLineTo(lineNumber)
                .endLineTo(lineNumber)
                .type(DiffType.MODIFY)
                .build();
    }
}
