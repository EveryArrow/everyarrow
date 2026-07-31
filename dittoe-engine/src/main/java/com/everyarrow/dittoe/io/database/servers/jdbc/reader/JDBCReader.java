package com.everyarrow.dittoe.io.database.servers.jdbc.reader;

import com.everyarrow.dittoe.io.database.DatabaseReader;
import com.everyarrow.dittoe.io.database.servers.jdbc.JDBCServer;
import com.everyarrow.dittoe.pipelines.behaviors.microbatch.MicroBatchManager;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.DataType;
import com.everyarrow.dittoe.utility.Utils;
import com.everyarrow.dittoe.utility.security.SqlSafety;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JDBCReader extends DatabaseReader {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Constructor.
     *
     * @throws ExecutionException Thrown if initialization fails.
     */
    public JDBCReader() throws ExecutionException {
        super();
    }

    @Override
    public JDBCServer getServer() {
        return (JDBCServer) super.getServer();
    }


    private ResultSet fetchTableMetadata(String tableName) throws SQLException, ExecutionException {
        return this.getServer()
                .getConnection()
                .getMetaData()
                .getTables(null, null, tableName, null);
    }


    public String getNewRecordsCondition() throws ExecutionException {
        Map<String, String> properties = this.getServer().getResource().getResourceProperties();

        if (!Utils.shouldGetNewRecords(properties)) {
            return null;
        }

        if (!properties.containsKey("newRecordsFieldValue")) {
            return null;
        }

        String fieldName = SqlSafety.requireKnownField(properties.get("newRecordsField"),
                this.getServer().getResource().getFields());
        String escapedFieldName = this.getServer().escapeIdentifierName(fieldName);
        String condition = "%s > %s".formatted(
                this.getServer().getDataTypeCast(escapedFieldName, DataType.valueOf(properties.get("newRecordsFieldType"))),
                SqlSafety.quoteSqlLiteral(properties.get("newRecordsFieldValue")));

        this.getLogger().info("Getting new records where {}", condition);

        return Utils.addParentheses(condition);
    }

    /**
     * Retrieves the filter expression specified in resource properties.
     *
     * @return Filter expression string or null if not specified.
     */
    public String getFilterExpression() throws ExecutionException {
        String filterExpression = SqlSafety.getUnsafeFilterExpression(this.getServer().getResource().getResourceProperties());

        if (filterExpression == null) {
            return null;
        }

        this.getLogger().warn("Using trusted legacy raw filterExpression. This is disabled by default for public-safe PMDs.");

        return Utils.addParentheses(filterExpression);
    }

    /**
     * Constructs a combined SQL condition using micro-batch, new records, and filter expressions.
     * Constructs the SQL condition for micro-batching based on pipeline metadata.
     *
     * @return SQL condition string for micro-batching or null if not applicable.
     */
    private String getMicroBatchCondition() throws ExecutionException {
        MicroBatchManager.Batch batch = MicroBatchManager.getBatch();

        if (batch == null)
            return null;

        String fieldName = SqlSafety.requireKnownField(batch.fieldName(), this.getServer().getResource().getFields());
        long start = SqlSafety.requireLongLiteral(batch.valueStart(), "micro batch start");
        long end = SqlSafety.requireLongLiteral(batch.valueEnd(), "micro batch end");
        StringBuilder condition = new StringBuilder(this.getServer().escapeIdentifierName(fieldName))
                .append(" between ")
                .append(start)
                .append(" and ")
                .append(end);

        this.getLogger().info("Micro Batch condition: {}", condition);

        return Utils.addParentheses(condition.toString());
    }

    /**
     * Constructs a combined SQL condition using micro-batch, new records, and filter expressions.
     *
     * @return Combined SQL condition string or null if no conditions are applicable.
     */
    public String getConditions() throws ExecutionException {
        String conditions = Stream.of(this.getMicroBatchCondition(), this.getNewRecordsCondition(), this.getFilterExpression())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" and "));

        return conditions.isEmpty() ? null : conditions;
    }


    public Logger getLogger() {
        return logger;
    }
}
