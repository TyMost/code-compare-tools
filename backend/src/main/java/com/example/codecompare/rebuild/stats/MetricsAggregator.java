package com.example.codecompare.rebuild.stats;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry.ProjectRootDescriptor;
import com.example.codecompare.rebuild.scanning.BlockLabelConstants;
import com.example.codecompare.rebuild.repository.BlockDecisionRepository;
import com.example.codecompare.rebuild.repository.DiffSnapshotRepository;
import com.example.codecompare.rebuild.repository.model.BlockDecisionRecord;
import com.example.codecompare.rebuild.repository.model.BlockDecisionSnapshot;
import com.example.codecompare.rebuild.repository.model.DiffSnapshotDocument;
import com.example.codecompare.rebuild.repository.model.PageRequest;
import com.example.codecompare.rebuild.repository.model.PageResult;
import com.example.codecompare.rebuild.scanning.ScanCompletedEvent;
import com.example.codecompare.rebuild.scanning.ScanResultRepository;
import com.example.codecompare.rebuild.scanning.ScanSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;


/**
 * 指标聚合核心类，订阅扫描事件并提供统计查询能力。
 */
@Component
public class MetricsAggregator implements ApplicationListener<ScanCompletedEvent> {

    private static final Logger log = LoggerFactory.getLogger(MetricsAggregator.class);

    private final ScanResultRepository scanResultRepository;
    private final DiffSnapshotRepository diffSnapshotRepository;
    private final BlockDecisionRepository blockDecisionRepository;
    private final Clock clock;
    private final CategoryLabelResolver categoryLabelResolver;
    private final ProjectRootRegistry projectRootRegistry;
    private final ConcurrentMap<String, ScanSummary> summaryCache = new ConcurrentHashMap<>();
    private static final Comparator<BlockItem> BLOCK_ITEM_COMPARATOR =
            Comparator.comparing(BlockItem::getFilePath, Comparator.nullsLast(String::compareTo))
                    .thenComparingInt(BlockItem::getStartLine)
                    .thenComparingInt(BlockItem::getEndLine)
                    .thenComparing(BlockItem::getId);

    public MetricsAggregator(ScanResultRepository scanResultRepository,
                             DiffSnapshotRepository diffSnapshotRepository,
                             BlockDecisionRepository blockDecisionRepository,
                             Clock clock,
                             CategoryLabelResolver categoryLabelResolver,
                             com.example.codecompare.rebuild.core.support.ProjectRootRegistry projectRootRegistry) {
        this.scanResultRepository = scanResultRepository;
        this.diffSnapshotRepository = diffSnapshotRepository;
        this.blockDecisionRepository = blockDecisionRepository;
        this.clock = clock;
        this.categoryLabelResolver = categoryLabelResolver;
        this.projectRootRegistry = projectRootRegistry;
    }

    @Override
    public void onApplicationEvent(ScanCompletedEvent event) {
        if (event == null || event.getSummary() == null) {
            return;
        }
        ScanSummary summary = event.getSummary();
        summaryCache.put(summary.getProjectCode(), summary);
        log.info("接收到扫描完成事件，已更新缓存项目：{}", summary.getProjectCode());
    }

    public ScanSummary latestSummary(String projectCode) {
        String key = StringUtils.hasText(projectCode) ? projectCode : defaultComparisonId();
        return summaryCache.computeIfAbsent(key, this::loadSummary);
    }

