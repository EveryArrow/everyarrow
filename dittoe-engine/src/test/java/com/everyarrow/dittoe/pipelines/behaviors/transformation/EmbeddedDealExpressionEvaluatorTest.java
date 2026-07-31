package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import org.junit.jupiter.api.Test;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.SourceField;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmbeddedDealExpressionEvaluatorTest {
    @Test
    void evaluatesWithContextAndCarriesAssignmentsInsideTheContext() throws Exception {
        ExpressionEvaluator evaluator = new EmbeddedDealExpressionEvaluator();
        ExpressionContext context = ExpressionContext.of(Map.of("a", 2, "b", 3));

        assertEquals(5, evaluator.evaluate("c=sum(a,b)", context));
        assertEquals(6, evaluator.evaluate("sum(c,1)", context));
    }

    @Test
    void doesNotLeakVariablesBetweenContexts() throws Exception {
        ExpressionEvaluator evaluator = new EmbeddedDealExpressionEvaluator();
        ExpressionContext context = ExpressionContext.of(Map.of("a", 2, "b", 3));

        evaluator.evaluate("c=sum(a,b)", context);

        ExpressionContext emptyContext = ExpressionContext.of(Map.of());
        assertThrows(ExpressionEvaluationException.class, () -> evaluator.evaluate("c", emptyContext));
    }

    @Test
    void resourceContextOnlyExposesFieldsDeclaredByThePmd() throws Exception {
        ExpressionEvaluator evaluator = new EmbeddedDealExpressionEvaluator();
        List<Field> fields = List.of(
                new Field("price", DataType.Integer),
                new SourceField("total", DataType.Integer, "sum(price,1)"));
        ExpressionContext context = ExpressionContext.forRow(
                PmdDealSchemaAdapter.fromResourceFields(fields), Map.of("price", 4, "connectorOnlyValue", 99));

        assertEquals(5, evaluator.evaluate("sum(price,1)", context));
        assertThrows(ExpressionEvaluationException.class,
                () -> evaluator.evaluate("connectorOnlyValue", context));
        assertThrows(ExpressionEvaluationException.class,
                () -> evaluator.evaluate("undeclared=sum(price,1)", context));
    }

    @Test
    void computedValuesBecomeAvailableToFollowingExpressions() throws Exception {
        ExpressionEvaluator evaluator = new EmbeddedDealExpressionEvaluator();
        List<Field> fields = List.of(
                new Field("price", DataType.Integer),
                new SourceField("subtotal", DataType.Integer, "sum(price,1)"),
                new SourceField("total", DataType.Integer, "sum(subtotal,2)"));
        ExpressionContext context = ExpressionContext.forRow(
                PmdDealSchemaAdapter.fromResourceFields(fields), Map.of("price", 4));

        Object subtotal = evaluator.evaluate("sum(price,1)", context);
        context.setVariable("subtotal", subtotal);

        assertEquals(7, evaluator.evaluate("sum(subtotal,2)", context));
    }

    @Test
    void evaluatesIndependentContextsConcurrently() throws Exception {
        ExpressionEvaluator evaluator = new EmbeddedDealExpressionEvaluator();
        List<Callable<Integer>> evaluations = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            int value = i;
            evaluations.add(() -> (Integer) evaluator.evaluate(
                    "sum(value,1)", ExpressionContext.of(Map.of("value", value))));
        }

        try (var executor = Executors.newFixedThreadPool(8)) {
            var results = executor.invokeAll(evaluations);
            for (int i = 0; i < results.size(); i++) {
                assertEquals(i + 1, results.get(i).get());
            }
        }
    }

    @Test
    void rejectsTrailingTokensDuringCompilation() {
        ExpressionEvaluator evaluator = new EmbeddedDealExpressionEvaluator();

        assertThrows(ExpressionEvaluationException.class, () -> evaluator.compile("sum(1,2) extra"));
    }

    @Test
    void rejectsUnknownFunctionsAndInvalidArityDuringCompilation() {
        ExpressionEvaluator evaluator = new EmbeddedDealExpressionEvaluator();

        assertThrows(ExpressionEvaluationException.class, () -> evaluator.compile("unknown_function(1)"));
        assertThrows(ExpressionEvaluationException.class, () -> evaluator.compile("sum()"));
        assertThrows(ExpressionEvaluationException.class, () -> evaluator.compile("if(true,1)"));
    }

    @Test
    void conditionalsAndNullFunctionsDoNotEvaluateUnselectedArguments() throws Exception {
        ExpressionEvaluator evaluator = new EmbeddedDealExpressionEvaluator();
        ExpressionContext context = ExpressionContext.of(Map.of());

        assertEquals("selected", evaluator.evaluate(
                "if(true,\"selected\",substring(\"x\",5,1))", context));
        assertEquals("selected", evaluator.evaluate(
                "caseWhen(false,substring(\"x\",5,1),true,\"selected\",\"default\")", context));
        assertEquals("selected", evaluator.evaluate(
                "coalesce(\"selected\",substring(\"x\",5,1))", context));
        assertEquals(false, evaluator.evaluate(
                "and(false,substring(\"x\",5,1))", context));
        assertEquals(true, evaluator.evaluate(
                "or(true,substring(\"x\",5,1))", context));
    }

    @Test
    void validatesSelectedFunctionArgumentsAgainstTheCatalogType() {
        ExpressionEvaluator evaluator = new EmbeddedDealExpressionEvaluator();

        ExpressionEvaluationException error = assertThrows(ExpressionEvaluationException.class,
                () -> evaluator.evaluate("sum(\"not-a-number\",1)", ExpressionContext.of(Map.of())));

        assertEquals("DEAL function 'sum' argument 1 expected NUMBER but received String",
                error.getCause().getMessage());
    }
}
