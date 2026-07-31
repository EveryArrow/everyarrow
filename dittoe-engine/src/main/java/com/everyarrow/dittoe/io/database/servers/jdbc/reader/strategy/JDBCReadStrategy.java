package com.everyarrow.dittoe.io.database.servers.jdbc.reader.strategy;

import com.everyarrow.dittoe.io.ReadStrategy;
import com.everyarrow.dittoe.io.data.Row;
import com.everyarrow.dittoe.io.database.servers.jdbc.reader.JDBCReader;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.Behavior;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.Field;
import com.everyarrow.dittoe.utility.Utils;
import com.everyarrow.dittoe.utility.security.SqlSafety;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import com.everyarrow.dittoe.utility.metadata.PipelineMetadataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.everyarrow.utilities.property.PropertyManagement.validateProperties;

/**
 * Implementation of the Select strategy for JDBC.
 */

public abstract class JDBCReadStrategy extends ReadStrategy {

    /**
     * Constant representing the column used for row numbering.
     */
    private static final String ROW_NUMBER_COLUMN = "rowNumberColumn";
    private final Logger logger = LoggerFactory.getLogger(JDBCReadStrategy.class);


    /**
     * Constructor.
     *
     * @throws ExecutionException Thrown if initialization fails.
     */
    public JDBCReadStrategy() throws ExecutionException {
    }

    @Override
    public JDBCReader getReader() {
        return (JDBCReader) super.getReader();
    }


    /**
     * Returns the logger instance.
     *
     * @return Logger instance.
     */
    public Logger getLogger() {
        return logger;
    }

    /**
     * Gets the average size in bytes of a row.
     *
     * @return Number of bytes.
     * @throws ExecutionException Thrown in case any error occurred while computing the size.
     */
    @Override
    public int getAverageRowSize() throws ExecutionException {
        try (Connection connection = this.getReader().getServer().getConnection();
             Statement statement = connection.createStatement()) {
            statement.setFetchSize(1);
            statement.setMaxRows(1);
            // We cannot use prepared statement because of table name
            try (ResultSet resultSet =
                         statement.executeQuery("select * from %s".formatted(this.getReader().getServer().getSafeResourceName()))) {
                List<Field> fields = PipelineMetadataUtils.getNonComputedFields(this.getReader().getServer().getResource().getFields());

                if (resultSet.next()) {
                    Map<String, Object> rowData = new HashMap<>();
                    for (Field field : fields)
                        rowData.put(field.getName(), resultSet.getObject(field.getName().trim()));

                    return Utils.getByteSize(new Row(rowData));
                } else {
                    return 0;
                }
            }
        } catch (SQLException | IOException e) {
            throw new ExecutionException("Could not get average row size", e);
        }
    }

    /**
     * Retrieves the fields (columns) of the resource.
     *
     * @return A list of Field objects representing the resource's fields.
     * @throws ExecutionException Thrown if retrieving the fields fails.
     */
    @Override
    public List<Field> getFields() throws ExecutionException {
        try (Connection connection = this.getReader().getServer().getConnection();
             Statement statement = connection.createStatement()) {

            // "WHERE 1=0" returns no rows but allows metadata extraction.
            String query = "select * from %s where 1=0".formatted(this.getReader().getServer().getSafeResourceName());

            try (ResultSet resultSet = statement.executeQuery(query)) {
                List<Field> fields = new ArrayList<>();
                ResultSetMetaData metaData = resultSet.getMetaData();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    fields.add(new Field(metaData.getColumnName(i),
                            this.getReader().getServer().getDataType(metaData.getColumnTypeName(i))));
                }
                return fields;
            }
        } catch (SQLException | ExecutionException e) {
            throw new ExecutionException("Could not retrieve fields.", e);
        }
    }

    @Override
    public List<Long> getRange() throws ExecutionException {
        Optional<Behavior> microBatch = MetadataRepositoryAPI.getPipelineMetadata().getBehaviors().stream().filter(
                behavior -> behavior.getBehaviorType() == BehaviorType.MicroBatch
        ).findFirst();

        if (microBatch.isEmpty()) {
            throw new ExecutionException("MicroBatch behavior is not set");
        }

        Map<String, String> microBatchProperties = microBatch.get().getProperties();
        validateProperties(microBatchProperties, ROW_NUMBER_COLUMN);

        String rowNumberColumn = SqlSafety.requireKnownField(microBatchProperties.get(ROW_NUMBER_COLUMN),
                this.getReader().getServer().getResource().getFields());
        String escapedRowNumberColumn = this.getReader().getServer().escapeIdentifierName(rowNumberColumn);

        try (Connection connection = this.getReader().getServer().getConnection();
             Statement statement = connection.createStatement()) {
            StringBuilder query = new StringBuilder("select min(")
                    .append(escapedRowNumberColumn)
                    .append("), max(")
                    .append(escapedRowNumberColumn)
                    .append(") from ")
                    .append(this.getReader().getServer().getSafeResourceName());

            String conditions = Stream.of(this.getReader().getNewRecordsCondition(),
                            this.getReader().getFilterExpression())
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(" and "));

            if (!conditions.isEmpty())
                query.append(" where ").append(conditions);

            ResultSet resultSet = statement.executeQuery(query.toString());

            if (resultSet.next())
                return List.of(resultSet.getLong(1), resultSet.getLong(2));

            throw new ExecutionException("Could not retrieve the range.");
        } catch (SQLException e) {
            throw new ExecutionException(e);
        }
    }

    /**
     * Gets the data count from the resource.
     *
     * @return Total data count.
     * @throws ExecutionException Thrown in case any error occurred while retrieving the count.
     */
    @Override
    public long getDataCount() throws ExecutionException {
        try (Connection connection = this.getReader().getServer().getConnection();
             Statement statement = connection.createStatement()) {
            // We cannot use prepared statement because of table name
            StringBuilder query = new StringBuilder("select count(*) from ")
                    .append(this.getReader().getServer().getSafeResourceName());

            String conditions = this.getReader().getConditions();

            if (conditions != null) {
                query.append(" where ")
                        .append(conditions);
            }

            ResultSet resultSet = statement.executeQuery(query.toString());

            if (!resultSet.next())
                throw new ExecutionException("DataCount ResultSet did not return any data");

            return resultSet.getLong(1);
        } catch (SQLException | ExecutionException e) {
            throw new ExecutionException("Could not retrieve data count.", e);
        }
    }
}
