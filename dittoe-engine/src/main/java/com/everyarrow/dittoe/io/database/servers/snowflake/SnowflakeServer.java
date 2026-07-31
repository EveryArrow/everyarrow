package com.everyarrow.dittoe.io.database.servers.snowflake;

import com.everyarrow.dittoe.io.database.servers.jdbc.JDBCServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.security.SqlSafety;
import org.everyarrow.dittoe.schema.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

public class SnowflakeServer extends JDBCServer {
    /**
     * Constant for property key "schema".
     */
    protected static final String SCHEMA = "schema";
    private static final String ACCOUNT = "account";
    private static final String ROLE = "role";
    private static final String WAREHOUSE = "warehouse";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public SnowflakeServer(Boolean isSource) throws ExecutionException {
        super(isSource ? new SnowflakeReader() : null, isSource ? null : new SnowflakeWriter());

        if (isSource)
            this.getReader().setServer(this);
        else
            this.getWriter().setServer(this);
    }

    @Override
    public void init() throws ExecutionException {
        super.init();
    }

    @Override
    public String getSafeResourceName() {
        String schema = this.getResource().getResourceProperties().get(SCHEMA);
        return "%s.%s".formatted(
                this.escapeIdentifierName(schema),
                this.escapeIdentifierName(this.getResource().getResourceName()));
    }

    @Override
    public String escapeIdentifierName(String identifierName) {
        return SqlSafety.quoteSnowflakeIdentifier(identifierName);
    }

    @Override
    protected void validateConnectionProperties() throws ExecutionException {
        this.validateConnectionStringProperties();
        this.validateConnectionCredentialProperties();
    }

    @Override
    protected Properties getCredentialProperties() {
        Properties connectionProperties = new Properties();

        for (String property : List.of(USER, ACCOUNT, PASSWORD, ACCOUNT, DATABASE, WAREHOUSE, ROLE)) {
            connectionProperties.put(property, this.getConnectionDetails().get(property));
        }

        connectionProperties.put(SCHEMA, this.getResource().getResourceProperties().get(SCHEMA));

        return connectionProperties;
    }

    @Override
    protected String getConnectionString() {
        return "jdbc:snowflake://%s.snowflakecomputing.com".formatted(this.getCredentialProperties().get(ACCOUNT));
    }

    @Override
    protected String getDriverClassName() {
        return "net.snowflake.client.jdbc.SnowflakeDriver";
    }

    @Override
    public String getNativeDataType(DataType dataType) {
        return switch (dataType) {
            case DataType.UUID -> "string"; // Snowflake does not have a native UUID type, stored as STRING
            case DataType.DateTime -> "timestamp_ntz"; // No time zone timestamp
            case DataType.Number -> "number"; // General number type
            case DataType.Date -> "date";
            case DataType.Time -> "time";
            case DataType.TimeWithTimeZone -> "time"; // Snowflake does not support TIMETZ separately
            case DataType.Double -> "double";
            case DataType.Integer -> "integer";
            case DataType.Boolean -> "boolean";
            case DataType.Decimal -> "decimal";
            case DataType.DateTimeWithTimeZone -> "timestamp_tz"; // Time zone-aware timestamp
            case DataType.JSON, DataType.JSONB -> "variant"; // Snowflake uses VARIANT for JSON-like structures
            case DataType.String, DataType.Bytes, DataType.XML -> "string"; // Use STRING instead of TEXT
            case DataType.Array -> "array"; // Snowflake supports ARRAY type natively
        };
    }

    @Override
    public DataType getDataType(String columnTypeName) throws ExecutionException {
        return switch (columnTypeName) {
            case "NUMBER" -> DataType.Number;
            case "VARCHAR" -> DataType.String;
            case "DATE" -> DataType.Date;
            case "TIMESTAMPNTZ" -> DataType.DateTime;
            case "TIMESTAMPTZ" -> DataType.DateTimeWithTimeZone;
            case "BOOLEAN" -> DataType.Boolean;
            case "DOUBLE" -> DataType.Double;

            default -> null;
        };
    }

    private void validateConnectionStringProperties() throws ExecutionException {
        String[] keys = new String[]{ACCOUNT, ROLE, WAREHOUSE, DATABASE};
        Map<String, String> connectionDetails = this.getConnectionDetails();

        for (String key : keys) {
            if (!connectionDetails.containsKey(key))
                throw new ExecutionException("%s property must be provided in server".formatted(key));
        }

        if (!this.getResource().getResourceProperties().containsKey(SCHEMA))
            throw new ExecutionException("%s property must be provided in resource".formatted(SCHEMA));
    }

    private void validateConnectionCredentialProperties() throws ExecutionException {
        String[] keys = new String[]{USER, PASSWORD};
        Map<String, String> properties = this.getConnectionDetails();

        for (String key : keys) {
            if (!properties.containsKey(key))
                throw new ExecutionException("%s property must be provided in server".formatted(key));
        }
    }

    @Override
    protected boolean isSystemSchema(String schemaName) {
        if (schemaName == null) {
            return true;
        }

        return switch (schemaName.toUpperCase(Locale.ROOT)) {
            case "INFORMATION_SCHEMA", "ACCOUNT_USAGE", "ORGANIZATION_USAGE", "READER_ACCOUNT_USAGE" -> true;
            default -> false;
        };
    }

    @Override
    public String getJsonBuilderQuery() {
        return "to_json(object_construct(t.*))";
    }

    public Logger getLogger() {
        return logger;
    }
}
