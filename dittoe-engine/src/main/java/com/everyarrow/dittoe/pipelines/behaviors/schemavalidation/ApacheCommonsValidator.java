package com.everyarrow.dittoe.pipelines.behaviors.schemavalidation;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.validator.GenericValidator;
import org.apache.commons.validator.routines.BigDecimalValidator;
import org.apache.commons.validator.routines.TimeValidator;
import com.everyarrow.dittoe.io.data.DataStoreException;
import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.data.Row;
import com.everyarrow.dittoe.pipelines.execution.abstraction.ExecutionException;
import org.everyarrow.dittoe.schema.DataType;
import org.everyarrow.dittoe.schema.Field;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Apache Commons Validator is used to validate data using Apache Commons Library.
 */
public class ApacheCommonsValidator implements SchemaValidator {

    /**
     * Pattern to validate a UUID
     */
    private static final Pattern UUID_REGEX =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    /**
     * DataStore on which validation is done.
     */
    private final DataStoreInterface dataStore;
    /**
     * List of fields of the resource.
     */
    private final List<Field> fields;

    /**
     * Constructor.
     * Use this constructor is you want to use validator on individual rows.
     *
     * @param fields List of fields of the resource.
     */
    public ApacheCommonsValidator(List<Field> fields) {
        this.dataStore = null;  // implies validator will be operating on individual input row data
        this.fields = fields;
    }

    /**
     * Constructor.
     *
     * @param dataStore DataStore on which validation is done.
     * @param fields    List of fields of the resource.
     */
    public ApacheCommonsValidator(DataStoreInterface dataStore, List<Field> fields) {
        this.dataStore = dataStore;
        this.fields = fields;
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
     * @param row Row to be validated.
     * @return True if row is valid else false.
     */
    @Override
    public boolean validate(Row row) {
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
     * Validates a single object based on the specified field.
     *
     * @param object The Object that has to be validated.
     * @param field  The field against which the object has to be validated.
     * @return Validation errors (if any) else null.
     */
    private String validate(Object object, Field field) {
        switch (field.getDataType()) {
            case DataType.Date: {
                if (!GenericValidator.isDate(object.toString(), Locale.ROOT)) {
                    return String.format("%s: %s is not a correct Date", field.getName(), object);
                }
                break;
            }
            case DataType.Boolean: {
                if (BooleanUtils.toBooleanObject(object.toString()) == null) {
                    return String.format("%s: %s is not a correct Boolean", field.getName(), object);
                }
                break;
            }
            case DataType.DateTime: {
                try {
                    LocalDateTime.parse(object.toString());
                } catch (DateTimeParseException ignored) {
                    return String.format("%s: %s is not a correct DateTime", field.getName(), object);
                }
                break;
            }
            case DataType.Decimal: {
                if (new BigDecimalValidator().validate(object.toString()) == null) {
                    return String.format("%s: %s is not a correct Date", field.getName(), object);
                }
                break;
            }
            case DataType.Integer: {
                if (!GenericValidator.isInt(object.toString())) {
                    return String.format("%s: %s is not a correct Integer", field.getName(), object);
                }
                break;
            }
            case DataType.Time: {
                if (new TimeValidator().validate(object.toString()) == null) {
                    return String.format("%s: %s is not a correct Time", field.getName(), object);
                }
                break;
            }
            case DataType.Double: {
                if (!GenericValidator.isDouble(object.toString())) {
                    return String.format("%s: %s is not a correct Boolean", field.getName(), object);
                }
                break;
            }
            case DataType.UUID: {
                if (!UUID_REGEX.matcher(object.toString()).matches()) {
                    return String.format("%s: %s is not a correct UUID", field.getName(), object);
                }
                break;
            }

            default:
            case DataType.String:
                return null;
        }
        return null;
    }

    /**
     * Validates the input data.
     *
     * @param rowData Data that is to be validated.
     * @return Validation errors (if any) else null.
     */
    private String getValidationError(Map<String, Object> rowData) {
        String error = null;
        for (Field field : this.getFields()) {
            String validationError = null;
            Object object = rowData.get(field.getName());
            if (object == null) {
                rowData.put(field.getName(), "");
//                validationError = String.format("%s [%s] field not found", field.getName(), field.getDataType().name());
            } else {
                validationError = validate(object, field);
            }

            if (validationError != null) {
                error = SchemaValidationUtils.concat(error, validationError);
            }
        }
        return error;
    }

    /**
     * Gets the list of fields of the resource.
     *
     * @return List of fields of the resource.
     */
    public List<Field> getFields() {
        return fields;
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
