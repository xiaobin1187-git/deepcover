param(
    [int]$Requests = 10000,
    [int]$Concurrency = 32,
    [int]$Warmup = 1000,
    [int]$Repeats = 3,
    [int]$AppPort = 18080,
    [int]$ReceiverPort = 18081,
    [string]$TargetRps = "",
    [int]$DurationSeconds = 15,
    [int]$DrainTimeoutSeconds = 120,
    [switch]$SkipBuild,
    [string]$MavenSettings = $env:DEEPCOVER_MAVEN_SETTINGS
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$resultRoot = Join-Path $PSScriptRoot ("results\" + (Get-Date -Format "yyyyMMdd-HHmmss"))
$runtimeHome = Join-Path $resultRoot "sandbox-runtime"
$demoProject = Join-Path $repoRoot "examples\demo-servlet"
$demoJar = Join-Path $demoProject "target\demo-servlet-1.0-SNAPSHOT-standalone.jar"
$agentJar = Join-Path $repoRoot "target\deepcover-agent-1.0-SNAPSHOT-jar-with-dependencies.jar"
$python = (Get-Command python).Source
$java = (Get-Command java).Source

New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null

if (!$SkipBuild) {
    $rootBuildArgs = @("-f", (Join-Path $repoRoot "pom.xml"), "clean", "package", "-Dmaven.javadoc.skip=true")
    if (![string]::IsNullOrWhiteSpace($MavenSettings)) {
        $rootBuildArgs = @("-s", $MavenSettings) + $rootBuildArgs
    }
    & mvn @rootBuildArgs
    if ($LASTEXITCODE -ne 0) { throw "DeepCover build failed" }
    Push-Location $demoProject
    try {
        $demoBuildArgs = @("clean", "package", "-Dmaven.javadoc.skip=true")
        if (![string]::IsNullOrWhiteSpace($MavenSettings)) {
            $demoBuildArgs = @("-s", $MavenSettings) + $demoBuildArgs
        }
        & mvn @demoBuildArgs
        if ($LASTEXITCODE -ne 0) { throw "Demo build failed" }
    } finally {
        Pop-Location
    }
}

if (!(Test-Path -LiteralPath $agentJar)) { throw "Agent jar not found: $agentJar" }
if (!(Test-Path -LiteralPath $demoJar)) { throw "Demo jar not found: $demoJar" }

New-Item -ItemType Directory -Path (Join-Path $runtimeHome "cfg"),(Join-Path $runtimeHome "lib"),(Join-Path $runtimeHome "module"),(Join-Path $runtimeHome "sandbox-module") -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $repoRoot "sandbox\lib\sandbox-agent.jar"),(Join-Path $repoRoot "sandbox\lib\sandbox-core.jar"),(Join-Path $repoRoot "sandbox\lib\sandbox-spy.jar") -Destination (Join-Path $runtimeHome "lib")
Copy-Item -LiteralPath (Join-Path $repoRoot "sandbox\module\sandbox-mgr-module.jar") -Destination (Join-Path $runtimeHome "module")
Copy-Item -LiteralPath $agentJar -Destination (Join-Path $runtimeHome "sandbox-module")
$sandboxProperties = @"
system_module=../module
user_module=../sandbox-module
server.ip=127.0.0.1
server.port=4769
server.charset=UTF-8
unsafe.enable=false
"@
Set-Content -LiteralPath (Join-Path $runtimeHome "cfg\sandbox.properties") -Value $sandboxProperties -Encoding ASCII

$javaVersion = (& $java -version 2>&1 | Out-String).Trim()
$gitCommit = (& git -C $repoRoot rev-parse HEAD 2>$null | Out-String).Trim()
$gitDirty = @(& git -C $repoRoot status --porcelain 2>$null).Count -gt 0
$cpu = Get-CimInstance Win32_Processor | Select-Object -First 1
$operatingSystem = Get-CimInstance Win32_OperatingSystem
$environment = [ordered]@{
    timestamp_utc = [DateTime]::UtcNow.ToString("o")
    os = [System.Runtime.InteropServices.RuntimeInformation]::OSDescription
    processor = $cpu.Name.Trim()
    physical_cores = $cpu.NumberOfCores
    logical_processors = $cpu.NumberOfLogicalProcessors
    memory_gb = [math]::Round($operatingSystem.TotalVisibleMemorySize / 1MB, 1)
    java = $javaVersion
    powershell = $PSVersionTable.PSVersion.ToString()
    git_commit = $gitCommit
    git_worktree_dirty = $gitDirty
    requests = $Requests
    concurrency = $Concurrency
    warmup_requests = $Warmup
    repeats = $Repeats
    target_rps = $TargetRps
    duration_seconds = $DurationSeconds
    drain_timeout_seconds = $DrainTimeoutSeconds
}
$environment | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $resultRoot "environment.json") -Encoding UTF8

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
    $response = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 2
    return (Get-ResponseText $response) | ConvertFrom-Json
}

