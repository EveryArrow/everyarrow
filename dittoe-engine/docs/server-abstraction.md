# Server Abstraction Notes

See also: [connector-implementation-checklist.md](./connector-implementation-checklist.md)

## Current shape in `dittoe-engine`

The current hierarchy is:

- `IOServer` owns a `Reader`, a `Writer`, retry wiring, the active `Resource`, and a mixed catalog/metadata contract.
- `DatabaseServer`, `FileServer`, `APIServer`, and `StreamServer` are currently mostly marker subclasses.
- `Reader` and `ReadStrategy` define the real read contract.
- `Writer` and `WriteStrategy` define the real write contract.

Today, `IOServer` requires every connector to implement all of these methods:

- `getResources()`
- `testConnection()`
- `getResourcesDependencies(List<Resource>)`
- `getResourcesFields(List<Resource>)`
- `getPropertiesForMerge(List<Resource>)`

That is stronger than what many connectors actually support. Several connectors compile by throwing `NotImplementedException` or `UnsupportedOperationException` instead of expressing capabilities directly.

## What the common read and write interfaces already look like

The current code already gives us a strong execution contract at the port level.

```java
public interface ReadPort {
    void init() throws ExecutionException;
    void read() throws ExecutionException;
    void read(int limit) throws ExecutionException;
    List<Long> getRange() throws ExecutionException;
    long getDataCount() throws ExecutionException;
    int getAverageRowSize() throws ExecutionException;
    List<Field> getFields() throws ExecutionException;
}

public interface WritePort {
    void init() throws ExecutionException;
    void write() throws ExecutionException;
}
```

That is basically what `Reader` plus `ReadStrategy` and `Writer` plus `WriteStrategy` already enforce. The main abstraction gap is not the read/write port. The gap is the server contract around those ports.

## Proposed base server interfaces

### 1. Small capability interfaces

```java
public interface ServerConnector extends AutoCloseable {
    String getConnectorName();
    ServerKind getKind();
    EnumSet<ServerCapability> getCapabilities();
    void init() throws ExecutionException;
    void testConnection() throws ExecutionException;
}

public interface ReadableServer<R extends Reader> {
    R getReader();
}

public interface WritableServer<W extends Writer> {
    W getWriter();
}

public interface ResourceCatalog {
    List<Resource> getResources() throws ExecutionException;
    Map<String, List<Field>> getResourcesFields(List<Resource> resources) throws ExecutionException;
}

public interface DependencyCatalog {
    Map<String, List<String>> getResourcesDependencies(List<Resource> resources) throws ExecutionException;
}

public interface MergeMetadataProvider {
    Map<String, Map<String, String>> getPropertiesForMerge(List<Resource> resources) throws ExecutionException;
}
```

### 2. Family interfaces

```java
public interface DatabaseServer
        extends ServerConnector,
                ReadableServer<DatabaseReader>,
                WritableServer<DatabaseWriter>,
                ResourceCatalog,
                DependencyCatalog,
                MergeMetadataProvider {

    String getSafeResourceName();
    String escapeIdentifierName(String identifierName);
    DataType getDataType(String nativeType) throws ExecutionException;
    String getNativeDataType(DataType dataType);
}
```

```java
public interface FileServer
        extends ServerConnector,
                ReadableServer<FileReader>,
                WritableServer<FileWriter> {

    String getBasePath();
    File downloadFile(String fileName, Path destinationPath) throws ExecutionException;
}
```

```java
public interface APIServer
        extends ServerConnector,
                ReadableServer<APIReader>,
                WritableServer<APIWriter> {
}
```

```java
public interface StreamServer
        extends ServerConnector,
                ReadableServer<StreamReader>,
                WritableServer<StreamWriter> {

    // Proposed because there are no concrete stream connectors yet.
    String getCheckpointKey();
}
```

## Why this split is better

This model separates:

- execution: read and write ports
- lifecycle: init, test, close
- discovery: list resources and fetch fields
- graph metadata: dependencies
- merge planning: target-side merge properties
- dialect or transport specifics: database, file, stream, api helpers

That lets a connector expose only what it really supports.

