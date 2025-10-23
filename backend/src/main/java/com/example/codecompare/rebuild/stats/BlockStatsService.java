package com.example.codecompare.rebuild.stats;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

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
                page.getTotalLines()
        );
    }

    public CodeBlockDetailDTO findDetail(String projectKey, String blockId) {
        return findDetail(projectKey, blockId, null);
    }

    public CodeBlockDetailDTO findDetail(String projectKey, String blockId, CodeBlockQuery query) {
        String comparisonId = StringUtils.hasText(projectKey) ? projectKey : null;
        MetricsAggregator.BlockContext context = metricsAggregator.findBlockContext(comparisonId, blockId, query);
        if (context == null || context.getCurrent() == null) {
            return null;
        }
        MetricsAggregator.BlockItem item = context.getCurrent();
        String diffMode = item.getDiffMode();
        boolean incremental = "incremental".equalsIgnoreCase(diffMode);
        List<DiffSegmentDTO> segments = incremental && item.getDiff() != null
                ? Collections.singletonList(DiffSegmentDTO.from(item.getDiff()))
                : Collections.<DiffSegmentDTO>emptyList();
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
                false,
                context.getPrevious() == null ? null : context.getPrevious().getId(),
                context.getNext() == null ? null : context.getNext().getId(),
                diffMode,
                segments
        );
    }
}
