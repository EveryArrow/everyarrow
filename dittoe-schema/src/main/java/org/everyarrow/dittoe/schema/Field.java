/*
 * DITTOE SCHEMA:
 *
 * Copyright (c) 2026 EveryArrow, Inc.
 *
 * Licensed under the Apache License, Version 2.0.
 * You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 */

package org.everyarrow.dittoe.schema;

import java.io.Serializable;

/**
 * Represents a key that would point to a data location in a resource.
 * For example: Field will point to a column in case of CSV, Database. It will point to a property in case of JSON.
 */
public class Field implements Serializable {

    /**
     * Name of the field.
     */
    private String name;

    /**
     * Type of the data contained in the field.
     * It may be String, Integer, Double, Decimal, Boolean,	Date, DateTime, or Time.
     */
    private DataType dataType;

    private String description;

    private boolean isNullable = false;

    /**
     * Default constructor.
     */
    public Field() {
    }

    /**
     * Parameterized constructor for a Field.
     *
     * @param name     Name of the field.
     * @param dataType Data type of the field.
     */
    public Field(String name, DataType dataType) {
        this.name = name;
        this.dataType = dataType;
    }

    public Field(String name, DataType dataType, boolean isNullable) {
        this(name, dataType);
        this.isNullable = isNullable;
    }

    /**
     * Gets the name of the field.
     *
     * @return name of the field.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the field.
     *
     * @param name Name that is to be set.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the type of the data of the field.
     *
     * @return Type of the data of the field.
     */
    public DataType getDataType() {
        return dataType;
    }

    /**
     * Sets the type of the data of the field.
     *
     * @param dataType the type of that data that is to be set.
     */
    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isNullable() {
        return isNullable;
    }

    public void setNullable(boolean nullable) {
        isNullable = nullable;
    }
}
