package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformationPlanTest {
    private final ExpressionEvaluator evaluator = new EmbeddedDealExpressionEvaluator();

    @Test
    void ordersComputedFieldsByReferencesRatherThanPmdListOrder() throws Exception {
        DealSchema schema = schema(
                field("price", false),
                field("total", true),
                field("subtotal", true));

        TransformationPlan plan = TransformationPlan.compile(schema, List.of(
                expression("total", "sum(subtotal,2)"),
                expression("subtotal", "sum(price,1)")), evaluator);

        assertEquals(List.of("subtotal", "total"), plan.fields().stream()
                .map(field -> field.field().name())
                .toList());
    }

    @Test
    void rejectsUnknownPmdFieldReferencesBeforeRowEvaluation() {
        DealSchema schema = schema(field("total", true));

        ExpressionEvaluationException error = assertThrows(ExpressionEvaluationException.class,
                () -> TransformationPlan.compile(schema,
                        List.of(expression("total", "sum(missing,1)")), evaluator));

        assertTrue(error.getMessage().contains("unknown PMD Resource field 'missing'"));
    }

    @Test
    void rejectsCircularComputedFieldDependencies() {
        DealSchema schema = schema(field("a", true), field("b", true));

        ExpressionEvaluationException error = assertThrows(ExpressionEvaluationException.class,
                () -> TransformationPlan.compile(schema, List.of(
                        expression("a", "sum(b,1)"),
                        expression("b", "sum(a,1)")), evaluator));

        assertTrue(error.getMessage().contains("Circular computed DEAL field dependency"));
    }

    @Test
    void rejectsAnOperatorAppliedToAnIncompatiblePmdFieldType() {
        DealSchema schema = schema(
                field("name", DealType.STRING, false, false),
                field("result", DealType.INTEGER, false, true));

        ExpressionEvaluationException error = compileError(schema, "result", "name + 1");

        assertTrue(error.getMessage().contains("Arithmetic operator requires NUMBER"));
        assertTrue(error.getMessage().contains("STRING"));
    }

    @Test
    void rejectsAFunctionArgumentWithTheWrongPmdType() {
        DealSchema schema = schema(
                field("quantity", DealType.INTEGER, false, false),
                field("result", DealType.STRING, false, true));

        ExpressionEvaluationException error = compileError(schema, "result", "upper(quantity)");

        assertTrue(error.getMessage().contains("argument 1 expected STRING"));
        assertTrue(error.getMessage().contains("type INTEGER"));
    }

    @Test
    void rejectsIncompatibleConditionalResultBranches() {
        DealSchema schema = schema(field("result", DealType.STRING, false, true));

        ExpressionEvaluationException error = compileError(schema, "result", "if(true,\"yes\",1)");

        assertTrue(error.getMessage().contains("result branches has incompatible types STRING and INTEGER"));
    }

    @Test
    void requiresNullHandlingBeforeUsingANullableFieldInATypedFunction() {
        DealSchema schema = schema(
                field("name", DealType.STRING, true, false),
                field("result", DealType.STRING, false, true));

        ExpressionEvaluationException error = compileError(schema, "result", "upper(name)");

        assertTrue(error.getMessage().contains("argument 1 expected STRING"));
        assertTrue(error.getMessage().contains("STRING (nullable)"));
    }

    @Test
    void coalesceMakesANullableValueSafeForANonNullableTarget() throws Exception {
        DealSchema schema = schema(
                field("name", DealType.STRING, true, false),
                field("result", DealType.STRING, false, true));

        TransformationPlan plan = TransformationPlan.compile(schema,
                List.of(expression("result", "upper(coalesce(name,\"\"))")), evaluator);

        assertEquals("result", plan.fields().getFirst().field().name());
    }

    @Test
    void rejectsANullableExpressionForANonNullablePmdField() {
        DealSchema schema = schema(
                field("name", DealType.STRING, false, false),
                field("result", DealType.STRING, false, true));

        ExpressionEvaluationException error = compileError(schema, "result", "nullif(name,name)");

        assertTrue(error.getMessage().contains("nullable type STRING"));
        assertTrue(error.getMessage().contains("is non-nullable"));
    }

    @Test
    void permitsSafeImplicitNumericWidening() throws Exception {
        DealSchema schema = schema(
                field("quantity", DealType.INTEGER, false, false),
                field("result", DealType.DECIMAL, false, true));

        TransformationPlan plan = TransformationPlan.compile(schema,
                List.of(expression("result", "sum(quantity,1)")), evaluator);

        assertEquals("result", plan.fields().getFirst().field().name());
    }

    @Test
    void requiresAnExplicitCastForPotentiallyLossyNumericCoercion() {
        DealSchema schema = schema(
                field("measurement", DealType.DOUBLE, false, false),
                field("result", DealType.INTEGER, false, true));

        ExpressionEvaluationException error = compileError(schema, "result", "measurement");

        assertTrue(error.getMessage().contains("has type DOUBLE"));
        assertTrue(error.getMessage().contains("to_integer"));
    }

    @Test
    void acceptsAnExplicitCastForPotentiallyLossyNumericCoercion() throws Exception {
        DealSchema schema = schema(
                field("measurement", DealType.DOUBLE, false, false),
                field("result", DealType.INTEGER, false, true));

        TransformationPlan plan = TransformationPlan.compile(schema,
                List.of(expression("result", "to_integer(measurement)")), evaluator);

        assertEquals("result", plan.fields().getFirst().field().name());
    }

    @Test
    void rejectsAStaticallyUnsupportedExplicitConversion() {
        DealSchema schema = schema(
                field("enabled", DealType.BOOLEAN, false, false),
                field("result", DealType.INTEGER, false, true));

        ExpressionEvaluationException error = compileError(schema, "result", "to_integer(enabled)");

        assertTrue(error.getMessage().contains("cannot convert expression type BOOLEAN"));
    }

    @Test
    void rejectsAnOutOfRangeIntegerLiteralWithoutAnExplicitCheckedCast() {
        DealSchema schema = schema(field("result", DealType.INTEGER, false, true));

        ExpressionEvaluationException error = compileError(schema, "result", "2147483648");

        assertTrue(error.getMessage().contains("has type NUMBER"));
        assertTrue(error.getMessage().contains("to_integer"));
    }

    @Test
    void preservesDecimalArithmeticWhenMixedWithADouble() throws Exception {
        DealSchema schema = schema(
                field("decimal_value", DealType.DECIMAL, false, false),
                field("double_value", DealType.DOUBLE, false, false),
                field("result", DealType.DECIMAL, false, true));

        TransformationPlan plan = TransformationPlan.compile(schema,
                List.of(expression("result", "decimal_value + double_value")), evaluator);

        assertEquals("result", plan.fields().getFirst().field().name());
    }

    @Test
    void permitsRepresentationsAcceptedByStructuredPmdTypes() throws Exception {
        DealSchema schema = schema(
                field("json_result", DealType.JSON, false, true),
                field("xml_result", DealType.XML, false, true));

        TransformationPlan plan = TransformationPlan.compile(schema, List.of(
                expression("json_result", "[1,2,3]"),
                expression("xml_result", "\"<root/>\"")), evaluator);

        assertEquals(List.of("json_result", "xml_result"), plan.fields().stream()
                .map(field -> field.field().name())
                .toList());
    }

    private static DealSchema schema(DealSchema.FieldDefinition... fields) {
        return DealSchema.of(List.of(fields));
    }

    private static DealSchema.FieldDefinition field(String name, boolean computed) {
        return new DealSchema.FieldDefinition(name, DealType.INTEGER, false, computed);
    }

    private static DealSchema.FieldDefinition field(String name,
                                                    DealType type,
                                                    boolean nullable,
                                                    boolean computed) {
        return new DealSchema.FieldDefinition(name, type, nullable, computed);
    }

    private ExpressionEvaluationException compileError(DealSchema schema, String name, String source) {
        return assertThrows(ExpressionEvaluationException.class,
                () -> TransformationPlan.compile(schema, List.of(expression(name, source)), evaluator));
    }

    private static TransformationPlan.FieldExpression expression(String name, String expression) {
        return new TransformationPlan.FieldExpression(name, expression);
    }
}
