[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$RunDirectory,

    [string]$OutputFile
)

$ErrorActionPreference = "Stop"
$resolvedRun = (Resolve-Path -LiteralPath $RunDirectory).Path
$samplesDirectory = Join-Path $resolvedRun "samples"
if (-not (Test-Path -LiteralPath $samplesDirectory -PathType Container)) {
    throw "Samples directory does not exist: $samplesDirectory"
}

if ([string]::IsNullOrWhiteSpace($OutputFile)) {
    $OutputFile = Join-Path $resolvedRun "summary-recomputed.json"
} else {
    $OutputFile = [System.IO.Path]::GetFullPath($OutputFile)
}
if (Test-Path -LiteralPath $OutputFile) {
    throw "Output already exists: $OutputFile"
}

function Get-ContinuousPercentile {
    param(
        [double[]]$SortedValues,
        [double]$Quantile
    )

    if ($SortedValues.Count -eq 1) {
        return $SortedValues[0]
    }
    $index = ($SortedValues.Count - 1) * $Quantile
    $lower = [math]::Floor($index)
    $upper = [math]::Ceiling($index)
    if ($lower -eq $upper) {
        return $SortedValues[$lower]
    }
    $fraction = $index - $lower
    return $SortedValues[$lower] `
        + ($SortedValues[$upper] - $SortedValues[$lower]) * $fraction
}

$results = @()
Get-ChildItem -LiteralPath $samplesDirectory -Filter "*.csv" |
    Sort-Object Name |
    ForEach-Object {
        if ($_.Name -notmatch "^(Q\d+_.+)_(DIRECT|MATERIALIZED)\.csv$") {
            throw "Unexpected sample filename: $($_.Name)"
        }
        $queryId = $Matches[1]
        $variant = $Matches[2]
        $rows = @(Import-Csv -LiteralPath $_.FullName)
        $measured = @($rows | Where-Object { $_.phase -eq "MEASURED" })
        $errors = @($measured | Where-Object { $_.success -ne "true" }).Count
        [double[]]$durations = @(
            $measured |
                Where-Object { $_.success -eq "true" } |
                ForEach-Object { [double]$_.duration_ns } |
                Sort-Object
        )

        if ($durations.Count -eq 0) {
            $statistics = [ordered]@{
                sampleCount = 0
                errorCount = $errors
                minimumMs = $null
                maximumMs = $null
                averageMs = $null
                p50Ms = $null
                p95Ms = $null
                standardDeviationMs = $null
            }
        } else {
            $average = ($durations | Measure-Object -Average).Average
            $variance = (
                $durations |
                    ForEach-Object { [math]::Pow($_ - $average, 2) } |
                    Measure-Object -Average
            ).Average
            $statistics = [ordered]@{
                sampleCount = $durations.Count
                errorCount = $errors
                minimumMs = [math]::Round($durations[0] / 1000000, 6)
                maximumMs = [math]::Round($durations[-1] / 1000000, 6)
                averageMs = [math]::Round($average / 1000000, 6)
                p50Ms = [math]::Round(
                    (Get-ContinuousPercentile $durations 0.50) / 1000000,
                    6
                )
                p95Ms = [math]::Round(
                    (Get-ContinuousPercentile $durations 0.95) / 1000000,
                    6
                )
                standardDeviationMs = [math]::Round(
                    [math]::Sqrt($variance) / 1000000,
                    6
                )
            }
        }
        $results += [ordered]@{
            queryId = $queryId
            variant = $variant
            statistics = $statistics
        }
    }

$output = [ordered]@{
    generatedAt = [DateTimeOffset]::UtcNow
    sourceRunDirectory = $resolvedRun
    method = "MEASURED rows; continuous percentile interpolation; population standard deviation"
    results = $results
}

$parent = Split-Path -Parent $OutputFile
if (-not (Test-Path -LiteralPath $parent)) {
    New-Item -ItemType Directory -Path $parent | Out-Null
}
$output | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputFile -Encoding UTF8
Write-Output $OutputFile
