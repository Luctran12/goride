param(
    [string]$ReleasePath,
    [switch]$All
)

$ErrorActionPreference = "Stop"

$requiredFiles = @(
    "manifest.yml",
    "precheck.sql",
    "apply.sql",
    "verify.sql",
    "rollback.sql"
)

$requiredManifestFields = @(
    "release_id",
    "title",
    "author",
    "created_at",
    "risk",
    "transactional",
    "requires_downtime",
    "applies_after",
    "related_commit",
    "objects",
    "rollback_plan"
)

function Fail {
    param([string]$Message)
    throw "Database release validation failed: $Message"
}

function Get-ReleaseDirectories {
    if ($All) {
        $root = Join-Path (Get-Location) "db/releases"
        if (-not (Test-Path -LiteralPath $root -PathType Container)) {
            Fail "db/releases directory does not exist"
        }
        return Get-ChildItem -LiteralPath $root -Directory
    }

    if ([string]::IsNullOrWhiteSpace($ReleasePath)) {
        Fail "Pass -ReleasePath <path> or -All"
    }

    $item = Get-Item -LiteralPath $ReleasePath
    if (-not $item.PSIsContainer) {
        Fail "$ReleasePath is not a directory"
    }
    return @($item)
}

function Test-Manifest {
    param(
        [System.IO.DirectoryInfo]$Directory,
        [string]$Manifest
    )

    foreach ($field in $requiredManifestFields) {
        if ($Manifest -notmatch "(?m)^$([regex]::Escape($field))\s*:") {
            Fail "$($Directory.FullName)/manifest.yml is missing '$field'"
        }
    }

    $releaseId = ($Manifest | Select-String -Pattern "(?m)^release_id\s*:\s*(.+)$").Matches[0].Groups[1].Value.Trim()
    if ($releaseId -ne $Directory.Name) {
        Fail "$($Directory.FullName)/manifest.yml release_id '$releaseId' must match folder name '$($Directory.Name)'"
    }

    if ($Manifest -match "(?m)^transactional\s*:\s*false\s*$") {
        return $false
    }

    return $true
}

function Test-SqlFile {
    param(
        [System.IO.DirectoryInfo]$Directory,
        [string]$FileName
    )

    $path = Join-Path $Directory.FullName $FileName
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Fail "$($Directory.FullName) is missing $FileName"
    }

    $content = Get-Content -LiteralPath $path -Raw
    if ([string]::IsNullOrWhiteSpace($content)) {
        Fail "$path must not be empty"
    }

    return $content
}

function Test-ReleaseDirectory {
    param([System.IO.DirectoryInfo]$Directory)

    foreach ($fileName in $requiredFiles) {
        if (-not (Test-Path -LiteralPath (Join-Path $Directory.FullName $fileName) -PathType Leaf)) {
            Fail "$($Directory.FullName) is missing $fileName"
        }
    }

    $manifest = Get-Content -LiteralPath (Join-Path $Directory.FullName "manifest.yml") -Raw
    $transactional = Test-Manifest -Directory $Directory -Manifest $manifest
    $applySql = Test-SqlFile -Directory $Directory -FileName "apply.sql"
    [void](Test-SqlFile -Directory $Directory -FileName "precheck.sql")
    [void](Test-SqlFile -Directory $Directory -FileName "verify.sql")
    [void](Test-SqlFile -Directory $Directory -FileName "rollback.sql")

    if ($transactional) {
        if ($applySql -notmatch "(?im)^\s*BEGIN\s*;") {
            Fail "$($Directory.FullName)/apply.sql must contain BEGIN; when transactional=true"
        }
        if ($applySql -notmatch "(?im)^\s*COMMIT\s*;") {
            Fail "$($Directory.FullName)/apply.sql must contain COMMIT; when transactional=true"
        }
    }

    $destructivePattern = "(?im)^\s*(DROP\s+TABLE|TRUNCATE\s+TABLE|DELETE\s+FROM|ALTER\s+TABLE\s+\S+\s+DROP\s+COLUMN)\b"
    if ($applySql -match $destructivePattern -and $applySql -notmatch "(?im)^\s*--\s*destructive-reviewed:\s*true\s*$") {
        Fail "$($Directory.FullName)/apply.sql contains destructive SQL without '-- destructive-reviewed: true'"
    }

    Write-Host "OK $($Directory.FullName)"
}

$directories = Get-ReleaseDirectories
if ($directories.Count -eq 0) {
    Fail "No release directories found to validate"
}

foreach ($directory in $directories) {
    Test-ReleaseDirectory -Directory $directory
}
