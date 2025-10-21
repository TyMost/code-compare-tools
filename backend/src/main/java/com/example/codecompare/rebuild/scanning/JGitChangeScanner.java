package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.example.codecompare.rebuild.repository.model.FileChangeType;
import com.example.codecompare.rebuild.repository.model.FileRecord;
import com.example.codecompare.rebuild.scanning.git.GitDiffFile;
import com.example.codecompare.rebuild.scanning.git.GitDiffHunk;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.FileHeader.PatchType;
import org.eclipse.jgit.patch.HunkHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 基于 JGit 的增量扫描实现，按配置的提交区间计算文件差异并回写摘要。
 */
public class JGitChangeScanner implements GitChangeScanner {

    private static final Logger log = LoggerFactory.getLogger(JGitChangeScanner.class);
    private static final String WORKING_TREE_COMMIT = "WORKING_TREE";

    private final ScanProperties scanProperties;
    private final ScanResultRepository scanResultRepository;
    private final ProjectRootRegistry projectRootRegistry;
    private final Clock clock;

    public JGitChangeScanner(ScanProperties scanProperties,
                              ScanResultRepository scanResultRepository,
                              ProjectRootRegistry projectRootRegistry,
                              Clock clock) {
        this.scanProperties = Objects.requireNonNull(scanProperties, "scanProperties must not be null");
        this.scanResultRepository = Objects.requireNonNull(scanResultRepository, "scanResultRepository must not be null");
        this.projectRootRegistry = Objects.requireNonNull(projectRootRegistry, "projectRootRegistry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public GitIncrementalResult scanIncremental(ProjectScanRequest request) {
        if (request == null) {
            log.warn("项目扫描请求为空，无法执行 JGit 增量扫描");
            return GitIncrementalResult.empty("default");
        }
        List<Path> roots = request.getProjectRoots();
        if (CollectionUtils.isEmpty(roots)) {
            log.warn("项目 {} 未提供 git 根目录，跳过增量扫描", request.getProjectCode());
            return GitIncrementalResult.empty(request.getProjectCode());
        }

        Instant startedAt = clock.instant();
        List<FileRecord> allRecords = new ArrayList<>();
        List<GitDiffFile> allDiffFiles = new ArrayList<>();
        Map<String, String> baseCommits = new LinkedHashMap<>();
        Map<String, String> latestCommits = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        long totalBytes = 0L;

        Map<String, String> previousLatest = resolvePreviousLatest(request.getProjectCode());

        for (Path root : roots) {
            RootContext rootContext = resolveRootContext(root);
            GitReferencePair referencePair = resolveReferencePair(rootContext, previousLatest);
            DiffComputationResult result = computeDiff(request, rootContext, referencePair);
            if (!result.records.isEmpty()) {
                allRecords.addAll(result.records);
            }
            if (!result.gitDiffFiles.isEmpty()) {
                allDiffFiles.addAll(result.gitDiffFiles);
            }
            if (result.baseCommit != null) {
                baseCommits.put(rootContext.key, result.baseCommit);
            }
            if (result.latestCommit != null) {
                latestCommits.put(rootContext.key, result.latestCommit);
            }
            if (!result.warnings.isEmpty()) {
                warnings.addAll(result.warnings);
            }
            totalBytes += result.totalBytes;
        }

        if (allRecords.isEmpty() && baseCommits.isEmpty() && latestCommits.isEmpty()) {
            log.info("项目 {} 的 JGit 增量扫描未检测到差异，返回空摘要", request.getProjectCode());
            return GitIncrementalResult.empty(request.getProjectCode());
        }

        Instant completedAt = clock.instant();
        ScanSummary.DiffConfiguration diffConfig = ScanSummary.DiffConfiguration.builder()
                .gitIncludeRenames(scanProperties.isGitIncludeRenames())
                .gitDetectCopies(scanProperties.isGitDetectCopies())
                .gitMaxDiffBytes(scanProperties.getGitMaxDiffBytes())
                .gitMaxFileSizeBytes(scanProperties.getGitMaxFileSizeBytes())
                .build();
        ScanSummary summary = ScanSummary.builder()
                .projectCode(request.getProjectCode())
                .scannedRoots(toRelativePaths(roots))
                .filesScanned(allRecords.size())
                .totalBytes(totalBytes)
                .startedAt(startedAt)
                .completedAt(completedAt)
                .warnings(warnings)
                .baseCommits(baseCommits)
                .latestCommits(latestCommits)
                .diffEngine(scanProperties.getDiffEngine())
                .diffConfiguration(diffConfig)
                .build();
        return GitIncrementalResult.of(allRecords, allDiffFiles, summary);
    }

    private Map<String, String> resolvePreviousLatest(String projectCode) {
        if (!scanProperties.isSinceLastSummary() || !StringUtils.hasText(projectCode)) {
            return Collections.emptyMap();
        }
        return scanResultRepository.findLatestSummary(projectCode)
                .map(ScanSummary::getLatestCommits)
                .map(HashMapCopy::of)
                .orElse(Collections.emptyMap());
    }

    private DiffComputationResult computeDiff(ProjectScanRequest request,
                                              RootContext rootContext,
                                              GitReferencePair referencePair) {
        List<FileRecord> records = new ArrayList<>();
        List<GitDiffFile> diffFiles = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        long totalBytes = 0L;
        Path root = rootContext.root;
        File gitDir = resolveGitDir(root);
        if (gitDir == null || !gitDir.exists()) {
            warnings.add("无法定位 git 仓库目录：" + root);
            return DiffComputationResult.empty(warnings);
        }

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .setWorkTree(root.toFile())
                .readEnvironment()
                .findGitDir()
                .build()) {

            Set<String> fetchedRefs = new LinkedHashSet<>();
            ObjectId headCommit = repository.resolve(Constants.HEAD);
            if (log.isDebugEnabled()) {
                log.debug("Starting diff for project={} root={} baseRef={} targetRef={} includeWorkingTree={}",
                        request.getProjectCode(),
                        root,
                        referencePair.baseRef,
                        referencePair.targetRef,
                        referencePair.useWorkingTree);
            }
            ObjectId targetCommit = resolveCommit(repository, referencePair.targetRef, headCommit, fetchedRefs, warnings);
            ObjectId baseCommit = resolveBaseCommit(repository, referencePair.baseRef, targetCommit, fetchedRefs, warnings);
            if (log.isDebugEnabled()) {
                log.debug("Resolved commits: baseRef={} -> {} targetRef={} -> {} head={}",
                        referencePair.baseRef,
                        formatCommitId(baseCommit),
                        referencePair.targetRef,
                        formatCommitId(targetCommit),
                        formatCommitId(headCommit));
            }
            CommitMetadata commitMetadata = resolveCommitMetadata(repository, targetCommit);
            AbstractTreeIterator oldTree = prepareTreeIterator(repository, baseCommit);
            AbstractTreeIterator newTree = referencePair.useWorkingTree
                    ? new FileTreeIterator(repository)
                    : prepareTreeIterator(repository, targetCommit);
            if (oldTree == null || newTree == null) {
                warnings.add("提交树无法解析，跳过增量扫描：" + root);
                return DiffComputationResult.empty(warnings);
            }

            try (DiffFormatter formatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                formatter.setRepository(repository);
                formatter.setDetectRenames(scanProperties.isGitIncludeRenames() || scanProperties.isGitDetectCopies());
                List<DiffEntry> entries = formatter.scan(oldTree, newTree);
                if (log.isDebugEnabled()) {
                    log.debug("Diff entries resolved: count={} project={} root={}", entries.size(), request.getProjectCode(), root);
                }
                for (DiffEntry entry : entries) {
                    if (!isSupportedChange(entry)) {
                        continue;
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("Processing entry changeType={} oldPath={} newPath={}",
                                entry.getChangeType(),
                                entry.getOldPath(),
                                entry.getNewPath());
                    }
                    FileRecord record = toFileRecord(request, repository, rootContext, entry, commitMetadata,
                            referencePair.useWorkingTree);
                    records.add(record);
                    totalBytes += Math.max(record.getSizeInBytes(), 0);
                    GitDiffFile diffFile = toGitDiffFile(formatter, entry, record, warnings);
                    if (diffFile != null) {
                        diffFiles.add(diffFile);
                    }
                }
            }

            String baseCommitValue = baseCommit == null ? null : baseCommit.name();
            String targetCommitValue = referencePair.useWorkingTree
                    ? (targetCommit == null ? WORKING_TREE_COMMIT : targetCommit.name())
                    : (targetCommit == null ? null : targetCommit.name());

            return new DiffComputationResult(records, diffFiles, warnings, totalBytes, baseCommitValue, targetCommitValue);
        } catch (IOException ex) {
            log.warn("项目 {} 的增量扫描失败，路径：{}，原因：{}", request.getProjectCode(), root, ex.getMessage(), ex);
            warnings.add("JGit 扫描失败：" + ex.getMessage());
        }
        return DiffComputationResult.empty(warnings);
    }

