package com.example.codecompare.rebuild.stats;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 代码块统计服务，提供分页与筛选能力。
 */
@Service
public class BlockStatsService {

    private final MetricsAggregator metricsAggregator;
    private final StatsViewMapper statsViewMapper;

    public BlockStatsService(MetricsAggregator metricsAggregator, StatsViewMapper statsViewMapper) {
        this.metricsAggregator = metricsAggregator;
        this.statsViewMapper = statsViewMapper;
    }

    public BlockStatsResponseDTO queryBlocks(CodeBlockQuery query) {
        MetricsAggregator.CodeBlockPage page = metricsAggregator.loadCodeBlocks(query);
        CodeBlockPageDTO pageDTO = statsViewMapper.toCodeBlockPage(page);
        return new BlockStatsResponseDTO(
                pageDTO.getData(),
                pageDTO.getPage(),
                pageDTO.getSize(),
                pageDTO.getTotal(),
                pageDTO.getTotalPages(),
                statsViewMapper.toCategoryFilters(page),
                page.getNewCodeRatio(),
                page.getTotalLines()
        );
    }

    public CodeBlockDetailDTO findDetail(String projectKey, String blockId) {
        String comparisonId = StringUtils.hasText(projectKey) ? projectKey : null;
        MetricsAggregator.BlockItem item = metricsAggregator.findBlockDetail(comparisonId, blockId);
        if (item == null) {
            item = metricsAggregator.findBlockDetailAcrossProjects(blockId);
        }
        if (item == null) {
            return null;
        }
        return new CodeBlockDetailDTO(
                item.getId(),
                item.getComparisonId(),
                item.getSourceProjectCode(),
                item.getTargetProjectCode(),
                item.getFilePath(),
                item.getStartLine(),
                item.getEndLine(),
                item.getSourceCode(),
                item.getTargetCode(),
                item.getStatus(),
                item.getStatusLabel(),
                item.getCategories(),
                false
        );
    }
}
