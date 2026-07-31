# DITTOE VB.NET SDK

Comprehensive VB.NET SDK with fluent builders and Facade API for DITTOE Pipeline Metadata Documents. This SDK replaces the legacy VBA implementation.

## Requirements

- .NET 6+ (uses `System.Text.Json`)

## Setup

```bash
cd dittoe-schema/sdk/vbnet

# Build
dotnet build

# Run local tests
dotnet run --project DittoeSDK.Tests\DittoeSDK.Tests.vbproj
```

## Quick Start (Facade API)

The easiest way to run a migration is using the `Dittoe` facade and `ConnectionBuilder`.

```vb
Imports DittoeSDK

Module Program
    Sub Main(args As String())
        ' 1. Configure (loads DITTOE_JAR / DITTOE_BASE_DIR from env vars)
        Dim config = DittoeConfig.FromEnv()

        ' 2. Define Source
        Dim source = New ConnectionBuilder("source_db") _
            .PostgreSQL("localhost", 5433, "adventureworks", "dittoe", "dittoe123") _
            .Schema("production") _
            .AddField("id", DataType.Integer, isPrimaryKey:=True) _
            .AddField("name", DataType.String) _
            .AsSource() _
            .Build()

        ' 3. Define Target
        Dim target = New ConnectionBuilder("target_db") _
            .PostgreSQL("localhost", 5434, "adventureworks", "dittoe", "dittoe123") _
            .Schema("staging") _
            .AddField("id", DataType.Integer, isPrimaryKey:=True) _
            .AddField("name", DataType.String) _
            .AsTarget() _
            .Build()

        ' 4. Migrate
        Dim result = Dittoe.Migrate(config, source, target)

        If result.Success Then
            Console.WriteLine($"Migration successful! Rows processed: {result.RowsProcessed}")
        Else
            Console.WriteLine($"Migration failed: {result.Stderr}")
        End If
    End Sub
End Module
```

## Configuration

The SDK uses `DittoeConfig` to manage execution settings. You can set these via environment variables:

- `DITTOE_JAR`: Path to the DITTOE Engine JAR file (`execution-1.0-shaded.jar`).
- `DITTOE_BASE_DIR`: Base directory for DITTOE execution (where `pipelines/` will be created).
- `DITTOE_TIMEOUT_SEC`: Execution timeout in seconds (default: 300).

## Feature Coverage

- **Complete Enum Support**: DataType, BehaviorType, etc.
- **Fluent Builders**: `ServerBuilder`, `ResourceBuilder`, `PipelineBuilder` matching C# SDK.
- **Facade API**: Simplified `Dittoe.Migrate` entry point.
- **Legacy Replacement**: Designed to replace VBA macros with modern .NET code.
- **Tests**: No-NuGet console test project under `DittoeSDK.Tests`.