    private boolean isSupportedChange(DiffEntry entry) {
        if (entry == null) {
            return false;
        }
        DiffEntry.ChangeType type = entry.getChangeType();
        return type == DiffEntry.ChangeType.ADD
                || type == DiffEntry.ChangeType.MODIFY
                || type == DiffEntry.ChangeType.RENAME;
    }

    private FileRecord toFileRecord(ProjectScanRequest request,
                                    Repository repository,
                                    RootContext rootContext,
                                    DiffEntry entry,
                                    CommitMetadata metadata,
                                    boolean workingTree) throws IOException {
        String projectCode = request.getProjectCode();
        String relativePath = resolveDiffPath(entry);
        Path absolutePath = rootContext.root.resolve(relativePath).normalize();
        boolean fileExists = Files.exists(absolutePath);
        long size = fileExists ? Files.size(absolutePath) : 0L;
        Instant lastModified = fileExists
                ? Files.getLastModifiedTime(absolutePath).toInstant()
                : metadata.commitTime.orElse(Instant.EPOCH);
        FileChangeType changeType = convertChangeType(entry.getChangeType());
        String branch = resolveBranch(repository);
        Instant scannedAt = clock.instant();

        return FileRecord.builder()
                .projectCode(projectCode)
                .path(relativePath)
                .sizeInBytes(size)
                .lastModified(lastModified)
                .scannedAt(scannedAt)
                .changeType(changeType)
                .gitCommitId(metadata.commitId.orElse(workingTree ? WORKING_TREE_COMMIT : null))
                .gitCommitTime(metadata.commitTime.orElse(null))
                .gitAuthor(metadata.author.orElse(null))
                .gitBranch(branch)
                .gitPreviousPath(resolvePreviousPath(entry))
                .workingTreeChange(workingTree)
                .build();
    }


