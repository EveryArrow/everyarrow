package com.everyarrow.dittoe.io.data;

import java.util.Map;

/**
 * Interface for the DataStore. DataStore is the object in which data is stored after retrieval from a source resource.
 * All the processing on the data is done through this the DataStore.
 */
public interface DataStoreInterface {

    ThreadLocal<DataStoreInterface> dataStoreInterface = new ThreadLocal<>();

    static DataStoreInterface getDataStoreInterface() {
        return dataStoreInterface.get();
    }

    static void setDataStoreInterface(DataStoreInterface dataStoreInterface) {
        DataStoreInterface.dataStoreInterface.set(dataStoreInterface);
    }

    /**
     * Adds a row.
     *
     * @param rowId      Row id.
     * @param columnsMap Map representing the row.
     * @throws DataStoreException Thrown if row id already exists in the DataStore.
     */
    void add(Integer rowId, Map<String, Object> columnsMap) throws DataStoreException;

    /**
     * Adds a row with a error.
     *
     * @param rowId      Row id.
     * @param columnsMap Map representing the row.
     * @param error      Error.
     * @throws DataStoreException Thrown if row id already exists in the DataStore.
     */
    void add(Integer rowId, Map<String, Object> columnsMap, String error) throws DataStoreException;


    /**
     * Updates or replaces a row.
     *
     * @param rowId      Row id.
     * @param columnsMap Map representing a single row.
     * @throws DataStoreException Thrown if row id does not exist in the DataStore.
     */
    void replace(Integer rowId, Map<String, Object> columnsMap) throws DataStoreException;

    /**
     * To get an immutable copy of the row data.
     * Updating this map won't have any effect on the original datastore.
     * Updating must be done using datastore methods.
     *
     * @param rowId Row id.
     * @return Map Representing the row.
     * @throws DataStoreException Thrown if row id doesn't exist in the DataStore.
     */
    Map<String, Object> get(Integer rowId) throws DataStoreException;


    /**
     * Removes a row from the DataStore at the specified key.
     *
     * @param rowId Row id to remove row at.
     * @throws DataStoreException Thrown if row id does not exist in the DataStore
     */
    void remove(Integer rowId) throws DataStoreException;

    /**
     * Clears the DataStore.
     */
    void clear();

    /**
     * Returns validity flag of a row.
     *
     * @param rowId ID of the row.
     * @return True if the row is marked as valid.
     * @throws DataStoreException Thrown if row id does not exist in the DataStore.
     */
    boolean isValid(Integer rowId) throws DataStoreException;

    /**
     * Gets the error of a row.
     *
     * @param rowId ID of the row.
     * @return Error associated with a row.
     * @throws DataStoreException Thrown if row id does not exist in the DataStore.
     */
    String getError(Integer rowId) throws DataStoreException;

    /**
     * Sets the error of a row.
     *
     * @param rowId ID of the row.
     * @param error String representing the error message.
     * @throws DataStoreException Thrown if row id does not exist in the DataStore.
     */
    void setError(Integer rowId, String error) throws DataStoreException;

    /**
     * Closes the DataStore.
     */
    void close();

    /**
     * Gets the size of the DataStore.
     *
     * @return Size of the DataStore.
     */
    int size();

    String getName();
}

