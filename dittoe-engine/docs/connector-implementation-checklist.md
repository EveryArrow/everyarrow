# Connector Implementation Checklist

This checklist is the working definition of done for adding or completing a connector in `dittoe-engine`.

It is written for two audiences:

- engineers implementing connectors by hand
- Codex runs that will fill out the connector library over time

It assumes the current abstraction split now in the codebase:

- runtime IO contract
- wizard discovery contract
- orchestration metadata contract
- family-specific connector helpers for database, file, API, and stream

See also: [server-abstraction.md](./server-abstraction.md)

## 1. Choose the right base class first

Do not start from `IOServer` directly unless there is a very unusual reason.

- New relational database connector:
  extend `DatabaseServer`
- New JDBC relational database connector:
  extend `JDBCServer`
- New file connector:
  extend `FileServer`
- New API connector:
  extend `APIServer`
- New stream connector:
  extend `StreamServer`

This is the preferred starting point because the family bases already provide the correct typed reader and writer contract.

## 2. Decide the readiness target up front

Every connector should be placed into one of these tiers before implementation starts.

### Tier 1: Runtime-ready

The connector can execute a pipeline as a source, a target, or both.

### Tier 2: Wizard-ready

The connector can be used by setup flows to:

- test a connection
- list resources
- inspect fields
- describe how those resources should be shown in UI

### Tier 3: Orchestration-ready

The connector can also support orchestration generation by providing:

- resource dependency metadata
- merge or upsert metadata

### Tier 4: Product-ready

The connector is not only implemented in the engine, but also wired into console and strategy metadata so a user can actually select it in the product.

## 3. Minimum engine contract for every connector

Every connector must satisfy the base `ServerConnector` behavior through its family base class.

Checklist:

- [ ] Extend the correct family base class
- [ ] Constructor passes a reader, a writer, or one of them as `null`
- [ ] Source connectors pass a reader and `null` writer
- [ ] Target connectors pass a writer and `null` reader
- [ ] Constructor calls `reader.setServer(this)` when a reader exists
- [ ] Constructor calls `writer.setServer(this)` when a writer exists
- [ ] Implement `init()`
- [ ] Implement `testConnection()`
- [ ] Implement `close()` if the connector owns clients, pools, sessions, cursors, temp files, or other resources
- [ ] Validate required connection details during initialization
- [ ] Fail with a clear exception message when required connection properties are missing

Notes:

- Read and write capability is inferred from whether the reader or writer is present.
- `supportsRead()` and `supportsWrite()` already come from the base contract.
- A connector does not need to fake unsupported behavior with `NotImplementedException` if that capability is intentionally absent.

## 4. Runtime-ready checklist

If the connector should actually execute pipelines, complete all of these.

- [ ] Implement the concrete `Reader` if the connector is a source
- [ ] Implement the concrete `Writer` if the connector is a target
- [ ] Ensure strategies are selected correctly by the reader or writer
- [ ] Confirm the reader can return row count and fields when the pipeline runtime expects them
- [ ] Confirm the writer handles the target resource naming and field mapping correctly
- [ ] Confirm `getConnectionDetails()` contains everything needed by the connector at runtime
- [ ] Confirm the connector behaves correctly when `getResource()` is set before execution
- [ ] Confirm source mode and target mode both work independently if both are supported

## 5. Wizard-ready checklist

If the connector should work in pipeline setup or orchestration setup, it should support the wizard discovery contract.

That means it should behave as a `WizardDiscoveryProvider`, which includes:

- `getResources()`
- `getResourcesFields(List<Resource>)`
- `getResourcePresentation()`

Checklist:

- [ ] Implement `getResources()`
- [ ] Implement `getResourcesFields(List<Resource>)`
- [ ] Implement `getResourcePresentation()`
- [ ] Return stable `resourceName`
- [ ] Return stable `resourceID` values within the discovery result
- [ ] Populate `resource.server`
- [ ] Populate `resource.resourceProperties` with consistent keys
- [ ] Ensure `getResourcesFields(...)` works for multiple resources, not just one
- [ ] Ensure field order is stable and meaningful
- [ ] Ensure field nullability and data type are populated when available

### Resource presentation rules

`getResourcePresentation()` should tell the UI how to present the resource list without hardcoded server-name logic.

Checklist:

- [ ] Choose the correct primary label: `Table`, `File`, `Object`, `Entity`, `Topic`, or similar
- [ ] List any important resource properties the UI should show
- [ ] Mark which resource properties should be used for auto-matching

Examples:

- PostgreSQL:
  label `Table`, property `schema`
