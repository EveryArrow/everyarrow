package com.everyarrow.dittoe.io.database.servers.snowflake;

import org.junit.jupiter.api.Test;

import java.sql.Driver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeDriverCompatibilityTest {

    @Test
    void configuredDriverLoadsAndAcceptsSnowflakeJdbcUrls() throws Exception {
        SnowflakeServer server = new SnowflakeServer(true);
        String driverClassName = server.getDriverClassName();
        Driver driver = (Driver) Class.forName(driverClassName).getDeclaredConstructor().newInstance();

        assertEquals("net.snowflake.client.jdbc.SnowflakeDriver", driverClassName);
        assertTrue(driver.acceptsURL("jdbc:snowflake://account.snowflakecomputing.com"));
    }
}
