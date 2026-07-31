package com.everyarrow.dittoe.pipelines.behaviors.schemavalidation;

import com.everyarrow.dittoe.io.IOServer;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorBase;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorInput;
import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.StageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of SchemaValidation behavior. It is a staged behavior.
 * There are different types of schema validators. Based on the type of schema validator chosen,
 * schema validation is performed.
 */
public class SchemaValidation extends StagedBehaviorBase {
    public static final String ALLOW_UNSAFE_INVALID_RECORD_LOGGING = "allowUnsafeInvalidRecordLogging";
    public static final String MAX_INVALID_ROWS_TO_LOG = "maxInvalidRowsToLog";
    private static final int DEFAULT_MAX_INVALID_ROWS_TO_LOG = 100;

    private final Logger logger = LoggerFactory.getLogger(SchemaValidation.class);

    /**
     * Type of the schema validation.
     */
    private final SchemaValidationType schemaValidationType;

    /**
     * Constructor.
     *
     * @param name             Name of the behavior.
     * @param description      Description of the behavior.
     * @param properties       Properties of the behavior.
     * @param pipelineMetadata Process Metadata Document to which the behavior belongs.
     * @param dataStore        DataStore on which behavior operates.
     */
    public SchemaValidation(String name, String description, Map<String, String> properties, PipelineMetadata pipelineMetadata, DataStoreInterface dataStore) {
        super(new StagedBehaviorInput(name,
                BehaviorType.SchemaValidation, properties, description, Integer.parseInt(properties.get("order")),
                StageType.valueOf(properties.get("stageType")), pipelineMetadata, dataStore));
        this.schemaValidationType = SchemaValidationType.valueOf(properties.get("schemaValidationType").toUpperCase(Locale.ROOT));
    }

    /**
     * Method contains the logic of the execution of the behavior.
     *
     * @throws BehaviorException Thrown if any error occurs during the behavior's executions.
     */
    @Override
    public void exec() throws BehaviorException {
        SchemaValidator schemaValidator = null;
        try {
            if (this.getSchemaValidationType().equals(SchemaValidationType.JSON)) {
                this.getLogger().info("Performing JSON SchemaValidation");

                String jsonSchema = new JSONSchemaGenerator(this.getPipelineMetadata().
                        getSourceResourceToProcess()).generate();
                schemaValidator = new JSONSchemaValidator(this.getDataStore(), jsonSchema);
            } else if (this.getSchemaValidationType().equals(SchemaValidationType.APACHE_COMMONS)) {
                this.getLogger().info("Performing Apache Commons SchemaValidation");

                schemaValidator = new ApacheCommonsValidator(this.getDataStore(),
                        this.getPipelineMetadata().getSourceResourceToProcess().getFields());
            } else if (this.getSchemaValidationType().equals(SchemaValidationType.FIELDS)) {
                this.getLogger().info("Performing Fields SchemaValidation");
                IOServer server = (IOServer) this.getPipelineMetadata().getSourceResourceToProcess().getServer();
                schemaValidator = new FieldsValidator(this.getDataStore(),
                        this.getPipelineMetadata().getSourceResourceToProcess().getFields(),
                        server.getReader().getFields());
            } else {
                throw new BehaviorException("Unsupported schema validation type.");
            }

            schemaValidator.validate();
            logRecordsWithError();
        } catch (ExecutionException e) {
            throw new BehaviorException(e);
        }
    }

