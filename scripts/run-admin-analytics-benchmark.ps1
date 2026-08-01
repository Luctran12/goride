[CmdletBinding()]
param(
    [ValidateSet("smoke", "medium", "thesis")]
    [string]$Profile = "smoke",

    [long]$Seed = 5537,

    [ValidateRange(1, 10000)]
    [int]$WarmupIterations = 10,

    [ValidateRange(1, 10000)]
    [int]$MeasuredIterations = 50,

    [string]$StorageDescription = "Docker Desktop virtual disk; host media not asserted",

    [string]$OutputDirectory,

    [string]$MavenCommand
)

$ErrorActionPreference = "Stop"
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

if ([string]::IsNullOrWhiteSpace($MavenCommand)) {
    $wrapper = Join-Path $repositoryRoot "mvnw.cmd"
    if (Test-Path -LiteralPath $wrapper) {
        $MavenCommand = $wrapper
    } else {
        $resolvedMaven = Get-Command "mvn" -ErrorAction Stop
        $MavenCommand = $resolvedMaven.Source
    }
}

if (-not (Test-Path -LiteralPath $MavenCommand)) {
    throw "Maven command does not exist: $MavenCommand"
}

$mavenArguments = @(
    "-q",
    "-Dtest=AdminAnalyticsBenchmarkIT",
    "-Dadmin.analytics.benchmark.profile=$Profile",
    "-Dadmin.analytics.benchmark.seed=$Seed",
    "-Dadmin.analytics.benchmark.warmups=$WarmupIterations",
    "-Dadmin.analytics.benchmark.measurements=$MeasuredIterations",
    "-Dadmin.analytics.benchmark.storage-description=$StorageDescription",
    "test"
)

if (-not [string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $resolvedOutput = if ([System.IO.Path]::IsPathRooted($OutputDirectory)) {
        [System.IO.Path]::GetFullPath($OutputDirectory)
    } else {
        [System.IO.Path]::GetFullPath(
            (Join-Path $repositoryRoot $OutputDirectory)
        )
    }
    $mavenArguments = @(
        "-Dadmin.analytics.benchmark.output=$resolvedOutput"
    ) + $mavenArguments
}

Push-Location $repositoryRoot
try {
    & $MavenCommand @mavenArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Admin Analytics benchmark failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
