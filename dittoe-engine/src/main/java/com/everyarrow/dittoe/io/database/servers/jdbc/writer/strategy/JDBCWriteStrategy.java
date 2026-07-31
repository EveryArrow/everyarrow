package com.everyarrow.dittoe.io.database.servers.jdbc.writer.strategy;

import com.opencsv.CSVWriter;
import com.opencsv.CSVWriterBuilder;
import com.everyarrow.dittoe.io.WriteStrategy;
import com.everyarrow.dittoe.io.database.servers.jdbc.writer.JDBCWriter;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.SourceToTargetMapping;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class JDBCWriteStrategy extends WriteStrategy {
    public static final int CHUNK_SIZE = 1000;
    public static final int BATCH_SIZE = 10;
    protected static final int[] RETRY_SEQUENCE = {1, 2, 8, 21, 60, 160, 748, 1000};
    public static final String ERROR_COUNT_THRESHOLD_PROPERTY = "errorCountThreshold";
    /**
     * Number of values sets in a single insert statement
     * Should be a property
     */

    private String errorOutputFile;
    private CSVWriter csvWriter;
    private List<String> dataStoreFieldNames;
    private long errorRowCount = 0;


    public JDBCWriteStrategy() throws ExecutionException {
        super();
    }

    @Override
    public void init() throws ExecutionException {
        super.init();
    }

    /**
     * Get the error output file path. Constructs it lazily when needed.
     * 
     * @return The error output file path, or null if pipeline context is not available.
     */
    private String getErrorOutputFilePath() {
        if (this.errorOutputFile == null && 
            com.everyarrow.dittoe.utility.Config.PIPELINE_NAME != null && 
            com.everyarrow.dittoe.utility.Config.PIPELINE_START_TIME != null) {
            this.errorOutputFile = com.everyarrow.dittoe.utility.Utils.getPipelinePath(
                com.everyarrow.dittoe.utility.Config.PIPELINE_NAME,
                com.everyarrow.dittoe.utility.Config.PIPELINE_START_TIME,
                "write-error-rows.csv"
            );
        }
        return this.errorOutputFile;
    }

    @Override
    public JDBCWriter getWriter() {
        return (JDBCWriter) super.getWriter();
    }

    protected synchronized void writeErrorRow(List<SourceToTargetMapping> sourceToTargetMappings, Map<String, Object> row, String error) throws IOException {
        // Increment error counter
        this.errorRowCount++;
        
        // Notify all observers that an error occurred - this will check threshold
        try {
            this.getWriter().notifyDataInsertionObserversError();
        } catch (ExecutionException e) {
            // Wrap ExecutionException as RuntimeException to propagate through IOException signature
            throw new RuntimeException(e.getMessage(), e);
        }
        
        if (this.getErrorOutputFilePath() == null)
            return;

        if (getDataStoreFieldNames() == null) {
            this.setDataStoreFieldNames(sourceToTargetMappings.stream().map(sourceToTargetMapping -> sourceToTargetMapping.getSourceField().getName()).toList());
        }

        CSVWriter csvWriter = this.getWriter(sourceToTargetMappings);
        List<String> values = new ArrayList<>(this.getDataStoreFieldNames().stream().map(field -> row.get(field) != null ? row.get(field).toString() : "")
                .toList());
        values.add(error);
        csvWriter.writeNext(values.toArray(new String[0]));
        csvWriter.flush();
    }

    public void closeErrorWriter() throws IOException {
        if (this.csvWriter != null)
            this.csvWriter.close();
    }

    private synchronized CSVWriter getWriter(List<SourceToTargetMapping> sourceToTargetMappings) throws IOException {
        if (this.csvWriter == null) {
            File file = new File(this.getErrorOutputFilePath());
            boolean fileAlreadyExists = file.isFile() && file.exists();

            Writer writer = new FileWriter(file, fileAlreadyExists);
            this.csvWriter = (CSVWriter) new CSVWriterBuilder(writer)
                    .withQuoteChar('"')
                    .build();

            if (!fileAlreadyExists) {
                List<String> headers = new ArrayList<>(sourceToTargetMappings.stream().map(sourceToTargetMapping ->
                        sourceToTargetMapping.getTargetField().getName()).toList());
                headers.add("error");
                this.csvWriter.writeNext(headers.toArray(new String[0]));
            }
        }

        return csvWriter;
    }

    public List<String> getDataStoreFieldNames() {
        return dataStoreFieldNames;
    }

    public void setDataStoreFieldNames(List<String> dataStoreFieldNames) {
        this.dataStoreFieldNames = dataStoreFieldNames;
    }

    protected long getErrorRowCount() {
        return errorRowCount;
    }

    protected void resetErrorRowCount() {
        this.errorRowCount = 0;
    }

}