    /**
     * Logs the records that did not pass SchemaValidation.
     *
     * @throws BehaviorException Thrown if any error occurs while logging the records.
     */
    private void logRecordsWithError() throws ExecutionException {
        int recordsWithError = 0;
        int loggedRecordsWithError = 0;
        int dataStoreSize = this.getDataStore().size();
        int maxInvalidRowsToLog = getMaxInvalidRowsToLog();
        boolean unsafeInvalidRecordLogging = isUnsafeInvalidRecordLoggingEnabled();

        if (unsafeInvalidRecordLogging) {
            this.getLogger().warn("Raw schema-validation record logging is enabled by explicit unsafe opt-in.");
        }

        try {
            for (int i = 0; i < dataStoreSize; i++) {
                if (!this.getDataStore().isValid(i)) {
                    recordsWithError++;
                    if (loggedRecordsWithError < maxInvalidRowsToLog) {
                        logInvalidRecord(i, unsafeInvalidRecordLogging);
                        loggedRecordsWithError++;
                    }
                }
            }
        } catch (DataStoreException e) {
            throw new ExecutionException(e);
        }

        if (recordsWithError > loggedRecordsWithError) {
            this.getLogger().info("Suppressed {} additional invalid schema-validation row summaries.",
                    recordsWithError - loggedRecordsWithError);
        }

        this.getLogger().info("Total records with error: {}", recordsWithError);
        double errorPercentage = dataStoreSize == 0 ? 0.0 : (recordsWithError * 100.0) / dataStoreSize;
        this.getLogger().info("Error percentage: {}%", errorPercentage);
    }

    private void logInvalidRecord(int rowId, boolean unsafeInvalidRecordLogging)
            throws DataStoreException {
        String error = this.getDataStore().getError(rowId);
        if (unsafeInvalidRecordLogging) {
            this.getLogger().info("Invalid schema-validation row {}: {}; Error: {}",
                    rowId, this.getDataStore().get(rowId), error);
            return;
        }

        this.getLogger().info("Invalid schema-validation row {}: {}",
                rowId, summarizeValidationError(error));
    }

    private boolean isUnsafeInvalidRecordLoggingEnabled() {
        return Boolean.parseBoolean(
                this.getProperties().getOrDefault(ALLOW_UNSAFE_INVALID_RECORD_LOGGING, "false"));
    }

    private int getMaxInvalidRowsToLog() {
        String value = this.getProperties().getOrDefault(MAX_INVALID_ROWS_TO_LOG,
                String.valueOf(DEFAULT_MAX_INVALID_ROWS_TO_LOG));
        try {
            int maxRows = Integer.parseInt(value);
            return Math.max(maxRows, 0);
        } catch (NumberFormatException e) {
            this.getLogger().warn("{} must be numeric; using default {}.",
                    MAX_INVALID_ROWS_TO_LOG, DEFAULT_MAX_INVALID_ROWS_TO_LOG);
            return DEFAULT_MAX_INVALID_ROWS_TO_LOG;
        }
    }

    static String summarizeValidationError(String error) {
        if (error == null || error.isBlank()) {
            return "validation failed";
        }

        Set<String> fields = new LinkedHashSet<>();
        for (String part : error.split(",")) {
            String fieldName = extractFieldName(part);
            if (!fieldName.isBlank()) {
                fields.add(fieldName);
            }
            if (fields.size() >= 5) {
                break;
            }
        }

        if (fields.isEmpty()) {
            return "validation failed";
        }
        return "validation failed for field(s): " + String.join(", ", fields);
    }

    private static String extractFieldName(String errorPart) {
        String normalized = errorPart.trim();
        int delimiterIndex = firstDelimiterIndex(normalized);
        if (delimiterIndex >= 0) {
            normalized = normalized.substring(0, delimiterIndex);
        }
        normalized = normalized.replaceFirst("^\\$\\.?", "");
        return normalized.replaceAll("[^A-Za-z0-9_$.-]", "");
    }

    private static int firstDelimiterIndex(String text) {
        int result = -1;
        for (String delimiter : new String[]{":", "[", " "}) {
            int index = text.indexOf(delimiter);
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    /**
     * Gets the type of schema validation to be done.
     *
     * @return Schema Validation type.
     */
    public SchemaValidationType getSchemaValidationType() {
        return schemaValidationType;
    }

    public Logger getLogger() {
        return logger;
    }
}
