package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DealLanguageArtifactTest {
    private static final Path CANONICAL_ARTIFACT =
            Path.of("src/main/resources/deal/deal-language-1.1.json");
    private static final Path GENERATED_ARTIFACT =
            Path.of("target/generated-deal/deal-language-1.1.json");

    @Test
    void engineConformsToThePublishedPortableCases() throws Exception {
        ExpressionEvaluator evaluator = new EmbeddedDealExpressionEvaluator();
        for (DealConformanceCase fixture : DealConformanceFixtures.cases()) {
            Object actual = evaluator.evaluate(fixture.expression(), ExpressionContext.of(Map.of()));
            assertEquals(fixture.expected(), actual, fixture.name());
        }
    }

    @Test
    void checkedInCatalogMatchesTheEngineOwnedDescriptor() throws Exception {
        String generated = DealLanguageArtifacts.catalogJson();
        Files.createDirectories(GENERATED_ARTIFACT.getParent());
        Files.writeString(GENERATED_ARTIFACT, generated);

        assertEquals(normalize(Files.readString(CANONICAL_ARTIFACT)), normalize(generated),
                "Regenerate the DEAL catalog after changing the language contract");
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").trim();
    }
}
