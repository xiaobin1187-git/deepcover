param(
    [int]$TargetRps = 200,
    [int]$DurationSeconds = 15,
    [int]$Concurrency = 64,
    [int]$Warmup = 200,
    [int]$SampleRate = 10000,
    [int]$AppPort = 18180,
    [int]$ReceiverPort = 18181,
    [int]$SandboxPort = 4870,
    [int]$DrainTimeoutSeconds = 120,
    [string]$JavaPath = "",
    [string]$JcmdPath = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$resultRoot = Join-Path $PSScriptRoot ("results\profile-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
$runtimeHome = Join-Path $resultRoot "sandbox-runtime"
$demoJar = Join-Path $repoRoot "examples\demo-servlet\target\demo-servlet-1.0-SNAPSHOT-standalone.jar"
$agentJar = Join-Path $repoRoot "target\deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar"
$python = (Get-Command python).Source

if ([string]::IsNullOrWhiteSpace($JavaPath)) {
    $JavaPath = (Get-Command java).Source
}
if ([string]::IsNullOrWhiteSpace($JcmdPath)) {
    $JcmdPath = Join-Path (Split-Path -Parent $JavaPath) "jcmd.exe"
}
if (!(Test-Path -LiteralPath $JavaPath)) { throw "Java not found: $JavaPath" }
if (!(Test-Path -LiteralPath $JcmdPath)) { throw "jcmd not found: $JcmdPath" }
if (!(Test-Path -LiteralPath $agentJar)) { throw "Agent jar not found: $agentJar" }
if (!(Test-Path -LiteralPath $demoJar)) { throw "Demo jar not found: $demoJar" }

New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $runtimeHome "cfg"),(Join-Path $runtimeHome "lib"),(Join-Path $runtimeHome "module"),(Join-Path $runtimeHome "sandbox-module") -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repoRoot "sandbox\lib\sandbox-agent.jar"),(Join-Path $repoRoot "sandbox\lib\sandbox-core.jar"),(Join-Path $repoRoot "sandbox\lib\sandbox-spy.jar") -Destination (Join-Path $runtimeHome "lib")
Copy-Item -LiteralPath (Join-Path $repoRoot "sandbox\module\sandbox-mgr-module.jar") -Destination (Join-Path $runtimeHome "module")
Copy-Item -LiteralPath $agentJar -Destination (Join-Path $runtimeHome "sandbox-module")

$sandboxProperties = @"
system_module=../module
user_module=../sandbox-module
server.ip=127.0.0.1
server.port=$SandboxPort
server.charset=UTF-8
unsafe.enable=false
"@
Set-Content -LiteralPath (Join-Path $runtimeHome "cfg\sandbox.properties") -Value $sandboxProperties -Encoding ASCII

function Wait-Http([string]$Url, [int]$TimeoutSeconds = 30) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 2
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) { return }
        } catch {
            Start-Sleep -Milliseconds 250
        }
    }
    throw "Timed out waiting for $Url"
}

function Get-ResponseText($Response) {
    if ($Response.Content -is [byte[]]) {
        return [Text.Encoding]::UTF8.GetString($Response.Content)
    }
    return [string]$Response.Content
}

function Get-JsonResponse([string]$Url) {
    $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 5
    return (Get-ResponseText $response) | ConvertFrom-Json
}

function Wait-AgentDrain([string]$MetricsUrl, [int]$TimeoutSeconds) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $metrics = Get-JsonResponse $MetricsUrl
        $settledSends = [long]$metrics.sendSuccess + [long]$metrics.sendFailed
        if ([long]$metrics.queueDepth -eq 0 -and $settledSends -ge [long]$metrics.collectedRequests) {
            return $metrics
        }
        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for DeepCover queue to drain"
}

function Stop-OwnedProcess($Process) {
    if ($null -ne $Process -and !$Process.HasExited) {
        Stop-Process -Id $Process.Id -Force
        $Process.WaitForExit(5000) | Out-Null
    }
}

$receiver = Start-Process -FilePath $python -ArgumentList @((Join-Path $PSScriptRoot "mock_receiver.py"), "--port", $ReceiverPort) -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $resultRoot "receiver.out.log") -RedirectStandardError (Join-Path $resultRoot "receiver.err.log")
$app = $null
$recordingName = "DeepCoverProfile"
$jfrPath = Join-Path $resultRoot "profile.jfr"

