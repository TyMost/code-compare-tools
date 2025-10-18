package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.core.support.IgnorePatternMatcher;
import com.example.codecompare.rebuild.repository.model.FileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 全量扫描服务，负责两阶段扫描：目录遍历 & 指纹计算。
 */
public class FullProjectScanService {

    private static final Logger log = LoggerFactory.getLogger(FullProjectScanService.class);

    private final FileFingerprintCalculator fingerprintCalculator;
    private final ScanResultRepository scanResultRepository;
    private final ScanProperties scanProperties;
    private final Clock clock;
    private final IgnoredArtifactCleaner ignoredArtifactCleaner;

    public FullProjectScanService(FileFingerprintCalculator fingerprintCalculator,
                                  ScanResultRepository scanResultRepository,
                                  ScanProperties scanProperties,
                                  Clock clock,
                                  IgnoredArtifactCleaner ignoredArtifactCleaner) {
        this.fingerprintCalculator = fingerprintCalculator;
        this.scanResultRepository = scanResultRepository;
        this.scanProperties = scanProperties;
        this.clock = clock;
        this.ignoredArtifactCleaner = ignoredArtifactCleaner;
    }

    public FullProjectScanResult scan(ProjectScanRequest request) {
        Assert.notNull(request, "ProjectScanRequest 不能为空");
        String projectCode = request.getProjectCode();
        Instant startedAt = request.getRequestedAt() == null ? clock.instant() : request.getRequestedAt();
        List<String> warnings = new CopyOnWriteArrayList<>();
        List<FileRecord> allRecords = new ArrayList<>();

        List<String> ignoreGlobs = mergeIgnoreGlobs(request);
        ignoredArtifactCleaner.purge(projectCode, ignoreGlobs);
        log.info("开始执行全量扫描，项目：{}，根目录数量：{}，忽略规则：{}", projectCode,
                request.getProjectRoots().size(), ignoreGlobs);

        for (Path root : request.getProjectRoots()) {
            if (!Files.exists(root)) {
                String warning = String.format(Locale.ROOT, "扫描根目录不存在，已跳过：%s", root);
                log.warn(warning);
                warnings.add(warning);
                continue;
            }
            List<Path> candidates = collectCandidates(root, request, ignoreGlobs, warnings);
            log.info("根目录 {} 收集候选文件 {} 个", root, candidates.size());
            List<FileRecord> records = fingerprintCalculator.calculate(projectCode, root, candidates, startedAt);
            allRecords.addAll(records);
        }

        List<FileRecord> persisted = persistInBatches(allRecords);
        Instant completedAt = clock.instant();
        Duration duration = Duration.between(startedAt, completedAt);

        ScanSummary summary = ScanSummary.builder()
                .projectCode(projectCode)
                .scannedRoots(request.getProjectRoots().stream()
                        .map(path -> path.toAbsolutePath().normalize().toString())
                        .collect(Collectors.toList()))
                .filesScanned(persisted.size())
                .totalBytes(persisted.stream().mapToLong(FileRecord::getSizeInBytes).sum())
                .startedAt(startedAt)
                .completedAt(completedAt)
                .duration(duration)
                .warnings(warnings)
                .build();
        scanResultRepository.saveSummary(summary);
        return new FullProjectScanResult(summary, persisted);
    }

    private List<String> mergeIgnoreGlobs(ProjectScanRequest request) {
        List<String> merged = new ArrayList<>(scanProperties.getIgnoreGlobs());
        merged.addAll(request.getIgnoreGlobs());
        return merged.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(glob -> !glob.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Path> collectCandidates(Path root,
                                         ProjectScanRequest request,
                                         List<String> ignoreGlobs,
                                         List<String> warnings) {
        List<Path> candidates = new ArrayList<>();
        Set<FileVisitOption> options = scanProperties.isFollowSymlinks()
                ? EnumSet.of(FileVisitOption.FOLLOW_LINKS)
                : EnumSet.noneOf(FileVisitOption.class);
        IgnorePatternMatcher ignoreMatcher = IgnorePatternMatcher.from(ignoreGlobs);
        try {
            Files.walkFileTree(root, options, Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(root) && ignore(ignoreMatcher, root, dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (request.isSkipHidden() && Files.isHidden(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isRegularFile()) {
                        if (request.isSkipHidden() && Files.isHidden(file)) {
                            return FileVisitResult.CONTINUE;
                        }
                        if (!ignore(ignoreMatcher, root, file)) {
                            candidates.add(file);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    String warning = String.format(Locale.ROOT, "访问文件失败：%s，错误：%s", file, exc.getMessage());
                    log.warn(warning);
                    warnings.add(warning);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            String warning = String.format(Locale.ROOT, "遍历目录失败：%s，错误：%s", root, ex.getMessage());
            log.warn(warning);
            warnings.add(warning);
        }
        return candidates;
    }

    private boolean ignore(IgnorePatternMatcher matcher, Path root, Path path) {
        return matcher != null && !matcher.isEmpty() && matcher.matches(root, path);
    }

    private List<FileRecord> persistInBatches(List<FileRecord> records) {
        if (records.isEmpty()) {
            return records;
        }
        int batchSize = Math.max(1, scanProperties.getBatchSize());
        List<FileRecord> persisted = new ArrayList<>(records.size());
        int batches = 0;
        for (int i = 0; i < records.size(); i += batchSize) {
            int toIndex = Math.min(i + batchSize, records.size());
            List<FileRecord> batch = records.subList(i, toIndex);
            persisted.addAll(scanResultRepository.saveAll(batch));
            batches++;
        }
        log.info("完成扫描结果持久化，批次数：{}，总文件数：{}", Math.max(1, batches), persisted.size());
        return persisted;
    }

    public static final class FullProjectScanResult {
        private final ScanSummary summary;
        private final List<FileRecord> records;

        private FullProjectScanResult(ScanSummary summary, List<FileRecord> records) {
            this.summary = summary;
            this.records = records;
        }

        public ScanSummary getSummary() {
            return summary;
        }

        public List<FileRecord> getRecords() {
            return records;
        }
    }
}
