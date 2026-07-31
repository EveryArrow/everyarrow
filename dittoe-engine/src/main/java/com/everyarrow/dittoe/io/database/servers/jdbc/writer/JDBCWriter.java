package com.everyarrow.dittoe.io.database.servers.jdbc.writer;

import com.everyarrow.utilities.calendar.DateParser;
import com.everyarrow.utilities.calendar.DateTimeParser;
import com.everyarrow.utilities.calendar.TimeParser;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.database.DatabaseWriter;
import com.everyarrow.dittoe.io.database.servers.jdbc.JDBCServer;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.SourceToTargetMapping;

import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.sql.*;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

public class JDBCWriter extends DatabaseWriter {

    /**
     * Constructor.
     *
     * @throws ExecutionException Thrown if initialization fails.
     */
    public JDBCWriter() throws ExecutionException {
        super();
    }

    /**
     * Constructs the SQL condition for fetching new records based on resource properties.
     *
     * @return SQL condition string for new records or null if not applicable.
     */


    public static <T> T createWriteStrategyInstance(Class<T> clazz, Object... constructorArgs) throws InvocationTargetException {
        try {
            // Extract the argument types
            Class<?>[] argTypes = new Class[constructorArgs.length];
            for (int i = 0; i < constructorArgs.length; i++) {
                argTypes[i] = constructorArgs[i].getClass();
            }

            // Find and invoke the matching constructor
            return clazz.getDeclaredConstructor(argTypes).newInstance(constructorArgs);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException e) {
            throw new RuntimeException("Failed to create instance of " + clazz.getName(), e);
        }
    }

    /**
     * Sets a single value of the PreparedStatement.
     *
     * @param preparedStatement Input PreparedStatement.
     * @param index             The index to set the value.
     * @param dataType          The DataType of the value.
     * @param value             The value to be set.
     */
    public static void setValue(PreparedStatement preparedStatement, int index, DataType dataType, Object value)
            throws SQLException, ParseException {
        boolean nullValue = value == null || value.equals("\\\\N");

        switch (dataType) {
            case DataType.Date: {
                if (nullValue || value.toString().isBlank())
                    preparedStatement.setNull(index, Types.DATE);
                else {
                    preparedStatement.setDate(index, value instanceof Date ? (Date) value :
                            new Date(DateParser.parse(value.toString()).getTime()));
                }
                return;
            }
            case DataType.Double: {
                if (nullValue || value.toString().isBlank())
                    preparedStatement.setNull(index, Types.DOUBLE);
                else
                    preparedStatement.setDouble(index, value instanceof Double ? (Double) value :
                            Double.valueOf(value.toString()));
                return;
            }
            case DataType.Time:
            case DataType.TimeWithTimeZone: {
                if (nullValue || value.toString().isBlank())
                    preparedStatement.setNull(index, Types.TIME);
                else
                    preparedStatement.setTime(index, value instanceof Time ? (Time) value :
                            new Time(TimeParser.parse(value.toString()).getTime()));
                return;
            }
            case DataType.Boolean: {
                if (nullValue || value.toString().isBlank())
                    preparedStatement.setNull(index, Types.BOOLEAN);
                else
                    preparedStatement.setBoolean(index, value instanceof Boolean ? (Boolean) value :
                            Boolean.valueOf(value.toString()));
                return;
            }
            case DataType.Decimal: {
                if (nullValue || value.toString().isBlank())
                    preparedStatement.setNull(index, Types.NUMERIC);
                else
                    preparedStatement.setBigDecimal(index, value instanceof BigDecimal ? (BigDecimal) value :
                            new BigDecimal(value.toString()));
                return;
            }
            case DataType.Integer: {
                if (nullValue || value.toString().isBlank())
                    preparedStatement.setNull(index, Types.INTEGER);
                else if (value instanceof Boolean boolValue)
                    preparedStatement.setInt(index, boolValue ? 1 : 0);
                else if ("true".equalsIgnoreCase(value.toString()) || "false".equalsIgnoreCase(value.toString()))
                    preparedStatement.setInt(index, Boolean.parseBoolean(value.toString()) ? 1 : 0);
                else
                    preparedStatement.setInt(index, value instanceof Integer ? (Integer) value :
                            Integer.parseInt(value.toString()));
                return;
            }
            case DataType.DateTime:
            case DataType.DateTimeWithTimeZone: {
                if (nullValue || value.toString().isBlank())
                    preparedStatement.setNull(index, Types.TIMESTAMP);
                else
                    preparedStatement.setTimestamp(index, toTimestampValue(value));
                return;
            }
            case DataType.String:
            default: {
                if (nullValue)
                    preparedStatement.setNull(index, Types.VARCHAR);
                else
                    preparedStatement.setString(index, value.toString());
            }
        }
    }

    private static Timestamp toTimestampValue(Object value) throws ParseException {
        if (value instanceof Timestamp timestampValue) {
            return timestampValue;
        }

        if (value instanceof java.util.Date dateValue) {
            return new Timestamp(dateValue.getTime());
        }

        String rawValue = value.toString().trim();

        try {
            return new Timestamp(DateTimeParser.parse(rawValue).getTime());
        } catch (ParseException ignored) {
            // Fall through to handle ISO-like values that DateTimeParser rejects.
        }

        try {
            return Timestamp.from(OffsetDateTime.parse(rawValue).toInstant());
        } catch (DateTimeParseException ignored) {
            // Fall through to local date-time parsing.
        }

        String normalized = rawValue.replace('T', ' ');
        if (normalized.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}$")) {
            normalized = normalized + ":00";
        }

        try {
            return Timestamp.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            // Fall through to a final LocalDateTime parse attempt.
        }

        try {
            return Timestamp.valueOf(LocalDateTime.parse(rawValue));
        } catch (DateTimeParseException ignored) {
            throw new ParseException("Unable to parse the date: " + rawValue, 0);
        }
    }

    @Override
    public JDBCServer getServer() {
        return (JDBCServer) super.getServer();
    }

    /**
     * Sets values of a PreparedStatement.
     *
     * @param preparedStatement      The prepared statement whose values are to be set.
     * @param sourceToTargetMappings Mapping of source to target fields.
     * @param dataStore              DataStore object.
     * @param rows                   Data store keys to get values from.
     * @throws SQLException       Thrown in case of any sql exception.
     * @throws DataStoreException Thrown in case of any datastore exception.
     */
    public void setValues(PreparedStatement preparedStatement, List<SourceToTargetMapping> sourceToTargetMappings,
                          DataStoreInterface dataStore, List<Integer> rows) throws SQLException,
            DataStoreException, ParseException {
        int index = 1;
        for (int rowNumber : rows) {
            if (!dataStore.isValid(rowNumber)) {
                continue;
            }

            Map<String, Object> rowData = dataStore.get(rowNumber);
            for (SourceToTargetMapping sourceToTargetMapping : sourceToTargetMappings) {
                DataType dataType = sourceToTargetMapping.getTargetField().getDataType();
                Object value = rowData.get(sourceToTargetMapping.getSourceField().getName().trim());

                setValue(preparedStatement, index++, dataType, value);
            }
        }
    }

}
