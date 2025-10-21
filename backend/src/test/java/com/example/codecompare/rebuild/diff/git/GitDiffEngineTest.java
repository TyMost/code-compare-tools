package com.example.codecompare.rebuild.diff.git;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.diff.DiffResult;
import com.example.codecompare.rebuild.diff.model.DiffSegmentType;
import com.example.codecompare.rebuild.repository.model.FileChangeType;
import com.example.codecompare.rebuild.scanning.git.GitDiffFile;
import com.example.codecompare.rebuild.scanning.git.GitDiffHunk;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GitDiffEngineTest {

    private final GitDiffEngine engine = new GitDiffEngine();

    @Test
    void shouldAssembleChangeHunkWithMetadata() {
        GitDiffHunk hunk = GitDiffHunk.builder()
                .oldRange(GitDiffHunk.Range.of(10, 3))
                .newRange(GitDiffHunk.Range.of(15, 4))
                .lines(Arrays.asList(
                        " context line",
                        "-System.out.println(\"old\");",
                        "+System.out.println(\"new\");"))
                .byteSize(120)
                .build();
        GitDiffFile diffFile = GitDiffFile.builder()
                .path("src/Main.java")
                .previousPath("src/MainLegacy.java")
                .changeType(FileChangeType.RENAMED)
                .addHunk(hunk)
                .totalBytes(120)
                .build();

        DiffResult result = engine.analyze(diffFile);

        assertThat(result.getSegments()).hasSize(1);
        BlockDiff segment = result.getSegments().get(0);
        assertThat(segment.getType()).isEqualTo(DiffSegmentType.CHANGE);
        assertThat(segment.getSourceLines()).containsExactly("System.out.println(\"old\");");
        assertThat(segment.getTargetLines()).containsExactly("System.out.println(\"new\");");
        assertThat(segment.getSourceStartLine()).isEqualTo(11);
        assertThat(segment.getTargetStartLine()).isEqualTo(16);
        assertThat(segment.getChangedLineCount()).isEqualTo(1);
        assertThat(segment.getReplacements()).isEqualTo(1);
        assertThat(segment.getSimilarityScore()).isLessThan(30d);
        Map<String, Object> metadata = segment.getMetadata();
        assertThat(metadata).containsEntry("gitPath", "src/Main.java");
        assertThat(metadata).containsEntry("gitRenamedFrom", "src/MainLegacy.java");
        assertThat(metadata).containsEntry("gitHunkIndex", 1);
    }

    @Test
    void shouldRecognizeInsertAndDeleteSegments() {
        GitDiffHunk insertHunk = GitDiffHunk.builder()
                .oldRange(GitDiffHunk.Range.of(0, 0))
                .newRange(GitDiffHunk.Range.of(1, 2))
                .lines(Arrays.asList("+first line", "+second line"))
                .byteSize(40)
                .build();
        GitDiffHunk deleteHunk = GitDiffHunk.builder()
                .oldRange(GitDiffHunk.Range.of(3, 2))
                .newRange(GitDiffHunk.Range.of(0, 0))
                .lines(Arrays.asList("-obsolete();", "-cleanup();"))
                .byteSize(36)
                .build();

        GitDiffFile newFile = GitDiffFile.builder()
                .path("docs/new.txt")
                .changeType(FileChangeType.NEW)
                .addHunk(insertHunk)
                .build();
        GitDiffFile deletedFile = GitDiffFile.builder()
                .path("src/Old.java")
                .changeType(FileChangeType.DELETED)
                .addHunk(deleteHunk)
                .build();

        DiffResult insertResult = engine.analyze(newFile);
        DiffResult deleteResult = engine.analyze(deletedFile);

        assertThat(insertResult.getSegments()).hasSize(1);
        assertThat(insertResult.getSegments().get(0).getType()).isEqualTo(DiffSegmentType.INSERT);
        assertThat(insertResult.getSegments().get(0).getSourceLines()).isEmpty();
        assertThat(insertResult.getSegments().get(0).getTargetLines()).containsExactly("first line", "second line");

        assertThat(deleteResult.getSegments()).hasSize(1);
        assertThat(deleteResult.getSegments().get(0).getType()).isEqualTo(DiffSegmentType.DELETE);
        assertThat(deleteResult.getSegments().get(0).getTargetLines()).isEmpty();
        assertThat(deleteResult.getSegments().get(0).getSourceLines()).containsExactly("obsolete();", "cleanup();");
    }

    @Test
    void shouldAggregateMultipleFiles() {
        GitDiffHunk hunk = GitDiffHunk.builder()
                .oldRange(GitDiffHunk.Range.of(1, 1))
                .newRange(GitDiffHunk.Range.of(1, 1))
                .lines(Collections.singletonList("+added"))
                .byteSize(8)
                .build();
        GitDiffFile file = GitDiffFile.builder()
                .path("README.md")
                .changeType(FileChangeType.MODIFIED)
                .addHunk(hunk)
                .build();

        DiffResult aggregated = engine.analyze(Collections.singletonList(file));

        assertThat(aggregated.getSegments()).hasSize(1);
        assertThat(aggregated.getSummary().getInsertSegments()).isEqualTo(1);
        assertThat(aggregated.getSummary().getTotalChangedLines()).isEqualTo(1);
    }
}
