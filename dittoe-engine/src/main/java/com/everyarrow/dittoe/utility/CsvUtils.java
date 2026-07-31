package com.everyarrow.dittoe.utility;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.CSVWriterBuilder;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.data.Row;
import com.everyarrow.dittoe.pipelines.behaviors.microbatch.MicroBatchManager;
import com.everyarrow.dittoe.pipelines.execution.PipelineExecutionStats;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import com.everyarrow.dittoe.utility.DataReadObserver;
import org.everyarrow.dittoe.schema.*;
import com.everyarrow.dittoe.utility.metadata.MetadataRepositoryAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.everyarrow.dittoe.pipelines.behaviors.microbatch.MicroBatch.ROW_NUMBER_COLUMN;
import static com.everyarrow.utilities.property.PropertyManagement.validateProperties;


public final class CsvUtils {
    private static final Logger logger = LoggerFactory.getLogger(CsvUtils.class);


    public static void read(java.io.Reader reader, List<Field> fields, Map<String, String> resourceProperties) throws ExecutionException {
        read(reader, fields, resourceProperties, null, null);
    }
    
    public static void read(java.io.Reader reader, List<Field> fields, Map<String, String> resourceProperties, PipelineExecutionStats.StageStats stageStats) throws ExecutionException {
        read(reader, fields, resourceProperties, stageStats, null);
    }
    
