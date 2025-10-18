package com.example.codecompare.rebuild.diff;

import com.example.codecompare.rebuild.block.model.CodeSnapshot;
import com.example.codecompare.rebuild.diff.config.DiffConfigurationProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DiffResultAssemblerSimilarityTest {

    private DiffService createService() {
        DiffConfigurationProperties properties = new DiffConfigurationProperties();
        properties.setMaxFileSize(1024 * 1024);
        return new DiffService(new LineDiffEngine(), new DiffResultAssembler(), DiffContentMasker.noop(), properties);
    }

    private double similarityScore(String source, String target) {
        DiffService service = createService();
        DiffResult result = service.analyze(DiffRequest.builder()
                .source(CodeSnapshot.of("java", "Sample.java", source))
                .target(CodeSnapshot.of("java", "Sample.java", target))
                .build());
        assertThat(result.getSegments()).hasSize(1);
        return result.getSegments().get(0).getSimilarityScore();
    }

    @Test
    void renamedIdentifierMaintainsPartialSimilarityThroughTokens() {
        String source = "int totalCount = countItems(items);\n";
        String target = "int matchedCount = countItems(items);\n";

        double score = similarityScore(source, target);

        assertThat(score).isCloseTo(30.0d, within(0.0001d));
    }

    @Test
    void completelyDifferentStatementsProduceZeroSimilarity() {
        String source = "return calculateTotal(amount, tax);\n";
        String target = "logger.info(\"done\");\n";

        double score = similarityScore(source, target);

        assertThat(score).isEqualTo(0.0d);
    }
}
