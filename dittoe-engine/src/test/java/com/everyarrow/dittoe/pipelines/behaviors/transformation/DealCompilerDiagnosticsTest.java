package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DealCompilerDiagnosticsTest {
    private final ExpressionEvaluator evaluator = new EmbeddedDealExpressionEvaluator();

    @Test
    void reportsASyntaxCodeAndExactUnexpectedCharacterSpan() {
        String source = "sum(1,@)";

        DealCompilationException error = assertThrows(
                DealCompilationException.class, () -> evaluator.compile(source));

        assertEquals(DealDiagnosticCode.SYNTAX_ERROR, error.diagnostic().code());
        assertEquals("@", selectedSource(source, error.diagnostic().span()));
    }

    @Test
    void reportsAnUnknownFunctionCodeAndCallSpan() {
        String source = "missing(quantity)";

        DealCompilationException error = compileError(schema(
                field("quantity", DealType.INTEGER, false, false),
                field("result", DealType.INTEGER, false, true)), source);

        assertEquals(DealDiagnosticCode.UNKNOWN_FUNCTION, error.diagnostic().code());
        assertEquals("result", error.diagnostic().fieldName());
        assertEquals(source, selectedSource(source, error.diagnostic().span()));
    }

    @Test
    void reportsTheNestedFunctionThatIntroducedATypeMismatch() {
        String source = "sum(1,upper(quantity))";

        DealCompilationException error = compileError(schema(
                field("quantity", DealType.INTEGER, false, false),
                field("result", DealType.INTEGER, false, true)), source);

        assertEquals(DealDiagnosticCode.TYPE_MISMATCH, error.diagnostic().code());
        assertEquals("upper(quantity)", selectedSource(source, error.diagnostic().span()));
    }

    @Test
    void distinguishesNullabilityFromGeneralTypeMismatches() {
        String source = "upper(name)";

        DealCompilationException error = compileError(schema(
                field("name", DealType.STRING, true, false),
                field("result", DealType.STRING, false, true)), source);

        assertEquals(DealDiagnosticCode.NULLABILITY_MISMATCH, error.diagnostic().code());
        assertEquals(source, selectedSource(source, error.diagnostic().span()));
    }

    @Test
    void reportsUnsupportedExplicitConversionsSeparately() {
        String source = "to_integer(enabled)";

        DealCompilationException error = compileError(schema(
                field("enabled", DealType.BOOLEAN, false, false),
                field("result", DealType.INTEGER, false, true)), source);

        assertEquals(DealDiagnosticCode.UNSUPPORTED_CONVERSION, error.diagnostic().code());
        assertEquals(source, selectedSource(source, error.diagnostic().span()));
    }

    private DealCompilationException compileError(DealSchema schema, String source) {
        return assertThrows(DealCompilationException.class,
                () -> TransformationPlan.compile(schema,
                        List.of(new TransformationPlan.FieldExpression("result", source)), evaluator));
    }

    private static String selectedSource(String source, DealSourceSpan span) {
        return source.substring(span.start(), span.end());
    }

    private static DealSchema schema(DealSchema.FieldDefinition... fields) {
        return DealSchema.of(List.of(fields));
    }

    private static DealSchema.FieldDefinition field(String name,
                                                    DealType type,
                                                    boolean nullable,
                                                    boolean computed) {
        return new DealSchema.FieldDefinition(name, type, nullable, computed);
    }
}
