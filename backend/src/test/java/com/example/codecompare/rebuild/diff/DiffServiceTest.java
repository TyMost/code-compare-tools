package com.example.codecompare.rebuild.diff;

import com.example.codecompare.rebuild.block.model.BlockDiff;
import com.example.codecompare.rebuild.block.model.CodeSnapshot;
import com.example.codecompare.rebuild.diff.config.DiffConfigurationProperties;
import com.example.codecompare.rebuild.diff.model.DiffSegmentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffServiceTest {

    private DiffService createService() {
        DiffConfigurationProperties properties = new DiffConfigurationProperties();
        properties.setMaxFileSize(1024 * 1024);
        return new DiffService(new LineDiffEngine(), new DiffResultAssembler(), DiffContentMasker.noop(), properties);
    }

    @Test
    void analyzeProducesPreciseLineNumbers() {
        DiffService service = createService();
        String sourceContent = "class Demo {\n"
                + "    void hello() {}\n"
                + "    void world() {}\n"
                + "}\n";
        String targetContent = "class Demo {\n"
                + "    void hello() {}\n"
                + "    void worldUpdated() {}\n"
                + "    void additional() {}\n"
                + "}\n";

        DiffResult result = service.analyze(DiffRequest.builder()
                .source(CodeSnapshot.of("java", "Demo.java", sourceContent))
                .target(CodeSnapshot.of("java", "Demo.java", targetContent))
                .build());

        assertThat(result.getSegments()).hasSize(2);
        BlockDiff changeSegment = result.getSegments().get(0);
        assertThat(changeSegment.getType()).isEqualTo(DiffSegmentType.CHANGE);
        assertThat(changeSegment.getSourceStartLine()).isEqualTo(3);
        assertThat(changeSegment.getTargetStartLine()).isEqualTo(3);
        assertThat(changeSegment.getSourceLines()).containsExactly("    void world() {}");
        assertThat(changeSegment.getTargetLines()).containsExactly("    void worldUpdated() {}");

        BlockDiff insertSegment = result.getSegments().get(1);
        assertThat(insertSegment.getType()).isEqualTo(DiffSegmentType.INSERT);
        assertThat(insertSegment.getTargetStartLine()).isEqualTo(4);
        assertThat(insertSegment.getTargetLines()).containsExactly("    void additional() {}");

        assertThat(result.getSummary().getChangeSegments()).isEqualTo(1);
        assertThat(result.getSummary().getInsertSegments()).isEqualTo(1);
        assertThat(result.getSummary().getTotalChangedLines()).isEqualTo(2);
    }

    @Test
    void analyzeReturnsEmptyWhenContentEquals() {
        DiffService service = createService();
        String content = "line1\nline2\n";
        DiffResult result = service.analyze(DiffRequest.builder()
                .source(CodeSnapshot.of("plain", "sample.txt", content))
                .target(CodeSnapshot.of("plain", "sample.txt", content))
                .build());
        assertThat(result.getSegments()).isEmpty();
        assertThat(result.getSummary().getTotalChangedLines()).isZero();
    }

    @Test
    void analyzeHonoursContentMasker() {
        DiffConfigurationProperties properties = new DiffConfigurationProperties();
        DiffContentMasker masker = (value, scope) -> value == null
                ? ""
                : value.replaceAll("(?m)^import.+$", "");
        DiffService service = new DiffService(new LineDiffEngine(), new DiffResultAssembler(), masker, properties);

        String source = "import java.util.List;\nclass Demo {}\n";
        String target = "class Demo {}\n";

        DiffResult result = service.analyze(DiffRequest.builder()
                .source(CodeSnapshot.of("java", "Demo.java", source))
                .target(CodeSnapshot.of("java", "Demo.java", target))
                .build());

        assertThat(result.getSegments()).isEmpty();
    }
}
