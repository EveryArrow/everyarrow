package com.everyarrow.dittoe.io.database.servers.jdbc.reader.strategy;

import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.behaviors.instrumentation.Instrumentation;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.Field;
import com.everyarrow.dittoe.utility.Utils;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import com.everyarrow.dittoe.utility.metadata.PipelineMetadataUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.everyarrow.dittoe.schema.DataType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the Select strategy for JDBC.
 */

public class SelectStrategy extends JDBCReadStrategy {

    private final Logger logger = LoggerFactory.getLogger(SelectStrategy.class);


    /**
     * Constructor.
     *
     * @throws ExecutionException Thrown if initialization fails.
     */
    public SelectStrategy() throws ExecutionException {
    }

    @Override
    public void read() throws ExecutionException {
        try (Connection connection = this.getReader().getServer().getConnection();
             Statement statement = connection.createStatement()) {
            StringBuilder query = new StringBuilder("select * from ")
                    .append(this.getReader().getServer().getSafeResourceName());

            String conditions = this.getReader().getConditions();
            if (conditions != null) {
                query.append(" where ").append(conditions);
            }

            ResultSet resultSet = statement.executeQuery(query.toString());
            int rowIndex = 0;

            while (resultSet.next()) {
                Map<String, Object> rowData = new HashMap<>();
                List<Field> fields = PipelineMetadataUtils.getNonComputedFields(this.getReader().getServer().getResource().getFields());
                for (Field field : fields) {
                    Object value = getValue(resultSet, field.getName().trim(), field.getDataType());
                    rowData.put(field.getName(), value);
                }

                DataStoreInterface.getDataStoreInterface().add(rowIndex++, rowData);
                Utils.saveMaxNewRecordsFieldValue(rowData, this.getReader().getServer().getResource().getResourceProperties());
            }

            logger.info("Records read from the source resource: {}", DataStoreInterface.getDataStoreInterface().size());

            Instrumentation.getInstance().addStatisticsToTask(
                    Instrumentation.getInstance().getRunningTaskExecutionId(
                            MetadataRepositoryAPI.getPipelineMetadata().getBehaviors().stream()
                                    .filter(behavior -> behavior.getBehaviorType().equals(BehaviorType.SourceRetrieval))
                                    .findFirst()
                                    .orElseThrow(() -> new ExecutionException("SourceRetrieval behavior not found"))
                                    .getName()
                    ),
                    Map.of("Records read from the source resource", DataStoreInterface.getDataStoreInterface().size()));
        } catch (SQLException | ExecutionException | DataStoreException e) {
            throw new ExecutionException("Could not read data", e);
        }
    }

    @Override
    public void read(int limit) throws ExecutionException {
        try (Connection connection = this.getReader().getServer().getConnection();
             Statement statement = connection.createStatement()) {
            StringBuilder query = new StringBuilder("select * from ")
                    .append(this.getReader().getServer().getSafeResourceName());

            String conditions = this.getReader().getConditions();
            if (conditions != null) {
                query.append(" where ").append(conditions);
            }
            // Add limit to query if positive
            if (limit > 0) {
                statement.setFetchSize(limit);
                statement.setMaxRows(limit);
            }

            ResultSet resultSet = statement.executeQuery(query.toString());
            int rowIndex = 0;

            while (resultSet.next() && rowIndex < limit) {
                Map<String, Object> rowData = new HashMap<>();
                List<Field> fields = PipelineMetadataUtils.getNonComputedFields(this.getReader().getServer().getResource().getFields());
                for (Field field : fields) {
                    Object value = getValue(resultSet, field.getName().trim(), field.getDataType());
                    rowData.put(field.getName(), value);
                }

                DataStoreInterface.getDataStoreInterface().add(rowIndex++, rowData);
                Utils.saveMaxNewRecordsFieldValue(rowData, this.getReader().getServer().getResource().getResourceProperties());
            }

            logger.info("Records read from the source resource (limited): {}", DataStoreInterface.getDataStoreInterface().size());
         } catch (SQLException | ExecutionException | DataStoreException e) {
            throw new ExecutionException("Could not read data (limited)", e);
        }
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
     * Gets a single value from the ResultSet based on DataType.
     *
     * @param resultSet The ResultSet to read from.
     * @param fieldName The name of the field to read.
     * @param dataType  The DataType of the field.
     * @return The value read from the ResultSet.
     * @throws SQLException Thrown in case of any SQL exception.
     */
    private static Object getValue(ResultSet resultSet, String fieldName, DataType dataType) throws SQLException {
        if (dataType.equals(DataType.Decimal)) {
            String value = resultSet.getString(fieldName);
            if (value != null && (value.startsWith("$") || value.contains(","))) {
                value = value.replace("$", "").replace(",", "");
                return new BigDecimal(value);
            }
        }

        return resultSet.getObject(fieldName);
    }
}