    private GitDiffFile toGitDiffFile(DiffFormatter formatter,
                                      DiffEntry entry,
                                      FileRecord record,
                                      List<String> warnings) throws IOException {
        if (!shouldCaptureGitDiff()) {
            log.debug("Git diff capture disabled, diffEngine={}, path={}", scanProperties.getDiffEngine(), record.getPath());
            return null;
        }
        long sizeLimit = scanProperties.getGitMaxFileSizeBytes();
        if (sizeLimit > 0 && record.getSizeInBytes() > sizeLimit) {
            warnings.add("Skip Git diff capture for " + record.getPath() + " due to file size limit");
            log.debug("Skip Git diff capture due to file size, path={}, size={}, limit={}",
                    record.getPath(), record.getSizeInBytes(), sizeLimit);
            return null;
        }
        FileHeader fileHeader = formatter.toFileHeader(entry);
        if (fileHeader == null) {
            log.debug("FileHeader not available, skip Git diff capture, path={}", record.getPath());
            return null;
        }
        PatchType patchType = fileHeader.getPatchType();
        if (log.isDebugEnabled()) {
            int hunkCount = fileHeader.getHunks() == null ? 0 : fileHeader.getHunks().size();
            log.debug("FileHeader summary: path={} patchType={} hunkCount={}", record.getPath(), patchType, hunkCount);
        }
        if (PatchType.GIT_BINARY.equals(patchType) || PatchType.BINARY.equals(patchType)) {
            warnings.add("Skip Git diff capture for " + record.getPath() + " (binary patch)");
            log.debug("Binary patch detected, skip Git diff capture, path={}", record.getPath());
            return null;
        }
        List<? extends HunkHeader> hunks = fileHeader.getHunks();
        if (hunks == null || hunks.isEmpty()) {
            log.debug("Git diff returned no hunks, path={}", record.getPath());
            return null;
        }

        long maxDiffBytes = scanProperties.getGitMaxDiffBytes();
        long accumulated = 0L;
        boolean truncated = false;
        List<GitDiffHunk> payload = new ArrayList<>();
        for (HunkHeader hunk : hunks) {
            long hunkBytes = estimateHunkBytes(fileHeader, hunk);
            HunkHeader.OldImage oldImage = hunk.getOldImage();
            int oldStart = oldImage != null ? Math.max(oldImage.getStartLine(), 0) : 0;
            int oldCount = oldImage != null ? Math.max(oldImage.getLineCount(), 0) : 0;
            int newStart = Math.max(hunk.getNewStartLine(), 0);
            int newCount = Math.max(hunk.getNewLineCount(), 0);
            if (log.isDebugEnabled()) {
                log.debug("Evaluating hunk: path={} startOffset={} endOffset={} oldStartLine={} oldLineCount={} newStartLine={} newLineCount={}",
                        record.getPath(),
                        hunk.getStartOffset(),
                        hunk.getEndOffset(),
                        oldStart,
                        oldCount,
                        newStart,
                        newCount);
            }
            if (maxDiffBytes > 0 && accumulated + hunkBytes > maxDiffBytes) {
                truncated = true;
                break;
            }
            List<String> lines = extractDiffLines(fileHeader, hunk);
            if (log.isDebugEnabled()) {
                log.debug("Decoded hunk lines: path={} startOffset={} endOffset={} lineCount={}",
                        record.getPath(),
                        hunk.getStartOffset(),
                        hunk.getEndOffset(),
                        lines.size());
            }
            GitDiffHunk gitDiffHunk = GitDiffHunk.builder()
                    .oldRange(GitDiffHunk.Range.of(oldStart, oldCount))
                    .newRange(GitDiffHunk.Range.of(newStart, newCount))
                    .lines(lines)
                    .byteSize(hunkBytes)
                    .build();
            payload.add(gitDiffHunk);
            accumulated += hunkBytes;
        }
        if (payload.isEmpty()) {
            log.debug("Git diff hunk payload empty, path={}", record.getPath());
            return null;
        }
        if (truncated) {
            warnings.add("Git diff payload truncated for " + record.getPath() + " after reaching size limit");
            log.debug("Git diff payload truncated, path={}, limit={}, accumulated={}", record.getPath(), maxDiffBytes, accumulated);
        }

        GitDiffFile gitDiffFile = GitDiffFile.builder()
                .path(record.getPath())
                .previousPath(record.getGitPreviousPath())
                .changeType(record.getChangeType())
                .hunks(payload)
                .truncated(truncated)
                .totalBytes(accumulated)
                .build();
        if (log.isDebugEnabled()) {
            log.debug("Git diff capture completed, path={} hunks={} bytes={} truncated={}",
                    record.getPath(), payload.size(), accumulated, truncated);
        }
        return gitDiffFile;
    }



