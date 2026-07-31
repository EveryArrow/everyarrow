package com.everyarrow.dittoe.io.database.servers.postgresql;

import org.everyarrow.dittoe.schema.DataType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostgreSQLServerDataTypeTest {

    private final PostgreSQLServer server = new PostgreSQLServer(true);

    PostgreSQLServerDataTypeTest() throws Exception {
    }

    @Test
    void mapsInformationSchemaTypeNames() throws Exception {
        assertEquals(DataType.Integer, server.getDataType("bigint"));
        assertEquals(DataType.Decimal, server.getDataType("double precision"));
        assertEquals(DataType.Time, server.getDataType("time without time zone"));
        assertEquals(DataType.TimeWithTimeZone, server.getDataType("time with time zone"));
        assertEquals(DataType.DateTimeWithTimeZone, server.getDataType("timestamp with time zone"));
    }
}
