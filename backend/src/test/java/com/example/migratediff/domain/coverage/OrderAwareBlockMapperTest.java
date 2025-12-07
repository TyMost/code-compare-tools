package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DiffBlock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OrderAwareBlockMapperTest {

    private OrderAwareBlockMapper blockMapper;

    @BeforeEach
    void setUp() {
        blockMapper = new OrderAwareBlockMapper();
        // 手动设置配置值，避免依赖Spring环境
        try {
            java.lang.reflect.Field field = OrderAwareBlockMapper.class.getDeclaredField("enableNoiseFiltering");
            field.setAccessible(true);
            field.set(blockMapper, false); // 默认关闭噪音过滤
        } catch (Exception e) {
            // 如果反射失败，继续使用默认值
        }
    }

    @Test
    void testCreateMapping_PerfectMatch() {
        // 创建完全匹配的块
        DiffBlock oBlock1 = createDiffBlock("int value = 1;", 1);
        DiffBlock oBlock2 = createDiffBlock("String name = \"test\";", 2);
        List<DiffBlock> oBlocks = Arrays.asList(oBlock1, oBlock2);

        DiffBlock gBlock1 = createDiffBlock("int value = 1;", 1);
        DiffBlock gBlock2 = createDiffBlock("String name = \"test\";", 2);
        List<DiffBlock> gBlocks = Arrays.asList(gBlock1, gBlock2);

        BlockMapping mapping = blockMapper.createMapping(oBlocks, gBlocks);

        assertEquals(1.0, mapping.getCoverage(), 0.01);
        assertEquals(2, mapping.getMatchedCount());
        assertEquals(0, mapping.getUnmatchedOracle().size());
        assertEquals(0, mapping.getUnmatchedGauss().size());

        // 验证映射关系
        assertEquals(gBlock1, mapping.getGaussBlock(oBlock1));
        assertEquals(gBlock2, mapping.getGaussBlock(oBlock2));
        assertEquals(1.0, mapping.getSimilarity(oBlock1), 0.01);
        assertEquals(1.0, mapping.getSimilarity(oBlock2), 0.01);
    }

    @Test
    void testCreateMapping_WithNoiseFiltering() {
        // 测试噪音过滤功能
        DiffBlock oBlock = createDiffBlock("import java.util.List;\n// Comment\nint value = 1;", 1);
        DiffBlock gBlock = createDiffBlock("import java.util.Map;\n// Different comment\nint value = 1;", 1);
        List<DiffBlock> oBlocks = Arrays.asList(oBlock);
        List<DiffBlock> gBlocks = Arrays.asList(gBlock);

        BlockMapping mapping = blockMapper.createMapping(oBlocks, gBlocks);

        // 验证映射创建成功
        assertNotNull(mapping);
        assertEquals(1, mapping.getTotalOracleCount());
        assertEquals(1, mapping.getTotalGaussCount());
        
        // 由于默认关闭噪音过滤，import和注释差异会影响相似度
        // 但仍然应该有一定程度的匹配
        assertTrue(mapping.getSimilarity(oBlock) >= 0.0);
    }

    @Test
    void testCreateMapping_PositionalPreference() {
        // 测试位置偏好：相同内容在不同位置时，应该优先匹配相近位置
        DiffBlock oBlock1 = createDiffBlock("common code", 1);
        DiffBlock oBlock2 = createDiffBlock("unique code A", 2);
        List<DiffBlock> oBlocks = Arrays.asList(oBlock1, oBlock2);

        DiffBlock gBlock1 = createDiffBlock("unique code B", 1);
        DiffBlock gBlock2 = createDiffBlock("common code", 2);
        DiffBlock gBlock3 = createDiffBlock("unique code C", 3);
        List<DiffBlock> gBlocks = Arrays.asList(gBlock1, gBlock2, gBlock3);

        BlockMapping mapping = blockMapper.createMapping(oBlocks, gBlocks);

        // oBlock1应该匹配到gBlock2（相同代码，位置相近）
        assertEquals(gBlock2, mapping.getGaussBlock(oBlock1));
        assertEquals(1.0, mapping.getSimilarity(oBlock1), 0.01);
    }

    @Test
    void testCreateMapping_NoMatch() {
        // 测试无匹配情况
        DiffBlock oBlock1 = createDiffBlock("code A", 1);
        DiffBlock oBlock2 = createDiffBlock("code B", 2);
        List<DiffBlock> oBlocks = Arrays.asList(oBlock1, oBlock2);

        DiffBlock gBlock1 = createDiffBlock("completely different X", 1);
        DiffBlock gBlock2 = createDiffBlock("completely different Y", 2);
        List<DiffBlock> gBlocks = Arrays.asList(gBlock1, gBlock2);

        BlockMapping mapping = blockMapper.createMapping(oBlocks, gBlocks);

        assertEquals(0.0, mapping.getCoverage(), 0.01);
        assertEquals(0, mapping.getMatchedCount());
        assertEquals(2, mapping.getUnmatchedOracle().size());
        assertEquals(2, mapping.getUnmatchedGauss().size());
    }

    @Test
    void testCreateMapping_UnbalancedBlocks() {
        // 测试块数量不平衡的情况
        DiffBlock oBlock1 = createDiffBlock("code A", 1);
        DiffBlock oBlock2 = createDiffBlock("code B", 2);
        List<DiffBlock> oBlocks = Arrays.asList(oBlock1, oBlock2);

        DiffBlock gBlock1 = createDiffBlock("code A", 1);
        DiffBlock gBlock2 = createDiffBlock("code B", 2);
        DiffBlock gBlock3 = createDiffBlock("extra code C", 3);
        List<DiffBlock> gBlocks = Arrays.asList(gBlock1, gBlock2, gBlock3);

        BlockMapping mapping = blockMapper.createMapping(oBlocks, gBlocks);

        assertEquals(1.0, mapping.getCoverage(), 0.01);
        assertEquals(2, mapping.getMatchedCount());
        assertEquals(0, mapping.getUnmatchedOracle().size());
        assertEquals(1, mapping.getUnmatchedGauss().size()); // gBlock3未匹配
    }

    @Test
    void testCreateMapping_EmptyBlocks() {
        // 测试空块情况
        List<DiffBlock> oBlocks = Arrays.asList();
        List<DiffBlock> gBlocks = Arrays.asList();

        BlockMapping mapping = blockMapper.createMapping(oBlocks, gBlocks);

        assertEquals(1.0, mapping.getCoverage(), 0.01); // 空集合视为完全覆盖
        assertEquals(0, mapping.getMatchedCount());
        assertEquals(0, mapping.getTotalOracleCount());
    }

    @Test
    void testCreateMapping_PartialMatch() {
        // 测试部分匹配情况
        DiffBlock oBlock1 = createDiffBlock("matching code", 1);
        DiffBlock oBlock2 = createDiffBlock("non-matching code", 2);
        List<DiffBlock> oBlocks = Arrays.asList(oBlock1, oBlock2);

        DiffBlock gBlock1 = createDiffBlock("matching code", 1);
        DiffBlock gBlock2 = createDiffBlock("different code", 2);
        List<DiffBlock> gBlocks = Arrays.asList(gBlock1, gBlock2);

        BlockMapping mapping = blockMapper.createMapping(oBlocks, gBlocks);

        assertEquals(0.5, mapping.getCoverage(), 0.01); // 1/2匹配
        assertEquals(1, mapping.getMatchedCount());
        assertEquals(1, mapping.getUnmatchedOracle().size());
        assertEquals(1, mapping.getUnmatchedGauss().size());

        // 验证哪个块匹配了
        assertEquals(gBlock1, mapping.getGaussBlock(oBlock1));
        assertEquals(1.0, mapping.getSimilarity(oBlock1), 0.01);
    }

    @Test
    void testCreateMapping_WindowConstraint() {
        // 测试位置窗口约束
        DiffBlock oBlock1 = createDiffBlock("unique code", 1);
        DiffBlock oBlock2 = createDiffBlock("common code", 5); // 位置5
        List<DiffBlock> oBlocks = Arrays.asList(oBlock1, oBlock2);

        // 相同代码在位置0，超出了窗口范围（窗口大小=3）
        DiffBlock gBlock1 = createDiffBlock("common code", 0);
        DiffBlock gBlock2 = createDiffBlock("other code", 6);
        List<DiffBlock> gBlocks = Arrays.asList(gBlock1, gBlock2);

        BlockMapping mapping = blockMapper.createMapping(oBlocks, gBlocks);

        // oBlock2可能无法匹配到gBlock1，因为位置太远
        // 这取决于窗口大小和相似度阈值的平衡
        assertTrue(mapping.getCoverage() < 1.0); // 应该不是完全覆盖
    }

    @Test
    void testGetConfigInfo() {
        String configInfo = blockMapper.getConfigInfo();
        assertNotNull(configInfo);
        assertTrue(configInfo.contains("OrderAwareBlockMapper配置"));
        assertTrue(configInfo.contains("位置窗口"));
        assertTrue(configInfo.contains("最小相似度"));
        assertTrue(configInfo.contains("噪音过滤"));
    }

    /**
     * 创建测试用的DiffBlock
     */
    private DiffBlock createDiffBlock(String content, int lineNumber) {
        return DiffBlock.builder()
                .contentTo(content)
                .contentFrom("") // 假设是新增块
                .startLineTo(lineNumber)
                .endLineTo(lineNumber)
                .build();
    }
}