Examples:

- `LocalFileServer` can be `FileServer`, `ResourceCatalog`, and `MergeMetadataProvider`.
- `S3Server` can be `FileServer`, `ResourceCatalog`, and maybe not `DependencyCatalog`.
- `SalesforceServer` can start as `APIServer`, `ReadableServer`, and `WritableServer` without pretending catalog support exists yet.
- A future webhook or queue connector can be `StreamServer` without implementing table-style discovery methods.

## What each family should mean

### Base server

Every connector should support:

- lifecycle: `init`, `testConnection`, `close`
- capability declaration: read, write, catalog, dependencies, merge metadata
- access to its read and write ports when supported

### Database server

A database connector should additionally support:

- safe resource naming and identifier escaping
- native type to canonical type conversion
- canonical type to native type conversion
- database resource discovery
- schema field discovery
- dependency discovery
- merge metadata discovery

### File server

A file connector should additionally support:

- base path resolution
- file materialization or download to a local path

Catalog support for files should be optional, because some remote file connectors may be write-only or path-explicit.

### API server

An API connector should be the thinnest family interface. It should not automatically be forced into table-style discovery unless the API actually supports object cataloging.

BigQuery behaves like a cataloged data platform, so it can also implement `ResourceCatalog`, `DependencyCatalog`, and `MergeMetadataProvider`.
Salesforce does not currently do that in this codebase.

### Stream server

There are no concrete stream connectors in the repository today, so this is an inference. A useful stream abstraction will likely need stream-native concepts rather than table-native ones:

- checkpoint or offset handling
- ordered or batched consumption
- publish semantics
- ack or commit semantics

That suggests `StreamServer` should grow stream-specific capability interfaces later instead of inheriting all catalog methods by default.

## JDBC vs PostgreSQL

### What `JDBCServer` already is

`JDBCServer` is already closer to a reusable relational connector base than to a generic database connector:

- it owns the Hikari connection pool
- it exposes `getConnection()`
- it defines dialect hooks:
  - `getSafeResourceName()`
  - `escapeIdentifierName(...)`
  - `getNativeDataType(...)`
  - `getDataType(...)`
  - `getJsonBuilderQuery()`
  - `isSystemSchema(...)`
- it provides generic resource discovery through JDBC metadata
- it provides generic foreign-key dependency discovery through JDBC metadata

In a cleaner design, this would probably be named something like `JdbcDatabaseServer`, not just `JDBCServer`.

### What PostgreSQL adds on top

`PostgreSQLServer` is not a different abstraction layer from JDBC. It is a dialect specialization of the JDBC layer.

It adds:

- PostgreSQL connection string and driver details
- PostgreSQL identifier escaping
- PostgreSQL native type mappings
- PostgreSQL-specific resource discovery using `information_schema`
- PostgreSQL-specific field discovery
- PostgreSQL-specific merge metadata based on primary key constraints
- PostgreSQL-specific writer strategies like `COPY` and `ON CONFLICT`

So the clean mental model is:

- `DatabaseServer` = the family contract
- `JdbcDatabaseServer` = the relational JDBC transport base
- `PostgreSQLServer` = one concrete JDBC dialect implementation

## Suggested refactor direction

1. Keep `Reader` and `Writer` largely as-is. They already define the useful execution contract.
2. Shrink `IOServer` into a smaller `ServerConnector`-style base.
3. Move catalog methods out of the base server into capability interfaces.
4. Treat `JDBCServer` as a transport or dialect base, not as the top-level database abstraction.
5. Let connectors advertise supported capabilities instead of throwing `NotImplementedException`.
6. Only make `StreamServer` stream-native once the first real stream connector exists.

## Practical first cut

If we want the smallest safe change, the first extraction should be:

- keep `IOServer`
- add `ResourceCatalog`, `DependencyCatalog`, and `MergeMetadataProvider`
- stop requiring those methods directly on `IOServer`
- make database and mature cataloged connectors implement those interfaces
- leave thin connectors like Salesforce or partial file connectors with only the capabilities they actually support

That gives us a better abstraction boundary without forcing a full rewrite of the current reader and writer stack.