try {
    Wait-Http "http://127.0.0.1:$ReceiverPort/stats"

    $javaVersion = (& $JavaPath -version 2>&1 | Out-String).Trim()
    $javaArguments = @()
    if ($javaVersion -match 'version "1\.8\.') {
        $javaArguments += @("-XX:+UnlockCommercialFeatures", "-XX:+FlightRecorder")
    }
    $agentPath = Join-Path $runtimeHome "lib\sandbox-agent.jar"
    $agentOptions = "-javaagent:$agentPath=home=$runtimeHome;server.ip=127.0.0.1;server.port=$SandboxPort;namespace=default"
    $javaArguments += @(
        $agentOptions,
        "-Dapp.name=demo-servlet",
        "-Denv=test",
        "-Ddeepcover.env=test",
        "-Ddeepcover.configCenterEnabled=false",
        "-Ddeepcover.packageName=io\.deepcover\.examples\.demo\..*",
        "-Ddeepcover.sampleRate=$SampleRate",
        "-Ddeepcover.dataCenterAddr=http://127.0.0.1:$ReceiverPort/collect",
        "-Ddeepcover.sendDataCenterType=1",
        "-Ddeepcover.queueNum=1",
        "-Ddeepcover.queueSize=20000",
        "-Ddeepcover.queueMsgSize=100",
        "-Ddeepcover.queueRecycleTime=10",
        "-Ddemo.port=$AppPort",
        "-jar",
        $demoJar
    )

    $app = Start-Process -FilePath $JavaPath -ArgumentList $javaArguments -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $resultRoot "app.out.log") -RedirectStandardError (Join-Path $resultRoot "app.err.log")
    Wait-Http "http://127.0.0.1:$AppPort/demo-servlet/user?action=list" 45

    & $JcmdPath $app.Id JFR.start "name=$recordingName" "settings=profile"
    if ($LASTEXITCODE -ne 0) { throw "JFR.start failed" }

    & $python (Join-Path $PSScriptRoot "http_benchmark.py") `
        --url "http://127.0.0.1:$AppPort/demo-servlet/user" `
        --target-rps $TargetRps `
        --duration-seconds $DurationSeconds `
        --concurrency $Concurrency `
        --warmup $Warmup `
        --process-pid $app.Id `
        --scenario "profile-sample-$SampleRate-rps-$TargetRps" `
        --output (Join-Path $resultRoot "benchmark.json")
    if ($LASTEXITCODE -ne 0) { throw "Benchmark client failed" }

    & $JcmdPath $app.Id JFR.dump "name=$recordingName" "filename=$jfrPath"
    if ($LASTEXITCODE -ne 0) { throw "JFR.dump failed" }
    & $JcmdPath $app.Id JFR.stop "name=$recordingName"
    if ($LASTEXITCODE -ne 0) { throw "JFR.stop failed" }

    $metricsUrl = "http://127.0.0.1:$SandboxPort/sandbox/default/module/http/deepcover/metrics"
    $metrics = Wait-AgentDrain $metricsUrl $DrainTimeoutSeconds
    $receiverStats = Get-JsonResponse "http://127.0.0.1:$ReceiverPort/stats"
    $metrics | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $resultRoot "metrics.json") -Encoding UTF8
    $receiverStats | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $resultRoot "receiver.json") -Encoding UTF8
    [ordered]@{
        timestamp_utc = [DateTime]::UtcNow.ToString("o")
        java = $javaVersion
        git_commit = (& git -C $repoRoot rev-parse HEAD | Out-String).Trim()
        git_worktree_dirty = @(& git -C $repoRoot status --porcelain).Count -gt 0
        sample_rate = $SampleRate
        target_rps = $TargetRps
        duration_seconds = $DurationSeconds
        concurrency = $Concurrency
        warmup_requests = $Warmup
    } | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $resultRoot "environment.json") -Encoding UTF8

    Write-Output "Profile results: $resultRoot"
    if ([long]$metrics.sendFailed -ne 0) {
        throw "DeepCover reported send failures: $($metrics.sendFailed)"
    }
    if ([long]$receiverStats.requests -ne [long]$metrics.sendSuccess) {
        throw "Receiver/sendSuccess mismatch: receiver=$($receiverStats.requests), sendSuccess=$($metrics.sendSuccess)"
    }
} finally {
    Stop-OwnedProcess $app
    Stop-OwnedProcess $receiver
}
