param([switch]$NoSpring)

$ErrorActionPreference = "Stop"
$projectDir = Split-Path $PSScriptRoot -Parent
$runtimeDir = Join-Path $projectDir ".runtime"
$logDir = Join-Path $runtimeDir "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

function Import-DotEnv([string]$path) {
    if (-not (Test-Path $path)) { return }
    foreach ($line in Get-Content -LiteralPath $path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#") -or -not $trimmed.Contains("=")) { continue }
        $parts = $trimmed.Split("=", 2)
        $value = $parts[1].Trim()
        if ($value.Length -ge 2 -and $value.StartsWith('"') -and $value.EndsWith('"')) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        [Environment]::SetEnvironmentVariable($parts[0].Trim(), $value, "Process")
    }
}

function Test-Port([int]$port) {
    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        $task = $client.ConnectAsync("127.0.0.1", $port)
        if (-not $task.Wait(800)) { $client.Dispose(); return $false }
        $ok = $client.Connected
        $client.Dispose()
        return $ok
    } catch { return $false }
}

function Wait-Port([int]$port, [int]$seconds, [string]$name) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do {
        if (Test-Port $port) { return }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)
    throw "$name khong san sang sau $seconds giay. Xem log trong $logDir"
}

function Wait-Http([string]$url, [int]$seconds, [string]$name) {
    $deadline = (Get-Date).AddSeconds($seconds)
    do {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) { return }
        } catch {}
        Start-Sleep -Milliseconds 800
    } while ((Get-Date) -lt $deadline)
    throw "$name khong san sang sau $seconds giay. Xem log trong $logDir"
}

function Start-ManagedProcess([string]$name, [string]$file, [string[]]$arguments, [string]$workingDir) {
    $stdout = Join-Path $logDir "$name.log"
    $stderr = Join-Path $logDir "$name-error.log"
    $quotedArguments = $arguments | ForEach-Object {
        if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ }
    }
    $process = Start-Process -FilePath $file -ArgumentList ($quotedArguments -join ' ') -WorkingDirectory $workingDir `
        -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
    Set-Content -LiteralPath (Join-Path $runtimeDir "$name.pid") -Value $process.Id -Encoding ASCII
    return $process
}

Import-DotEnv (Join-Path $projectDir ".env")
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;C:\Tools\apache-maven-3.9.16\bin;C:\Users\ADMIN\AppData\Roaming\Python\Python312\Scripts;$env:PATH"

$comfyBase = "C:\Users\ADMIN\AppData\Local\Comfy-Desktop\ComfyUI-Installs\ComfyUI"
$comfyPython = Join-Path $comfyBase "ComfyUI\.venv\Scripts\python.exe"
$comfyMain = Join-Path $comfyBase "ComfyUI\main.py"
$modelConfig = "C:\Users\ADMIN\AppData\Roaming\Comfy Desktop\shared_model_paths.yaml"
$sharedBase = "C:\Users\ADMIN\AppData\Local\Comfy-Desktop\ComfyUI-Shared"

if (-not (Test-Port 8188)) {
    if (-not (Test-Path $comfyPython) -or -not (Test-Path $comfyMain)) {
        throw "Khong tim thay ComfyUI runtime. Hay cai ComfyUI Desktop truoc."
    }
    $comfyArgs = @(
        "-s", $comfyMain,
        "--enable-manager",
        "--extra-model-paths-config", $modelConfig,
        "--input-directory", (Join-Path $sharedBase "input"),
        "--output-directory", (Join-Path $sharedBase "output"),
        "--port", "8188"
    )
    Start-ManagedProcess "comfyui" $comfyPython $comfyArgs $comfyBase | Out-Null
}
Wait-Http "http://127.0.0.1:8188/system_stats" 120 "ComfyUI"

if (-not (Test-Port 8189)) {
    $bridge = Join-Path $projectDir "tools\mcp-image-server\server.py"
    $systemPython = "C:\Program Files\Python312\python.exe"
    Start-ManagedProcess "mcp-image" $systemPython @("-u", $bridge) $projectDir | Out-Null
}
Wait-Port 8189 15 "MCP Image Server"

if (-not $NoSpring) {
    if (-not (Test-Port 8080)) {
        $maven = "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd"
        Start-ManagedProcess "spring" $maven @("-f", (Join-Path $projectDir "pom.xml"), "spring-boot:run") $projectDir | Out-Null
    }
    Wait-Http "http://127.0.0.1:8080/api/jobs" 90 "AI Video Pipeline"
}

Write-Host "AI Video Studio da san sang:" -ForegroundColor Green
Write-Host "  Dashboard : http://localhost:8080"
Write-Host "  ComfyUI   : http://localhost:8188 (headless backend)"
Write-Host "  MCP Image : http://localhost:8189/mcp"
Write-Host "  Logs      : $logDir"
