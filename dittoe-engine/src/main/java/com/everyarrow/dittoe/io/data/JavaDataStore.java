package com.everyarrow.dittoe.io.data;

import java.util.HashMap;
import java.util.Map;

/**
 * Table represents a collection of rows, implementing the DataStoreInterface.
 * It manages rows using a map with Integer row IDs.
 */
public class JavaDataStore implements DataStoreInterface {

    private final Map<Integer, Row> rows;
    private final String name;

    /**
     * Constructor for Table.
     *
     * @param name Name of the table.
     */
    public JavaDataStore(String name) {
        this.name = name;
        this.rows = new HashMap<>();
    }

    /**
     * Adds a row to the table.
     *
     * @param rowId      Row ID.
     * @param columnsMap Map representing the row.
     * @throws DataStoreException Thrown if row ID already exists in the table.
     */
    @Override
    public void add(Integer rowId, Map<String, Object> columnsMap) throws DataStoreException {
        if (rows.containsKey(rowId)) {
            throw new DataStoreException("Row ID " + rowId + " already exists in the table.");
        }
        rows.put(rowId, new Row(columnsMap));
    }

    /**
     * Adds a row with an error message.
     *
     * @param rowId      Row ID.
     * @param columnsMap Map representing the row.
     * @param error      Error message.
     * @throws DataStoreException Thrown if row ID already exists in the table.
     */
    @Override
    public void add(Integer rowId, Map<String, Object> columnsMap, String error) throws DataStoreException {
        if (rows.containsKey(rowId)) {
            throw new DataStoreException("Row ID " + rowId + " already exists in the table.");
        }
        rows.put(rowId, new Row(columnsMap, error));
    }

    /**
     * Updates or replaces a row.
     *
     * @param rowId      Row ID.
     * @param columnsMap Map representing the row.
     * @throws DataStoreException Thrown if row ID does not exist in the table.
     */
    @Override
    public void replace(Integer rowId, Map<String, Object> columnsMap) throws DataStoreException {
        if (!rows.containsKey(rowId)) {
            throw new DataStoreException("Row ID " + rowId + " does not exist in the table.");
        }
        rows.put(rowId, new Row(columnsMap));
    }

    /**
     * Gets a copy of a row's data.
     *
     * @param rowId Row ID.
     * @return Map representing the row.
     * @throws DataStoreException Thrown if row ID does not exist in the table.
     */
    @Override
    public Map<String, Object> get(Integer rowId) throws DataStoreException {
        if (!rows.containsKey(rowId)) {
            throw new DataStoreException("Row ID " + rowId + " does not exist in the table.");
        }
        return rows.get(rowId).getData();
    }

    /**
     * Removes a row from the table.
     *
     * @param rowId Row ID.
     * @throws DataStoreException Thrown if row ID does not exist in the table.
     */
    @Override
    public void remove(Integer rowId) throws DataStoreException {
        if (!rows.containsKey(rowId)) {
            throw new DataStoreException("Row ID " + rowId + " does not exist in the table.");
        }
        rows.remove(rowId);
    }

    /**
     * Clears all rows in the table.
     */
    @Override
    public void clear() {
        rows.clear();
    }

    /**
     * Checks if a row is valid.
     *
     * @param rowId Row ID.
     * @return True if the row is valid, false otherwise.
     * @throws DataStoreException Thrown if row ID does not exist in the table.
     */
    @Override
    public boolean isValid(Integer rowId) throws DataStoreException {
        if (!rows.containsKey(rowId)) {
            throw new DataStoreException("Row ID " + rowId + " does not exist in the table.");
        }
        return rows.get(rowId).isValid();
    }

    /**
     * Gets the error message for a row.
     *
     * @param rowId Row ID.
     * @return Error message associated with the row.
     * @throws DataStoreException Thrown if row ID does not exist in the table.
     */
    @Override
    public String getError(Integer rowId) throws DataStoreException {
        if (!rows.containsKey(rowId)) {
            throw new DataStoreException("Row ID " + rowId + " does not exist in the table.");
        }
        return rows.get(rowId).getError();
    }

    /**
     * Sets the error message for a row.
     *
     * @param rowId Row ID.
     * @param error Error message.
     * @throws DataStoreException Thrown if row ID does not exist in the table.
     */
    @Override
    public void setError(Integer rowId, String error) throws DataStoreException {
        if (!rows.containsKey(rowId)) {
            throw new DataStoreException("Row ID " + rowId + " does not exist in the table.");
        }
        rows.get(rowId).setError(error);
    }

    /**
     * Closes the table (no-op in this implementation).
     */
    @Override
    public void close() {
        rows.clear();
    }

    /**
     * Gets the size of the table.
     *
     * @return Number of rows in the table.
     */
    @Override
    public int size() {
        return rows.size();
    }

    /**
     * Gets the name of the table.
     *
     * @return Name of the table.
     */
    public String getName() {
        return name;
    }
}