- BigQuery:
  label `Table`, property `dataset`
- S3:
  label `Object`, property `bucket`
- Local file:
  label `File`, no extra property if not needed

## 6. Orchestration-ready checklist

If the connector should support orchestration generation, it should behave as an `OrchestrationMetadataProvider`.

That adds:

- `getResourcesDependencies(List<Resource>)`
- `getPropertiesForMerge(List<Resource>)`

Checklist:

- [ ] Implement `getResourcesDependencies(List<Resource>)`
- [ ] Implement `getPropertiesForMerge(List<Resource>)`
- [ ] Dependency results are keyed by resource name
- [ ] Merge property results are keyed by resource name
- [ ] Empty metadata is returned as empty maps, not exceptions, when the connector supports the concept but has no entries
- [ ] Only throw unsupported exceptions when the capability truly does not exist for that connector

### Dependency metadata guidance

Dependencies should describe execution order between resources.

Examples:

- foreign key dependencies for relational tables
- parent object dependencies if an API has them
- usually empty for flat file connectors

### Merge metadata guidance

Merge properties should tell write strategies how to upsert safely.

Examples:

- PostgreSQL:
  conflict constraint name
- SQLite:
  upsert column metadata
- Some file targets:
  often empty
- MySQL:
  currently safe to return empty maps unless a merge strategy is introduced that needs more

## 7. Checklist for a new file connector

Start from `FileServer`.

### Required

- [ ] Implement a `FileReader` for source mode if needed
- [ ] Implement a `FileWriter` for target mode if needed
- [ ] Implement `testConnection()`
- [ ] Implement `downloadFile(String fileName, Path destinationPath)`

### Usually required for wizard support

- [ ] Implement `getResources()`
- [ ] Implement `getResourcesFields(...)`
- [ ] Implement `getResourcePresentation()`

### File-specific questions to answer

- [ ] What is the base path concept: `path`, `bucket`, `container`, `drive`, `folder`, or something else?
- [ ] Is the connector browsing a directory-like namespace or only explicit paths?
- [ ] Can it inspect files to infer fields?
- [ ] Which formats are supported: CSV, JSON, Parquet, Avro, XML, ZIP, or mixed?
- [ ] Does field introspection depend on downloading the object first?
- [ ] Are dependencies ever meaningful, or should they always be empty?
- [ ] Are merge properties meaningful, or should they be empty?

### File connector examples

- Local file:
  full wizard and orchestration support
- S3:
  full wizard and orchestration support
- A write-only archive sink:
  runtime-ready only may be acceptable

## 8. Checklist for a new database connector

### If it is JDBC-based

Start from `JDBCServer`.

`JDBCServer` already gives you:

- connection pool handling
- generic resource listing
- generic dependency discovery
- typed database family behavior
- resource presentation with `schema`

### JDBC database methods to implement

- [ ] `validateConnectionProperties()`
- [ ] `getCredentialProperties()`
- [ ] `getConnectionString()`
- [ ] `getDriverClassName()`
- [ ] `getSafeResourceName()`
- [ ] `escapeIdentifierName(String identifierName)`
- [ ] `getNativeDataType(DataType dataType)`
- [ ] `getDataType(String nativeType)`
- [ ] `isSystemSchema(String schemaName)`
- [ ] `getJsonBuilderQuery()`
- [ ] `testConnection()`

### JDBC database methods usually needed for wizard and orchestration

- [ ] `getResourcesFields(...)`
- [ ] `getPropertiesForMerge(...)`
- [ ] Override `getResources()` only if the generic JDBC metadata query is not good enough

### Database-specific questions to answer

- [ ] What connection properties are required?
- [ ] What schemas or catalogs should be filtered out as system objects?
- [ ] What is the identifier escaping syntax?
- [ ] How are canonical data types mapped to native types?
- [ ] How are native types mapped back into canonical types?
- [ ] What metadata can identify safe upsert keys?
- [ ] Are foreign-key dependencies accessible from metadata?

### Database connector examples

- PostgreSQL:
  JDBC + full wizard + orchestration support
- MySQL:
  JDBC + wizard + orchestration support, with currently empty merge metadata unless strategy support expands
- SQLite:
  JDBC-like family with custom resource discovery and custom merge guidance

## 9. Checklist for a new API connector

Start from `APIServer`.

Checklist:

- [ ] Implement API reader and/or API writer
- [ ] Implement auth setup in `init()`
- [ ] Implement `testConnection()`
- [ ] Decide whether the API supports resource browsing
- [ ] Decide whether the API supports field introspection
- [ ] Decide whether dependencies exist
- [ ] Decide whether merge metadata exists
- [ ] If the API supports browsing, implement `getResourcePresentation()`

