package com.everyarrow.dittoe.io.database.servers.mysql;

import org.junit.jupiter.api.Test;

import java.sql.Driver;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySQLDriverCompatibilityTest {

    @Test
    void mariadbDriverPreservesTheMysqlConnectorContract() throws Exception {
        MySQLServer server = new MySQLServer(true);
        server.setConnectionDetails(Map.of(
                "host", "mysql.example.test",
                "port", "3306",
                "database", "customer"
        ));

        String connectionString = server.getConnectionString();
        String driverClassName = server.getDriverClassName();
        Driver driver = (Driver) Class.forName(driverClassName).getDeclaredConstructor().newInstance();

        assertEquals("jdbc:mysql://mysql.example.test:3306/customer?permitMysqlScheme=true", connectionString);
        assertEquals("org.mariadb.jdbc.Driver", driverClassName);
        assertTrue(driver.acceptsURL(connectionString));
    }
}
