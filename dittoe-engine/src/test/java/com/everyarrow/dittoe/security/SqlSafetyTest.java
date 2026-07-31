package com.everyarrow.dittoe.security;

import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.security.SqlSafety;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlSafetyTest {
    @Test
    void rejectsRawFilterExpressionByDefault() {
        Map<String, String> properties = Map.of("filterExpression", "1=1; drop table users");

        assertThrows(ExecutionException.class, () -> SqlSafety.getUnsafeFilterExpression(properties));
    }

    @Test
    void allowsRawFilterExpressionOnlyWithExplicitLegacyFlag() throws ExecutionException {
        Map<String, String> properties = Map.of(
                "filterExpression", "status = 'ACTIVE'",
                SqlSafety.ALLOW_UNSAFE_FILTER_EXPRESSION, "true");

        assertEquals("status = 'ACTIVE'", SqlSafety.getUnsafeFilterExpression(properties));
    }

    @Test
    void requiresFieldToExistInMetadata() throws ExecutionException {
        List<Field> fields = List.of(new Field("created_at", DataType.DateTime));

        assertEquals("created_at", SqlSafety.requireKnownField("created_at", fields));
        assertThrows(ExecutionException.class, () -> SqlSafety.requireKnownField("missing", fields));
    }

    @Test
    void quotesLiteralsByEscapingSingleQuotes() throws ExecutionException {
        assertEquals("'Andy''s row'", SqlSafety.quoteSqlLiteral("Andy's row"));
    }

    @Test
    void rejectsUnsafeIdentifierSegments() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlSafety.quoteBigQueryPath("project", "dataset", "table;DROP"));
        assertThrows(IllegalArgumentException.class,
                () -> SqlSafety.quoteSnowflakeIdentifier("schema.name"));
    }

    @Test
    void quotesSafeBigQueryPath() {
        assertEquals("`project-1.dataset_1.table_1`",
                SqlSafety.quoteBigQueryPath("project-1", "dataset_1", "table_1"));
    }
}