    public CategoryMetrics categoryMetrics(String comparisonId) {
        String key = StringUtils.hasText(comparisonId) ? comparisonId : defaultComparisonId();
        Map<String, Long> labelCounts = new LinkedHashMap<>();
        Map<String, Long> lineCounts = new LinkedHashMap<>();
        long totalBlocks = 0L;
        long totalLinesFromSnapshots = 0L;
        long sourceChangedLines = 0L;
        long targetChangedLines = 0L;

        int pageIndex = 0;
        final int pageSize = 200;
        while (true) {
            PageRequest request = PageRequest.of(pageIndex, pageSize);
            PageResult<DiffSnapshotDocument> page = diffSnapshotRepository.findRecent(key, request);
            List<DiffSnapshotDocument> documents = page.getItems();
            if (documents.isEmpty()) {
                break;
            }
            for (DiffSnapshotDocument document : documents) {
                totalBlocks += document.getTotalBlocks();
                mergeCounts(labelCounts, document.getLabelCounts());
                mergeCounts(lineCounts, document.getLineCounts());
                GitLineTotals gitTotals = extractGitLineTotals(document);
                sourceChangedLines += gitTotals.getRemovedLines();
                targetChangedLines += gitTotals.getAddedLines();
                long documentLineSum = document.getLineCounts() == null
                        ? 0L
                        : document.getLineCounts().values().stream()
                        .filter(Objects::nonNull)
                        .mapToLong(Integer::longValue)
                        .sum();
                int documentTotalLines = document.getTotalLineCount();
                if (documentTotalLines > 0) {
                    if (documentTotalLines > documentLineSum) {
                        int migratedLines = documentTotalLines - (int) documentLineSum;
                        mergeCounts(labelCounts, Collections.singletonMap(BlockLabelConstants.STATUS_NO_RULES, 1));
                        mergeCounts(lineCounts, Collections.singletonMap(BlockLabelConstants.STATUS_NO_RULES, migratedLines));
                        documentLineSum += migratedLines;
                    }
                    totalLinesFromSnapshots += documentTotalLines;
                } else {
                    totalLinesFromSnapshots += documentLineSum;
                }
            }
            if (!page.hasNext()) {
                break;
            }
            pageIndex++;
        }

        long totalLines = totalLinesFromSnapshots > 0
                ? totalLinesFromSnapshots
                : lineCounts.values().stream().mapToLong(Long::longValue).sum();
        Set<String> categoryKeys = new LinkedHashSet<>();
        categoryKeys.addAll(labelCounts.keySet());
        categoryKeys.addAll(lineCounts.keySet());

        List<CategoryCount> categories = categoryKeys.stream()
                .sorted((left, right) -> Long.compare(
                        lineCounts.getOrDefault(right, labelCounts.getOrDefault(right, 0L)),
                        lineCounts.getOrDefault(left, labelCounts.getOrDefault(left, 0L))))
                .map(categoryKey -> new CategoryCount(
                        categoryKey,
                        categoryLabelResolver.resolve(categoryKey),
                        labelCounts.getOrDefault(categoryKey, 0L),
                        lineCounts.getOrDefault(categoryKey, 0L)))
                .collect(Collectors.toList());

        return new CategoryMetrics(categories, totalLines, totalBlocks, sourceChangedLines, targetChangedLines);
    }

