$projectDir = Split-Path $PSScriptRoot -Parent
$runtimeDir = Join-Path $projectDir ".runtime"

foreach ($name in @("spring", "mcp-image", "comfyui")) {
    $pidFile = Join-Path $runtimeDir "$name.pid"
    if (-not (Test-Path $pidFile)) { continue }
    $processId = [int](Get-Content -LiteralPath $pidFile -ErrorAction SilentlyContinue)
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($process) {
        & taskkill.exe /PID $processId /T /F 2>$null | Out-Null
        Write-Host "Da dung $name (PID $processId)"
    }
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}
