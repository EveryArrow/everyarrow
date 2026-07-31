package com.everyarrow.eventengine.core.services.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Compatibility facade for Postgres-backed runtime helpers.
 *
 * Keep this public surface stable until the API/SPI review decides whether
 * plugins should depend on smaller collaborators directly.
 */
public class PostgresHelper {

    static final String ALLOW_PLAINTEXT_DB_CONFIG = DatabaseConnectionProvider.ALLOW_PLAINTEXT_DB_CONFIG;

    private final DatabaseConnectionProvider connectionProvider;
    private final DbFunctionExecutor dbFunctionExecutor;
    private final ServiceCallLogStore serviceCallLogStore;

    public PostgresHelper() {
        this(new DatabaseConnectionProvider());
    }

    PostgresHelper(DatabaseConnectionProvider connectionProvider) {
        this(connectionProvider, new DbFunctionExecutor(connectionProvider), new ServiceCallLogStore(connectionProvider));
    }

    PostgresHelper(DatabaseConnectionProvider connectionProvider,
                   DbFunctionExecutor dbFunctionExecutor,
                   ServiceCallLogStore serviceCallLogStore) {
        this.connectionProvider = connectionProvider;
        this.dbFunctionExecutor = dbFunctionExecutor;
        this.serviceCallLogStore = serviceCallLogStore;
    }

    protected Connection getConnection() throws SQLException {
        return connectionProvider.getConnection();
    }

    protected Connection getConnection(String dbName) throws SQLException {
        return connectionProvider.getConnection(dbName);
    }

    static boolean resolveSsl(String host, String sslValue, String configKey, String allowPlaintextValue) {
        return DatabaseConnectionProvider.resolveSsl(host, sslValue, configKey, allowPlaintextValue);
    }

    public void createServiceCall(ServiceCall serviceCall) {
        serviceCallLogStore.createServiceCall(serviceCall);
    }

    public void updateServiceCall(ServiceCall serviceCall) {
        serviceCallLogStore.updateServiceCall(serviceCall);
    }

    public String process(ServiceCall serviceCall, String schema) {
        return dbFunctionExecutor.process(serviceCall, schema);
    }

    public JsonObject process(String serviceType, String typeName, JsonObject params) {
        return dbFunctionExecutor.process(serviceType, typeName, params);
    }

    public String process(ServiceCall serviceCall) {
        return dbFunctionExecutor.process(serviceCall);
    }

    public JsonObject processDbFunction(String dbName,
                                        String schema,
                                        String function,
                                        String jsonType,
                                        JsonObject params) {
        return dbFunctionExecutor.processDbFunction(dbName, schema, function, jsonType, params);
    }

    public JsonObject processDbFunction(String dbName,
                                        String schema,
                                        String function,
                                        JsonObject params) {
        return dbFunctionExecutor.processDbFunction(dbName, schema, function, params);
    }

    public JsonElement processDbFunctionElement(String dbName,
                                                String schema,
                                                String function,
                                                String jsonType,
                                                JsonObject params) {
        return dbFunctionExecutor.processDbFunctionElement(dbName, schema, function, jsonType, params);
    }

    public JsonElement processDbFunctionElement(String dbName,
                                                String schema,
                                                String function,
                                                JsonObject params) {
        return dbFunctionExecutor.processDbFunctionElement(dbName, schema, function, params);
    }

}
