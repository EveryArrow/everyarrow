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
    $header = Get-Content -LiteralPath (Join-Path $Root "include\dittoe.hpp") -Raw
    $tests = Get-Content -LiteralPath (Join-Path $Root "tests\test_dittoe.cpp") -Raw

    Assert-Contains $header 'SALESFORCE = "com.everyarrow.dittoe.io.api.servers.salesforce.readstrategy.RestAPI"' "include/dittoe.hpp"
    Assert-Contains $header 'SALESFORCE = "com.everyarrow.dittoe.io.api.servers.salesforce.writestrategy.RestAPI"' "include/dittoe.hpp"
    Assert-Contains $tests 'salesforce.readstrategy.RestAPI' "tests/test_dittoe.cpp"
    Assert-Contains $tests 'salesforce.writestrategy.RestAPI' "tests/test_dittoe.cpp"
    Assert-Contains $header 'isValidPipelineName' "include/dittoe.hpp"
    Assert-Contains $header 'shellQuote' "include/dittoe.hpp"
    Assert-Contains $tests 'test_executor_input_validation' "tests/test_dittoe.cpp"

    if ($header.Contains("SalesforceReadStrategy") -or $header.Contains("SalesforceWriteStrategy")) {
        throw "C++ SDK header still references abstract Salesforce strategy classes."
    }

    Write-Host "PASS C++ SDK source-contract checks"
}

function Find-CppCompiler {
    foreach ($name in @("g++", "clang++")) {
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

$compiler = Find-CppCompiler
if (-not $compiler) {
    if ($RequireNative) {
        throw "No G++/Clang++ compiler found on PATH."
    }
    Write-Warning "No G++/Clang++ compiler found on PATH. Native C++ unit binary was skipped after source-contract checks passed."
    return
}

New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null
$output = Join-Path $BuildDir "test_dittoe.exe"
& $compiler.Source "-Wall" "-Wextra" "-std=c++17" "-I" "include" "tests\test_dittoe.cpp" "-o" $output
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& $output
exit $LASTEXITCODE
