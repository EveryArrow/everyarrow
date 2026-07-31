# DITTOE Engine

> Part of the combined [EveryArrow repository](../README.md). The root README
> describes how DITTOE fits beside Event Manager and the complete platform.

DITTOE Engine executes a Pipeline Metadata Document (PMD) using the connectors
and runtime behaviors implemented in this module. The PMD owns the source,
target, field mappings, behaviors, and connection properties; the engine owns
execution.

## License

DITTOE Engine is source-available under the Business Source License 1.1.
There is no Additional Use Grant. Before the Change Date, non-production use is
permitted, and all production use requires a separate written commercial
license from EveryArrow LLC.

The DITTOE Schema models all four FADS families: File, API, Database, and
Stream. This engine currently ships concrete file, API, and database
connectors. It defines the stream connector interfaces, but it does not yet
ship a concrete stream runtime.

## Runtime capabilities

| Capability | Current support |
| --- | --- |
| Metadata-driven execution | Runs one named PMD from `<BASE_DIR>/<pipeline-name>/pmd.json` |
| Runtime behaviors | Transformation, formatting, schema validation, compression, encryption, micro-batching, instrumentation, and cache |
| Data formats | Local CSV and JSON read/write, plus connector-specific strategies such as BigQuery Avro |
| Local execution | Local file and SQLite pipelines run without an EveryArrow database or cloud service |
| Connector readiness | Tracked separately for runtime, wizard, orchestration, and product readiness |

Schema-defined behaviors that are not implemented by this engine are rejected
before execution. See [Engine Runtime Behavior Support](#engine-runtime-behavior-support)
for the authoritative list.

## Connector Readiness

Connector names are not all equivalent public-readiness claims. DITTOE tracks
connector readiness by tier so runtime support, wizard/orchestration support,
and product-readiness can be reviewed separately.

| Tier | Connectors | What This Means |
| --- | --- | --- |
| Checklist complete | BigQuery, Local File, MSSQL, MySQL, PostgreSQL, S3, SQLite | Directional source/target contracts, resource presentation, wizard discovery, orchestration metadata, and obvious placeholder checks are covered by local tests. Live external integration testing is still required before production claims for remote systems. |
| Legacy gap | Azure, DigitalOcean, Dropbox, FTP, GCS, Google Drive, IBM Object Storage, Oracle, Salesforce, SFTP, Snowflake | Connector code exists, but the public promotion checklist does not yet prove the full wizard/orchestration/resource-presentation contract. Treat these as migration or review candidates before public production claims. |

Readiness tiers are enforced by
`src/test/java/com/everyarrow/dittoe/io/ConnectorImplementationChecklistAuditTest.java`.

### MySQL runtime driver

The public connector remains named MySQL, and its PMD connection properties are
unchanged. At runtime it uses MariaDB Connector/J's MySQL compatibility mode.
The build places the unmodified driver in `target/drivers` and references it
from the shaded engine JAR manifest instead of embedding it in the BSL JAR. Its
matching source archive and third-party license notice are placed in the same
directory.

Keep the generated `drivers` directory beside the shaded JAR when distributing
or running it. Operators may instead place a compatible driver on the runtime
classpath.

---

## Engine Runtime Behavior Support

The DITTOE schema can describe behaviors for this engine, external
orchestrators, and future runtime components. This module only executes the
behavior types listed as engine-supported below. PMDs containing schema-only or
external-orchestration behavior types are rejected before execution with a clear
validation error.

| Runtime Status | Behavior Types |
| --- | --- |
| Engine-supported | SourceRetrieval, TargetInsertUpdate, Transformation, Formatting, SchemaValidation, Compression, Encryption, MicroBatch, Instrumentation, Cache |
| Schema-only or not yet implemented in this engine | Logging, lineageTracking, KeyGeneration, Masking, Deduplication, SchemaEvolution, Retry, DQRuleChecks, Archive, DataScienceAlgorithms, CustomCode, ExternalWidget |

Runtime behavior support is enforced by
`src/main/java/com/everyarrow/dittoe/pipelines/execution/EngineBehaviorSupport.java`
and covered by `EngineBehaviorSupportTest`.

---

## Security Notes

DITTOE pipeline metadata can select local paths, remote objects, database
objects, behavior classes, and connector endpoints. Treat PMDs as trusted
configuration unless they have been reviewed and validated.

Local filesystem access is constrained to the working directory, `BASE_DIR`,
the JVM temporary directory, and any additional roots listed in
`DITTOE_ALLOWED_FILE_ROOTS`. Runtime staging files are created under
`DITTOE_TEMP_DIR` when set, otherwise under `BASE_DIR/tmp`.

Unsafe compatibility options such as `allowUnsafeFilterExpression`,
`allowUnsafeCustomClass`, `allowInsecurePlainFtp`,
`allowInsecureSftpHostKey`, and `allowUnsafeEndpoint` are for trusted legacy
or local development scenarios only. An unsafe connector endpoint additionally
requires the operator-controlled `DITTOE_ALLOW_UNSAFE_ENDPOINT_OVERRIDES=true`;
a PMD cannot enable that escape hatch by itself.

## Standalone database pipelines

DITTOE Engine can run directly between two connector databases without an
EveryArrow or Event Manager database. Database connection values come from the
PMD itself (or from references resolved by the selected connector).

For each source or target server, the engine combines PMD `globalProperties`
with that server's `connectionDetails`. A server value overrides the same
global key. Inherited values remain global when the PMD is serialized, so a
read/write cycle does not duplicate shared settings into both endpoints.

This also supports local runs: for example, a SQLite source can override only
its database path while a SQLite target inherits its type and path from global
properties. Remote and cloud connectors are initialized only when selected by
the PMD; local database execution has no AWS or EFS prerequisite.

## Architecture

- **PMD:** Defines the source, target, mappings, behaviors, and properties.
- **Behaviors:** Execute reusable work such as retrieval, transformation,
  validation, formatting, packaging, and target writes.
- **Stages:** Place behaviors in the six BAPPTE lifecycle stages: Begin,
  Acquire, Process, Package, Transmit, and End.
- **DataStore:** Carries row-oriented data through one pipeline run.
- **Servers and strategies:** Bind the PMD's selected source and target to
  concrete connector readers and writers.

Release and vulnerability policy is maintained centrally in the root
[Security Policy](../SECURITY.md).