Important:

- Not every API connector needs wizard support on day one.
- A connector like Salesforce may be runtime-ready before it is wizard-ready.
- Do not pretend catalog support exists if it does not.

## 10. Checklist for a new stream connector

Start from `StreamServer`.

Checklist:

- [ ] Implement stream reader and/or stream writer
- [ ] Implement `init()`
- [ ] Implement `testConnection()`
- [ ] Decide how offsets, checkpoints, commits, or acknowledgements are handled
- [ ] Decide whether the connector is runtime-only for now
- [ ] Do not force table-style discovery unless the stream platform actually supports it

Important:

- Stream connectors likely need a future stream-native discovery model.
- Until that exists, it is fine for a stream connector to be runtime-ready but not wizard-ready.

## 11. Product-ready checklist outside `dittoe-engine`

A connector is not fully usable in the product just because the engine class exists.

To be product-ready, also verify these external pieces:

- [ ] A supported server record exists for the connector
- [ ] Supported server properties exist for the connection details the UI should collect
- [ ] Protected properties are marked correctly
- [ ] Read strategy metadata exists if the connector is a source
- [ ] Write strategy metadata exists if the connector is a target
- [ ] A default read strategy exists when appropriate
- [ ] A default write strategy exists when appropriate
- [ ] Console can serialize the connector type into `databaseType`, `fileType`, `apiType`, or `streamType`
- [ ] Console service can deserialize the server JSON back into the connector class

### Current UI compatibility note

The engine now exposes `getResourcePresentation()`, but the current console UI still has some hardcoded heuristics around `schema`, `dataset`, and `bucket`.

That means:

- [ ] For connectors that must work in the current UI immediately, align `resourceProperties` with the existing console expectations
- [ ] Prefer stable keys like `schema`, `dataset`, and `bucket` where they fit naturally
- [ ] Treat the presentation descriptor as the future-proof contract, but do not ignore current UI behavior yet

## 12. Testing checklist

Every connector should come with tests appropriate to its target tier.

### Base capability tests

- [ ] Add or extend capability tests similar to `IOServerCapabilityTest`
- [ ] Assert read support and write support
- [ ] Assert discovery capability support flags
- [ ] Assert orchestration capability support flags
- [ ] Assert unsupported capabilities fail clearly when absent

### Connector-focused unit tests

- [ ] Test connection property validation
- [ ] Test type mapping logic
- [ ] Test resource discovery
- [ ] Test field introspection
- [ ] Test dependency discovery
- [ ] Test merge property generation

### Integration tests

- [ ] Source pipeline test if the connector reads
- [ ] Target pipeline test if the connector writes
- [ ] Source-to-target integration path if both matter
- [ ] Resource discovery against a real or controlled test backend when feasible

### Final verification

- [ ] Run targeted tests for the connector
- [ ] Run `mvn test`
- [ ] Confirm no unrelated integration suites regressed

## 13. Fast definition of done

Use this short version during implementation.

### Runtime-ready done

- [ ] Correct base class
- [ ] Reader and/or writer wired
- [ ] `init()` implemented
- [ ] `testConnection()` implemented
- [ ] Runtime tests passing

### Wizard-ready done

- [ ] `getResources()` implemented
- [ ] `getResourcesFields(...)` implemented
- [ ] `getResourcePresentation()` implemented
- [ ] Capability tests passing

### Orchestration-ready done

- [ ] `getResourcesDependencies(...)` implemented
- [ ] `getPropertiesForMerge(...)` implemented
- [ ] Metadata behavior verified

### Product-ready done

- [ ] Supported server and property metadata created
- [ ] Strategies registered
- [ ] Console and service path verified
- [ ] Full test suite passing

## 14. Recommended Codex workflow for bulk connector expansion

When Codex is used to fill out many connectors, follow this order for each connector:

1. Choose the family base and target tier.
2. Make the connector runtime-ready first.
3. Add wizard discovery support.
4. Add orchestration metadata support only when the connector has a meaningful implementation.
5. Add capability tests.
6. Run targeted tests.
7. Run the full suite before moving on.

Recommended discipline:

- implement one connector family pattern well first
- reuse that pattern for the next similar connector
- do not mark unsupported discovery methods as implemented if they only throw
- prefer empty metadata maps over fake implementations when the capability exists but has no data

That keeps the connector library honest and makes long-running Codex expansion much safer.
