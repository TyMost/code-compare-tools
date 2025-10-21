package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.api.dto.GitComparisonFileView;
import com.example.codecompare.rebuild.api.dto.GitComparisonProjectView;
import com.example.codecompare.rebuild.api.dto.GitComparisonResponseView;
import com.example.codecompare.rebuild.api.dto.DualIncrementalComparisonView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffBlockView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffFileCategoryView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffFileDetailView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffFileItemView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffGitDiffView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffGitHunkView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffGitSnapshotView;
import com.example.codecompare.rebuild.api.dto.IncrementalDiffOverviewView;
import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.scanning.compare.DualIncrementalComparisonCalculator;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry.ProjectRootDescriptor;
import com.example.codecompare.rebuild.repository.BlockDecisionRepository;
import com.example.codecompare.rebuild.repository.DiffSnapshotRepository;
import com.example.codecompare.rebuild.repository.model.BlockDecisionRecord;
import com.example.codecompare.rebuild.repository.model.BlockDecisionSnapshot;
import com.example.codecompare.rebuild.repository.model.DiffSnapshotDocument;
import com.example.codecompare.rebuild.repository.model.PageRequest;
import com.example.codecompare.rebuild.repository.model.PageResult;
import com.example.codecompare.rebuild.stats.CategoryLabelResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggregates incremental diff resources for REST endpoints.
 */
@Component
public class IncrementalDiffFacade {

    private static final Logger log = LoggerFactory.getLogger(IncrementalDiffFacade.class);
    private static final int DEFAULT_PAGE_SIZE = 500;

    private final FileScanService fileScanService;
    private final ScanResultRepository scanResultRepository;
    private final DiffSnapshotRepository diffSnapshotRepository;
    private final BlockDecisionRepository blockDecisionRepository;
    private final ProjectRootRegistry projectRootRegistry;
    private final CategoryLabelResolver categoryLabelResolver;
    private final DualIncrementalComparisonCalculator dualIncrementalComparisonCalculator;

    public IncrementalDiffFacade(FileScanService fileScanService,
                                 ScanResultRepository scanResultRepository,
                                 DiffSnapshotRepository diffSnapshotRepository,
                                 BlockDecisionRepository blockDecisionRepository,
                                 ProjectRootRegistry projectRootRegistry,
                                 CategoryLabelResolver categoryLabelResolver,
                                 DualIncrementalComparisonCalculator dualIncrementalComparisonCalculator) {
        this.fileScanService = fileScanService;
        this.scanResultRepository = scanResultRepository;
        this.diffSnapshotRepository = diffSnapshotRepository;
        this.blockDecisionRepository = blockDecisionRepository;
        this.projectRootRegistry = projectRootRegistry;
        this.categoryLabelResolver = categoryLabelResolver;
        this.dualIncrementalComparisonCalculator = dualIncrementalComparisonCalculator;
    }

    public GitComparisonResponseView compareProjects(String sourceProjectKey,
                                                     String targetProjectKey,
                                                     boolean refresh) {
        if (!StringUtils.hasText(sourceProjectKey)) {
            throw new IllegalArgumentException("sourceProjectKey must not be empty");
        }
        if (!StringUtils.hasText(targetProjectKey)) {
            throw new IllegalArgumentException("targetProjectKey must not be empty");
        }
        IncrementalDiffOverviewView sourceOverview = loadOverview(sourceProjectKey, refresh);
        IncrementalDiffOverviewView targetOverview = loadOverview(targetProjectKey, refresh);

        GitComparisonProjectView sourceProject = GitComparisonProjectView.builder()
                .projectCode(sourceOverview.getProjectCode())
                .generatedAt(sourceOverview.getGeneratedAt())
                .projectRoots(sourceOverview.getProjectRoots())
                .baseCommits(sourceOverview.getBaseCommits())
                .latestCommits(sourceOverview.getLatestCommits())
                .build();
        GitComparisonProjectView targetProject = GitComparisonProjectView.builder()
                .projectCode(targetOverview.getProjectCode())
                .generatedAt(targetOverview.getGeneratedAt())
                .projectRoots(targetOverview.getProjectRoots())
                .baseCommits(targetOverview.getBaseCommits())
                .latestCommits(targetOverview.getLatestCommits())
                .build();

        Set<String> filePaths = new LinkedHashSet<>();
        if (!CollectionUtils.isEmpty(sourceOverview.getFiles())) {
            sourceOverview.getFiles().forEach(item -> filePaths.add(item.getFilePath()));
        }
        if (!CollectionUtils.isEmpty(targetOverview.getFiles())) {
            targetOverview.getFiles().forEach(item -> filePaths.add(item.getFilePath()));
        }

        List<GitComparisonFileView> files = new ArrayList<>();
        for (String path : filePaths) {
            IncrementalDiffFileDetailView sourceDetail = loadFileDetail(sourceOverview.getProjectCode(), path);
            IncrementalDiffFileDetailView targetDetail = loadFileDetail(targetOverview.getProjectCode(), path);
            DualIncrementalComparisonView dualComparison = dualIncrementalComparisonCalculator.compare(sourceDetail, targetDetail)
                    .orElse(null);
            GitComparisonFileView view = GitComparisonFileView.builder()
                    .filePath(path)
                    .source(sourceDetail)
                    .target(targetDetail)
                    .dualComparison(dualComparison)
                    .build();
            files.add(view);
        }

        files.sort(Comparator.comparing(GitComparisonFileView::getFilePath, String::compareToIgnoreCase));

        return GitComparisonResponseView.builder()
                .sourceProject(sourceProject)
                .targetProject(targetProject)
                .files(files)
                .build();
    }