function Wait-AgentDrain([string]$MetricsUrl, [int]$TimeoutSeconds = 30) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $metrics = Get-JsonResponse $MetricsUrl
        $settledSends = [long]$metrics.sendSuccess + [long]$metrics.sendFailed
        if ([long]$metrics.queueDepth -eq 0 -and $settledSends -ge [long]$metrics.collectedRequests) {
            return $metrics
        }
        Start-Sleep -Milliseconds 100
    } while ((Get-Date) -lt $deadline)

    throw "Timed out waiting for DeepCover queue to drain: queueDepth=$($metrics.queueDepth), collected=$($metrics.collectedRequests), sendSuccess=$($metrics.sendSuccess), sendFailed=$($metrics.sendFailed)"
}

function Stop-OwnedProcess($Process) {
    if ($null -ne $Process -and !$Process.HasExited) {
        Stop-Process -Id $Process.Id -Force
        $Process.WaitForExit(5000) | Out-Null
    }
}

$receiverOut = Join-Path $resultRoot "receiver.out.log"
$receiverErr = Join-Path $resultRoot "receiver.err.log"
$receiver = Start-Process -FilePath $python -ArgumentList @((Join-Path $PSScriptRoot "mock_receiver.py"), "--port", $ReceiverPort) -PassThru -WindowStyle Hidden -RedirectStandardOutput $receiverOut -RedirectStandardError $receiverErr

