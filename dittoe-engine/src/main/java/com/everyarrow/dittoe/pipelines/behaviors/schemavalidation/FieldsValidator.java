package com.everyarrow.dittoe.pipelines.behaviors.schemavalidation;

import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.data.Row;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;

import java.util.List;

/**
 * Field validator is used to validate if the fields types specified in Process Metadata Document correspond
 * to the actual fields types of the resource.
 */
public class FieldsValidator implements SchemaValidator {

    /**
     * DataStore on which validation is done.
     */
    private final DataStoreInterface dataStore;

    /**
     * List of field specified in Process Metadata Document.
     */
    private final List<Field> metaDataResourceFields;

    /**
     * List of fields retrieved from the resource.
     */
    private final List<Field> actualFields;


    /**
     * Constructor.
     * Use this constructor is you want to use validator on individual rows.
     *
     * @param metaDataResourceFields List of field specified in Process Metadata Document.
     * @param actualFields           List of fields retrieved from the resource.
     */
    public FieldsValidator(List<Field> metaDataResourceFields, List<Field> actualFields) {
        this.dataStore = null;
        this.metaDataResourceFields = metaDataResourceFields;
        this.actualFields = actualFields;
    }

    /**
     * Constructor.
     *
     * @param dataStore              DataStore on which validation is done.
     * @param metaDataResourceFields List of field specified in Process Metadata Document.
     * @param actualFields           List of fields retrieved from the resource.
     */
    public FieldsValidator(DataStoreInterface dataStore, List<Field> metaDataResourceFields, List<Field> actualFields) {
        this.dataStore = dataStore;
        this.metaDataResourceFields = metaDataResourceFields;
        this.actualFields = actualFields;
    }

    /**
     * Validates all the data in the DataStore.
     *
     * @throws ExecutionException Thrown if any error occurs during validation.
     */
    @Override
    public void validate() throws ExecutionException {
        try {
            String error = this.getValidationError();
            int dataStoreSize = this.getDataStore().size();
            if (error == null) {
                for (int i = 0; i < dataStoreSize; i++)
                    this.getDataStore().setError(i, null);
            } else {
                for (int i = 0; i < dataStoreSize; i++)
                    this.getDataStore().setError(i, error);
            }
        } catch (DataStoreException e) {
            throw new ExecutionException(e);
        }
    }


    /**
     * Validates a single row.
     *
     * @param row Row to be validated.
     * @return True if row is valid else false.
     */
    @Override
    public boolean validate(Row row) {
        String error = this.getValidationError();
        if (error == null) {
            row.setError(null);
            return true;
        } else {
            row.setError(error);
            return false;
        }
    }

    /**
     * Validates a single field.
     *
     * @param field Field to be validated.
     * @return Validation errors (if any) else null.
     */
    private String validate(Field field) {
        List<Field> foundFields = this.actualFields.stream().filter(f ->
                f.getName().equals(field.getName())).toList();

        if (foundFields.isEmpty())
            return String.format("%s [%s] field not found", field.getName(), field.getDataType().name());
        Field foundField = foundFields.getFirst();

        boolean error = false;

        if (foundField.getDataType().equals(DataType.Number)) {
            if (!List.of(DataType.Number, DataType.Integer, DataType.Decimal, DataType.Double).contains(field.getDataType())) {
                error = true;
            }
        } else if (!field.getDataType().equals(foundField.getDataType())) {
            error = true;
        }

        return error ? String.format("%s: %s type expected but %s type found", field.getName(), field.getDataType().name(),
                foundField.getDataType()) : null;
    }


    /**
     * Validates the fields.
     *
     * @return Validation errors (if any) else null.
     */
    public String getValidationError() {
        String error = null;
        for (Field field : this.getMetaDataResourceFields()) {
            String validationError = validate(field);
            if (validationError != null)
                error = SchemaValidationUtils.concat(error, validationError);
        }
        return error;
    }

    /**
     * Gets the list of field specified in Process Metadata Document.
     *
     * @return List of field specified in Process Metadata Document.
     */
    public List<Field> getMetaDataResourceFields() {
        return metaDataResourceFields;
    }

    /**
     * Gets the list of fields retrieved from the resource.
     *
     * @return List of fields retrieved from the resource.
     */
    public List<Field> getActualFields() {
        return actualFields;
    }

    /**
     * Gets the DataStore on which validation is done.
     *
     * @return DataStore on which validation is done.
     */
    public DataStoreInterface getDataStore() {
        return dataStore;
    }
}
