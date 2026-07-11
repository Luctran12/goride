[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl,

    [Parameter()]
    [string]$AdminToken = $env:GORIDE_ADMIN_TOKEN,

    [Parameter(Mandatory)]
    [ValidateSet("momo", "vnpay")]
    [string]$Provider,

    [Parameter(Mandatory)]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$SuccessTripId,

    [Parameter(Mandatory)]
    [ValidateRange(1, [long]::MaxValue)]
    [long]$FailureTripId,

    [Parameter(Mandatory)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$SuccessCallbackPayloadPath,

    [Parameter(Mandatory)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$FailureCallbackPayloadPath,

    [Parameter(Mandatory)]
    [ValidateScript({ Test-Path -LiteralPath $_ -PathType Leaf })]
    [string]$StaleCallbackPayloadPath,

    [Parameter()]
    [ValidateRange(1, 120)]
    [int]$TimeoutSeconds = 20,

    [Parameter()]
    [ValidateLength(0, 700)]
    [string]$Notes = "Automated sandbox callback verification",

    [Parameter()]
    [string]$OutputPath
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$baseUri = $BaseUrl.TrimEnd("/")
$providerName = $Provider.ToLowerInvariant()
$expectedMethod = $providerName.ToUpperInvariant()
$checks = [System.Collections.Generic.List[object]]::new()

$parsedBaseUri = $null
if (-not [uri]::TryCreate($baseUri, [UriKind]::Absolute, [ref]$parsedBaseUri) -or
        $parsedBaseUri.Scheme -ne "https" -or
        $parsedBaseUri.AbsolutePath -ne "/" -or
        -not [string]::IsNullOrEmpty($parsedBaseUri.Query) -or
        -not [string]::IsNullOrEmpty($parsedBaseUri.Fragment)) {
    throw "BaseUrl must be an absolute HTTPS origin without query or fragment."
}

if ([string]::IsNullOrWhiteSpace($AdminToken)) {
    throw "GORIDE_ADMIN_TOKEN or -AdminToken is required."
}
if ($SuccessTripId -eq $FailureTripId) {
    throw "SuccessTripId and FailureTripId must identify different pending payments."
}

function Write-SanitizedReport {
    param([object]$Report)

    if ([string]::IsNullOrWhiteSpace($OutputPath)) {
        return
    }
    $resolvedOutput = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
    $outputDirectory = Split-Path -Parent $resolvedOutput
    if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
        New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
    }
    $Report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $resolvedOutput -Encoding UTF8
    Write-Host "Report: $resolvedOutput"
}

trap {
    $failureReport = [ordered]@{
        generatedAt = [DateTimeOffset]::UtcNow.ToString("o")
        baseUrl = $baseUri
        provider = $providerName
        success = $false
        successTripId = $SuccessTripId
        failureTripId = $FailureTripId
        checks = @($checks)
    }
    try {
        Write-SanitizedReport -Report $failureReport
    }
    catch {
        [Console]::Error.WriteLine("Could not write the sanitized failure report.")
    }
    [Console]::Error.WriteLine("Payment sandbox E2E verification failed; inspect the named checks without exposing callback payloads.")
    exit 1
}

function Add-Check {
    param(
        [string]$Name,
        [ValidateSet("PASS", "FAIL")]
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

function Assert-Condition {
    param(
        [bool]$Condition,
        [string]$Name,
        [string]$FailureMessage,
        [string]$SuccessMessage
    )

    if (-not $Condition) {
        Add-Check -Name $Name -Status "FAIL" -Details $FailureMessage
        throw $FailureMessage
    }
    Add-Check -Name $Name -Status "PASS" -Details $SuccessMessage
}

function Read-CallbackPayload {
    param([string]$Path)

    $payload = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    if ($null -eq $payload -or @($payload.PSObject.Properties).Count -eq 0) {
        throw "Callback payload file is empty or is not a JSON object: $Path"
    }
    return $payload
}

function Invoke-GorideApi {
    param(
        [ValidateSet("GET", "POST")]
        [string]$Method,
        [string]$Path,
        [object]$Body
    )

    $headers = @{
        Accept = "application/json"
        Authorization = "Bearer $AdminToken"
        "X-Request-Id" = [guid]::NewGuid().ToString()
    }
    $parameters = @{
        Method = $Method
        Uri = "$baseUri$Path"
        Headers = $headers
        TimeoutSec = $TimeoutSeconds
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }

    $response = Invoke-RestMethod @parameters
    if ($response.success -ne $true) {
        throw "$Path returned success=false."
    }
    return $response.data
}

function Invoke-MomoCallback {
    param(
        [object]$Payload,
        [switch]$ExpectRejected
    )

    $parameters = @{
        Method = "POST"
        Uri = "$baseUri/api/v1/payments/providers/momo/webhook"
        ContentType = "application/json"
        Body = $Payload | ConvertTo-Json -Depth 10 -Compress
        TimeoutSec = $TimeoutSeconds
        UseBasicParsing = $true
    }
    try {
        $response = Invoke-WebRequest @parameters
        if ($ExpectRejected) {
            throw "Stale MoMo callback was accepted with HTTP $($response.StatusCode)."
        }
        if ([int]$response.StatusCode -ne 204) {
            throw "MoMo callback expected HTTP 204, received $($response.StatusCode)."
        }
    }
    catch {
        if (-not $ExpectRejected) {
            throw
        }
        $statusCode = $null
        $responseProperty = $_.Exception.PSObject.Properties["Response"]
        if ($null -ne $responseProperty -and $null -ne $responseProperty.Value) {
            try {
                $statusCode = [int]$responseProperty.Value.StatusCode
            }
            catch {
                $statusCode = $null
            }
        }
        if ($statusCode -ne 400) {
            throw "Stale MoMo callback expected HTTP 400, received '$statusCode'."
        }
    }
}

function ConvertTo-QueryString {
    param([object]$Payload)

    return (($Payload.PSObject.Properties | Sort-Object Name | ForEach-Object {
        "{0}={1}" -f [System.Net.WebUtility]::UrlEncode($_.Name),
            [System.Net.WebUtility]::UrlEncode([string]$_.Value)
    }) -join "&")
}

function Invoke-VnpayCallback {
    param(
        [object]$Payload,
        [switch]$ExpectRejected
    )

    $query = ConvertTo-QueryString -Payload $Payload
    $response = Invoke-RestMethod `
        -Method Get `
        -Uri "$baseUri/api/v1/payments/providers/vnpay/webhook?$query" `
        -TimeoutSec $TimeoutSeconds
    $responseCode = [string]$response.RspCode
    if ($ExpectRejected) {
        if ($responseCode -ne "99") {
            throw "Stale VNPay callback expected RspCode=99, received '$responseCode'."
        }
        return
    }
    if ($responseCode -ne "00") {
        throw "VNPay callback expected RspCode=00, received '$responseCode'."
    }
}

function Invoke-ProviderCallback {
    param(
        [object]$Payload,
        [switch]$ExpectRejected
    )

    if ($providerName -eq "momo") {
        Invoke-MomoCallback -Payload $Payload -ExpectRejected:$ExpectRejected
        return
    }
    Invoke-VnpayCallback -Payload $Payload -ExpectRejected:$ExpectRejected
}

function Get-Checkout {
    param([long]$TripId)

    $checkout = Invoke-GorideApi -Method GET -Path "/api/v1/payments/trips/$TripId/checkout"
    Assert-Condition `
        -Condition ($checkout.provider -eq $providerName -and $checkout.method -eq $expectedMethod) `
        -Name "Trip $TripId provider" `
        -FailureMessage "Checkout does not belong to $expectedMethod." `
        -SuccessMessage "Checkout uses $expectedMethod."
    $checkoutUri = $null
    if (-not [uri]::TryCreate([string]$checkout.checkoutUrl, [UriKind]::Absolute, [ref]$checkoutUri) -or
            $checkoutUri.Scheme -ne "https") {
        throw "Trip $TripId checkout URL is missing or does not use HTTPS."
    }
    Assert-Condition `
        -Condition ($checkout.checkoutRequired -eq $true -and [long]$checkout.paymentId -gt 0) `
        -Name "Trip $TripId checkout" `
        -FailureMessage "Checkout response is missing a payment id or checkoutRequired=true." `
        -SuccessMessage "Payment $($checkout.paymentId) returned an HTTPS checkout URL."
    return $checkout
}

function Get-PaymentDetail {
    param([long]$TripId)
    return Invoke-GorideApi -Method GET -Path "/api/v1/payments/trips/$TripId"
}

function Assert-PaymentStatus {
    param(
        [object]$Payment,
        [string]$ExpectedStatus,
        [string]$CheckName
    )

    Assert-Condition `
        -Condition ($Payment.provider -eq $providerName -and
            $Payment.method -eq $expectedMethod -and
            $Payment.status -eq $ExpectedStatus) `
        -Name $CheckName `
        -FailureMessage "Expected $expectedMethod/$ExpectedStatus payment state." `
        -SuccessMessage "Payment $($Payment.paymentId) is $ExpectedStatus."
}

Write-Host "GoRide payment sandbox E2E automation"
Write-Host "Target: $baseUri"
Write-Host "Provider: $providerName"
Write-Host ""

$successPayload = Read-CallbackPayload -Path $SuccessCallbackPayloadPath
$failurePayload = Read-CallbackPayload -Path $FailureCallbackPayloadPath
$stalePayload = Read-CallbackPayload -Path $StaleCallbackPayloadPath

$readinessItems = @(Invoke-GorideApi -Method GET -Path "/api/v1/payments/providers/readiness")
$readiness = $readinessItems | Where-Object { $_.provider -eq $providerName } | Select-Object -First 1
Assert-Condition `
    -Condition ($null -ne $readiness -and $readiness.sandboxReady -eq $true) `
    -Name "Provider readiness" `
    -FailureMessage "$providerName is not sandboxReady." `
    -SuccessMessage "$providerName checkout and webhook configuration are ready."

$successCheckout = Get-Checkout -TripId $SuccessTripId
$failureCheckout = Get-Checkout -TripId $FailureTripId

Invoke-ProviderCallback -Payload $successPayload
Add-Check -Name "Success callback" -Status "PASS" -Details "Provider accepted the signed success callback."
Invoke-ProviderCallback -Payload $successPayload
Add-Check -Name "Idempotent replay" -Status "PASS" -Details "Provider accepted replay without a state conflict."

$successPayment = Get-PaymentDetail -TripId $SuccessTripId
Assert-PaymentStatus -Payment $successPayment -ExpectedStatus "COMPLETED" -CheckName "Success payment state"
Assert-Condition `
    -Condition (-not [string]::IsNullOrWhiteSpace([string]$successPayment.transactionRef)) `
    -Name "Success transaction reference" `
    -FailureMessage "Completed payment has no transaction reference." `
    -SuccessMessage "Completed payment has a provider transaction reference."

Invoke-ProviderCallback -Payload $stalePayload -ExpectRejected
Add-Check -Name "Freshness rejection" -Status "PASS" -Details "Provider rejected the signed stale callback."
$pendingFailurePayment = Get-PaymentDetail -TripId $FailureTripId
Assert-PaymentStatus -Payment $pendingFailurePayment -ExpectedStatus "PENDING" -CheckName "Stale callback state safety"

Invoke-ProviderCallback -Payload $failurePayload
Add-Check -Name "Failure callback" -Status "PASS" -Details "Provider accepted the signed failure callback."
$failurePayment = Get-PaymentDetail -TripId $FailureTripId
Assert-PaymentStatus -Payment $failurePayment -ExpectedStatus "FAILED" -CheckName "Failure payment state"
Assert-Condition `
    -Condition (-not [string]::IsNullOrWhiteSpace([string]$failurePayment.transactionRef)) `
    -Name "Failure transaction reference" `
    -FailureMessage "Failed payment has no transaction reference." `
    -SuccessMessage "Failed payment has a provider transaction reference."

$sessionRequest = [ordered]@{
    status = "PASSED"
    checkoutPaymentId = [long]$successCheckout.paymentId
    checkoutUrl = [string]$successCheckout.checkoutUrl
    successPaymentId = [long]$successPayment.paymentId
    successTransactionRef = [string]$successPayment.transactionRef
    failurePaymentId = [long]$failurePayment.paymentId
    failureTransactionRef = [string]$failurePayment.transactionRef
    replayTransactionRef = [string]$successPayment.transactionRef
    checkoutUrlTested = $true
    successCallbackTested = $true
    failureCallbackTested = $true
    idempotentReplayTested = $true
    freshnessRejectionTested = $true
    notes = $Notes
    testedAt = [DateTimeOffset]::UtcNow.ToString("o")
}
$session = Invoke-GorideApi `
    -Method POST `
    -Path "/api/v1/payments/providers/$providerName/sandbox-e2e-sessions" `
    -Body $sessionRequest
Assert-Condition `
    -Condition ($session.status -eq "PASSED" -and $session.sessionEvidencePassed -eq $true) `
    -Name "Session evidence" `
    -FailureMessage "Backend did not persist a complete PASSED sandbox session." `
    -SuccessMessage "Sandbox session $($session.id) persisted with all required checks."

$uatItems = @(Invoke-GorideApi -Method GET -Path "/api/v1/payments/providers/sandbox-uat-results")
$uat = $uatItems | Where-Object { $_.provider -eq $providerName } | Select-Object -First 1
Assert-Condition `
    -Condition ($null -ne $uat -and $uat.readyForFrontendExposure -eq $true) `
    -Name "Frontend exposure gate" `
    -FailureMessage "Aggregate UAT evidence is not ready for frontend exposure." `
    -SuccessMessage "Aggregate UAT evidence allows frontend exposure."

$report = [ordered]@{
    generatedAt = [DateTimeOffset]::UtcNow.ToString("o")
    baseUrl = $baseUri
    provider = $providerName
    success = $true
    successTripId = $SuccessTripId
    failureTripId = $FailureTripId
    checkoutPaymentId = [long]$successCheckout.paymentId
    successPaymentId = [long]$successPayment.paymentId
    failurePaymentId = [long]$failurePayment.paymentId
    sessionId = [long]$session.id
    aggregateReadyForFrontendExposure = [bool]$uat.readyForFrontendExposure
    checks = @($checks)
}

Write-SanitizedReport -Report $report

Write-Host "Payment sandbox E2E verification passed."
