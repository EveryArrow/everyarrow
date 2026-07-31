package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DealLanguageTest {
    private final ExpressionEvaluator evaluator = new EmbeddedDealExpressionEvaluator();
    private final ExpressionContext context = ExpressionContext.of(Map.of());

    @Test
    void publishesTheVersionedLanguageContract() {
        DealLanguageDescriptor descriptor = DealLanguage.descriptor();

        assertEquals(DealLanguage.CURRENT_VERSION, descriptor.version());
        assertTrue(descriptor.literals().contains("null"));
        assertTrue(descriptor.operators().contains("??"));
        assertEquals(FunctionRegistry.descriptors(), descriptor.functions());
    }

    @Test
    void evaluatesArithmeticWithStandardPrecedence() throws Exception {
        assertEquals(7, evaluator.evaluate("1 + 2 * 3", context));
        assertEquals(9, evaluator.evaluate("(1 + 2) * 3", context));
        assertEquals(-3, evaluator.evaluate("-5 + 2", context));
        assertEquals(2.5d, evaluator.evaluate("5 / 2", context));
        assertEquals(1, evaluator.evaluate("7 % 3", context));
    }

    @Test
    void evaluatesComparisonLogicalAndNullOperators() throws Exception {
        assertEquals(true, evaluator.evaluate("2 < 3 && 3 <= 3", context));
        assertEquals(true, evaluator.evaluate("2 == 2.0", context));
        assertEquals(true, evaluator.evaluate("null == null", context));
        assertEquals("fallback", evaluator.evaluate("null ?? \"fallback\"", context));
        assertEquals(true, evaluator.evaluate("true || (1 / 0 == 0)", context));
        assertEquals(false, evaluator.evaluate("false && (1 / 0 == 0)", context));
        assertNull(evaluator.evaluate("null", context));
    }

    @Test
    void supportsQuotedStringsAndEscapes() throws Exception {
        assertEquals("single quoted", evaluator.evaluate("'single quoted'", context));
        assertEquals("line\nvalue", evaluator.evaluate("\"line\\nvalue\"", context));
        assertEquals("say \"hello\"", evaluator.evaluate("\"say \\\"hello\\\"\"", context));
    }

    @Test
    void reportsTheSourcePositionForSyntaxErrors() {
        ExpressionEvaluationException error = assertThrows(
                ExpressionEvaluationException.class, () -> evaluator.compile("sum(1,@)"));

        DealSyntaxException syntaxError = assertInstanceOf(DealSyntaxException.class, error.getCause());
        assertEquals(6, syntaxError.position());
        assertTrue(syntaxError.getMessage().contains("position 6"));
    }
}
