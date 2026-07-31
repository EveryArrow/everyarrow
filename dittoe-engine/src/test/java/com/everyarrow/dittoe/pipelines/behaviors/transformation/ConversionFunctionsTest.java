package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConversionFunctionsTest {
    private final ExpressionEvaluator evaluator = new EmbeddedDealExpressionEvaluator();
    private final ExpressionContext context = ExpressionContext.of(Map.of());

    @Test
    void convertsValuesToPmdCompatibleRuntimeTypes() throws Exception {
        assertEquals(42, evaluator.evaluate("to_integer(\"42\")", context));
        assertEquals(42.5d, evaluator.evaluate("to_double(\"42.5\")", context));
        assertEquals(new BigDecimal("42.50"), evaluator.evaluate("to_decimal(\"42.50\")", context));
        assertEquals(true, evaluator.evaluate("to_boolean(\"true\")", context));
        assertEquals(LocalDate.of(2026, 7, 19), evaluator.evaluate("to_date(\"2026-07-19\")", context));
        assertEquals(LocalDateTime.of(2026, 7, 19, 14, 30),
                evaluator.evaluate("to_datetime(\"2026-07-19T14:30:00\")", context));
        assertEquals(LocalTime.of(14, 30), evaluator.evaluate("to_time(\"14:30:00\")", context));
        assertEquals(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                evaluator.evaluate("to_uuid(\"123e4567-e89b-12d3-a456-426614174000\")", context));
    }

    @Test
    void propagatesNullAndExposesNullPredicates() throws Exception {
        assertNull(evaluator.evaluate("to_integer(null)", context));
        assertEquals(true, evaluator.evaluate("is_null(to_integer(null))", context));
        assertEquals(false, evaluator.evaluate("is_not_null(null)", context));
    }

    @Test
    void rejectsLossyOrAmbiguousConversions() {
        assertThrows(ExpressionEvaluationException.class,
                () -> evaluator.evaluate("to_integer(\"1.5\")", context));
        assertThrows(ExpressionEvaluationException.class,
                () -> evaluator.evaluate("to_boolean(\"yes\")", context));
        assertThrows(ExpressionEvaluationException.class,
                () -> evaluator.evaluate("to_date(\"07/19/2026\")", context));
    }
}
