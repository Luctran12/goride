[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl = "http://localhost:8080",

    [Parameter()]
    [string]$AdminToken = $env:GORIDE_ADMIN_TOKEN,

    [Parameter()]
    [switch]$RequireServiceAreas,

    [Parameter()]
    [switch]$RequireOnlinePayments,

    [Parameter()]
    [switch]$RequireTracing,

    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string]$ExpectedAppName = "goride",

    [Parameter()]
    [ValidateRange(1, 120)]
    [int]$TimeoutSeconds = 15,

    [Parameter()]
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"
$baseUri = $BaseUrl.TrimEnd("/")
$checks = [System.Collections.Generic.List[object]]::new()

function Add-Check {
    param(
        [string]$Name,
        [ValidateSet("PASS", "WARN", "SKIP", "FAIL")]
        [string]$Status,
        [string]$Details
    )

    $checks.Add([pscustomobject]@{
        name = $Name
        status = $Status
        details = $Details
    })
    Write-Host ("[{0}] {1}: {2}" -f $Status, $Name, $Details)
}

function Get-HttpFailureDetails {
    param([System.Management.Automation.ErrorRecord]$ErrorRecord)

    $statusCode = $null
    if ($null -ne $ErrorRecord.Exception.Response) {
        try {
            $statusCode = [int]$ErrorRecord.Exception.Response.StatusCode
        }
        catch {
            $statusCode = $null
        }
    }

    if ($null -ne $statusCode) {
        return "HTTP $statusCode"
    }
    return $ErrorRecord.Exception.Message
}

function Invoke-GorideGet {
    param(
        [string]$Path,
        [switch]$UseAdminToken
    )

    $headers = @{
        Accept = "application/json"
        "X-Request-Id" = [guid]::NewGuid().ToString()
    }
    if ($UseAdminToken) {
        $headers.Authorization = "Bearer $AdminToken"
    }

    try {
        $body = Invoke-RestMethod -Method Get -Uri "$baseUri$Path" -Headers $headers -TimeoutSec $TimeoutSeconds
        return [pscustomobject]@{
            succeeded = $true
            body = $body
            error = $null
        }
    }
    catch {
        return [pscustomobject]@{
            succeeded = $false
            body = $null
            error = Get-HttpFailureDetails -ErrorRecord $_
        }
    }
}

function Get-ApiData {
    param(
        [string]$CheckName,
        [string]$Path,
        [switch]$UseAdminToken
    )

    $result = Invoke-GorideGet -Path $Path -UseAdminToken:$UseAdminToken
    if (-not $result.succeeded) {
        Add-Check -Name $CheckName -Status "FAIL" -Details "$Path failed: $($result.error)"
        return [pscustomobject]@{
            succeeded = $false
            data = $null
        }
    }
    if ($result.body.success -ne $true) {
        Add-Check -Name $CheckName -Status "FAIL" -Details "$Path returned success=false"
        return [pscustomobject]@{
            succeeded = $false
            data = $null
        }
    }
    return [pscustomobject]@{
        succeeded = $true
        data = $result.body.data
    }
}

function Find-ByMethod {
    param(
        [object[]]$Items,
        [string]$Method
    )

    return $Items | Where-Object { $_.method -eq $Method } | Select-Object -First 1
}

Write-Host "GoRide staging readiness smoke"
Write-Host "Target: $baseUri"
Write-Host ""

$liveness = Invoke-GorideGet -Path "/actuator/health/liveness"
if (-not $liveness.succeeded) {
    Add-Check -Name "Liveness" -Status "FAIL" -Details $liveness.error
}
elseif ($liveness.body.status -eq "UP") {
    Add-Check -Name "Liveness" -Status "PASS" -Details "Application process is UP."
}
else {
    Add-Check -Name "Liveness" -Status "FAIL" -Details "Expected UP, received '$($liveness.body.status)'."
}

$readiness = Invoke-GorideGet -Path "/actuator/health/readiness"
if (-not $readiness.succeeded) {
    Add-Check -Name "Readiness" -Status "FAIL" -Details $readiness.error
}
elseif ($readiness.body.status -eq "UP") {
    Add-Check -Name "Readiness" -Status "PASS" -Details "Application, database and Redis are ready."
}
else {
    Add-Check -Name "Readiness" -Status "FAIL" -Details "Expected UP, received '$($readiness.body.status)'."
}

