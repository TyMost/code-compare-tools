package com.example.migratediff.api.mapper;

import com.example.migratediff.api.dto.DiffContentDTO;
import com.example.migratediff.api.dto.DiffDetailResponseDTO;
import com.example.migratediff.api.dto.DiffMatrixItemDTO;
import com.example.migratediff.api.dto.DiffRequestDTO;
import com.example.migratediff.api.dto.DiffStatsDTO;
import com.example.migratediff.api.dto.FileCommitHistoryDTO;
import com.example.migratediff.api.dto.ScanCacheEntryDTO;
import com.example.migratediff.api.dto.ScanPresetDTO;
import com.example.migratediff.api.dto.ScanRequestDTO;
import com.example.migratediff.api.dto.ScanResponseDTO;
import com.example.migratediff.api.dto.ScanSummaryDTO;
import com.example.migratediff.api.dto.ScanTaskSummaryDTO;
import com.example.migratediff.api.validation.DiffRequestValidator;
import com.example.migratediff.application.commit.GitCommitHistoryService;
import com.example.migratediff.application.scan.DiffDetail;
import com.example.migratediff.application.scan.DiffMatrixAssembler;
import com.example.migratediff.application.scan.DiffMatrixRow;
import com.example.migratediff.application.scan.ScanInput;
import com.example.migratediff.application.scan.ScanMode;
import com.example.migratediff.application.scan.ScanPresetProperties;
import com.example.migratediff.application.scan.ScanReport;
import com.example.migratediff.application.scan.ScanSnapshot;
import com.example.migratediff.domain.coverage.CoverageDetail;
import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffType;
import com.example.migratediff.domain.diff.DiffSummary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ScanMapper {

    private static final String UTF8_BOM = "\uFEFF";
    private static final char UTF8_BOM_CHAR_1 = '\u00EF';
    private static final char UTF8_BOM_CHAR_2 = '\u00BB';
    private static final char UTF8_BOM_CHAR_3 = '\u00BF';

    private final DiffMapper diffMapper;
    private final ScanPresetProperties presetProperties;
    private final DiffMatrixAssembler diffMatrixAssembler;
    private final DiffRequestValidator diffRequestValidator;
    private final GitCommitHistoryService gitCommitHistoryService;

    public ScanMapper(DiffMapper diffMapper,
                      ScanPresetProperties presetProperties,
                      DiffMatrixAssembler diffMatrixAssembler,
                      DiffRequestValidator diffRequestValidator,
                      GitCommitHistoryService gitCommitHistoryService) {
        this.diffMapper = diffMapper;
        this.presetProperties = presetProperties;
        this.diffMatrixAssembler = diffMatrixAssembler;
        this.diffRequestValidator = diffRequestValidator;
        this.gitCommitHistoryService = gitCommitHistoryService;
    }

    public ScanInput toInput(ScanRequestDTO requestDTO, ScanMode mode) {
        ScanPresetProperties.ScanPreset preset = resolvePreset(requestDTO.getPresetName());
        DiffRequestDTO oracleRequest = mergeWithPreset(requestDTO.getOracle(), preset != null ? preset.getSource() : null);
        DiffRequestDTO gaussRequest = mergeWithPreset(requestDTO.getGauss(), preset != null ? preset.getTarget() : null);
        diffRequestValidator.validate(oracleRequest, "oracle");
        diffRequestValidator.validate(gaussRequest, "gauss");
        DiffSummary oracleSummary = diffMapper.toDomain(oracleRequest, com.example.migratediff.domain.diff.DeltaType.DELTA_O);
        DiffSummary gaussSummary = diffMapper.toDomain(gaussRequest, com.example.migratediff.domain.diff.DeltaType.DELTA_G);
        if (mode == ScanMode.INCREMENTAL) {
            enableWorkingTree(oracleSummary);
            enableWorkingTree(gaussSummary);
        }
        return ScanInput.builder()
                .taskId(requestDTO.getTaskId())
                .persistResult(requestDTO.isPersistResult())
                .presetName(requestDTO.getPresetName())
                .repoId(trimToNull(requestDTO.getRepoId()))
                .repoName(trimToNull(requestDTO.getRepoName()))
                .oracleSummary(oracleSummary)
                .gaussSummary(gaussSummary)
                .mode(mode)
                .build();
    }

    public ScanResponseDTO toResponse(ScanReport report) {
        ScanResponseDTO responseDTO = new ScanResponseDTO();
        responseDTO.setTaskId(report.getTaskId());
        List<DiffMatrixRow> rows = diffMatrixAssembler.assemble(report);
        List<DiffMatrixItemDTO> matrix = rows.stream()
                .map(this::toMatrixItem)
                .collect(Collectors.toList());
        responseDTO.setSummary(buildSummary(report, rows));
        responseDTO.getDiffMatrix().addAll(matrix);
        return responseDTO;
    }

    public DiffDetailResponseDTO toDetailResponse(DiffDetail detail, String migrationDiff) {
        DiffDetailResponseDTO responseDTO = new DiffDetailResponseDTO();
        responseDTO.setTaskId(detail.getTaskId());
        responseDTO.setFilePath(detail.getFilePath());
        responseDTO.setOracleDiff(buildContent(detail.getOracleFile(), true));
        // 使用相同方向的内容映射，确保 Gauss diff 的 before/after 与 git diff 输出一致
        responseDTO.setGaussDiff(buildContent(detail.getGaussFile(), true));
        responseDTO.setMigrationDiff(migrationDiff);
        responseDTO.setStats(buildStats(detail));
        responseDTO.setCoverage(resolveCoverage(detail.getCoverageDetail()));
        
        // 提交历史信息暂时设置为null，需要在ScanController中通过其他方式获取
        responseDTO.setCommitHistory(null);
        
        return responseDTO;
    }

    public List<ScanPresetDTO> toPresetDTOs(List<ScanPresetProperties.ScanPreset> presets) {
        if (CollectionUtils.isEmpty(presets)) {
            return new ArrayList<>();
        }
        List<ScanPresetDTO> results = new ArrayList<>(presets.size());
        for (ScanPresetProperties.ScanPreset preset : presets) {
            results.add(toPresetDTO(preset));
        }
        return results;
    }

    public ScanTaskSummaryDTO toTaskSummary(ScanReport report) {
        if (report == null) {
            return null;
        }
        ScanTaskSummaryDTO dto = new ScanTaskSummaryDTO();
        dto.setTaskId(report.getTaskId());
        dto.setPresetName(report.getPresetName());
        dto.setRepoId(report.getRepoId());
        dto.setRepoName(report.getRepoName());
        dto.setMode(report.getMode() != null ? report.getMode().name() : null);
        dto.setGeneratedAt(report.getGeneratedAt());
        dto.setTotalFiles(report.filePaths().size());
        dto.setOverallCoverage(round(report.overallCoverage()));
        return dto;
    }

    public List<ScanCacheEntryDTO> toCacheEntries(List<ScanSnapshot> snapshots) {
        if (CollectionUtils.isEmpty(snapshots)) {
            return new ArrayList<>();
        }
        List<ScanCacheEntryDTO> entries = new ArrayList<>(snapshots.size());
        for (ScanSnapshot snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            ScanCacheEntryDTO dto = new ScanCacheEntryDTO();
            dto.setRepoId(snapshot.getRepoId());
            dto.setRepoName(snapshot.getRepoName());
            dto.setTaskId(snapshot.getTaskId());
            dto.setMode(snapshot.getMode() != null ? snapshot.getMode().name() : null);
            dto.setPersisted(snapshot.isPersisted());
            dto.setCachedAt(snapshot.getCachedAt());
            dto.setResponse(snapshot.getResponse());
            entries.add(dto);
        }
        return entries;
    }

    private ScanPresetDTO toPresetDTO(ScanPresetProperties.ScanPreset preset) {
        if (preset == null) {
            return null;
        }
        ScanPresetDTO dto = new ScanPresetDTO();
        dto.setName(preset.getName());
        dto.setSource(toPresetRepo(preset.getSource()));
        dto.setTarget(toPresetRepo(preset.getTarget()));
        return dto;
    }

    private ScanPresetDTO.RepoDTO toPresetRepo(ScanPresetProperties.RepoPreset preset) {
        if (preset == null) {
            return null;
        }
        ScanPresetDTO.RepoDTO repoDTO = new ScanPresetDTO.RepoDTO();
        repoDTO.setCode(preset.getCode());
        repoDTO.setPath(preset.getPath());
        repoDTO.setBranchFrom(preset.getBranchFrom());
        repoDTO.setBranchTo(preset.getBranchTo());
        repoDTO.setTimeFrom(preset.getTimeFrom());
        repoDTO.setTimeTo(preset.getTimeTo());
        repoDTO.setRefHint(preset.getRefHint());
        repoDTO.setDeltaType(preset.getDeltaType());
        repoDTO.setIncludeWorkingTree(preset.isIncludeWorkingTree());
        repoDTO.setFetchIfMissing(preset.isFetchIfMissing());
        repoDTO.setRemoteName(preset.getRemoteName());
        repoDTO.setScanStrategy(preset.getScanStrategy());
        repoDTO.setSnapshotIncludeRemoteRefs(preset.isSnapshotIncludeRemoteRefs());
        repoDTO.setSnapshotIncludeTags(preset.isSnapshotIncludeTags());
        repoDTO.setSnapshotMaxRefs(preset.getSnapshotMaxRefs());
        return repoDTO;
    }

    private ScanPresetProperties.ScanPreset resolvePreset(String presetName) {
        if (!StringUtils.hasText(presetName)) {
            return null;
        }
        return presetProperties.findPreset(presetName)
                .orElse(null); // 修改：找不到预设时返回null而不是抛出异常，支持导入的配置
    }

    private DiffRequestDTO mergeWithPreset(DiffRequestDTO override, ScanPresetProperties.RepoPreset preset) {
        DiffRequestDTO result = new DiffRequestDTO();
        if (preset != null) {
            result.setRepoPath(preset.getPath());
            result.setBranchFrom(preset.getBranchFrom());
            result.setBranchTo(preset.getBranchTo());
            result.setTimeFrom(preset.getTimeFrom());
            result.setTimeTo(preset.getTimeTo());
            result.setRefHint(preset.getRefHint());
            result.setDeltaType(preset.getDeltaType());
            result.setIncludeWorkingTree(preset.isIncludeWorkingTree());
            result.setFetchIfMissing(preset.isFetchIfMissing());
            result.setRemoteName(preset.getRemoteName());
            result.setSnapshotIncludeRemoteRefs(preset.isSnapshotIncludeRemoteRefs());
            result.setSnapshotIncludeTags(preset.isSnapshotIncludeTags());
            result.setSnapshotMaxRefs(preset.getSnapshotMaxRefs());
        }
        if (override != null) {
            if (StringUtils.hasText(override.getRepoPath())) {
                result.setRepoPath(override.getRepoPath());
            }
            if (StringUtils.hasText(override.getBranchFrom())) {
                result.setBranchFrom(override.getBranchFrom());
            }
            if (StringUtils.hasText(override.getBranchTo())) {
                result.setBranchTo(override.getBranchTo());
            }
            if (StringUtils.hasText(override.getDeltaType())) {
                result.setDeltaType(override.getDeltaType());
            }
            if (StringUtils.hasText(override.getTimeFrom())) {
                result.setTimeFrom(override.getTimeFrom());
            }
            if (StringUtils.hasText(override.getTimeTo())) {
                result.setTimeTo(override.getTimeTo());
            }
            if (StringUtils.hasText(override.getRefHint())) {
                result.setRefHint(override.getRefHint());
            }
            if (override.getIncludeWorkingTree() != null) {
                result.setIncludeWorkingTree(override.getIncludeWorkingTree());
            }
            if (override.getFetchIfMissing() != null) {
                result.setFetchIfMissing(override.getFetchIfMissing());
            }
            if (StringUtils.hasText(override.getRemoteName())) {
                result.setRemoteName(override.getRemoteName());
            }
            if (StringUtils.hasText(override.getScanStrategy())) {
                result.setScanStrategy(override.getScanStrategy());
            }
            if (override.getSnapshotIncludeRemoteRefs() != null) {
                result.setSnapshotIncludeRemoteRefs(override.getSnapshotIncludeRemoteRefs());
            }
            if (override.getSnapshotIncludeTags() != null) {
                result.setSnapshotIncludeTags(override.getSnapshotIncludeTags());
            }
            if (override.getSnapshotMaxRefs() != null) {
                result.setSnapshotMaxRefs(override.getSnapshotMaxRefs());
            }
        }
        if (!StringUtils.hasText(result.getRemoteName())) {
            result.setRemoteName("origin");
        }
        if (result.getFetchIfMissing() == null) {
            result.setFetchIfMissing(Boolean.TRUE);
        }
        if (result.getIncludeWorkingTree() == null) {
            result.setIncludeWorkingTree(Boolean.FALSE);
        }
        if (!StringUtils.hasText(result.getRefHint())) {
            if (StringUtils.hasText(result.getBranchTo())) {
                result.setRefHint(result.getBranchTo());
            } else if (StringUtils.hasText(result.getBranchFrom())) {
                result.setRefHint(result.getBranchFrom());
            } else {
                result.setRefHint("HEAD");
            }
        }
        return result;
    }

    private ScanSummaryDTO buildSummary(ScanReport report, List<DiffMatrixRow> rows) {
        ScanSummaryDTO summaryDTO = new ScanSummaryDTO();
        summaryDTO.setTotalFiles(rows.size());
        summaryDTO.setOracleOnly((int) rows.stream().filter(item -> "oracle-only".equals(item.getStatus())).count());
        summaryDTO.setGaussOnly((int) rows.stream().filter(item -> "gauss-only".equals(item.getStatus())).count());
        summaryDTO.setMatched((int) rows.stream().filter(item -> "matched".equals(item.getStatus())).count());
        double overallCoverage = resolveOverallCoverage(report);
        summaryDTO.setConsistencyRate(overallCoverage);
        summaryDTO.setOverallCoverage(overallCoverage);
        return summaryDTO;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private DiffMatrixItemDTO toMatrixItem(DiffMatrixRow row) {
        DiffMatrixItemDTO item = new DiffMatrixItemDTO();
        item.setFilePath(row.getFilePath());
        item.setOracleDelta(formatDelta(row.getOracleAdded(), row.getOracleRemoved()));
        item.setGaussDelta(formatDelta(row.getGaussAdded(), row.getGaussRemoved()));
        double coverage = row.getCoverage() == null ? 0D : round(row.getCoverage());
        item.setCoverage(coverage);
        item.setStatus(row.getStatus());
        return item;
    }

    private DiffContentDTO buildContent(DiffFile file, boolean preferSource) {
        DiffContentDTO contentDTO = new DiffContentDTO();
        contentDTO.setBefore(concatenate(file, preferSource ? DiffBlock::getContentFrom : DiffBlock::getContentTo));
        contentDTO.setAfter(concatenate(file, preferSource ? DiffBlock::getContentTo : DiffBlock::getContentFrom));
        return contentDTO;
    }

    private DiffStatsDTO buildStats(DiffDetail detail) {
        DiffStatsDTO statsDTO = new DiffStatsDTO();
        statsDTO.setOracleAdded(countAdded(detail.getOracleFile()));
        statsDTO.setOracleRemoved(countRemoved(detail.getOracleFile()));
        statsDTO.setGaussAdded(countAdded(detail.getGaussFile()));
        statsDTO.setGaussRemoved(countRemoved(detail.getGaussFile()));
        return statsDTO;
    }

    private double resolveOverallCoverage(ScanReport report) {
        return round(report.overallCoverage());
    }

    private double resolveCoverage(CoverageDetail detail) {
        return detail == null ? 0D : round(detail.getCoverage());
    }

    private String formatDelta(int added, int removed) {
        return "+" + Math.max(0, added) + "/-" + Math.max(0, removed);
    }

    private int countAdded(DiffFile file) {
        if (file == null || CollectionUtils.isEmpty(file.getBlocks())) {
            return 0;
        }
        return file.getBlocks().stream()
                .mapToInt(this::addedLines)
                .sum();
    }

    private int countRemoved(DiffFile file) {
        if (file == null || CollectionUtils.isEmpty(file.getBlocks())) {
            return 0;
        }
        return file.getBlocks().stream()
                .mapToInt(this::removedLines)
                .sum();
    }

    private int addedLines(DiffBlock block) {
        if (block == null) {
            return 0;
        }
        DiffType type = block.getType();
        if (type == null) {
            type = DiffType.MODIFY;
        }
        switch (type) {
            case ADD:
                return safeCount(block.getStartLineTo(), block.getEndLineTo());
            case MODIFY:
                return Math.max(
                        safeCount(block.getStartLineTo(), block.getEndLineTo()),
                        safeCount(block.getStartLineFrom(), block.getEndLineFrom()));
            default:
                return safeCount(block.getStartLineTo(), block.getEndLineTo());
        }
    }

    private int removedLines(DiffBlock block) {
        if (block == null) {
            return 0;
        }
        DiffType type = block.getType();
        if (type == null) {
            type = DiffType.MODIFY;
        }
        switch (type) {
            case DELETE:
                return safeCount(block.getStartLineFrom(), block.getEndLineFrom());
            case MODIFY:
                return Math.max(
                        safeCount(block.getStartLineFrom(), block.getEndLineFrom()),
                        safeCount(block.getStartLineTo(), block.getEndLineTo()));
            default:
                return safeCount(block.getStartLineFrom(), block.getEndLineFrom());
        }
    }

    private int safeCount(int start, int end) {
        if (start <= 0 || end < start) {
            return 0;
        }
        return end - start + 1;
    }

    private String concatenate(DiffFile file, java.util.function.Function<DiffBlock, String> extractor) {
        if (file == null || CollectionUtils.isEmpty(file.getBlocks())) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (DiffBlock block : file.getBlocks()) {
            String content = extractor.apply(block);
            String cleaned = sanitize(content);
            if (StringUtils.hasText(cleaned)) {
                lines.add(cleaned);
            }
        }
        return String.join(System.lineSeparator(), lines);
    }

    private double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private void enableWorkingTree(DiffSummary summary) {
        if (summary == null || summary.getRepoConfig() == null) {
            return;
        }
        summary.getRepoConfig().setIncludeWorkingTree(true);
    }

    private String sanitize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String cleaned = value
                .replace(UTF8_BOM, "")
                .replace(String.valueOf(UTF8_BOM_CHAR_1), "")
                .replace(String.valueOf(UTF8_BOM_CHAR_2), "")
                .replace(String.valueOf(UTF8_BOM_CHAR_3), "");
        return cleaned.trim();
    }
}
