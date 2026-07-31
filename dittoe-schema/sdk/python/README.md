# DITTOE Python SDK

Comprehensive Python SDK for building Pipeline Metadata Documents (PMDs) and executing the DITTOE data integration engine.

## Installation

```bash
cd dittoe-schema/sdk/python
python3 -m venv .venv
.venv/bin/pip install -e ".[dev]"
```

## Quick Start (Facade API)

The easiest way to run a migration is using the `Dittoe` facade and `ConnectionBuilder`.

```python
from dittoe_sdk import Dittoe, DittoeConfig, ConnectionBuilder, DataType

# 1. Configure (loads DITTOE_JAR / DITTOE_BASE_DIR from env vars)
config = DittoeConfig.from_env()

# 2. Define Source
source = (ConnectionBuilder("source_db")
    .postgresql("localhost", 5433, "adventureworks", "dittoe", "dittoe123")
    .schema("production")
    .add_field("id", DataType.INTEGER, is_primary_key=True)
    .add_field("name", DataType.STRING)
    .as_source()
    .build())

# 3. Define Target
target = (ConnectionBuilder("target_db")
    .postgresql("localhost", 5434, "adventureworks", "dittoe", "dittoe123")
    .schema("staging")
    .add_field("id", DataType.INTEGER, is_primary_key=True)
    .add_field("name", DataType.STRING)
    .as_target()
    .build())

# 4. Migrate
result = Dittoe.migrate(config, source, target)

if result.success:
    print(f"Migration successful! Rows processed: {result.rows_processed}")
else:
    print(f"Migration failed: {result.stderr}")
```

## Configuration

The SDK uses `DittoeConfig` to manage execution settings. You can set these via environment variables:

- `DITTOE_JAR`: Path to the DITTOE Engine JAR file (`execution-1.0-shaded.jar`).
- `DITTOE_BASE_DIR`: Base directory for DITTOE execution (where `pipelines/` will be created).
- `DITTOE_TIMEOUT_SEC`: Execution timeout in seconds (default: 300).

## Advanced Usage (Builders)

For complex scenarios, you can use the low-level builders directly.

```python
from dittoe_sdk import PipelineBuilder, ServerBuilder, ResourceBuilder, DataType, DittoeExecutor

# ... (rest of the builder documentation)
```

## Feature Coverage

### Data Types (17)
`String`, `Integer`, `Double`, `Decimal`, `Boolean`, `Date`, `DateTime`, `Time`, `UUID`, `TimeWithTimeZone`, `DateTimeWithTimeZone`, `JSON`, `JSONB`, `Array`, `Number`, `Bytes`, `XML`

### Server Types (19)

| Category | Servers |
|----------|---------|
| **Database** | PostgreSQL, MySQL, MSSQL, SQLite, Snowflake |
| **File** | Local, S3, GCS, Azure, Dropbox, SFTP, FTP, IBM, Oracle Cloud, DigitalOcean, Google Drive |
| **API** | BigQuery, Salesforce |

### Write Strategies

| Strategy | Method |
|----------|--------|
| JDBC Insert | `.insert()` |
| JDBC Replace | `.replace()` |
| PG Upsert | `.pg_upsert("constraint")` |
| PG Insert Do Nothing | `.pg_insert_do_nothing("constraint")` |
| PG Copy | `.pg_copy()` |
| PG Copy/AutoID | `.pg_copy_or_auto_id()` |
| PG Insert/Update | `.pg_insert_or_update()` |
| MSSQL Merge | `.mssql_merge()` |
| SQLite Upsert | `.sqlite_upsert("constraint")` |
| CSV Write | `.csv_write()` |
| JSON Write | `.json_write()` |
| Cloud CSV Write | `.s3_csv_write()`, `.gcs_csv_write()`, etc. |
| BigQuery | `.bigquery_write()` |
| Salesforce | `.salesforce_write()` |

### Read Strategies

| Strategy | Method |
|----------|--------|
| JDBC Select | `.jdbc_read()` |
| CSV Read | `.csv_read()` |
| JSON Read | `.json_read()` |
| Cloud CSV Read | `.s3_csv_read()`, `.gcs_csv_read()`, etc. |
| BigQuery Query | `.bigquery_query_read()` |
| BigQuery Avro | `.bigquery_avro_read()` |
| Salesforce | `.salesforce_read()` |

### Behavior Types (22)
All behavior types from the schema: `SourceRetrieval`, `TargetInsertUpdate`, `Transformation`, `Instrumentation`, `Formatting`, `Encryption`, `Logging`, `LineageTracking`, `KeyGeneration`, `Masking`, `Deduplication`, `SchemaEvolution`, `Retry`, `SchemaValidation`, `DQRuleChecks`, `Archive`, `MicroBatch`, `DataScienceAlgorithms`, `CustomCode`, `Compression`, `ExternalWidget`, `Cache`

### Template Types (16)
All FADS combinations: `FileToFile`, `FileToDatabase`, `FileToStream`, `FileToAPI`, `DatabaseToDatabase`, `DatabaseToFile`, `DatabaseToStream`, `DatabaseToAPI`, `StreamToStream`, `StreamToFile`, `StreamToDatabase`, `StreamToAPI`, `APIToAPI`, `APIToFile`, `APIToDatabase`, `APIToStream`

### Advanced Features

**Expression Fields (DEAL):**
```python
FieldBuilder("computed_price").decimal().expression("price * 1.1").build()
```

**Source Fields:**
```python
FieldBuilder("trimmed").string().source_expression("TRIM(raw_value)").build()
```

**DQ Rules:**
```python
DQRuleBuilder("not_null").expression("name IS NOT NULL").build()
```

**Staged Behaviors:**
```python
BehaviorBuilder("Compress").compression().staged(StageType.PACKAGE, order=1).build()
```

**Custom Field Mapping:**
```python
pipeline_builder.map_field(source_field, target_field)  # Different names
pipeline_builder.map_fields_same_name()  # Auto-map matching names
```

## Running Tests

```bash
# Preferred: all unit tests with pytest
.venv/bin/python -m pytest tests/ -v

# With coverage
.venv/bin/python -m pytest tests/ -v --cov=dittoe_sdk --cov-report=term-missing

# Minimal local runner when pytest is not installed
python run_unit_tests.py
```

`tests/test_integration.py` runs live Docker/PostgreSQL/JAR migrations and is
not included in the no-dependency unit runner.

## Test Coverage

Current no-dependency unit runner result: 145/145 local unit tests passing.

| Category | Tests | Status |
|----------|-------|--------|
| Enum completeness | 9 | ✅ |
| Field models | 7 | ✅ |
| Server builders (19 types) | 21 | ✅ |
| Resource strategies (45 types) | 42 | ✅ |
| Behavior builders (22 types) | 5 | ✅ |
| DQ Rules | 2 | ✅ |
| Dependency mapping | 1 | ✅ |
| Pipeline builders (16 templates) | 8 | ✅ |
| Strategy constants | 3 | ✅ |
| Executor | 3 | ✅ |
| Cross-database pipelines | 4 | ✅ |
| **Total** | **106** | **✅ All passing** |