$info = Invoke-GorideGet -Path "/actuator/info"
if (-not $info.succeeded) {
    Add-Check -Name "Application identity" -Status "FAIL" -Details $info.error
}
elseif ($info.body.app.name -eq $ExpectedAppName) {
    Add-Check -Name "Application identity" -Status "PASS" -Details "app.name=$ExpectedAppName"
}
else {
    Add-Check -Name "Application identity" -Status "FAIL" -Details "Expected app.name=$ExpectedAppName."
}

if (-not $info.succeeded) {
    $status = if ($RequireTracing) { "FAIL" } else { "SKIP" }
    Add-Check -Name "Distributed tracing" -Status $status -Details "Actuator info is unavailable."
}
else {
    $tracingEnabled = $info.body.observability.tracingEnabled -eq $true
    $otlpExportEnabled = $info.body.observability.otlpExportEnabled -eq $true
    if ($tracingEnabled -and $otlpExportEnabled) {
        Add-Check -Name "Distributed tracing" -Status "PASS" -Details "Tracing and OTLP export are enabled."
    }
    elseif ($RequireTracing) {
        Add-Check -Name "Distributed tracing" -Status "FAIL" -Details "Tracing or OTLP export is disabled."
    }
    else {
        Add-Check -Name "Distributed tracing" -Status "WARN" -Details "Tracing or OTLP export is disabled."
    }
}

$serviceAreaResult = Get-ApiData -CheckName "Service areas endpoint" -Path "/api/v1/service-areas"
$serviceAreas = @(if ($serviceAreaResult.succeeded) { $serviceAreaResult.data })
if ($serviceAreaResult.succeeded) {
    $invalidAreas = @($serviceAreas | Where-Object {
        $_.active -ne $true -or $null -eq $_.boundary -or @($_.boundary).Count -lt 3
    })
    if ($invalidAreas.Count -gt 0) {
        Add-Check -Name "Service area data" -Status "FAIL" -Details "$($invalidAreas.Count) active response item(s) have invalid status or boundary."
    }
    elseif ($serviceAreas.Count -eq 0 -and $RequireServiceAreas) {
        Add-Check -Name "Service area data" -Status "FAIL" -Details "No active service area is configured."
    }
    elseif ($serviceAreas.Count -eq 0) {
        Add-Check -Name "Service area data" -Status "WARN" -Details "No active service area; rollout remains open until zones are configured."
    }
    else {
        Add-Check -Name "Service area data" -Status "PASS" -Details "$($serviceAreas.Count) active service area(s) returned."
    }
}

$methodResult = Get-ApiData -CheckName "Payment methods endpoint" -Path "/api/v1/payments/methods"
$paymentMethods = @(if ($methodResult.succeeded) { $methodResult.data })
$cash = Find-ByMethod -Items $paymentMethods -Method "CASH"
if (-not $methodResult.succeeded) {
    Add-Check -Name "Cash payment" -Status "FAIL" -Details "Payment metadata is unavailable."
}
elseif ($null -ne $cash -and $cash.enabled -eq $true) {
    Add-Check -Name "Cash payment" -Status "PASS" -Details "CASH is exposed as an enabled payment method."
}
else {
    Add-Check -Name "Cash payment" -Status "FAIL" -Details "CASH is missing or disabled."
}

$onlineMethods = @("MOMO", "VNPAY")
foreach ($methodName in $onlineMethods) {
    $method = Find-ByMethod -Items $paymentMethods -Method $methodName
    if ($null -eq $method) {
        Add-Check -Name "$methodName metadata" -Status "FAIL" -Details "Payment method is missing from the public response."
    }
    elseif ($RequireOnlinePayments -and $method.enabled -ne $true) {
        Add-Check -Name "$methodName metadata" -Status "FAIL" -Details "Online payment is required but public metadata is disabled."
    }
    elseif ($method.enabled -eq $true) {
        Add-Check -Name "$methodName metadata" -Status "PASS" -Details "Public metadata is enabled."
    }
    else {
        Add-Check -Name "$methodName metadata" -Status "WARN" -Details "Public metadata is disabled; CASH-only rollout remains possible."
    }
}

