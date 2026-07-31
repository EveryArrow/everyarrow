package com.everyarrow.dittoe.utility;

import com.everyarrow.dittoe.pipelines.behaviors.microbatch.MicroBatchManager;
import org.everyarrow.dittoe.schema.DataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class CsvRowFilter implements Predicate<Map<String, Object>> {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final List<Predicate<Map<String, Object>>> conditions = new ArrayList<>();

    public CsvRowFilter(Map<String, String> properties, Map<String, DataType> fieldTypes) {
        // New Records Condition
        if (Utils.shouldGetNewRecords(properties) && properties.containsKey("newRecordsFieldValue")) {
            String newRecordsField = properties.get("newRecordsField");
            DataType dataType = DataType.valueOf(properties.get("newRecordsFieldType"));
            Comparable<Object> newRecordsFieldValue = comparableValue(properties.get("newRecordsFieldValue"), dataType);

            // Add new records predicate
            conditions.add(row -> {
                Object value = row.get(newRecordsField);
                if (value == null) {
                    return false;
                }
                // Assume both are comparable; adjust conversion as needed
                Comparable<Object> rowValue = comparableValue(value, dataType);
                return rowValue.compareTo(newRecordsFieldValue) > 0;
            });
        }

        // Micro-Batch Condition
        MicroBatchManager.Batch batch = MicroBatchManager.getBatch();
        if (batch != null) {
            String batchField = batch.fieldName();
            Object batchStart = batch.valueStart();
            Object batchEnd = batch.valueEnd();
            DataType fieldType = fieldTypes.get(batchField);
            conditions.add(row -> {
                Object value = row.get(batchField);
                if (value == null) {
                    return false;
                }
                Comparable<Object> rowValue = comparableValue(value, fieldType);
                Comparable<Object> startValue = comparableValue(batchStart, fieldType);
                Comparable<Object> endValue = comparableValue(batchEnd, fieldType);

                return rowValue.compareTo(startValue) >= 0 && rowValue.compareTo(endValue) <= 0;
            });
        }
    }

    @Override
    public boolean test(Map<String, Object> rowData) {
        // Only rows that pass all enabled conditions are accepted.
        return conditions.stream().allMatch(predicate -> predicate.test(rowData));
    }

    public Logger getLogger() {
        return logger;
    }

    @SuppressWarnings("unchecked")
    private Comparable<Object> comparableValue(Object value, DataType dataType) {
        Object convertedValue = Utils.convertToDataType(value, dataType);
        if (!(convertedValue instanceof Comparable<?> comparable)) {
            throw new IllegalArgumentException("Value is not comparable after conversion: " + value);
        }

        return (Comparable<Object>) comparable;
    }
}
