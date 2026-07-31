package com.everyarrow.dittoe.io.database.servers.jdbc;

import com.everyarrow.dittoe.io.database.DatabaseServerConnector;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.DataType;

import java.sql.Connection;

/**
 * Relational database contract shared by JDBC-backed connectors.
 */
public interface JDBCServerConnector extends DatabaseServerConnector {
    Connection getConnection() throws ExecutionException;

    String getSafeResourceName();

    String escapeIdentifierName(String identifierName);

    String getDataTypeCast(String fieldName, DataType dataType);

    String getNativeDataType(DataType dataType);

    DataType getDataType(String columnTypeName) throws ExecutionException;
}
