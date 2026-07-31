# DITTOE C++ SDK

Header-only C++17 SDK with fluent builders for DITTOE Pipeline Metadata Documents.

## Requirements

- C++17 compiler (GCC 7+, Clang 5+, MSVC 2017+)
- No external dependencies (uses `std::filesystem`, `popen`)

## Build & Test

```bash
cd dittoe-schema/sdk/cpp
make test
```

On Windows/PowerShell:

```powershell
cd dittoe-schema\sdk\cpp
powershell -ExecutionPolicy Bypass -File .\test.ps1
```

The PowerShell runner compiles and runs the native unit test binary when G++ or
Clang++ is available. If no C++ compiler is installed, it still verifies the
source-level SDK strategy contract and reports that the native binary was
skipped.

Use `-RequireNative` when a CI/release check must fail unless the native binary
tests compile and run.

## Quick Start (Facade API)

The easiest way to run a migration is using the `migrate()` facade and `ConnectionBuilder`.

```cpp
#include "dittoe.hpp"
#include <iostream>

using namespace dittoe;

int main() {
    // 1. Configure (loads DITTOE_JAR / DITTOE_BASE_DIR from env vars)
    DittoeConfig config = DittoeConfig::fromEnv();

    // 2. Define Source
    auto source = ConnectionBuilder("source_db")
        .postgresql("localhost", 5433, "adventureworks", "dittoe", "dittoe123")
        .schema("production")
        .fields({"id:integer", "name:string"})
        .asSource()
        .build();

    // 3. Define Target
    auto target = ConnectionBuilder("target_db")
        .postgresql("localhost", 5434, "adventureworks", "dittoe", "dittoe123")
        .schema("staging")
        .fields({"id:integer", "name:string"})
        .asTarget()
        .build();

    // 4. Migrate
    auto result = dittoe::migrate(config, source, target);

    if (result.success) {
        std::cout << "Migration successful! Rows: " << result.rowsProcessed << "\n";
    } else {
        std::cerr << "Migration failed: " << result.stderrStr << "\n";
    }
    return 0;
}
```

## Configuration

The SDK uses `DittoeConfig` to manage execution settings. You can set these via environment variables or programmatically:

- `DITTOE_JAR`: Path to the DITTOE Engine JAR file (`execution-1.0-shaded.jar`).
- `DITTOE_BASE_DIR`: Base directory for DITTOE execution (where `pipelines/` will be created).
- `DITTOE_TIMEOUT_SEC`: Execution timeout in seconds (default: 300).

## Executor Safety

`dittoe::execute()` still uses the platform shell through `popen`, so executor
inputs are constrained before launch:

- Pipeline names must start with a letter or digit and may only contain letters,
  digits, `_`, `-`, and `.`.
- JAR and base directory paths must be non-empty and must not contain control
  characters or single quotes.
- Command arguments are single-quoted before execution.

Use the higher-level Java/Python/.NET SDKs when you need a non-shell execution
path.

## Feature Coverage

- **9 enums**: DataType(17), BehaviorType(22), ServerType(4), TemplateType(16), StageType(6), DatabaseSubType(5), FileSubType(11), APISubType(2)
- **19 server builders**: 5 DB + 11 File + 2 API + Stream
- **45 strategy constants**: `strategy::read::*` and `strategy::write::*`
- **22 behavior builders**: All types with fluent method chaining
- **16 template shortcuts**: All FADS combinations
- **Inline JSON serialization**: No external dependencies
- **Executor**: JAR runner via `popen`
- **Tests**: 57/57 passing (including facade & integration)
