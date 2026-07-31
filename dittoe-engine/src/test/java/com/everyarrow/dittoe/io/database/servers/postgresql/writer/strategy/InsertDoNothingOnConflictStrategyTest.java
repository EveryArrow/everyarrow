package com.everyarrow.dittoe.io.database.servers.postgresql.writer.strategy;

import com.everyarrow.dittoe.io.database.servers.postgresql.PostgreSQLServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;
import org.everyarrow.dittoe.schema.Resource;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsertDoNothingOnConflictStrategyTest {

    @Test
    void quotesMetadataControlledConflictConstraintName() throws Exception {
        String sql = buildOneRowInsert("orders_id_key\"; drop table users; --");

        assertTrue(sql.contains(
                " on conflict on constraint \"orders_id_key\"\"; drop table users; --\" do nothing"));
        assertFalse(sql.contains(" on conflict on constraint orders_id_key\"; drop table users; -- do nothing"));
    }

    @Test
    void rejectsBlankConflictConstraintName() throws ExecutionException {
        InsertDoNothingOnConflictStrategy strategy = createStrategyWithConstraint(" ");

        assertThrows(ExecutionException.class, strategy::init);
    }

    @SuppressWarnings("unchecked")
    private static String buildOneRowInsert(String constraintName) throws Exception {
        InsertDoNothingOnConflictStrategy strategy = createStrategyWithConstraint(constraintName);
        strategy.init();

        Class<?> builderClass = Class.forName(InsertDoNothingOnConflictStrategy.class.getName() + "$InsertStatementBuilder");
        Constructor<?> constructor = builderClass.getDeclaredConstructor(InsertDoNothingOnConflictStrategy.class, List.class);
        constructor.setAccessible(true);
        Object builder = constructor.newInstance(strategy, List.of(new Field("id", DataType.Integer)));

        Method getRows = builderClass.getDeclaredMethod("getRows");
        getRows.setAccessible(true);
        ((List<Integer>) getRows.invoke(builder)).add(0);

        Method build = builderClass.getDeclaredMethod("build");
        build.setAccessible(true);
        return (String) build.invoke(builder);
    }

    private static InsertDoNothingOnConflictStrategy createStrategyWithConstraint(String constraintName)
            throws ExecutionException {
        PostgreSQLServer server = new PostgreSQLServer(false);
        Resource resource = new Resource();
        resource.setResourceName("orders");
        resource.setResourceProperties(new HashMap<>(Map.of(
                "schema", "public",
                InsertDoNothingOnConflictStrategy.INSERT_CONFLICT_CONSTRAINT, constraintName
        )));
        server.setResource(resource);

        InsertDoNothingOnConflictStrategy strategy = new InsertDoNothingOnConflictStrategy();
        strategy.setWriter(server.getWriter());
        return strategy;
    }
}