    public IncrementalDiffOverviewView loadOverview(String requestedProjectCode, boolean refresh) {
        String comparisonId = resolveComparisonId(requestedProjectCode);
        ScanSummary summary = refresh ? triggerIncrementalScan(comparisonId) : findLatestSummary(comparisonId);
        if (summary == null) {
            log.info("未找到增量扫描概要，projectCode={}", comparisonId);
            return IncrementalDiffOverviewView.builder()
                    .projectCode(comparisonId)
                    .files(Collections.emptyList())
                    .filesChanged(0)
                    .totalBlocks(0)
                    .totalUnlabeledBlocks(0)
                    .averageSimilarity(0d)
                    .baseCommits(Collections.emptyMap())
                    .latestCommits(Collections.emptyMap())
                    .build();
        }
        String effectiveProjectCode = summary.getProjectCode();
        PageResult<DiffSnapshotDocument> page = diffSnapshotRepository.findRecent(
                effectiveProjectCode,
                PageRequest.of(0, DEFAULT_PAGE_SIZE));
        List<IncrementalDiffFileItemView> files = page.getItems().stream()
                .map(document -> toFileItemView(effectiveProjectCode, document))
                .collect(Collectors.toList());

        int totalBlocks = files.stream().mapToInt(IncrementalDiffFileItemView::getTotalBlocks).sum();
        int totalUnlabeled = files.stream().mapToInt(IncrementalDiffFileItemView::getUnlabeledBlocks).sum();
        double averageSimilarity = files.isEmpty()
                ? 0d
                : files.stream().mapToDouble(IncrementalDiffFileItemView::getAverageSimilarity).average().orElse(0d);
        Map<String, String> projectRoots = resolveProjectRoots(summary);

        return IncrementalDiffOverviewView.builder()
                .projectCode(effectiveProjectCode)
                .generatedAt(summary.getCompletedAt())
                .projectRoots(projectRoots)
                .baseCommits(summary.getBaseCommits())
                .latestCommits(summary.getLatestCommits())
                .filesChanged(files.size())
                .totalBlocks(totalBlocks)
                .totalUnlabeledBlocks(totalUnlabeled)
                .averageSimilarity(averageSimilarity)
                .files(files)
                .build();
    }