    public static void read(java.io.Reader reader, List<Field> fields, Map<String, String> resourceProperties, PipelineExecutionStats.StageStats stageStats, DataReadObserver observer) throws ExecutionException {
        CsvRowFilter filter = new CsvRowFilter(resourceProperties, getFieldTypes(fields));
        try (CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {
            DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
            Map<String, Integer> headersIndicesMap = Utils.getFieldsIndicesMap(fields);
            int lineNumber = 0;
            int malformedRows = 0;
            int currentRowNumber = 0; // Track actual row number for logging

            for (String[] parsedLine : csvReader) {
                currentRowNumber++;
                boolean isMalformed = false;
                
                if (parsedLine.length > fields.size()) {
                    logger.warn("Row {} has {} columns but expected {}. Row will be skipped.", 
                        currentRowNumber, parsedLine.length, fields.size());
                    malformedRows++;
                    if (stageStats != null) {
                        stageStats.incrementRowsErrored(1);
                    }
                    if (observer != null) {
                        observer.recordErrored();
                    }
                    continue; // Skip this row entirely
                }

                Map<String, Object> rowData = createDataMap(parsedLine, headersIndicesMap, fields);
                if (filter.test(rowData)) {
                    dataStore.add(lineNumber++, rowData);
                    Utils.saveMaxNewRecordsFieldValue(rowData, resourceProperties);
                    if (observer != null) {
                        observer.recordRead();
                    }
                }
            }
            
            if (malformedRows > 0) {
                logger.info("{} rows with malformed data skipped, {} rows successfully processed", 
                    malformedRows, lineNumber);
            }
        } catch (IOException | DataStoreException e) {
            throw new ExecutionException("Could not read data", e);
        } catch (ExecutionException e) {
            throw e; // Re-throw ExecutionException from observer threshold check
        }
    }

    public static void read(java.io.Reader reader, List<Field> fields, Map<String, String> resourceProperties, int limit) throws ExecutionException {
        read(reader, fields, resourceProperties, limit, null, null);
    }
    
    public static void read(java.io.Reader reader, List<Field> fields, Map<String, String> resourceProperties, int limit, PipelineExecutionStats.StageStats stageStats) throws ExecutionException {
        read(reader, fields, resourceProperties, limit, stageStats, null);
    }
    
    public static void read(java.io.Reader reader, List<Field> fields, Map<String, String> resourceProperties, int limit, PipelineExecutionStats.StageStats stageStats, DataReadObserver observer) throws ExecutionException {
        CsvRowFilter filter = new CsvRowFilter(resourceProperties, getFieldTypes(fields));
        try (CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {
            DataStoreInterface dataStore = DataStoreInterface.getDataStoreInterface();
            Map<String, Integer> headersIndicesMap = Utils.getFieldsIndicesMap(fields);
            int lineNumber = 0;
            int malformedRows = 0;
            int currentRowNumber = 0; // Track actual row number for logging
            
            for (String[] parsedLine : csvReader) {
                if (lineNumber >= limit) break;
                currentRowNumber++;
                
                if (parsedLine.length > fields.size()) {
                    logger.warn("Row {} has {} columns but expected {}. Row will be skipped.", 
                        currentRowNumber, parsedLine.length, fields.size());
                    malformedRows++;
                    if (stageStats != null) {
                        stageStats.incrementRowsErrored(1);
                    }
                    if (observer != null) {
                        observer.recordErrored();
                    }
                    continue; // Skip this row entirely
                }
                
                Map<String, Object> rowData = createDataMap(parsedLine, headersIndicesMap, fields);
                if (filter.test(rowData)) {
                    dataStore.add(lineNumber++, rowData);
                    Utils.saveMaxNewRecordsFieldValue(rowData, resourceProperties);
                    if (observer != null) {
                        observer.recordRead();
                    }
                }
            }
            
            if (malformedRows > 0) {
                logger.info("{} rows with malformed data skipped, {} rows successfully processed", 
                    malformedRows, lineNumber);
            }
        } catch (IOException | DataStoreException e) {
            throw new ExecutionException("Could not read data with limit", e);
        } catch (ExecutionException e) {
            throw e; // Re-throw ExecutionException from observer threshold check
        }
    }

    public static List<Long> getRange(java.io.Reader reader, List<Field> fields, Map<String, String> resourceProperties) throws ExecutionException {
        // Retrieve micro-batch behavior from the pipeline metadata
        Optional<Behavior> microBatchOpt = MetadataRepositoryAPI.getPipelineMetadata()
                .getBehaviors()
                .stream()
                .filter(behavior -> behavior.getBehaviorType() == BehaviorType.MicroBatch)
                .findFirst();

        if (microBatchOpt.isEmpty()) {
            throw new ExecutionException("MicroBatch behavior is not set");
        }

        Map<String, String> microBatchProperties = microBatchOpt.get().getProperties();
        // Validate that the required row number column property exists
        validateProperties(microBatchProperties, ROW_NUMBER_COLUMN);
        String rowNumberColumn = microBatchProperties.get(ROW_NUMBER_COLUMN);

        // Create our reusable CSV row filter. This composite filter will only add conditions that are enabled.
        CsvRowFilter filter = new CsvRowFilter(resourceProperties, getFieldTypes(fields));

        long minValue = Long.MAX_VALUE;
        long maxValue = Long.MIN_VALUE;
        boolean found = false;

        try (CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {
            Map<String, Integer> headersIndicesMap = Utils.getFieldsIndicesMap(fields);
            for (String[] parsedLine : csvReader) {
                // Create a map for the current row. This method is assumed to exist as in your CSV processing code.
                Map<String, Object> rowData = createDataMap(parsedLine, headersIndicesMap, fields);
                // Only consider rows that pass the enabled conditions
                if (filter.test(rowData)) {
                    Object valObj = rowData.get(rowNumberColumn);
                    if (valObj != null) {
                        try {
                            long value = Long.parseLong(valObj.toString());
                            minValue = Math.min(minValue, value);
                            maxValue = Math.max(maxValue, value);
                            found = true;
                        } catch (NumberFormatException e) {
                            throw new ExecutionException("Invalid row number value: " + valObj, e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new ExecutionException("Could not read CSV for range", e);
        }

        if (!found) {
            throw new ExecutionException("Could not retrieve the range.");
        }

        return List.of(minValue, maxValue);
    }

    public static long getDataCount(java.io.Reader reader, List<Field> fields, Map<String, String> resourceProperties) throws ExecutionException {
        CsvRowFilter filter = new CsvRowFilter(resourceProperties, getFieldTypes(fields));
        try (CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {
            long dataCount = 0;
            Map<String, Integer> headersIndicesMap = Utils.getFieldsIndicesMap(fields);
            for (String[] parsedLine : csvReader) {
                Map<String, Object> rowData = createDataMap(parsedLine, headersIndicesMap, fields);
                if (filter.test(rowData)) {
                    dataCount++;
                }
            }
            return dataCount;
        } catch (IOException e) {
            throw new ExecutionException("Could not retrieve data count", e);
        }
    }

    public static int getAverageRowSize(java.io.Reader reader, List<Field> fields) throws ExecutionException {
        Map<String, Integer> headersIndicesMap = Utils.getFieldsIndicesMap(fields);
        Map<String, Object> rowData = null;
        try (CSVReader csvReader = new CSVReaderBuilder(reader).withSkipLines(1).build()) {
            for (String[] parsedLine : csvReader) {
                rowData = createDataMap(parsedLine, headersIndicesMap, fields);
                break;
            }
            if (rowData == null)
                return 0;
            else
                return Utils.getByteSize(new Row(rowData));
        } catch (IOException e) {
            throw new ExecutionException("Could not get average row size", e);
        }
    }

    public static List<Field> getFields(java.io.Reader reader) throws ExecutionException {
        try (CSVReader csvReader = new CSVReaderBuilder(reader).build()) {
            List<Field> fields = new ArrayList<>();
            for (String[] parsed : csvReader) {
                for (String header : parsed)
                    fields.add(new Field(header, DataType.String));
                break;
            }
            return fields;
        } catch (IOException e) {
            throw new ExecutionException("Could not retrieve fields", e);
        }
    }

    /**
     * Optimized method to get fields from CSV content by reading only the first line.
     * This method reads only the necessary bytes to extract headers, making it efficient for large files.
     *
     * @param firstLineContent The first line content of the CSV file
     * @param delimiter The CSV delimiter (default: comma)
     * @return List of fields extracted from the first line
     * @throws ExecutionException if field extraction fails
     */
    public static List<Field> getFieldsFromFirstLine(String firstLineContent, String delimiter) throws ExecutionException {
        try {
            if (firstLineContent == null || firstLineContent.trim().isEmpty()) {
                return new ArrayList<>();
            }

            // Use OpenCSV to properly parse the first line (handles quoted fields, escaped delimiters, etc.)
            try (java.io.StringReader stringReader = new java.io.StringReader(firstLineContent);
                 CSVReader csvReader = new CSVReaderBuilder(stringReader).build()) {
                
                List<Field> fields = new ArrayList<>();
                String[] headers = csvReader.readNext();
                
                if (headers != null) {
                    for (String header : headers) {
                        if (header != null && !header.trim().isEmpty()) {
                            fields.add(new Field(header.trim(), DataType.String));
                        }
                    }
                }
                
                return fields;
            }
        } catch (Exception e) {
            throw new ExecutionException("Could not extract fields from first line: " + firstLineContent, e);
        }
    }

    private static Map<String, Object> createDataMap(String[] dataArray,
                                                     Map<String, Integer> headersIndicesMap,
                                                     List<Field> fields) {
        Map<String, Object> dataMap = new HashMap<>();
        fields.forEach(field -> {
                    String val = null;
                    int idx = headersIndicesMap.get(field.getName());

                    if (idx < dataArray.length)
                        val = Utils.removeDoubleQuotes(dataArray[idx].trim());
                    dataMap.put(field.getName(), val);
                }
        );
        return dataMap;
    }

    public static void write(java.io.Writer writer, DataStoreInterface dataStore, PipelineMetadata pipelineMetadata) throws ExecutionException {
        int size = dataStore.size();
        if (size == 0)
            return;

        try {
            try (CSVWriter csvWriter = (CSVWriter) new CSVWriterBuilder(writer)
                    .withQuoteChar('"')
                    .build()) {
                if (MicroBatchManager.getBatch() == null || Utils.isFirstMicroBatch(pipelineMetadata)) {
                    writeHeaders(pipelineMetadata.getS2TMap(), csvWriter);
                }

                List<Chunk> chunks = Batch.divideIntoChunks(dataStore, 100000);
                for (Chunk chunk : chunks) {
                    for (int rowIndex = chunk.startIndex(); rowIndex <= chunk.endIndex(); rowIndex++) {
                        String[] row = getMappedData(dataStore.get(rowIndex), pipelineMetadata.getS2TMap());
                        csvWriter.writeNext(row, false);
                    }
                }
                csvWriter.flush();
            }
        } catch (Exception e) {
            throw new ExecutionException("Could not write data", e);
        }
    }

    /**
     * Writes header fields to the CSV file using OpenCSV.
     *
     * @param sourceToTargetMappings Mapping of source to target fields.
     * @throws IOException If an I/O error occurs.
     */
    private static void writeHeaders(List<SourceToTargetMapping> sourceToTargetMappings, CSVWriter csvWriter) throws IOException {
        String[] headers = new String[sourceToTargetMappings.size()];
        int i = 0;
        for (SourceToTargetMapping mapping : sourceToTargetMappings) {
            headers[i++] = mapping.getTargetField().getName();
        }
        csvWriter.writeNext(headers, false);
        csvWriter.flush();
    }

    /**
     * Applies source to target mapping to the data.
     *
     * @param data                   The input data.
     * @param sourceToTargetMappings Mapping of source to target fields.
     * @return Mapped data as a String array.
     */
    private static String[] getMappedData(Map<String, Object> data, List<SourceToTargetMapping> sourceToTargetMappings) {
        String[] mappedData = new String[sourceToTargetMappings.size()];
        int i = 0;
        for (SourceToTargetMapping mapping : sourceToTargetMappings) {
            Object obj = data.get(mapping.getSourceField().getName());
            String value = (obj != null) ? obj.toString() : "";
            mappedData[i++] = value;
        }
        return mappedData;
    }

    private static Map<String, DataType> getFieldTypes(List<Field> fields) {
        return fields.stream().collect(Collectors.toMap(Field::getName, Field::getDataType));
    }
}
