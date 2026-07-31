package com.everyarrow.dittoe.pipelines.behaviors.schemavalidation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.data.Row;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;

import java.util.Map;
import java.util.Set;

/**
 * JSON Schema Validator is used to validate data using a json schema.
 */
public class JSONSchemaValidator implements SchemaValidator {

    /**
     * DataStore on which validation is done.
     */
    private final DataStoreInterface dataStore;

    /**
     * Json schema against which data is validated.
     */
    private final JsonSchema jsonSchema;

    /**
     * Constructor.
     *
     * @param jsonSchema Json schema against which data is validated.
     * @throws ExecutionException Thrown if any error occurs while processing the json schema.
     */
    public JSONSchemaValidator(String jsonSchema) throws ExecutionException {
        this.dataStore = null;
        try {
            JsonNode schema = new ObjectMapper().readTree(jsonSchema);
            this.jsonSchema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(schema);
        } catch (JsonProcessingException e) {
            throw new ExecutionException(e);
        }
    }

    /**
     * Constructor.
     *
     * @param dataStore  DataStore on which validation is done.
     * @param jsonSchema Json schema against which data is validated.
     * @throws ExecutionException Thrown if any error occurs while processing the json schema.
     */
    public JSONSchemaValidator(DataStoreInterface dataStore, String jsonSchema) throws ExecutionException {
        this.dataStore = dataStore;
        try {
            JsonNode schema = new ObjectMapper().readTree(jsonSchema);
            this.jsonSchema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(schema);
        } catch (JsonProcessingException e) {
            throw new ExecutionException(e);
        }
    }

    /**
     * Validates all the data in the DataStore.
     *
     * @throws ExecutionException Thrown if any error occurs during validation.
     */
    @Override
    public void validate() throws ExecutionException {
        try {
            int dataStoreSize = this.getDataStore().size();
            for (int i = 0; i < dataStoreSize; i++) {
                String error = this.getValidationError(this.getDataStore().get(i));
                this.getDataStore().setError(i, error);
            }
        } catch (DataStoreException e) {
            throw new ExecutionException(e);
        }
    }

    /**
     * Validates a single row.
     *
     * @throws ExecutionException Thrown if any error occurs during validation.
     */
    @Override
    public boolean validate(Row row) throws ExecutionException {
        String error = this.getValidationError(row.getData());
        if (error == null) {
            row.setError(null);
            return true;
        } else {
            row.setError(error);
            return false;
        }
    }

    /**
     * Validates the input data.
     *
     * @param rowData Data that is to be validated.
     * @return Validation errors (if any) else null.
     * @throws ExecutionException Thrown if any error occurs while getting the errors.
     */
    private String getValidationError(Map<String, Object> rowData) throws ExecutionException {
        try {
            Set<ValidationMessage> validationMessageSet = this.getJsonSchema().validate(
                    new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(rowData)));
            String error = null;
            for (ValidationMessage validationError : validationMessageSet)
                error = SchemaValidationUtils.concat(error, validationError.getMessage());
            return error;
        } catch (JsonProcessingException e) {
            throw new ExecutionException(e);
        }
    }

    /**
     * Gets the DataStore on which validation is done.
     *
     * @return DataStore on which validation is done.
     */
    public DataStoreInterface getDataStore() {
        return dataStore;
    }

    /**
     * Gets the Json schema against which data is validated.
     *
     * @return Json schema against which data is validated.
     */
    public JsonSchema getJsonSchema() {
        return jsonSchema;
    }
}