try {
    Wait-Http "http://127.0.0.1:$ReceiverPort/stats"
    $scenarioIndex = 0
    $scenarios = @(
        @{ Name = "baseline"; SampleRate = $null },
        @{ Name = "sample-10"; SampleRate = 1000 },
        @{ Name = "sample-100"; SampleRate = 10000 }
    )
    $loadProfiles = @()
    if ([string]::IsNullOrWhiteSpace($TargetRps)) {
        $loadProfiles += @{ Name = "max"; TargetRps = $null }
    } else {
        foreach ($value in $TargetRps.Split(',')) {
            $parsed = 0
            if (![int]::TryParse($value.Trim(), [ref]$parsed) -or $parsed -le 0) {
                throw "TargetRps must contain positive comma-separated integers: $TargetRps"
            }
            $loadProfiles += @{ Name = "rps-$parsed"; TargetRps = $parsed }
        }
    }

    for ($repeat = 1; $repeat -le $Repeats; $repeat++) {
        foreach ($loadProfile in $loadProfiles) {
            $scenarioOffset = ($repeat - 1) % $scenarios.Count
            $orderedScenarios = for ($index = 0; $index -lt $scenarios.Count; $index++) {
                $scenarios[($scenarioOffset + $index) % $scenarios.Count]
            }
            foreach ($scenario in $orderedScenarios) {
            $scenarioIndex++
            Invoke-WebRequest -UseBasicParsing -Method Post -Uri "http://127.0.0.1:$ReceiverPort/reset" | Out-Null
            $name = "$($scenario.Name)-$($loadProfile.Name)-run-$repeat"
            $appOut = Join-Path $resultRoot "$name.out.log"
            $appErr = Join-Path $resultRoot "$name.err.log"
            $arguments = @("-Ddemo.port=$AppPort")
            if ($null -ne $scenario.SampleRate) {
                $sandboxPort = 4769 + $scenarioIndex
                $agentPath = Join-Path $runtimeHome "lib\sandbox-agent.jar"
                $agentOptions = "-javaagent:$agentPath=home=$runtimeHome;server.ip=127.0.0.1;server.port=$sandboxPort;namespace=default"
                $arguments = @(
                    $agentOptions,
                    "-Dapp.name=demo-servlet",
                    "-Denv=test",
                    "-Ddeepcover.env=test",
                    "-Ddeepcover.configCenterEnabled=false",
                    "-Ddeepcover.packageName=io\.deepcover\.examples\.demo\..*",
                    "-Ddeepcover.sampleRate=$($scenario.SampleRate)",
                    "-Ddeepcover.dataCenterAddr=http://127.0.0.1:$ReceiverPort/collect",
                    "-Ddeepcover.sendDataCenterType=1",
                    "-Ddeepcover.queueNum=1",
                    "-Ddeepcover.queueSize=20000",
                    "-Ddeepcover.queueMsgSize=100",
                    "-Ddeepcover.queueRecycleTime=10",
                    "-Ddemo.port=$AppPort"
                )
            }
            $arguments += @("-jar", $demoJar)
            $app = Start-Process -FilePath $java -ArgumentList $arguments -PassThru -WindowStyle Hidden -RedirectStandardOutput $appOut -RedirectStandardError $appErr
            try {
                Wait-Http "http://127.0.0.1:$AppPort/demo-servlet/user?action=list" 45
                $output = Join-Path $resultRoot "$name.json"
                $benchmarkArgs = @(
                    (Join-Path $PSScriptRoot "http_benchmark.py"),
                    "--url", "http://127.0.0.1:$AppPort/demo-servlet/user",
                    "--requests", $Requests,
                    "--concurrency", $Concurrency,
                    "--warmup", $Warmup,
                    "--process-pid", $app.Id,
                    "--scenario", $name,
                    "--output", $output
                )
                if ($null -ne $loadProfile.TargetRps) {
                    $benchmarkArgs += @("--target-rps", $loadProfile.TargetRps, "--duration-seconds", $DurationSeconds)
                }
                & $python @benchmarkArgs
                if ($LASTEXITCODE -ne 0) { throw "Benchmark client failed for $name" }
                if ($null -ne $scenario.SampleRate) {
                    $metricsUrl = "http://127.0.0.1:$sandboxPort/sandbox/default/module/http/deepcover/metrics"
                    Wait-Http $metricsUrl 15
                    $metrics = Wait-AgentDrain $metricsUrl $DrainTimeoutSeconds
                    $metrics | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $resultRoot "$name-metrics.json") -Encoding UTF8
                }
                $receiverStats = Get-JsonResponse "http://127.0.0.1:$ReceiverPort/stats"
                $receiverStats | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $resultRoot "$name-receiver.json") -Encoding UTF8
                if ($null -ne $scenario.SampleRate) {
                    if ([long]$metrics.sendFailed -ne 0) {
                throw "DeepCover reported send failures for ${name}: $($metrics.sendFailed)"
                    }
                    if ([long]$receiverStats.requests -ne [long]$metrics.sendSuccess) {
                throw "Receiver/sendSuccess mismatch for ${name}: receiver=$($receiverStats.requests), sendSuccess=$($metrics.sendSuccess)"
                    }
                } elseif ([long]$receiverStats.requests -ne 0) {
                    throw "Baseline unexpectedly sent DeepCover payloads: $($receiverStats.requests)"
                }
            } finally {
                Stop-OwnedProcess $app
                Start-Sleep -Seconds 2
            }
        }
        }
    }
    Write-Output "Benchmark results: $resultRoot"
} finally {
    Stop-OwnedProcess $receiver
}
