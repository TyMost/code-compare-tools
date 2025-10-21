package com.example.codecompare.rebuild.scanning;

import com.example.codecompare.rebuild.core.properties.ApplicationProperties;
import com.example.codecompare.rebuild.core.support.ProjectRootRegistry;
import com.example.codecompare.rebuild.repository.model.FileChangeType;
import com.example.codecompare.rebuild.repository.model.FileRecord;
import com.example.codecompare.rebuild.scanning.git.GitDiffFile;
import com.example.codecompare.rebuild.scanning.git.GitDiffHunk;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JGitChangeScannerTest {

    @TempDir
    Path tempDir;

    private Git git;
    private RevCommit baseCommit;
    private RevCommit targetCommit;

    @BeforeEach
    void setUp() throws Exception {
        git = Git.init()
                .setDirectory(tempDir.toFile())
                .call();
        Path file = tempDir.resolve("sample.txt");
        Files.write(file, Collections.singletonList("hello world"));
        git.add().addFilepattern("sample.txt").call();
        baseCommit = git.commit()
                .setMessage("initial")
                .setAuthor("tester", "tester@example.com")
                .call();

        Files.write(file, Collections.singletonList("hello codex"));
        git.add().addFilepattern("sample.txt").call();
        targetCommit = git.commit()
                .setMessage("second")
                .setAuthor("tester", "tester@example.com")
                .call();
    }

    @AfterEach
    void tearDown() {
        if (git != null) {
            git.close();
        }
    }

    @Test
    void shouldCollectModifiedFilesBetweenConfiguredCommits() throws IOException {
        ScanProperties scanProperties = new ScanProperties();
        scanProperties.setGitBaseRefSource(baseCommit.getId().name());
        scanProperties.setGitTargetRefSource(targetCommit.getId().name());
        scanProperties.setSinceLastSummary(false);
        scanProperties.setIncludeWorkingTree(false);
        scanProperties.setDiffEngine("git");
        scanProperties.setGitMaxDiffBytes(64 * 1024);
        scanProperties.setGitMaxFileSizeBytes(128 * 1024);

        StubScanResultRepository repository = new StubScanResultRepository();
        ApplicationProperties applicationProperties = new ApplicationProperties();
        applicationProperties.getProject().setRoots(Collections.singletonList(tempDir.toString()));
        ProjectRootRegistry rootRegistry = new ProjectRootRegistry(applicationProperties);

        ProjectScanRequest request = ProjectScanRequest.builder()
                .projectCode("demo")
                .addRoot(tempDir)
                .requestedAt(Instant.now())
                .build();

        JGitChangeScanner scanner = new JGitChangeScanner(
                scanProperties,
                repository,
                rootRegistry,
                Clock.systemUTC()
        );

        GitIncrementalResult result = scanner.scanIncremental(request);

        assertThat(result.isEmpty()).isFalse();
        List<FileRecord> records = result.getRecords();
        assertThat(records).hasSize(1);
        FileRecord record = records.get(0);
        assertThat(record.getPath()).isEqualTo("sample.txt");
        assertThat(record.getChangeType()).isEqualTo(FileChangeType.MODIFIED);
        assertThat(record.getGitCommitId()).isEqualTo(targetCommit.getId().name());
        assertThat(record.isWorkingTreeChange()).isFalse();

        ScanSummary summary = result.getSummary().orElseThrow(() -> new AssertionError("summary missing"));
        assertThat(summary.getFilesScanned()).isEqualTo(1);
        assertThat(summary.getBaseCommits().values()).contains(baseCommit.getId().name());
        assertThat(summary.getLatestCommits().values()).contains(targetCommit.getId().name());
        assertThat(summary.getDiffEngine()).isEqualTo("git");
        assertThat(summary.getDiffConfiguration().isGitIncludeRenames()).isTrue();
        assertThat(summary.getDiffConfiguration().getGitMaxDiffBytes()).isEqualTo(64 * 1024);

        List<GitDiffFile> diffFiles = result.getGitDiffFiles();
        assertThat(diffFiles).hasSize(1);
        GitDiffFile diffFile = diffFiles.get(0);
        assertThat(diffFile.getPath()).isEqualTo("sample.txt");
        assertThat(diffFile.isTruncated()).isFalse();
        assertThat(diffFile.getHunks()).hasSize(1);
        GitDiffHunk hunk = diffFile.getHunks().get(0);
        assertThat(hunk.getLines()).contains("-hello world", "+hello codex");
        assertThat(hunk.getOldRange().getStartLine()).isEqualTo(1);
        assertThat(hunk.getNewRange().getStartLine()).isEqualTo(1);
    }

    private static final class StubScanResultRepository implements ScanResultRepository {

        private ScanSummary summary;

        @Override
        public List<FileRecord> saveAll(Collection<FileRecord> records) {
            if (records == null) {
                return Collections.emptyList();
            }
            return new ArrayList<>(records);
        }

        @Override
        public Optional<ScanSummary> findLatestSummary(String projectCode) {
            return Optional.ofNullable(summary);
        }

        @Override
        public void saveSummary(ScanSummary summary) {
            this.summary = summary;
        }

        @Override
        public List<FileRecord> findLatestFiles(String projectCode) {
            return Collections.emptyList();
        }

        @Override
        public void deletePaths(String projectCode, Collection<String> paths) {
            // no-op for tests
        }

        @Override
        public void deleteSummary(String projectCode) {
            this.summary = null;
        }

    }
}
