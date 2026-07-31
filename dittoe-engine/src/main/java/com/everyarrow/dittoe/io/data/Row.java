package com.everyarrow.dittoe.io.data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Row represents a single row of data in the DataStore.
 * It implements Serializable so that it can be serialized by Map.
 */
public class Row implements Serializable {

    /**
     * Map representing the row.
     * key (String): Key of the data.
     * Value (Object): Data itself.
     */
    private Map<String, Object> data;


    /**
     * Error message that tells why the row is invalid.
     */
    private String error;

    /**
     * Default Constructor.
     */
    public Row() {
        this.data = new HashMap<>();
    }

    /**
     * Parameterized Constructor.
     *
     * @param rowData Map representing the row of data.
     */
    public Row(Map<String, Object> rowData) {
        this.data = rowData;
    }

    /**
     * Parameterized Constructor.
     *
     * @param data  Map representing the row of data.
     * @param valid Valid flag.
     * @param error Error message.
     */
    public Row(Map<String, Object> data, String error) {
        this.data = data;
        this.error = error;
    }

    /**
     * Gets the data of the row.
     *
     * @return Data of the row.
     */
    public Map<String, Object> getData() {
        return data;
    }

    /**
     * Sets the Data of the row.
     *
     * @param data Data that is to be set.
     */
    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    /**
     * Gets the valid flag of the row.
     *
     * @return Valid flag of the row.
     */
    public Boolean isValid() {
        return this.getError() == null;
    }

    /**
     * Gets the error message of the row.
     *
     * @return Error message of the row.
     */
    public String getError() {
        return error;
    }

    /**
     * Sets the error message of the row.
     *
     * @param error Error message that is to be set.
     */
    public void setError(String error) {
        this.error = error;
    }
}
