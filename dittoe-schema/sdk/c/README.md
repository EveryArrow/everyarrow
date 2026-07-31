# DITTOE C SDK

Lightweight C SDK for building DITTOE Pipeline Metadata Documents (PMDs). Uses string-based JSON construction with no external dependencies.

## Build & Test

```bash
cd dittoe-schema/sdk/c
make test    # Compiles and runs unit tests on POSIX/GCC environments
make         # Build libdittoe.a only
make clean   # Clean build artifacts
```

On Windows/PowerShell:

```powershell
cd dittoe-schema\sdk\c
powershell -ExecutionPolicy Bypass -File .\test.ps1
```

The PowerShell runner compiles and runs the native unit test binary when GCC or
Clang is available. If no C compiler is installed, it still verifies the
source-level SDK strategy contract and reports that the native binary was
skipped.

Use `-RequireNative` when a CI/release check must fail unless the native binary
tests compile and run.

## Quick Start (Facade API)

The easiest way to run a migration is using the `dittoe_migrate()` facade function.

```c
#include "dittoe.h"
#include <stdio.h>

int main(void) {
    /* 1. Configure (loads DITTOE_JAR / DITTOE_BASE_DIR from env vars) */
    DittoeConfig config;
    dittoe_config_from_env(&config);

    /* 2. Define Source */
    DittoeConnection source;
    dittoe_connection_init_pg(&source, "source_db",
        "localhost", 5433, "adventureworks", "dittoe", "dittoe123");
    dittoe_connection_set_schema(&source, "production");
    dittoe_connection_add_field(&source, "id", DT_INTEGER, 1); // 1 = is_primary_key
    dittoe_connection_add_field(&source, "name", DT_STRING, 0);

    /* 3. Define Target */
    DittoeConnection target;
    dittoe_connection_init_pg(&target, "target_db",
        "localhost", 5434, "adventureworks", "dittoe", "dittoe123");
    dittoe_connection_set_schema(&target, "staging");
    dittoe_connection_add_field(&target, "id", DT_INTEGER, 1);
    dittoe_connection_add_field(&target, "name", DT_STRING, 0);

    /* 4. Migrate */
    DittoeExecResult result = dittoe_migrate(&config, &source, &target, "MyMigration");

    if (result.success) {
        printf("Migration successful! Rows: %d\n", result.rows_processed);
    } else {
        printf("Migration failed: %s\n", result.stderr_buf);
    }
    return 0;
}
```

## Configuration

The SDK uses `DittoeConfig` to manage execution settings. You can set these via environment variables:

- `DITTOE_JAR`: Path to the DITTOE Engine JAR file (`execution-1.0-shaded.jar`).
- `DITTOE_BASE_DIR`: Base directory for DITTOE execution (where `pipelines/` will be created).
- `DITTOE_TIMEOUT_SEC`: Execution timeout in seconds (default: 300).

## Executor Safety

`dittoe_execute()` still uses the platform shell through `popen`, so executor
inputs are constrained before launch:

- Pipeline names must start with a letter or digit and may only contain letters,
  digits, `_`, `-`, and `.`.
- JAR and base directory paths must be non-empty and must not contain control
  characters or single quotes.
- Command arguments are single-quoted before execution.

Use the higher-level Java/Python/.NET SDKs when you need a non-shell execution
path.

## Feature Coverage

- **17 DataTypes**, **22 BehaviorTypes**, **16 TemplateTypes**, **6 StageTypes**
- **19 server types**: PostgreSQL, MySQL, MSSQL, SQLite, Snowflake, LocalFile, S3, GCS, Azure, Dropbox, SFTP, FTP, IBM, Oracle Cloud, DigitalOcean, Google Drive, BigQuery, Salesforce
- **45 strategy constants**: All read/write strategies as `STRATEGY_*` constants
- **StagedBehavior** support with BAPPTE stages
- **Pipeline validation** and **JSON serialization**
- **Executors**: `dittoe_execute` and `dittoe_migrate` via `popen`

## Test Results: 45/45 ✅ (Unit + Integration)