    private boolean shouldCaptureGitDiff() {
        String engine = scanProperties.getDiffEngine();
        return "git".equalsIgnoreCase(engine);
    }

    private long estimateHunkBytes(FileHeader fileHeader, HunkHeader hunk) {
        int start = Math.max(hunk.getStartOffset(), 0);
        int end = Math.max(hunk.getEndOffset(), start);
        return end - start;
    }

    private List<String> extractDiffLines(FileHeader fileHeader, HunkHeader hunk) {
        String slice = decodeDiffSlice(fileHeader, hunk.getStartOffset(), hunk.getEndOffset());
        if (!StringUtils.hasText(slice)) {
            return Collections.emptyList();
        }
        String[] rawLines = slice.split("\n");
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < rawLines.length; i++) {
            String line = trimLineEnding(rawLines[i]);
            if (i == 0 && line.startsWith("@@")) {
                // header line already described by range metadata
                continue;
            }
            if (line.isEmpty() && i == rawLines.length - 1) {
                continue;
            }
            lines.add(line);
        }
        return lines;
    }

    private String decodeDiffSlice(FileHeader fileHeader, int startOffset, int endOffset) {
        byte[] buffer = fileHeader.getBuffer();
        if (buffer == null || startOffset < 0 || endOffset <= startOffset || endOffset > buffer.length) {
            if (log.isDebugEnabled()) {
                log.debug("Skip diff slice decode due to invalid bounds: startOffset={} endOffset={} bufferLength={}",
                        startOffset, endOffset, buffer == null ? -1 : buffer.length);
            }
            return "";
        }
        return RawParseUtils.decode(buffer, startOffset, endOffset);
    }

    private String formatCommitId(ObjectId id) {
        if (id == null) {
            return "<null>";
        }
        return id.name();
    }

    private String trimLineEnding(String line) {
        if (line == null) {
            return "";
        }
        if (line.endsWith("\r")) {
            return line.substring(0, line.length() - 1);
        }
        return line;
    }

    private String resolveDiffPath(DiffEntry entry) {
        if (entry.getChangeType() == DiffEntry.ChangeType.ADD) {
            return entry.getNewPath();
        }
        if (entry.getChangeType() == DiffEntry.ChangeType.RENAME) {
            return entry.getNewPath();
        }
        return DiffEntry.DEV_NULL.equals(entry.getNewPath()) ? entry.getOldPath() : entry.getNewPath();
    }

    private String resolvePreviousPath(DiffEntry entry) {
        if (entry.getChangeType() == DiffEntry.ChangeType.RENAME) {
            return DiffEntry.DEV_NULL.equals(entry.getOldPath()) ? null : entry.getOldPath();
        }
        return null;
    }

    private FileChangeType convertChangeType(DiffEntry.ChangeType changeType) {
        if (changeType == DiffEntry.ChangeType.ADD) {
            return FileChangeType.NEW;
        }
        if (changeType == DiffEntry.ChangeType.DELETE) {
            return FileChangeType.DELETED;
        }
        if (changeType == DiffEntry.ChangeType.RENAME) {
            return FileChangeType.RENAMED;
        }
        return FileChangeType.MODIFIED;
    }

    private String resolveBranch(Repository repository) {
        try {
            return repository.getBranch();
        } catch (IOException ex) {
            return null;
        }
    }

    private AbstractTreeIterator prepareTreeIterator(Repository repository, ObjectId commitId) throws IOException {
        if (commitId == null) {
            return null;
        }
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(commitId);
            ObjectId treeId = commit.getTree().getId();
            try (ObjectReader reader = repository.newObjectReader()) {
                CanonicalTreeParser parser = new CanonicalTreeParser();
                parser.reset(reader, treeId);
                return parser;
            }
        }
    }

    private ObjectId resolveCommit(Repository repository,
                                   String ref,
                                   ObjectId defaultId,
                                   Set<String> fetchedRefs,
                                   List<String> warnings) throws IOException {
        ObjectId resolved = resolveDirectCommit(repository, ref);
        if (resolved != null) {
            return resolved;
        }
        if (shouldAttemptFetch(ref) && attemptFetch(repository, ref, fetchedRefs, warnings)) {
            resolved = resolveDirectCommit(repository, ref);
            if (resolved != null) {
                return resolved;
            }
        }
        return defaultId;
    }

    private ObjectId resolveBaseCommit(Repository repository,
                                       String ref,
                                       ObjectId targetCommit,
                                       Set<String> fetchedRefs,
                                       List<String> warnings) throws IOException {
        ObjectId resolved = resolveDirectCommit(repository, ref);
        if (resolved != null) {
            return resolved;
        }
        if (shouldAttemptFetch(ref) && attemptFetch(repository, ref, fetchedRefs, warnings)) {
            resolved = resolveDirectCommit(repository, ref);
            if (resolved != null) {
                return resolved;
            }
        }
        if (targetCommit == null) {
            return null;
        }
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(targetCommit);
            if (commit.getParentCount() > 0) {
                return commit.getParent(0).getId();
            }
            return commit.getId();
        }
    }

    private ObjectId resolveDirectCommit(Repository repository, String ref) throws IOException {
        if (!StringUtils.hasText(ref)) {
            return null;
        }
        return repository.resolve(ref);
    }

    private boolean shouldAttemptFetch(String ref) {
        return scanProperties.isGitFetchMissingRefs() && StringUtils.hasText(ref);
    }

    private boolean attemptFetch(Repository repository,
                                 String ref,
                                 Set<String> fetchedRefs,
                                 List<String> warnings) {
        RefSpec refSpec = buildRefSpec(scanProperties.getGitRemoteName(), ref);
        if (refSpec == null) {
            return false;
        }
        String remote = scanProperties.getGitRemoteName();
        String key = remote + "::" + refSpec.toString();
        if (fetchedRefs != null && !fetchedRefs.add(key)) {
            return false;
        }
        try {
            Git git = Git.wrap(repository);
            git.fetch()
                    .setRemote(remote)
                    .setRefSpecs(Collections.singletonList(refSpec))
                    .setTagOpt(TagOpt.NO_TAGS)
                    .call();
            return true;
        } catch (GitAPIException ex) {
            String message = "Auto fetch of remote ref failed: remote=" + remote + ", ref=" + ref + " (" + ex.getMessage() + ")";
            log.warn(message, ex);
            if (warnings != null) {
                warnings.add(message);
            }
            return false;
        }
    }

    private RefSpec buildRefSpec(String remote, String ref) {
        if (!StringUtils.hasText(ref)) {
            return null;
        }
        String effectiveRemote = StringUtils.hasText(remote) ? remote : "origin";
        if (ref.startsWith(Constants.R_HEADS)) {
            String branch = ref.substring(Constants.R_HEADS.length());
            String remoteRef = Constants.R_HEADS + branch;
            String localRef = Constants.R_REMOTES + effectiveRemote + "/" + branch;
            return new RefSpec(remoteRef + ":" + localRef);
        }
        if (ref.startsWith(Constants.R_REMOTES)) {
            String remainder = ref.substring(Constants.R_REMOTES.length());
            int slash = remainder.indexOf('/');
            if (slash > 0) {
                String remoteName = remainder.substring(0, slash);
                String branch = remainder.substring(slash + 1);
                String remoteRef = Constants.R_HEADS + branch;
                String localRef = Constants.R_REMOTES + remoteName + "/" + branch;
                return new RefSpec(remoteRef + ":" + localRef);
            }
        }
        if (ref.startsWith(Constants.R_TAGS)) {
            String tag = ref.substring(Constants.R_TAGS.length());
            String remoteRef = Constants.R_TAGS + tag;
            String localRef = Constants.R_TAGS + tag;
            return new RefSpec(remoteRef + ":" + localRef);
        }
        return null;
    }

    private CommitMetadata resolveCommitMetadata(Repository repository, ObjectId commitId) throws IOException {
        if (commitId == null) {
            return CommitMetadata.empty();
        }
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(commitId);
            PersonIdent authorIdent = commit.getAuthorIdent();
            String commitHash = commitId.name();
            Instant commitTime = Instant.ofEpochSecond(commit.getCommitTime());
            String author = authorIdent == null ? null : authorIdent.getName();
            return new CommitMetadata(commitHash, commitTime, author);
        }
    }

    private File resolveGitDir(Path root) {
        Path gitDir = root.resolve(".git");
        if (Files.isDirectory(gitDir)) {
            return gitDir.toFile();
        }
        return null;
    }

    private RootContext resolveRootContext(Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        ProjectRootRegistry.ProjectRootDescriptor matched = projectRootRegistry.getDescriptors().stream()
                .filter(descriptor -> descriptor.getPath().equals(normalized))
                .findFirst()
                .orElse(null);
        String key;
        if (matched != null && StringUtils.hasText(matched.getCode())) {
            key = matched.getCode();
        } else if (matched != null) {
            key = matched.getType().name();
        } else {
            key = normalized.getFileName() == null ? normalized.toString() : normalized.getFileName().toString();
        }
        return new RootContext(normalized, matched, key);
    }

    private GitReferencePair resolveReferencePair(RootContext rootContext, Map<String, String> previousLatest) {
        ProjectRootRegistry.ProjectRootDescriptor descriptor = rootContext.descriptor;
        boolean isSource = descriptor == null
                || descriptor.getType() == ProjectRootRegistry.ProjectRootType.SOURCE
                || descriptor.getType() == ProjectRootRegistry.ProjectRootType.LEGACY;
        String baseRef = isSource ? scanProperties.getGitBaseRefSource() : scanProperties.getGitBaseRefTarget();
        String targetRef = isSource ? scanProperties.getGitTargetRefSource() : scanProperties.getGitTargetRefTarget();
        String previous = previousLatest.get(rootContext.key);
        if (StringUtils.hasText(previous)) {
            baseRef = previous;
        }
        boolean includeWorkingTree = scanProperties.isIncludeWorkingTree();
        return new GitReferencePair(baseRef, targetRef, includeWorkingTree);
    }

    private List<String> toRelativePaths(List<Path> roots) {
        if (CollectionUtils.isEmpty(roots)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(roots.size());
        for (Path root : roots) {
            if (root == null) {
                continue;
            }
            Path normalized = root.toAbsolutePath().normalize();
            result.add(normalized.toString());
        }
        return result;
    }

    private static final class HashMapCopy {
        private static Map<String, String> of(Map<String, String> source) {
            Map<String, String> copy = new LinkedHashMap<>();
            if (source != null) {
                copy.putAll(source);
            }
            return copy;
        }
    }

    private static final class RootContext {
        private final Path root;
        private final ProjectRootRegistry.ProjectRootDescriptor descriptor;
        private final String key;

        private RootContext(Path root,
                            ProjectRootRegistry.ProjectRootDescriptor descriptor,
                            String key) {
            this.root = root;
            this.descriptor = descriptor;
            this.key = key;
        }
    }

    private static final class GitReferencePair {
        private final String baseRef;
        private final String targetRef;
        private final boolean useWorkingTree;

        private GitReferencePair(String baseRef, String targetRef, boolean useWorkingTree) {
            this.baseRef = baseRef;
            this.targetRef = targetRef;
            this.useWorkingTree = useWorkingTree;
        }
    }

    private static final class CommitMetadata {
        private final Optional<String> commitId;
        private final Optional<Instant> commitTime;
        private final Optional<String> author;

        private CommitMetadata(String commitId, Instant commitTime, String author) {
            this.commitId = Optional.ofNullable(commitId);
            this.commitTime = Optional.ofNullable(commitTime);
            this.author = Optional.ofNullable(author);
        }

        private static CommitMetadata empty() {
            return new CommitMetadata(null, null, null);
        }
    }

    private static final class DiffComputationResult {
        private final List<FileRecord> records;
        private final List<GitDiffFile> gitDiffFiles;
        private final List<String> warnings;
        private final long totalBytes;
        private final String baseCommit;
        private final String latestCommit;

        private DiffComputationResult(List<FileRecord> records,
                                      List<GitDiffFile> gitDiffFiles,
                                      List<String> warnings,
                                      long totalBytes,
                                      String baseCommit,
                                      String latestCommit) {
            this.records = records;
            this.gitDiffFiles = gitDiffFiles;
            this.warnings = warnings;
            this.totalBytes = totalBytes;
            this.baseCommit = baseCommit;
            this.latestCommit = latestCommit;
        }

        private static DiffComputationResult empty(List<String> warnings) {
            return new DiffComputationResult(Collections.emptyList(),
                    Collections.emptyList(),
                    warnings == null ? Collections.emptyList() : warnings,
                    0L,
                    null,
                    null);
        }
    }
}
