package com.everyarrow.dittoe.utility;

import com.everyarrow.dittoe.io.data.Row;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.utilities.calendar.DateParser;
import com.everyarrow.utilities.calendar.DateTimeParser;
import com.everyarrow.utilities.calendar.TimeParser;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class RowValueUtils {
    private RowValueUtils() {
    }

    static int getByteSize(Row row) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(row);
            objectOutputStream.flush();
            return byteArrayOutputStream.size();
        }
    }

    static String removeDoubleQuotes(String string) {
        if (string == null) {
            return null;
        }
        if (hasDoubleQuotes(string)) {
            return string.substring(1, string.length() - 1);
        }
        return string;
    }

    static Map<String, Integer> getFieldsIndicesMap(List<Field> fields) {
        Map<String, Integer> fieldsIndicesMap = new HashMap<>();
        for (int i = 0; i < fields.size(); i++) {
            fieldsIndicesMap.put(fields.get(i).getName(), i);
        }
        return fieldsIndicesMap;
    }

    static String doubleQuote(String string, DataType dataType) {
        if (hasDoubleQuotes(string)) {
            return string;
        }
        if (dataType.equals(DataType.String) || dataType.equals(DataType.DateTime) ||
                dataType.equals(DataType.Date) || dataType.equals(DataType.Time)) {
            return doubleQuote(string);
        }
        return string;
    }

    static String singleQuoteIfNotExist(String string) {
        if (hasSingleQuotes(string)) {
            return string;
        }
        return singleQuote(string);
    }

    static boolean hasSingleQuotes(String string) {
        return string.length() >= 2 && string.charAt(0) == '\'' && string.charAt(string.length() - 1) == '\'';
    }

    static String singleQuote(String string) {
        return "'".concat(string).concat("'");
    }

    static String doubleQuoteIfNotExist(String string) {
        if (hasDoubleQuotes(string)) {
            return string;
        }
        return doubleQuote(string);
    }

    static boolean hasDoubleQuotes(String string) {
        return string.length() >= 2 && string.charAt(0) == '"' && string.charAt(string.length() - 1) == '"';
    }

    static String doubleQuote(String string) {
        return "\"".concat(string).concat("\"");
    }

    static String addParentheses(String str) {
        return str == null ? null : "(" + str + ")";
    }

    static Object convertToDataType(Object value, DataType dataType) {
        String strValue = value.toString();
        try {
            return switch (dataType) {
                case Date -> DateParser.parse(strValue);
                case Double -> Double.parseDouble(strValue);
                case Time, TimeWithTimeZone -> TimeParser.parse(strValue);
                case Boolean -> Boolean.parseBoolean(strValue);
                case Decimal -> new BigDecimal(strValue);
                case Integer -> Integer.parseInt(strValue);
                case DateTime, DateTimeWithTimeZone -> DateTimeParser.parse(strValue);
                default -> strValue;
            };
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }

    static void saveMaxNewRecordsFieldValue(Map<String, Object> rowData,
                                            Map<String, String> resourceProperties) throws ExecutionException {
        if (!PipelineMetadataRuntime.shouldGetNewRecords(resourceProperties)) {
            return;
        }

        String newRecordsField = resourceProperties.get("newRecordsField");
        Object newRecordsFieldValue = rowData.get(newRecordsField);

        if (newRecordsFieldValue == null) {
            throw new ExecutionException("Failed to save max new records field value because '" + newRecordsField
                    + "' is null. Row: " + rowData);
        }

        String currentNewRecordsFieldValue = resourceProperties.get("newRecordsFieldValue");
        DataType dataType = DataType.valueOf(resourceProperties.get("newRecordsFieldType"));

        if (currentNewRecordsFieldValue == null ||
                compareConvertedValues(newRecordsFieldValue, currentNewRecordsFieldValue, dataType) > 0) {
            resourceProperties.put("newRecordsFieldValue", newRecordsFieldValue.toString());
        }
    }

    private static int compareConvertedValues(Object leftValue, Object rightValue, DataType dataType) {
        return comparableValue(leftValue, dataType).compareTo(comparableValue(rightValue, dataType));
    }

    @SuppressWarnings("unchecked")
    private static Comparable<Object> comparableValue(Object value, DataType dataType) {
        Object convertedValue = convertToDataType(value, dataType);
        if (!(convertedValue instanceof Comparable<?> comparable)) {
            throw new IllegalArgumentException("Value is not comparable after conversion: " + value);
        }

        return (Comparable<Object>) comparable;
    }
}
