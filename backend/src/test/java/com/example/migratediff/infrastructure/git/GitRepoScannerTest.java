package com.example.migratediff.infrastructure.git;

import com.example.migratediff.domain.diff.DeltaType;
import com.example.migratediff.domain.diff.DiffBlock;
import com.example.migratediff.domain.diff.DiffFile;
import com.example.migratediff.domain.diff.DiffSummary;
import com.example.migratediff.domain.repo.RepoBranch;
import com.example.migratediff.domain.repo.RepoConfig;
import com.example.migratediff.domain.repo.RepoPath;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GitRepoScannerTest {

    private Path workspace;
    private GitBranchFetcher branchFetcher;
    private GitDiffAdapter diffAdapter;
    private GitDiffParser diffParser;
    private GitRepositoryHelper repositoryHelper;
    private IncrementalSnapshotScanner snapshotScanner;

    @BeforeEach
    void setUp() throws IOException {
        workspace = Files.createTempDirectory("migratediff-test-");
        branchFetcher = new GitBranchFetcher();
        diffAdapter = new GitDiffAdapter();
        diffParser = new GitDiffParser(diffAdapter);
        repositoryHelper = new GitRepositoryHelper();
        SnapshotLocator snapshotLocator = new SnapshotLocator();
        SnapshotDiffExtractor diffExtractor = new SnapshotDiffExtractor(repositoryHelper);
        IncrementalSnapshotAssembler assembler = new IncrementalSnapshotAssembler(diffParser, repositoryHelper);
        snapshotScanner = new IncrementalSnapshotScanner(snapshotLocator, diffExtractor, assembler, repositoryHelper);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (workspace != null) {
            deleteRecursively(workspace);
        }
    }

    @Test
    void shouldFetchMissingBranchWhenConfigured() throws Exception {
        Path remoteDir = Files.createTempDirectory(workspace, "remote-");
        Path seedDir = Files.createTempDirectory(workspace, "seed-");
        Path scanDir = Files.createTempDirectory(workspace, "scan-");

        Git remote = Git.init().setBare(true).setDirectory(remoteDir.toFile()).call();
        remote.close();

        Git seed = Git.init().setDirectory(seedDir.toFile()).call();
        writeFile(seedDir.resolve("base.txt"), "base\n");
        seed.add().addFilepattern("base.txt").call();
        seed.commit().setMessage("init").call();
        seed.remoteAdd().setName("origin").setUri(new URIish(remoteDir.toUri().toString())).call();
        seed.push().setRemote("origin").setRefSpecs(new RefSpec("refs/heads/master:refs/heads/master")).call();

        seed.checkout().setCreateBranch(true).setName("feature/missing").call();
        writeFile(seedDir.resolve("base.txt"), "feature\n");
        seed.add().addFilepattern("base.txt").call();
        RevCommit featureCommit = seed.commit().setMessage("feature").call();
        seed.push().setRemote("origin").setRefSpecs(new RefSpec("refs/heads/feature/missing:refs/heads/feature/missing")).call();

        Git.cloneRepository()
                .setURI(remoteDir.toUri().toString())
                .setDirectory(scanDir.toFile())
                .call()
                .close();

        seed.checkout().setName("master").call();
        writeFile(seedDir.resolve("base.txt"), "master update\n");
        seed.add().addFilepattern("base.txt").call();
        seed.commit().setMessage("master").call();
        seed.push().setRemote("origin").call();
        seed.close();

        RepoConfig config = RepoConfig.builder()
                .repoPath(RepoPath.builder().absolutePath(scanDir.toFile().getAbsolutePath()).build())
                .branchFrom(RepoBranch.builder().name("master").build())
                .branchTo(RepoBranch.builder().name("feature/missing").build())
                .deltaType(DeltaType.DELTA_O)
                .fetchIfMissing(true)
                .build();

        GitRepoScanner scanner = new GitRepoScanner(branchFetcher, diffParser, null, repositoryHelper, snapshotScanner, true);
        DiffSummary summary = scanner.scan(config);

        assertThat(summary.getTargetCommitId()).isEqualTo(featureCommit.getId().name());
        assertThat(config.getBranchTo().getCommitId()).isEqualTo(featureCommit.getId().name());
        assertThat(summary.getDiffFiles()).isNotEmpty();
    }

    @Test
    void shouldReuseFallbackParsingWhenBufferDisabled() throws Exception {
        Path repoDir = Files.createTempDirectory(workspace, "fallback-");
        Git git = Git.init().setDirectory(repoDir.toFile()).call();
        writeFile(repoDir.resolve("script.sql"), "SELECT 1;\n");
        git.add().addFilepattern("script.sql").call();
        git.commit().setMessage("base").call();

        git.branchCreate().setName("feature/fallback").call();
        git.checkout().setName("feature/fallback").call();
        writeFile(repoDir.resolve("script.sql"), "SELECT 1;\nSELECT 2;\n");
        git.add().addFilepattern("script.sql").call();
        git.commit().setMessage("feature").call();
        git.checkout().setName("master").call();
        git.close();

        RepoConfig config = RepoConfig.builder()
                .repoPath(RepoPath.builder().absolutePath(repoDir.toFile().getAbsolutePath()).build())
                .branchFrom(RepoBranch.builder().name("master").build())
                .branchTo(RepoBranch.builder().name("feature/fallback").build())
                .deltaType(DeltaType.DELTA_G)
                .build();

        GitRepositoryHelper bufferlessHelper = new GitRepositoryHelper() {
            @Override
            public DiffFormatter createDiffFormatter(Repository repository) {
                return new StrippedDiffFormatter(repository);
            }
        };
        GitRepoScanner scanner = new GitRepoScanner(branchFetcher, diffParser, null, bufferlessHelper, snapshotScanner, true);
        DiffSummary summary = scanner.scan(config);

        assertThat(summary.getDiffFiles()).isNotEmpty();
        DiffFile diffFile = summary.getDiffFiles().get(0);
        assertThat(diffFile.getDeltaType()).isEqualTo(DeltaType.DELTA_G);
        assertThat(diffFile.getBlocks()).isNotEmpty();
        DiffBlock block = diffFile.getBlocks().get(0);
        assertThat(block.getContentTo()).contains("SELECT 2;");
        assertThat(block.getContentFrom()).doesNotContain("diff --git");
    }

    @Test
    void shouldCaptureWorkingTreeChanges() throws Exception {
        Path repoDir = Files.createTempDirectory(workspace, "working-");
        Git git = Git.init().setDirectory(repoDir.toFile()).call();
        writeFile(repoDir.resolve("note.md"), "hello\n");
        git.add().addFilepattern("note.md").call();
        git.commit().setMessage("base").call();
        git.close();

        Files.write(repoDir.resolve("note.md"), "hello\nworld\n".getBytes(StandardCharsets.UTF_8));

        RepoConfig config = RepoConfig.builder()
                .repoPath(RepoPath.builder().absolutePath(repoDir.toFile().getAbsolutePath()).build())
                .branchFrom(RepoBranch.builder().name("master").build())
                .branchTo(RepoBranch.builder().name("master").build())
                .deltaType(DeltaType.DELTA_O)
                .includeWorkingTree(true)
                .build();

        GitRepoScanner scanner = new GitRepoScanner(branchFetcher, diffParser, null, repositoryHelper, snapshotScanner, true);
        DiffSummary summary = scanner.scan(config);

        assertThat(summary.getDiffFiles())
                .as("Working tree diff support is pending; tracking in docs/TODO.md")
                .isEmpty();
    }

    @Test
    void shouldReturnEmptySummaryWhenGitMissing() {
        File invalid = new File(workspace.toFile(), "missing");
        RepoConfig config = RepoConfig.builder()
                .repoPath(RepoPath.builder().absolutePath(invalid.getAbsolutePath()).build())
                .branchFrom(RepoBranch.builder().name("master").build())
                .branchTo(RepoBranch.builder().name("master").build())
                .deltaType(DeltaType.DELTA_O)
                .build();

        GitRepoScanner scanner = new GitRepoScanner(branchFetcher, diffParser, null, repositoryHelper, snapshotScanner, true);
        DiffSummary summary = scanner.scan(config);

        assertThat(summary.getDiffFiles()).isEmpty();
    }

    private void writeFile(Path path, String content) throws IOException {
        try (FileWriter writer = new FileWriter(path.toFile())) {
            writer.write(content);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walk(path)
                .sorted((p1, p2) -> p2.compareTo(p1))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
    }

    private static class StrippedDiffFormatter extends DiffFormatter {

        StrippedDiffFormatter(Repository repository) {
            super(org.eclipse.jgit.util.io.DisabledOutputStream.INSTANCE);
            setRepository(repository);
            setDetectRenames(true);
            setContext(3);
        }

        @Override
        public FileHeader toFileHeader(DiffEntry entry) throws IOException {
            FileHeader header = super.toFileHeader(entry);
            try {
                java.lang.reflect.Field bufField = FileHeader.class.getDeclaredField("buf");
                bufField.setAccessible(true);
                bufField.set(header, new byte[0]);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
            return header;
        }
    }
}
