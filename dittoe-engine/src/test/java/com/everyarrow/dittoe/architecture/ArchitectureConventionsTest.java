package com.everyarrow.dittoe.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureConventionsTest {
    private static final Path SOURCE_ROOT = Path.of("src/main/java");

    @Test
    void driverKeepsOnlyThinCompatibilityWrapper() throws IOException {
        String driverSource = readSource("com/everyarrow/dittoe/pipelines/execution/Driver.java");

        assertTrue(driverSource.contains("@Deprecated(since = \"1.0\", forRemoval = false)"));
        assertTrue(Pattern.compile(
                        "public\\s+static\\s+void\\s+driver\\s*\\(String\\[]\\s+args\\)\\s+throws\\s+Exception\\s*\\{\\s*run\\(args\\);\\s*\\}",
                        Pattern.DOTALL)
                .matcher(driverSource)
                .find());
        assertTrue(driverSource.contains("new PipelineRunApplication().run(args);"));
    }

    @Test
    void executionPackageDoesNotUseUnderscorePrefixedMethods() throws IOException {
        Pattern underscoreMethod = Pattern.compile("\\b_[A-Za-z][A-Za-z0-9_]*\\s*\\(");
        List<String> matches = new ArrayList<>();

        Path executionPackage = SOURCE_ROOT.resolve("com/everyarrow/dittoe/pipelines/execution");
        try (var paths = Files.walk(executionPackage)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> collectMatches(path, underscoreMethod, matches));
        }

        assertTrue(matches.isEmpty(), "Underscore-prefixed methods found: " + matches);
    }

    @Test
    void runnerDelegatesBehaviorConstructionAndStageExecution() throws IOException {
        String runnerSource = readSource("com/everyarrow/dittoe/pipelines/execution/UniversalRunnerTemplate.java");

        assertTrue(runnerSource.contains("new BehaviorPlanFactory"));
        assertTrue(runnerSource.contains("new PipelineStageExecutor"));
        assertFalse(runnerSource.contains("new SourceRetrieval"));
        assertFalse(runnerSource.contains("new TargetInsertUpdate"));
        assertFalse(runnerSource.contains("new Transformation"));
        assertFalse(runnerSource.contains("_executeBehaviors"));
    }

    @Test
    void transformationDependsOnEvaluatorBoundary() throws IOException {
        String transformationSource = readSource(
                "com/everyarrow/dittoe/pipelines/behaviors/transformation/Transformation.java");

        assertTrue(transformationSource.contains("ExpressionEvaluator"));
        assertTrue(transformationSource.contains("TransformationPlan.compile"));
        assertFalse(transformationSource.contains("Expression.evaluate"));
        assertFalse(transformationSource.contains("Environment."));
        assertFalse(transformationSource.contains("Parser"));
        assertFalse(transformationSource.contains("Tokenizer"));
        assertFalse(transformationSource.contains("FunctionRegistry"));
    }

    @Test
    void embeddedDealEvaluatorIsTheOnlyLegacyExpressionAdapter() throws IOException {
        String evaluatorSource = readSource(
                "com/everyarrow/dittoe/pipelines/behaviors/transformation/EmbeddedDealExpressionEvaluator.java");

        assertTrue(evaluatorSource.contains("Expression.parse"));
        assertTrue(evaluatorSource.contains("parsedExpression"));
        assertFalse(evaluatorSource.contains("Environment"));
    }

    @Test
    void dealCoreDoesNotDependOnPmdSchemaTypes() throws IOException {
        String dealSchemaSource = readSource(
                "com/everyarrow/dittoe/pipelines/behaviors/transformation/DealSchema.java");
        String expressionContextSource = readSource(
                "com/everyarrow/dittoe/pipelines/behaviors/transformation/ExpressionContext.java");

        assertFalse(dealSchemaSource.contains("org.everyarrow.dittoe.schema"));
        assertFalse(expressionContextSource.contains("org.everyarrow.dittoe.schema"));
    }

    private static String readSource(String sourcePath) throws IOException {
        return Files.readString(SOURCE_ROOT.resolve(sourcePath));
    }

    private static void collectMatches(Path path, Pattern pattern, List<String> matches) {
        try {
            String source = Files.readString(path);
            var matcher = pattern.matcher(source);
            while (matcher.find()) {
                matches.add(path + ":" + matcher.group());
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
