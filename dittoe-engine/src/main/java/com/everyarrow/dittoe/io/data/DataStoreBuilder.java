package com.everyarrow.dittoe.io.data;

import java.io.File;

public abstract class DataStoreBuilder<T extends DataStoreBuilder<T>> {
    /**
     * Number of rows that the DataStore will contain.
     */
    private long rowCount;

    /**
     * Average row size.
     */
    private double averageRowSize;

    /**
     * The file to store the state of a persistent DataStore.
     */
    private File persistentFile;

    /**
     * Specifies whether the DataStore be recovered in case of an abnormal termination of the program or not.
     */
    private boolean recover;

    /**
     * Name of the DataStore.
     */
    private String name;

    /**
     * Gets the name of the DataStore.
     *
     * @return Name of the DataStore.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of the DataStore.
     *
     * @param name Name that is to be set.
     * @return DataStoreBuilder instance.
     */
    public T setName(String name) {
        this.name = name;
        return self();
    }

    /**
     * Tells whether the DataStore should be recovered or not.
     *
     * @return True if DataStore should be recovered else false.
     */
    public boolean isRecover() {
        return recover;
    }

    /**
     * Sets whether the DataStore should be recovered after an abnormal termination of program or not.
     *
     * @param recover Recover flag.
     * @return DataStoreBuilder instance.
     */
    public T setRecover(boolean recover) {
        this.recover = recover;
        return self();
    }

    /**
     * Gets the total number of rows of the DataStore.
     *
     * @return total number of rows.
     */
    public long getRowCount() {
        return rowCount;
    }

    /**
     * Sets the row count of the DataStore.
     *
     * @param rowCount Count/Size of the rows.
     * @return DataStoreBuilder instance.
     */
    public T setRowCount(long rowCount) {
        this.rowCount = rowCount;
        return self();
    }

    /**
     * Gets the average row size.
     *
     * @return Average row size.
     */
    public double getAverageRowSize() {
        return averageRowSize;
    }

    /**
     * Sets the average row size.
     *
     * @param averageRowSize Average row size to be set.
     * @return DataStoreBuilder instance.
     */
    public T setAverageRowSize(double averageRowSize) {
        this.averageRowSize = averageRowSize;
        return self();
    }

    /**
     * Gets the file in which state of a persistent DataStore will be saved.
     *
     * @return File representing the persistent DataStore file.
     */
    public File getPersistentFile() {
        return persistentFile;
    }

    /**
     * Sets the file to which state of a persistent DataStore will be saved.
     *
     * @param persistentFile The file to which the state will be saved.
     * @return DataStoreBuilder instance.
     */
    public T setPersistentFile(File persistentFile) {
        this.persistentFile = persistentFile;
        return self();
    }

    /**
     * Validates if all the necessary conditions required to build the DataStore are fulfilled or not
     *
     * @throws DataStoreException Thrown if any condition does not fulfill
     */
    protected void validateBuild() throws DataStoreException {
        if (this.getName() == null || this.getName().isEmpty())
            throw new DataStoreException("Name cannot be null or empty");
        if (this.isRecover() && this.getPersistentFile() == null)
            throw new DataStoreException("Persistence file not set whereas recover is set to true. " +
                    "Consider using setPersistentFile()");
        if (!this.isRecover() && this.getPersistentFile() == null) {
            if (this.getRowCount() <= 0)
                throw new DataStoreException("Row count not set or row count <= 0. Consider using setRowCount()");
            if (this.getAverageRowSize() <= 0)
                throw new DataStoreException("Column size not set or column size <= 0. Consider using setAverageColumnSize()");
        }
    }

    protected abstract T self();

    public abstract DataStoreInterface build() throws DataStoreException;
}
