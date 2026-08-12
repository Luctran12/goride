param(
    [Parameter(Mandatory = $true)]
    [string]$DataRoot,
    [ValidateSet("VerifyFrozen", "FullReproduction")]
    [string]$Mode = "VerifyFrozen",
    [string]$OutputDirectory = "target/phase11/thesis-verification",
    [string]$ReproductionConfig = "analytics-processing/configs/porto-phase11-reproduction.json",
    [string]$PythonExecutable = "analytics-processing/.venv-3.11/Scripts/python.exe"
)

$ErrorActionPreference = "Stop"
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$dataRootPath = (Resolve-Path -LiteralPath $DataRoot).Path
$pythonPath = (Resolve-Path -LiteralPath (Join-Path $repositoryRoot $PythonExecutable)).Path
$configPath = (Resolve-Path -LiteralPath (Join-Path $repositoryRoot $ReproductionConfig)).Path
$config = Get-Content -LiteralPath $configPath -Raw -Encoding UTF8 | ConvertFrom-Json

if ($config.schemaVersion -ne 1) {
    throw "Unsupported reproduction configuration schemaVersion"
}
if ($Mode -eq "FullReproduction" -and [string]::IsNullOrWhiteSpace($env:ANALYTICS_DATABASE_PASSWORD)) {
    throw "ANALYTICS_DATABASE_PASSWORD is required for FullReproduction"
}

$processingRoot = Join-Path $repositoryRoot "analytics-processing"
$env:GORIDE_ANALYTICS_DATA_ROOT = $dataRootPath

function Invoke-Analytics {
    param([string[]]$Arguments)
    $lines = @(& $pythonPath -m goride_analytics @Arguments)
    if ($LASTEXITCODE -ne 0) {
        throw "goride-analytics failed with exit code $LASTEXITCODE"
    }
    if ($lines.Count -eq 0) {
        throw "goride-analytics returned no machine-readable result"
    }
    return $lines[-1] | ConvertFrom-Json
}

Push-Location $processingRoot
try {
    if ($Mode -eq "VerifyFrozen") {
        $result = Invoke-Analytics @(
            "verify-thesis-evidence",
            "--config", $config.profileConfig,
            "--evidence-contract", $config.evidenceContract,
            "--output-directory", (Join-Path $repositoryRoot $OutputDirectory)
        )
        $result | ConvertTo-Json -Depth 12
        exit 0
    }

    $extract = Invoke-Analytics @(
        "extract", "--config", $config.profileConfig,
        "--from-utc", $config.extractFromUtc,
        "--cutoff-utc", $config.sourceCutoffUtc
    )
    $extractionRun = "runs/extraction/$($extract.artifactRunId)"

    $features = Invoke-Analytics @(
        "build-features", "--config", $config.profileConfig,
        "--extraction-run", $extractionRun,
        "--cell-size-meters", [string]$config.cellSizeMeters
    )
    $featureRun = "runs/feature-build/$($features.artifactRunId)"

    $baseline = Invoke-Analytics @(
        "evaluate", "--config", $config.profileConfig,
        "--feature-run", $featureRun
    )
    $baselineRun = "runs/evaluation/$($baseline.artifactRunId)"

    $training = Invoke-Analytics @(
        "train", "--config", $config.profileConfig,
        "--feature-run", $featureRun,
        "--baseline-run", $baselineRun,
        "--experiment-config", $config.experimentConfig
    )
    $trainingRun = "runs/training/$($training.artifactRunId)"

    $registered = Invoke-Analytics @(
        "register-model", "--config", $config.profileConfig,
        "--training-run", $trainingRun,
        "--actor", $config.actor,
        "--reason", $config.reason
    )
    $approved = Invoke-Analytics @(
        "approve-model", "--config", $config.profileConfig,
        "--model-version", $registered.modelVersion,
        "--actor", $config.actor,
        "--reason", $config.reason
    )

    $forecast = Invoke-Analytics @(
        "forecast", "--config", $config.profileConfig,
        "--operations-config", $config.operationsConfig,
        "--model-version", $approved.modelVersion,
        "--inference-cutoff", $config.inferenceCutoffUtc,
        "--purpose", $config.forecastPurpose
    )
    $backfill = Invoke-Analytics @(
        "backfill-actual", "--config", $config.profileConfig,
        "--operations-config", $config.operationsConfig,
        "--forecast-run", $forecast.forecastRun.forecastRunId,
        "--watermark-utc", $config.actualWatermarkUtc
    )

    [ordered]@{
        status = "SUCCEEDED"
        mode = $Mode
        extractionArtifactRunId = $extract.artifactRunId
        featureArtifactRunId = $features.artifactRunId
        baselineArtifactRunId = $baseline.artifactRunId
        trainingArtifactRunId = $training.artifactRunId
        modelVersion = $approved.modelVersion
        forecastRunId = $forecast.forecastRun.forecastRunId
        backfillArtifactRunId = $backfill.artifactRunId
        claimScope = "RESEARCH_DEMONSTRATION"
    } | ConvertTo-Json -Depth 12
}
finally {
    Pop-Location
}
