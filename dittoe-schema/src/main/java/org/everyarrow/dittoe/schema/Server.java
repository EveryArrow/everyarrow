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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a server object that can support various resource types such as Database, API, File, or Stream.
 * This class contains generic fields to accommodate different connection details.
 */
public abstract class Server implements Serializable {

    /**
     * Unique identifier for the server
     */
    private int id;

    /**
     * Name for the server
     */
    private String name;

    /**
     * Type of the server (e.g., Database, API, File, Stream)
     */
    private ServerType type;

    /**
     * Flexible connection details map
     * The map may include various optional fields such as:
     * - host: Hostname or IP address
     * - port: Port number
     * - filePath: Path to the file
     * - baseUrl: Base URL for the API
     * - topic: Topic or channel name for streams
     * - user: Username for authentication
     * - password: Password for authentication
     * - sslEnabled: Whether SSL is enabled
     * - certFile: Path to the certificate file
     * - databaseType: Type of database (e.g., postgresql, mysql)
     * - databaseName: Name of the database
     * - apiType: Type of API (e.g., REST, GraphQL)
     * - fileFormat: Format of the file (e.g., csv, json)
     * - streamType: Type of stream (e.g., Kafka, Kinesis)
     */
    private Map<String, String> connectionDetails = new HashMap<>();

    /**
     * Runtime-only defaults inherited from the PMD global properties. They are not
     * part of the server's declared connection details and are therefore not
     * serialized with the server.
     */
    private transient Map<String, String> connectionDefaults = Collections.emptyMap();

    /**
     * Additional properties specific to the server configuration
     * These properties may include:
     * - retryAttempts: Number of retry attempts for connecting to the server
     * - timeout: Connection timeout value in milliseconds
     * - bufferSize: Buffer size for data transfer
     * - protocol: Specific protocol used by the server (e.g., SFTP, HTTPS)
     * - region: The region where the server is located (useful for cloud services)
     */
    private Map<String, String> additionalProperties = new HashMap<>();

    /**
     * Default constructor
     */
    public Server() {
    }

    /**
     * Parameterized constructor
     */
    public Server(int id, String name, ServerType type, Map<String, String> connectionDetails, Map<String, String> additionalProperties) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.connectionDetails = connectionDetails;
        this.additionalProperties = additionalProperties;
    }

    /**
     * Gets the unique identifier of the server.
     *
     * @return the unique identifier of the server
     */
    public int getId() {
        return id;
    }

    /**
     * Sets the unique identifier of the server.
     *
     * @param id the unique identifier of the server
     */
    public void setId(int id) {
        this.id = id;
    }

    /**
     * Gets the name of the server.
     *
     * @return the name of the server
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the server.
     *
     * @param name the name of the server
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the type of the server.
     *
     * @return the type of the server
     */
    public ServerType getType() {
        return type;
    }

    /**
     * Sets the type of the server.
     *
     * @param type the type of the server
     */
    public void setType(ServerType type) {
        this.type = type;
    }

    /**
     * Gets the connection details as a map.
     *
     * @return the connection details map
     */
    public Map<String, String> getConnectionDetails() {
        if (connectionDefaults == null || connectionDefaults.isEmpty()) {
            return connectionDetails;
        }

        Map<String, String> effectiveDetails = new HashMap<>(connectionDefaults);
        effectiveDetails.putAll(connectionDetails);
        return effectiveDetails;
    }

    /**
     * Gets only the connection details declared directly on this server.
     *
     * @return the server-owned connection details without PMD global defaults
     */
    public Map<String, String> getDeclaredConnectionDetails() {
        return connectionDetails;
    }

    /**
     * Sets the connection details map.
     * The map can include various optional fields as described in the getConnectionDetails method.
     *
     * @param connectionDetails the connection details map
     */
    public void setConnectionDetails(Map<String, String> connectionDetails) {
        this.connectionDetails = connectionDetails == null ? new HashMap<>() : connectionDetails;
    }

    /**
     * Supplies runtime connection defaults from the containing PMD. A value
     * declared directly in {@link #getDeclaredConnectionDetails()} takes
     * precedence over a same-named default.
     *
     * @param connectionDefaults PMD global properties available as connection defaults
     */
    public void setConnectionDefaults(Map<String, String> connectionDefaults) {
        this.connectionDefaults = connectionDefaults == null
                ? Collections.emptyMap()
                : connectionDefaults;
    }

    /**
     * Gets the additional properties specific to the server configuration.
     *
     * @return the additional properties map
     */
    public Map<String, String> getAdditionalProperties() {
        return additionalProperties;
    }

    /**
     * Sets the additional properties specific to the server configuration.
     *
     * @param additionalProperties the additional properties map
     */
    public void setAdditionalProperties(Map<String, String> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    @Override
    public String toString() {
        return "Server{" +
                "id=" + id +
                ", serverName='" + name + '\'' +
                ", serverType=" + type +
                ", connectionDetails=" + connectionDetails +
                ", additionalProperties=" + additionalProperties +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true; // Same object reference
        if (o == null || getClass() != o.getClass()) return false; // Null or different class

        Server server = (Server) o;
        return id == server.id &&
                Objects.equals(name, server.name) &&
                type == server.type &&
                Objects.equals(connectionDetails, server.connectionDetails) &&
                Objects.equals(additionalProperties, server.additionalProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, connectionDetails, additionalProperties);
    }
}