    public IncrementalDiffFileDetailView loadFileDetail(String requestedProjectCode, String rawPath) {
        String normalizedPath = normalizeFilePath(rawPath);
        if (!StringUtils.hasText(normalizedPath)) {
            return null;
        }
        String comparisonId = resolveComparisonId(requestedProjectCode);
        Optional<DiffSnapshotDocument> snapshotOptional = diffSnapshotRepository.findLatest(comparisonId, normalizedPath);
        Optional<BlockDecisionSnapshot> decisionOptional = blockDecisionRepository.findLatest(comparisonId, normalizedPath);
        if (!snapshotOptional.isPresent() && !decisionOptional.isPresent()) {
            return null;
        }

        DiffSnapshotDocument snapshot = snapshotOptional.orElse(null);
        BlockDecisionSnapshot decisionSnapshot = decisionOptional.orElse(null);

        Map<String, Integer> labelCounts = snapshot == null ? Collections.emptyMap() : snapshot.getLabelCounts();
        Map<String, Integer> lineCounts = snapshot == null ? Collections.emptyMap() : snapshot.getLineCounts();
        int totalBlocks = snapshot == null ? 0 : snapshot.getTotalBlocks();
        int unlabeledBlocks = snapshot == null ? 0 : snapshot.getUnlabeledBlocks();
        double averageSimilarity = snapshot == null ? 0d : snapshot.getAverageSimilarity();
        int totalLineCount = snapshot == null ? 0 : snapshot.getTotalLineCount();
        Instant generatedAt = snapshot == null ? null : snapshot.getGeneratedAt();

        List<IncrementalDiffBlockView> blockViews = decisionSnapshot == null
                ? Collections.emptyList()
                : decisionSnapshot.getRecords().stream()
                .map(this::toBlockView)
                .collect(Collectors.toList());

        List<IncrementalDiffGitSnapshotView> gitSnapshots = fromGitSnapshots(snapshot == null ? null : snapshot.getGitSnapshots());
        if (gitSnapshots.isEmpty() && decisionSnapshot != null) {
            gitSnapshots = extractGitSnapshots(decisionSnapshot.getRecords());
        }

        IncrementalDiffGitDiffView gitDiff = fromGitDiff(snapshot == null ? null : snapshot.getGitDiff());
        if (gitDiff == null && decisionSnapshot != null) {
            gitDiff = extractGitDiff(decisionSnapshot.getRecords());
        }

        String effectiveProjectCode = snapshot != null ? snapshot.getComparisonId() : decisionSnapshot.getComparisonId();

        return IncrementalDiffFileDetailView.builder()
                .projectCode(effectiveProjectCode)
                .filePath(normalizedPath)
                .gitSnapshots(gitSnapshots)
                .gitDiff(gitDiff)
                .blocks(blockViews)
                .labelCounts(labelCounts)
                .lineCounts(lineCounts)
                .totalBlocks(totalBlocks)
                .unlabeledBlocks(unlabeledBlocks)
                .averageSimilarity(averageSimilarity)
                .totalLineCount(totalLineCount)
                .generatedAt(generatedAt)
                .build();
    }

    private IncrementalDiffFileItemView toFileItemView(String comparisonId, DiffSnapshotDocument document) {
        return IncrementalDiffFileItemView.builder()
                .filePath(document.getFilePath())
                .averageSimilarity(document.getAverageSimilarity())
                .totalBlocks(document.getTotalBlocks())
                .unlabeledBlocks(document.getUnlabeledBlocks())
                .totalLineCount(document.getTotalLineCount())
                .generatedAt(document.getGeneratedAt())
                .categories(toCategoryViews(document.getLabelCounts()))
                .build();
    }

    private IncrementalDiffBlockView toBlockView(BlockDecisionRecord record) {
        Map<String, Object> metadata = record.getMetadata() == null
                ? Collections.emptyMap()
                : new LinkedHashMap<>(record.getMetadata());
        BlockDiff diff = record.getDiff();
        if (diff != null && !CollectionUtils.isEmpty(diff.getMetadata()) && metadata.isEmpty()) {
            metadata = new LinkedHashMap<>(diff.getMetadata());
        }
        return IncrementalDiffBlockView.builder()
                .blockId(record.getBlockIdentifier())
                .status(record.getStatus())
                .riskLevel(record.getRiskLevel())
                .metadata(metadata)
                .diff(diff)
                .analyzedAt(record.getAnalyzedAt())
                .build();
    }

    private List<IncrementalDiffGitSnapshotView> extractGitSnapshots(List<BlockDecisionRecord> records) {
        if (CollectionUtils.isEmpty(records)) {
            return Collections.emptyList();
        }
        for (BlockDecisionRecord record : records) {
            List<IncrementalDiffGitSnapshotView> snapshots = fromGitSnapshots(record.getMetadata());
            if (!snapshots.isEmpty()) {
                return snapshots;
            }
            BlockDiff diff = record.getDiff();
            if (diff != null) {
                snapshots = fromGitSnapshots(diff.getMetadata());
                if (!snapshots.isEmpty()) {
                    return snapshots;
                }
            }
        }
        return Collections.emptyList();
    }

    private IncrementalDiffGitDiffView extractGitDiff(List<BlockDecisionRecord> records) {
        if (CollectionUtils.isEmpty(records)) {
            return null;
        }
        for (BlockDecisionRecord record : records) {
            IncrementalDiffGitDiffView diffView = fromGitDiff(record.getMetadata());
            if (diffView != null) {
                return diffView;
            }
            BlockDiff diff = record.getDiff();
            if (diff != null) {
                diffView = fromGitDiff(diff.getMetadata());
                if (diffView != null) {
                    return diffView;
                }
            }
        }
        return null;
    }

