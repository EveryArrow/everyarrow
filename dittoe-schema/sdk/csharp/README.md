# DITTOE C# / .NET SDK

Comprehensive C# SDK with fluent builders for DITTOE Pipeline Metadata Documents.

## Requirements

- .NET 6+ (for `System.Text.Json` and `Enum.GetValues<T>()`)

## Setup

```bash
cd dittoe-schema/sdk/csharp

# Build
dotnet build

# Run Tests
dotnet test
```

## Quick Start (Facade API)

The easiest way to run a migration is using the `Dittoe` facade and `ConnectionBuilder`.

```csharp
using DittoeSDK;

// 1. Configure (loads DITTOE_JAR / DITTOE_BASE_DIR from env vars)
var config = DittoeConfig.FromEnv();

// 2. Define Source
var source = new ConnectionBuilder("source_db")
    .PostgreSQL("localhost", 5433, "adventureworks", "dittoe", "dittoe123")
    .Schema("production")
    .AddField("id", DataType.Integer, isPrimaryKey: true)
    .AddField("name", DataType.String)
    .AsSource()
    .Build();

// 3. Define Target
var target = new ConnectionBuilder("target_db")
    .PostgreSQL("localhost", 5434, "adventureworks", "dittoe", "dittoe123")
    .Schema("staging")
    .AddField("id", DataType.Integer, isPrimaryKey: true)
    .AddField("name", DataType.String)
    .AsTarget()
    .Build();

// 4. Migrate
var result = Dittoe.Migrate(config, source, target);

if (result.Success)
{
    Console.WriteLine($"Migration successful! Rows processed: {result.RowsProcessed}");
}
else
{
    Console.WriteLine($"Migration failed: {result.Stderr}");
}
```

## Configuration

The SDK uses `DittoeConfig` to manage execution settings. You can set these via environment variables:

- `DITTOE_JAR`: Path to the DITTOE Engine JAR file (`execution-1.0-shaded.jar`).
- `DITTOE_BASE_DIR`: Base directory for DITTOE execution (where `pipelines/` will be created).
- `DITTOE_TIMEOUT_SEC`: Execution timeout in seconds (default: 300).

## Advanced Usage (Builders)

For complex scenarios, you can use the low-level builders directly.

```csharp
var pipeline = new PipelineBuilder("ProductMigration")
    .DatabaseToDatabase()
    .Source(source).Target(target)
    .MapFieldsSameName()
    .WithDefaultBehaviors()
    .Build();

var executor = new DittoeExecutor(config.JarPath);
var result = executor.Run(pipeline, config.BaseDir, config.Timeout);
```

## Feature Coverage

- **9 enums**: DataType(17), BehaviorType(22), ServerType(4), TemplateType(16), StageType(6), ProcessExecutionType(4), DatabaseType(5), FileType(11), APIType(2)
- **19 server builders**: 5 DB + 11 File + 2 API + 1 Stream
- **45 strategy constants**: All in `ReadStrategy` and `WriteStrategy` classes
- **22 behavior builders**: All types with fluent API
- **16 template shortcuts**: All FADS combinations
- **Executor**: JAR runner with validation