    public CodeBlockPage loadCodeBlocks(CodeBlockQuery query) {
        String comparisonId = StringUtils.hasText(query.getComparisonId())
                ? query.getComparisonId()
                : StringUtils.hasText(query.getProjectKey()) ? query.getProjectKey() : defaultComparisonId();

        List<BlockItem> allBlocks = loadAllBlockItems(comparisonId);
        List<BlockItem> filtered = filterBlockItems(allBlocks, query);

        Map<String, List<BlockItem>> groupedByFile = new LinkedHashMap<>();
        for (BlockItem item : filtered) {
            String key = StringUtils.hasText(item.getFilePath())
                    ? item.getFilePath()
                    : "__MISSING_FILE__";
            groupedByFile.computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(item);
        }
        List<List<BlockItem>> fileBuckets = groupedByFile.values().stream()
                .peek(bucket -> bucket.sort(Comparator.comparingInt(BlockItem::getStartLine)))
                .collect(Collectors.toList());
        long totalFiles = fileBuckets.size();
        int size = query.getSize();
        int page = Math.max(1, query.getPage());
        int fromIndex = Math.min((page - 1) * size, fileBuckets.size());
        int toIndex = Math.min(fromIndex + size, fileBuckets.size());
        List<BlockItem> pageItems = fileBuckets.subList(fromIndex, toIndex).stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
        int totalPages = totalFiles == 0 ? 0 : (int) Math.ceil(totalFiles / (double) size);

        long totalLines = filtered.stream()
                .map(BlockItem::getLineCount)
                .filter(Objects::nonNull)
                .mapToLong(Integer::longValue)
                .sum();

        Set<String> categoryKeys = filtered.stream()
                .flatMap(item -> item.getCategories().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new CodeBlockPage(pageItems, page, size, totalFiles, totalPages, categoryKeys, filtered.size(), totalLines);
    }

    private boolean filterByCategory(BlockItem item, Set<String> filter) {
        if (CollectionUtils.isEmpty(filter)) {
            return true;
        }
        Set<String> categories = item.getCategories().stream()
                .map(label -> label.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (String candidate : filter) {
            if (categories.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean filterByExcludedCategory(BlockItem item, Set<String> excludes) {
        if (CollectionUtils.isEmpty(excludes)) {
            return true;
        }
        Set<String> categories = item.getCategories().stream()
                .map(label -> label.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (String candidate : excludes) {
            if (categories.contains(candidate)) {
                return false;
            }
        }
        return true;
    }

    private boolean filterByFilePath(BlockItem item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return item.getFilePath() != null
                && item.getFilePath().toLowerCase(Locale.ROOT).contains(keyword);
    }

    private boolean filterByFileName(BlockItem item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String filePath = item.getFilePath();
        if (!StringUtils.hasText(filePath)) {
            return false;
        }
        String fileName = extractFileName(filePath);
        return fileName.contains(keyword);
    }

    private String extractFileName(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return "";
        }
        String normalized = filePath.replace('\\', '/');
        int index = normalized.lastIndexOf('/');
        String name = index >= 0 ? normalized.substring(index + 1) : normalized;
        return name.toLowerCase(Locale.ROOT);
    }

    private List<BlockItem> loadAllBlockItems(String comparisonId) {
        List<BlockItem> result = new ArrayList<>();
        int pageIndex = 0;
        final int pageSize = 100;
        while (true) {
            PageResult<BlockDecisionSnapshot> page = blockDecisionRepository.findHistory(
                    StringUtils.hasText(comparisonId) ? comparisonId : defaultComparisonId(),
                    PageRequest.of(pageIndex, pageSize));
            List<BlockDecisionSnapshot> snapshots = page.getItems();
            if (snapshots.isEmpty()) {
                break;
            }
            for (BlockDecisionSnapshot snapshot : snapshots) {
                for (BlockDecisionRecord record : snapshot.getRecords()) {
                    result.add(toBlockItem(snapshot, record));
                }
            }
            if (!page.hasNext()) {
                break;
            }
            pageIndex++;
        }
        result.sort(BLOCK_ITEM_COMPARATOR);
        return result;
    }

    private List<BlockItem> filterBlockItems(List<BlockItem> source, CodeBlockQuery query) {
        if (CollectionUtils.isEmpty(source)) {
            return Collections.emptyList();
        }
        if (query == null) {
            return new ArrayList<>(source);
        }
        Set<String> categoryFilter = query.getCategories().stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> excludeFilter = query.getExcludedCategories().stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String trimmedFileFilter = StringUtils.hasText(query.getFilePath())
                ? query.getFilePath().toLowerCase(Locale.ROOT)
                : null;
        String trimmedFileNameFilter = StringUtils.hasText(query.getFileName())
                ? query.getFileName().toLowerCase(Locale.ROOT)
                : null;
        return source.stream()
                .filter(item -> filterByCategory(item, categoryFilter))
                .filter(item -> filterByExcludedCategory(item, excludeFilter))
                .filter(item -> filterByFilePath(item, trimmedFileFilter))
                .filter(item -> filterByFileName(item, trimmedFileNameFilter))
                .collect(Collectors.toList());
    }

    private BlockItem toBlockItem(BlockDecisionSnapshot snapshot, BlockDecisionRecord record) {
        List<String> categories = Collections.emptyList();
        if (record.getDiff() != null) {
            List<String> labelIds = record.getDiff().getLabelIds();
            if (!CollectionUtils.isEmpty(labelIds)) {
                categories = new ArrayList<>(labelIds);
            } else if (!CollectionUtils.isEmpty(record.getDiff().getLabels())) {
                categories = new ArrayList<>(record.getDiff().getLabels());
            }
        }
        String targetCode = record.getDiff() == null ? "" : record.getDiff().getTargetContent();
        String sourceCode = record.getDiff() == null ? "" : record.getDiff().getSourceContent();
        String snippet = StringUtils.hasText(targetCode) ? targetCode : sourceCode;
        int diffStartLine = record.getDiff() != null ? record.getDiff().getTargetStartLine() : 0;
        int diffChangedLines = record.getDiff() != null ? record.getDiff().getChangedLineCount() : 0;
        Integer fallbackStart = extractInt(record.getMetadata().get("startLine"));
        Integer fallbackEnd = extractInt(record.getMetadata().get("endLine"));
        int startLine = diffStartLine > 0 ? diffStartLine : (fallbackStart == null ? 0 : fallbackStart);
        int endLine;
        if (diffChangedLines > 0 && diffStartLine > 0) {
            endLine = startLine + diffChangedLines - 1;
        } else {
            endLine = fallbackEnd == null ? startLine : fallbackEnd;
        }
        Integer lineCount = diffChangedLines > 0 ? diffChangedLines : extractInt(record.getMetadata().get("changedLines"));
        String status = record.getStatus();
        String statusLabel = record.getRiskLevel();
        String statusColor = categoryLabelResolver.resolveColor(status);

        return new BlockItem(
                record.getId(),
                snapshot.getComparisonId(),
                snapshot.getSourceProjectCode(),
                snapshot.getTargetProjectCode(),
                snapshot.getFilePath(),
                startLine,
                endLine,
                snippet,
                sourceCode,
                targetCode,
                categories,
                status,
                statusLabel,
                statusColor,
                lineCount,
                record.getDiff(),
                snapshot.getDiffMode()
        );
    }

    private Integer extractInt(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private long toLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String && StringUtils.hasText((String) value)) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ex) {
                return 0L;
            }
        }
        return 0L;
    }

    private void mergeCounts(Map<String, Long> target, Map<String, Integer> source) {
        if (source == null) {
            return;
        }
        source.forEach((key, value) -> target.merge(key, value == null ? 0L : value.longValue(), Long::sum));
    }

    private GitLineTotals extractGitLineTotals(DiffSnapshotDocument document) {
        if (document == null) {
            return GitLineTotals.EMPTY;
        }
        Map<String, Object> gitDiffContainer = document.getGitDiff();
        if (gitDiffContainer == null || gitDiffContainer.isEmpty()) {
            return GitLineTotals.EMPTY;
        }
        Object payloadObject = gitDiffContainer.get("gitDiff");
        if (!(payloadObject instanceof Map)) {
            return GitLineTotals.EMPTY;
        }
        Map<?, ?> payload = (Map<?, ?>) payloadObject;
        long added = Math.max(0L, toLong(payload.get("addedLineCount")));
        long removed = Math.max(0L, toLong(payload.get("removedLineCount")));
        if (added == 0L && removed == 0L) {
            GitLineTotals fromHunks = computeGitTotalsFromHunks(payload.get("hunks"));
            if (!fromHunks.isEmpty()) {
                return fromHunks;
            }
        }
        return new GitLineTotals(added, removed);
    }

    private GitLineTotals computeGitTotalsFromHunks(Object hunksObject) {
        if (!(hunksObject instanceof Iterable<?>)) {
            return GitLineTotals.EMPTY;
        }
        long added = 0L;
        long removed = 0L;
        for (Object hunkObject : (Iterable<?>) hunksObject) {
            if (!(hunkObject instanceof Map)) {
                continue;
            }
            Map<?, ?> hunkMap = (Map<?, ?>) hunkObject;
            Object linesObject = hunkMap.get("lines");
            if (!(linesObject instanceof Iterable<?>)) {
                continue;
            }
            for (Object lineObject : (Iterable<?>) linesObject) {
                if (!(lineObject instanceof String)) {
                    continue;
                }
                String line = (String) lineObject;
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                char marker = line.charAt(0);
                if (marker == '+') {
                    added++;
                } else if (marker == '-') {
                    removed++;
                }
            }
        }
        if (added == 0L && removed == 0L) {
            return GitLineTotals.EMPTY;
        }
        return new GitLineTotals(added, removed);
    }

    private String defaultComparisonId() {
        ProjectRootDescriptor primarySource = projectRootRegistry.getSources().stream()
                .findFirst()
                .orElse(null);
        if (primarySource == null) {
            primarySource = projectRootRegistry.getDescriptors().stream().findFirst().orElse(null);
        }
        if (primarySource != null) {
            if (StringUtils.hasText(primarySource.getCode())) {
                return primarySource.getCode();
            }
            Path path = primarySource.getPath();
            if (path != null && path.getFileName() != null) {
                return path.getFileName().toString();
            }
        }
        return "default-project";
    }

    private ScanSummary loadSummary(String projectCode) {
        return scanResultRepository.findLatestSummary(projectCode)
                .orElse(ScanSummary.empty(projectCode, Instant.now(clock)));
    }

    public static final class CategoryMetrics {
        private final List<CategoryCount> categories;
        private final long totalLines;
        private final long totalBlocks;
        private final long gitSourceChangedLines;
        private final long gitTargetChangedLines;

        public CategoryMetrics(List<CategoryCount> categories,
                               long totalLines,
                               long totalBlocks,
                               long gitSourceChangedLines,
                               long gitTargetChangedLines) {
            this.categories = categories == null ? Collections.emptyList() : categories;
            this.totalLines = totalLines;
            this.totalBlocks = totalBlocks;
            this.gitSourceChangedLines = Math.max(0L, gitSourceChangedLines);
            this.gitTargetChangedLines = Math.max(0L, gitTargetChangedLines);
        }

        public List<CategoryCount> getCategories() {
            return categories;
        }

        public long getTotalLines() {
            return totalLines;
        }

        public long getTotalBlocks() {
            return totalBlocks;
        }

        public long getGitSourceChangedLines() {
            return gitSourceChangedLines;
        }

        public long getGitTargetChangedLines() {
            return gitTargetChangedLines;
        }
    }

    private static final class GitLineTotals {
        private static final GitLineTotals EMPTY = new GitLineTotals(0L, 0L);
        private final long addedLines;
        private final long removedLines;

        private GitLineTotals(long addedLines, long removedLines) {
            this.addedLines = Math.max(0L, addedLines);
            this.removedLines = Math.max(0L, removedLines);
        }

        private boolean isEmpty() {
            return addedLines == 0L && removedLines == 0L;
        }

        private long getAddedLines() {
            return addedLines;
        }

        private long getRemovedLines() {
            return removedLines;
        }
    }

    public static final class CategoryCount {
        private final String key;
        private final String label;
        private final long count;
        private final long lineCount;

        public CategoryCount(String key, String label, long count, long lineCount) {
            this.key = key;
            this.label = label;
            this.count = count;
            this.lineCount = lineCount;
        }

        public String getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }

        public long getCount() {
            return count;
        }

        public long getLineCount() {
            return lineCount;
        }
    }

    public static final class CodeBlockPage {
        private final List<BlockItem> items;
        private final int page;
        private final int size;
        private final long total;
        private final int totalPages;
        private final Set<String> categoryKeys;
        private final long totalBlocks;
        private final long totalLines;

        public CodeBlockPage(List<BlockItem> items,
                             int page,
                             int size,
                             long total,
                             int totalPages,
                             Set<String> categoryKeys,
                             long totalBlocks,
                             long totalLines) {
            this.items = items == null ? Collections.emptyList() : items;
            this.page = page;
            this.size = size;
            this.total = total;
            this.totalPages = totalPages;
            this.categoryKeys = categoryKeys == null ? Collections.emptySet() : categoryKeys;
            this.totalBlocks = totalBlocks;
            this.totalLines = totalLines;
        }

        public List<BlockItem> getItems() {
            return items;
        }

        public int getPage() {
            return page;
        }

        public int getSize() {
            return size;
        }

        public long getTotal() {
            return total;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public Set<String> getCategoryKeys() {
            return categoryKeys;
        }

        public long getTotalBlocks() {
            return totalBlocks;
        }

        public long getTotalLines() {
            return totalLines;
        }
    }

    public BlockItem findBlockDetail(String comparisonId, String blockId) {
        if (!StringUtils.hasText(blockId)) {
            return null;
        }
        return loadAllBlockItems(StringUtils.hasText(comparisonId) ? comparisonId : defaultComparisonId()).stream()
                .filter(item -> blockId.equals(item.getId()))
                .findFirst()
                .orElse(null);
    }

    public BlockItem findBlockDetailAcrossProjects(String blockId) {
        if (!StringUtils.hasText(blockId)) {
            return null;
        }
        Set<String> comparisonIds = blockDecisionRepository.listComparisonIds();
        for (String candidate : comparisonIds) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            BlockItem item = loadAllBlockItems(candidate).stream()
                    .filter(block -> blockId.equals(block.getId()))
                    .findFirst()
                    .orElse(null);
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    public BlockContext findBlockContext(String comparisonId, String blockId) {
        return findBlockContext(comparisonId, blockId, null);
    }

    public BlockContext findBlockContext(String comparisonId, String blockId, CodeBlockQuery query) {
        if (!StringUtils.hasText(blockId)) {
            return null;
        }
        BlockItem current = findBlockDetail(comparisonId, blockId);
        if (current == null) {
            current = findBlockDetailAcrossProjects(blockId);
        }
        if (current == null) {
            return null;
        }
        String resolvedComparisonId = StringUtils.hasText(current.getComparisonId())
                ? current.getComparisonId()
                : (StringUtils.hasText(comparisonId) ? comparisonId : defaultComparisonId());
        List<BlockItem> items = loadAllBlockItems(resolvedComparisonId);
        if (CollectionUtils.isEmpty(items)) {
            return new BlockContext(current, null, null);
        }
        List<BlockItem> filtered = query == null
                ? new ArrayList<>(items)
                : new ArrayList<>(filterBlockItems(items, query));
        if (filtered.isEmpty()) {
            filtered.add(current);
        } else if (filtered.stream().noneMatch(candidate -> blockId.equals(candidate.getId()))) {
            filtered.add(current);
        }
        filtered.sort(BLOCK_ITEM_COMPARATOR);
        BlockItem previous = null;
        BlockItem next = null;
        for (int index = 0; index < filtered.size(); index++) {
            BlockItem candidate = filtered.get(index);
            if (!blockId.equals(candidate.getId())) {
                continue;
            }
            if (index > 0) {
                previous = filtered.get(index - 1);
            }
            if (index < filtered.size() - 1) {
                next = filtered.get(index + 1);
            }
            break;
        }
        return new BlockContext(current, previous, next);
    }

    public static final class BlockContext {
        private final BlockItem current;
        private final BlockItem previous;
        private final BlockItem next;

        public BlockContext(BlockItem current, BlockItem previous, BlockItem next) {
            this.current = current;
            this.previous = previous;
            this.next = next;
        }

        public BlockItem getCurrent() {
            return current;
        }

        public BlockItem getPrevious() {
            return previous;
        }

        public BlockItem getNext() {
            return next;
        }
    }

    public static final class BlockItem {
        private final String id;
        private final String comparisonId;
        private final String sourceProjectCode;
        private final String targetProjectCode;
        private final String filePath;
        private final int startLine;
        private final int endLine;
        private final String codeSnippet;
        private final String sourceCode;
        private final String targetCode;
        private final List<String> categories;
        private final String status;
        private final String statusLabel;
        private final String statusColor;
        private final Integer lineCount;
        private final BlockDiff diff;
        private final String diffMode;

        public BlockItem(String id,
                         String comparisonId,
                         String sourceProjectCode,
                         String targetProjectCode,
                         String filePath,
                         int startLine,
                         int endLine,
                         String codeSnippet,
                         String sourceCode,
                         String targetCode,
                         List<String> categories,
                         String status,
                         String statusLabel,
                         String statusColor,
                         Integer lineCount,
                         BlockDiff diff,
                         String diffMode) {
            this.id = id;
            this.comparisonId = comparisonId;
            this.sourceProjectCode = sourceProjectCode;
            this.targetProjectCode = targetProjectCode;
            this.filePath = filePath;
            this.startLine = startLine;
            this.endLine = endLine;
            this.codeSnippet = codeSnippet;
            this.sourceCode = sourceCode == null ? "" : sourceCode;
            this.targetCode = targetCode == null ? "" : targetCode;
            this.categories = categories == null ? Collections.emptyList() : categories;
            this.status = status;
            this.statusLabel = statusLabel;
            this.statusColor = statusColor;
            this.lineCount = lineCount;
            this.diff = diff;
            this.diffMode = diffMode == null || diffMode.trim().isEmpty() ? "full" : diffMode.trim();
        }

        public String getId() {
            return id;
        }

        public String getComparisonId() {
            return comparisonId;
        }

        public String getSourceProjectCode() {
            return sourceProjectCode;
        }

        public String getTargetProjectCode() {
            return targetProjectCode;
        }

        public String getFilePath() {
            return filePath;
        }

        public int getStartLine() {
            return startLine;
        }

        public int getEndLine() {
            return endLine;
        }

        public String getCodeSnippet() {
            return codeSnippet;
        }

        public String getSourceCode() {
            return sourceCode;
        }

        public String getTargetCode() {
            return targetCode;
        }

        public List<String> getCategories() {
            return categories;
        }

        public String getStatus() {
            return status;
        }

        public String getStatusLabel() {
            return statusLabel;
        }

        public String getStatusColor() {
            return statusColor;
        }

        public Integer getLineCount() {
            return lineCount;
        }

        public BlockDiff getDiff() {
            return diff;
        }

        public String getDiffMode() {
            return diffMode;
        }
    }
}
