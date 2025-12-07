package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoverageEvaluatorMappingTest {

    @Mock
    private OrderAwareBlockMapper blockMapper;

    @InjectMocks
    private CoverageEvaluator coverageEvaluator;

    private DiffFile oracleFile;
    private DiffFile gaussFile;

    @BeforeEach
    void setUp() {
        // 创建测试用的ΔO文件
        DiffBlock oBlock1 = createDiffBlock("int value = 1;", 1, 1);
        DiffBlock oBlock2 = createDiffBlock("String name = \"test\";", 2, 2);
        oracleFile = createDiffFile("src/Test.java", Arrays.asList(oBlock1, oBlock2));

        // 创建测试用的ΔG文件
        DiffBlock gBlock1 = createDiffBlock("int value = 1;", 1, 1);
        DiffBlock gBlock2 = createDiffBlock("String name = \"test\";", 2, 2);
        gaussFile = createDiffFile("src/Test.java", Arrays.asList(gBlock1, gBlock2));
    }

    @Test
    void testEvaluateFileWithMapping_PerfectMatch() {
        // 模拟完美的块映射
        BlockMapping mapping = createPerfectMapping();
        when(blockMapper.createMapping(anyList(), anyList())).thenReturn(mapping);

        CoverageDetail result = coverageEvaluator.evaluateFileWithMapping(oracleFile, gaussFile, 0.8);

        assertEquals(1.0, result.getCoverage(), 0.01);
        assertEquals(2, result.getMatchedBlocks().size());
        assertEquals(0, result.getUnmatchedBlocks().size());
        assertEquals("src/Test.java", result.getFilePath());
    }

    @Test
    void testEvaluateFileWithMapping_PartialMatch() {
        // 模拟部分匹配
        BlockMapping mapping = createPartialMapping();
        when(blockMapper.createMapping(anyList(), anyList())).thenReturn(mapping);

        CoverageDetail result = coverageEvaluator.evaluateFileWithMapping(oracleFile, gaussFile, 0.8);

        assertEquals(0.5, result.getCoverage(), 0.01);
        assertEquals(1, result.getMatchedBlocks().size());
        assertEquals(1, result.getUnmatchedBlocks().size());
    }

    @Test
    void testEvaluateFileWithMapping_NoMatch() {
        // 模拟无匹配
        BlockMapping mapping = createNoMatchMapping();
        when(blockMapper.createMapping(anyList(), anyList())).thenReturn(mapping);

        CoverageDetail result = coverageEvaluator.evaluateFileWithMapping(oracleFile, gaussFile, 0.8);

        assertEquals(0.0, result.getCoverage(), 0.01);
        assertEquals(0, result.getMatchedBlocks().size());
        assertEquals(2, result.getUnmatchedBlocks().size());
    }

    @Test
    void testEvaluateFileWithMapping_EmptyFiles() {
        DiffBlock emptyBlock1 = createDiffBlock("", 1, 1);
        DiffFile emptyOracleFile = createDiffFile("src/Empty.java", Arrays.asList(emptyBlock1));
        DiffFile emptyGaussFile = createDiffFile("src/Empty.java", Collections.emptyList());

        BlockMapping mapping = createEmptyMapping();
        when(blockMapper.createMapping(anyList(), anyList())).thenReturn(mapping);

        CoverageDetail result = coverageEvaluator.evaluateFileWithMapping(emptyOracleFile, emptyGaussFile, 0.8);

        // 修复：根据无阈值算法的逻辑，空内容的块行数为0，所以总行数为0，覆盖率返回1.0
        assertEquals(1.0, result.getCoverage(), 0.01); // 总行数为0时返回1.0
        assertEquals(0, result.getTotalLines()); // 空内容块行数为0
    }

    @Test
    void testGetBlockMapping() {
        // 测试获取块映射的方法
        BlockMapping expectedMapping = createPerfectMapping();
        when(blockMapper.createMapping(anyList(), anyList())).thenReturn(expectedMapping);

        BlockMapping result = coverageEvaluator.getBlockMapping(oracleFile, gaussFile);

        assertNotNull(result);
        assertEquals(expectedMapping, result);
    }

    @Test
    void testGetBlockMapping_NullFiles() {
        // 测试空文件的处理
        BlockMapping expectedMapping = createEmptyMapping();
        when(blockMapper.createMapping(anyList(), anyList())).thenReturn(expectedMapping);

        BlockMapping result = coverageEvaluator.getBlockMapping(null, null);

        assertNotNull(result);
        assertEquals(0, result.getTotalOracleCount());
        assertEquals(0, result.getTotalGaussCount());
    }

    @Test
    void testEvaluateFileWithMapping_BackwardCompatibility() {
        // 测试新方法与原有方法的兼容性
        BlockMapping mapping = createPerfectMapping();
        when(blockMapper.createMapping(anyList(), anyList())).thenReturn(mapping);

        CoverageDetail newResult = coverageEvaluator.evaluateFileWithMapping(oracleFile, gaussFile, 0.85);
        CoverageDetail oldResult = coverageEvaluator.evaluateFile(oracleFile, gaussFile, 0.85);

        // 验证基本结构一致
        assertEquals(newResult.getFilePath(), oldResult.getFilePath());
        assertEquals(newResult.getTotalLines(), oldResult.getTotalLines());
        
        // 新方法应该基于映射给出更精确的结果
        assertNotNull(newResult.getCoverage());
        assertNotNull(oldResult.getCoverage());
    }

    @Test
    void testEvaluateFileWithMapping_ThresholdFiltering() {
        // 测试阈值过滤功能
        BlockMapping mapping = createMixedSimilarityMapping();
        when(blockMapper.createMapping(anyList(), anyList())).thenReturn(mapping);

        // 使用高阈值，应该只有一个块被认为是匹配的
        CoverageDetail highThresholdResult = coverageEvaluator.evaluateFileWithMapping(oracleFile, gaussFile, 0.9);
        assertEquals(1, highThresholdResult.getMatchedBlocks().size());

        // 使用低阈值，应该所有块都被认为是匹配的
        CoverageDetail lowThresholdResult = coverageEvaluator.evaluateFileWithMapping(oracleFile, gaussFile, 0.3);
        assertEquals(2, lowThresholdResult.getMatchedBlocks().size());
    }

    /**
     * 创建完美的块映射（全部匹配）
     */
    private BlockMapping createPerfectMapping() {
        List<DiffBlock> oBlocks = oracleFile.getBlocks();
        List<DiffBlock> gBlocks = gaussFile.getBlocks();
        
        BlockMapping mapping = BlockMapping.builder().build();
        mapping.addMatch(oBlocks.get(0), gBlocks.get(0), 1.0);
        mapping.addMatch(oBlocks.get(1), gBlocks.get(1), 1.0);
        
        return mapping;
    }

    /**
     * 创建部分匹配的块映射
     */
    private BlockMapping createPartialMapping() {
        List<DiffBlock> oBlocks = oracleFile.getBlocks();
        List<DiffBlock> gBlocks = gaussFile.getBlocks();
        
        BlockMapping mapping = BlockMapping.builder().build();
        mapping.addMatch(oBlocks.get(0), gBlocks.get(0), 1.0);
        mapping.addUnmatchedOracle(oBlocks.get(1));
        mapping.addUnmatchedGauss(gBlocks.get(1));
        
        return mapping;
    }

    /**
     * 创建无匹配的块映射
     */
    private BlockMapping createNoMatchMapping() {
        List<DiffBlock> oBlocks = oracleFile.getBlocks();
        List<DiffBlock> gBlocks = gaussFile.getBlocks();
        
        BlockMapping mapping = BlockMapping.builder().build();
        mapping.addUnmatchedOracle(oBlocks.get(0));
        mapping.addUnmatchedOracle(oBlocks.get(1));
        mapping.addUnmatchedGauss(gBlocks.get(0));
        mapping.addUnmatchedGauss(gBlocks.get(1));
        
        return mapping;
    }

    /**
     * 创建空的块映射
     */
    private BlockMapping createEmptyMapping() {
        return BlockMapping.builder().build();
    }

    /**
     * 创建混合相似度的块映射
     */
    private BlockMapping createMixedSimilarityMapping() {
        List<DiffBlock> oBlocks = oracleFile.getBlocks();
        List<DiffBlock> gBlocks = gaussFile.getBlocks();
        
        BlockMapping mapping = BlockMapping.builder().build();
        mapping.addMatch(oBlocks.get(0), gBlocks.get(0), 0.95); // 高相似度
        mapping.addMatch(oBlocks.get(1), gBlocks.get(1), 0.7);  // 低相似度
        
        return mapping;
    }

    /**
     * 创建测试用的DiffBlock
     */
    private DiffBlock createDiffBlock(String content, int startLine, int endLine) {
        return DiffBlock.builder()
                .contentTo(content)
                .contentFrom("")
                .startLineTo(startLine)
                .endLineTo(endLine)
                .type(DiffType.ADD)
                .build();
    }

    /**
     * 创建测试用的DiffFile
     */
    private DiffFile createDiffFile(String relativePath, List<DiffBlock> blocks) {
        return DiffFile.builder()
                .relativePath(relativePath)
                .blocks(blocks)
                .diffType(DiffType.ADD)
                .build();
    }
}