    private List<IncrementalDiffGitSnapshotView> fromGitSnapshots(Object value) {
        Object source = value;
        if (value instanceof Map) {
            source = ((Map<?, ?>) value).get("gitSnapshots");
        }
        if (!(source instanceof List)) {
            return Collections.emptyList();
        }
        List<?> rawList = (List<?>) source;
        if (rawList.isEmpty()) {
            return Collections.emptyList();
        }
        List<IncrementalDiffGitSnapshotView> result = new ArrayList<>();
        for (Object element : rawList) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) element;
            IncrementalDiffGitSnapshotView snapshot = IncrementalDiffGitSnapshotView.builder()
                    .projectCode(asString(map.get("projectCode")))
                    .projectType(asString(map.get("projectType")))
                    .gitCommitId(asString(map.get("gitCommitId")))
                    .gitAuthor(asString(map.get("gitAuthor")))
                    .gitTimestamp(asInstant(map.get("gitTimestamp")))
                    .gitBranch(asString(map.get("gitBranch")))
                    .workingTreeChange(asBoolean(map.get("workingTreeChange")))
                    .changeType(asString(map.get("changeType")))
                    .previousPath(asString(map.get("previousPath")))
                    .scannedAt(asInstant(map.get("scannedAt")))
                    .build();
            result.add(snapshot);
        }
        return result;
    }

    private IncrementalDiffGitDiffView fromGitDiff(Object value) {
        Object source = value;
        if (value instanceof Map) {
            source = ((Map<?, ?>) value).get("gitDiff");
        }
        if (!(source instanceof Map)) {
            return null;
        }
        Map<?, ?> map = (Map<?, ?>) source;
        List<IncrementalDiffGitHunkView> hunks = fromGitHunks(map.get("hunks"));
        return IncrementalDiffGitDiffView.builder()
                .path(asString(map.get("path")))
                .previousPath(asString(map.get("previousPath")))
                .changeType(asString(map.get("changeType")))
                .truncated(asBoolean(map.get("truncated")))
                .totalBytes(asLong(map.get("totalBytes")))
                .sameLineCount(asInt(map.get("sameLineCount")))
                .changedLineCount(asInt(map.get("changedLineCount")))
                .weightedBlockLineCount(asInt(map.get("weightedBlockLineCount")))
                .fileSimilarity(asDouble(map.get("fileSimilarity")))
                .hunks(hunks)
                .build();
    }

    private List<IncrementalDiffGitHunkView> fromGitHunks(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<?> rawList = (List<?>) value;
        if (rawList.isEmpty()) {
            return Collections.emptyList();
        }
        List<IncrementalDiffGitHunkView> result = new ArrayList<>();
        for (Object element : rawList) {
            if (!(element instanceof Map)) {
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) element;
            IncrementalDiffGitHunkView hunk = IncrementalDiffGitHunkView.builder()
                    .oldStartLine(asInt(map.get("oldStartLine")))
                    .oldLineCount(asInt(map.get("oldLineCount")))
                    .newStartLine(asInt(map.get("newStartLine")))
                    .newLineCount(asInt(map.get("newLineCount")))
                    .lines(map.get("lines") instanceof List ? castLines((List<?>) map.get("lines")) : Collections.emptyList())
                    .byteSize(asLong(map.get("byteSize")))
                    .build();
            result.add(hunk);
        }
        return result;
    }

    private Map<String, String> resolveProjectRoots(ScanSummary summary) {
        Map<String, String> roots = new LinkedHashMap<>();
        if (summary == null) {
            return roots;
        }
        Set<String> keys = new LinkedHashSet<>();
        Map<String, String> base = summary.getBaseCommits();
        if (base != null) {
            keys.addAll(base.keySet());
        }
        Map<String, String> latest = summary.getLatestCommits();
        if (latest != null) {
            keys.addAll(latest.keySet());
        }
        if (keys.isEmpty()) {
            for (ProjectRootDescriptor descriptor : projectRootRegistry.getDescriptors()) {
                String key = StringUtils.hasText(descriptor.getCode())
                        ? descriptor.getCode()
                        : descriptor.getType().name();
                roots.putIfAbsent(key, descriptor.getPath().toString());
            }
            return roots;
        }
        for (String key : keys) {
            String path = resolveRootPathByKey(key);
            if (StringUtils.hasText(path)) {
                roots.put(key, path);
            }
        }
        if (roots.isEmpty()) {
            for (ProjectRootDescriptor descriptor : projectRootRegistry.getDescriptors()) {
                String key = StringUtils.hasText(descriptor.getCode())
                        ? descriptor.getCode()
                        : descriptor.getType().name();
                roots.putIfAbsent(key, descriptor.getPath().toString());
            }
        }
        return roots;
    }

    private String resolveRootPathByKey(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        Optional<ProjectRootDescriptor> descriptor = projectRootRegistry.findByCode(key);
        if (descriptor.isPresent()) {
            return descriptor.get().getPath().toString();
        }
        try {
            ProjectRootRegistry.ProjectRootType type = ProjectRootRegistry.ProjectRootType.valueOf(key);
            List<ProjectRootDescriptor> candidates = type == ProjectRootRegistry.ProjectRootType.SOURCE
                    ? projectRootRegistry.getSources()
                    : projectRootRegistry.getTargets();
            if (!CollectionUtils.isEmpty(candidates)) {
                return candidates.get(0).getPath().toString();
            }
        } catch (IllegalArgumentException ignored) {
            // key is not a known enum constant
        }
        return null;
    }

    private ScanSummary triggerIncrementalScan(String requestedProjectCode) {
        try {
            ProjectScanRequest request = buildScanRequest(requestedProjectCode);
            return fileScanService.scanIncremental(request);
        } catch (Exception ex) {
            log.warn("增量扫描失败，projectCode={}，尝试返回最近结果", requestedProjectCode, ex);
            return findLatestSummary(requestedProjectCode);
        }
    }

    private ProjectScanRequest buildScanRequest(String projectCode) {
        if (!StringUtils.hasText(projectCode)) {
            return null;
        }
        Optional<ProjectRootDescriptor> descriptor = projectRootRegistry.findByCode(projectCode);
        if (descriptor.isPresent()) {
            ProjectScanRequest.Builder builder = ProjectScanRequest.builder()
                    .projectCode(projectCode)
                    .skipHidden(true);
            builder.addRoot(descriptor.get().getPath());
            return builder.build();
        }
        return null;
    }

    private ScanSummary findLatestSummary(String projectCode) {
        if (StringUtils.hasText(projectCode)) {
            Optional<ScanSummary> summary = scanResultRepository.findLatestSummary(projectCode);
            if (summary.isPresent()) {
                return summary.get();
            }
        }
        String fallback = defaultComparisonId();
        return scanResultRepository.findLatestSummary(fallback).orElse(null);
    }

    private String resolveComparisonId(String requestedProjectCode) {
        if (StringUtils.hasText(requestedProjectCode)) {
            return requestedProjectCode.trim();
        }
        Optional<String> existing = scanResultRepository.findLatestSummary(defaultComparisonId())
                .map(ScanSummary::getProjectCode);
        return existing.orElse(defaultComparisonId());
    }

    private String defaultComparisonId() {
        List<ProjectRootDescriptor> descriptors = projectRootRegistry.getDescriptors();
        if (!CollectionUtils.isEmpty(descriptors)) {
            ProjectRootDescriptor descriptor = descriptors.get(0);
            if (StringUtils.hasText(descriptor.getCode())) {
                return descriptor.getCode();
            }
            if (descriptor.getPath() != null && descriptor.getPath().getFileName() != null) {
                return descriptor.getPath().getFileName().toString();
            }
        }
        return "default-project";
    }

    private String normalizeFilePath(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.contains("..")) {
            return null;
        }
        return trimmed.replace('\\', '/');
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
    }

    private Instant asInstant(Object value) {
        if (value instanceof Instant) {
            return (Instant) value;
        }
        if (value instanceof String) {
            try {
                return Instant.parse((String) value);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private int asInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private Double asDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private List<IncrementalDiffFileCategoryView> toCategoryViews(Map<String, Integer> labelCounts) {
        if (CollectionUtils.isEmpty(labelCounts)) {
            return Collections.emptyList();
        }
        return labelCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(entry -> IncrementalDiffFileCategoryView.builder()
                        .key(entry.getKey())
                        .label(categoryLabelResolver.resolve(entry.getKey()))
                        .color(categoryLabelResolver.resolveColor(entry.getKey()))
                        .count(entry.getValue() == null ? 0 : entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }

    private List<String> castLines(List<?> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(values.size());
        for (Object value : values) {
            result.add(asString(value));
        }
        return result;
    }
}