if ([string]::IsNullOrWhiteSpace($AdminToken)) {
    if ($RequireOnlinePayments) {
        Add-Check -Name "Payment admin gates" -Status "FAIL" -Details "GORIDE_ADMIN_TOKEN or -AdminToken is required for online-payment launch validation."
    }
    else {
        Add-Check -Name "Payment admin gates" -Status "SKIP" -Details "No admin token supplied; readiness and UAT evidence were not queried."
    }
}
else {
    $providerReadinessResult = Get-ApiData -CheckName "Payment provider readiness endpoint" -Path "/api/v1/payments/providers/readiness" -UseAdminToken
    $providerReadiness = @(if ($providerReadinessResult.succeeded) { $providerReadinessResult.data })

    $uatResult = Get-ApiData -CheckName "Payment UAT evidence endpoint" -Path "/api/v1/payments/providers/sandbox-uat-results" -UseAdminToken
    $uatResults = @(if ($uatResult.succeeded) { $uatResult.data })

    foreach ($methodName in $onlineMethods) {
        $method = Find-ByMethod -Items $paymentMethods -Method $methodName
        $provider = Find-ByMethod -Items $providerReadiness -Method $methodName
        $uat = Find-ByMethod -Items $uatResults -Method $methodName

        if ($null -eq $provider) {
            Add-Check -Name "$methodName provider readiness" -Status "FAIL" -Details "Provider readiness result is missing."
        }
        elseif ($RequireOnlinePayments -and $provider.sandboxReady -ne $true) {
            $missing = @($provider.missingRequirements) -join ", "
            Add-Check -Name "$methodName provider readiness" -Status "FAIL" -Details "sandboxReady=false; missing: $missing"
        }
        elseif ($provider.sandboxReady -eq $true) {
            Add-Check -Name "$methodName provider readiness" -Status "PASS" -Details "Checkout and webhook sandbox configuration are ready."
        }
        else {
            Add-Check -Name "$methodName provider readiness" -Status "WARN" -Details "Provider sandbox is not ready."
        }

        if ($null -eq $uat) {
            Add-Check -Name "$methodName UAT evidence" -Status "FAIL" -Details "Aggregate UAT result is missing."
        }
        elseif ($RequireOnlinePayments -and $uat.readyForFrontendExposure -ne $true) {
            $missingChecks = @($uat.missingChecks) -join ", "
            Add-Check -Name "$methodName UAT evidence" -Status "FAIL" -Details "readyForFrontendExposure=false; missing: $missingChecks"
        }
        elseif ($uat.readyForFrontendExposure -eq $true) {
            Add-Check -Name "$methodName UAT evidence" -Status "PASS" -Details "Aggregate evidence allows frontend exposure."
        }
        else {
            Add-Check -Name "$methodName UAT evidence" -Status "WARN" -Details "Aggregate evidence does not allow frontend exposure."
        }

        if ($null -ne $method -and $method.enabled -eq $true -and
                ($null -eq $uat -or $uat.readyForFrontendExposure -ne $true)) {
            Add-Check -Name "$methodName exposure consistency" -Status "FAIL" -Details "Public metadata is enabled before aggregate UAT exposure gate passed."
        }
        elseif ($null -ne $method -and $method.enabled -eq $true) {
            Add-Check -Name "$methodName exposure consistency" -Status "PASS" -Details "Public metadata and aggregate UAT gate agree."
        }
        else {
            Add-Check -Name "$methodName exposure consistency" -Status "PASS" -Details "Provider is not exposed publicly."
        }
    }
}

$failedCount = @($checks | Where-Object { $_.status -eq "FAIL" }).Count
$warningCount = @($checks | Where-Object { $_.status -eq "WARN" }).Count
$report = [ordered]@{
    generatedAt = [DateTimeOffset]::UtcNow.ToString("o")
    baseUrl = $baseUri
    requireServiceAreas = [bool]$RequireServiceAreas
    requireOnlinePayments = [bool]$RequireOnlinePayments
    requireTracing = [bool]$RequireTracing
    success = ($failedCount -eq 0)
    failedCount = $failedCount
    warningCount = $warningCount
    checks = @($checks)
}

if (-not [string]::IsNullOrWhiteSpace($OutputPath)) {
    $resolvedOutput = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
    $outputDirectory = Split-Path -Parent $resolvedOutput
    if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
        New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
    }
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $resolvedOutput -Encoding UTF8
    Write-Host ""
    Write-Host "Report: $resolvedOutput"
}

Write-Host ""
Write-Host ("Summary: {0} failed, {1} warning(s), {2} total check(s)." -f $failedCount, $warningCount, $checks.Count)
if ($failedCount -gt 0) {
    exit 1
}
exit 0
