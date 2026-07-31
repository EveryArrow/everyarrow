[CmdletBinding()]
param(
    [switch]$SourceOnly,
    [switch]$RequireNative
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Root = $PSScriptRoot
$BuildDir = Join-Path $Root "build"

function Assert-Contains {
    param(
        [string]$Content,
        [string]$Needle,
        [string]$Label
    )

    if (-not $Content.Contains($Needle)) {
        throw "$Label did not contain expected text: $Needle"
    }
}

function Invoke-SourceContractChecks {
    $source = Get-Content -LiteralPath (Join-Path $Root "src\dittoe.c") -Raw
    $tests = Get-Content -LiteralPath (Join-Path $Root "tests\test_dittoe.c") -Raw

    Assert-Contains $source 'STRATEGY_SF_READ = "com.everyarrow.dittoe.io.api.servers.salesforce.readstrategy.RestAPI"' "src/dittoe.c"
    Assert-Contains $source 'STRATEGY_SF_WRITE = "com.everyarrow.dittoe.io.api.servers.salesforce.writestrategy.RestAPI"' "src/dittoe.c"
    Assert-Contains $tests 'salesforce.readstrategy.RestAPI' "tests/test_dittoe.c"
    Assert-Contains $tests 'salesforce.writestrategy.RestAPI' "tests/test_dittoe.c"
    Assert-Contains $source 'dittoe_is_valid_pipeline_name' "src/dittoe.c"
    Assert-Contains $source 'shell_quote' "src/dittoe.c"
    Assert-Contains $tests 'test_executor_input_validation' "tests/test_dittoe.c"

    if ($source.Contains("SalesforceReadStrategy") -or $source.Contains("SalesforceWriteStrategy")) {
        throw "C SDK source still references abstract Salesforce strategy classes."
    }

    Write-Host "PASS C SDK source-contract checks"
}

function Find-CCompiler {
    foreach ($name in @("gcc", "clang")) {
        $cmd = Get-Command $name -ErrorAction SilentlyContinue
        if ($cmd) {
            return $cmd
        }
    }
    return $null
}

Invoke-SourceContractChecks
if ($SourceOnly) {
    return
}

$compiler = Find-CCompiler
if (-not $compiler) {
    if ($RequireNative) {
        throw "No GCC/Clang C compiler found on PATH."
    }
    Write-Warning "No GCC/Clang C compiler found on PATH. Native C unit binary was skipped after source-contract checks passed."
    return
}

New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null
$output = Join-Path $BuildDir "test_dittoe.exe"
& $compiler.Source "-Wall" "-Wextra" "-std=c11" "-I" "include" "src\dittoe.c" "tests\test_dittoe.c" "-o" $output
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& $output
exit $LASTEXITCODE
