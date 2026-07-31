package com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsertStrategyTest {

    @Test
    void recognizesPostgreSqlUndefinedTableBySqlState() {
        SQLException exception = new SQLException(
                "ERROR: relation \"humanresources.vemployee\" does not exist",
                "42P01");

        assertTrue(InsertStrategy.isMissingTableException(exception));
    }

    @Test
    void recognizesPostgreSqlUndefinedTableByMessage() {
        SQLException exception = new SQLException(
                "ERROR: relation \"humanresources.vemployee\" does not exist");

        assertTrue(InsertStrategy.isMissingTableException(exception));
    }

    @Test
    void doesNotTreatOtherSqlErrorsAsMissingTables() {
        SQLException exception = new SQLException(
                "ERROR: duplicate key value violates unique constraint",
                "23505");

        assertFalse(InsertStrategy.isMissingTableException(exception));
    }
}
