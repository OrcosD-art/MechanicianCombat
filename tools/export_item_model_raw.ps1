param(
    [Parameter(Mandatory = $true)][string]$Model,
    [Parameter(Mandatory = $true)][string]$Output,
    [string]$Prefix = "item",
    [int]$Port = 9346,
    [string]$Exe = "D:\codex\mod\rebuild\bb516\Blockbench.exe",
    [string]$Profile = "D:\codex\mod\rebuild\bbprofile5"
)
$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $Model)) {
    throw "Item model not found: $Model"
}
$outputPath = [IO.Path]::GetFullPath($Output)
$workDir = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Force -Path $workDir | Out-Null
$jobPath = Join-Path $workDir "item_export_job.js"
$modelText = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $Model).Path,
    [Text.Encoding]::UTF8)
$job = "globalThis.__BB_JOB = {name:" + ($Prefix | ConvertTo-Json -Compress) +
    ",modelJson:" + $modelText + "};`nreturn 1;"
[IO.File]::WriteAllText($jobPath, $job, (New-Object Text.UTF8Encoding($false)))

$process = Start-Process -FilePath $Exe -ArgumentList @(
    "--remote-debugging-port=$Port",
    "--no-sandbox",
    "--user-data-dir=$Profile"
) -WindowStyle Hidden -PassThru
try {
    $ready = $false
    for ($i = 0; $i -lt 45; $i++) {
        try {
            $pages = (Invoke-WebRequest "http://127.0.0.1:$Port/json/list" `
                -UseBasicParsing -TimeoutSec 3).Content | ConvertFrom-Json
            if ($pages | Where-Object { $_.type -eq "page" -and $_.webSocketDebuggerUrl }) {
                $ready = $true
                break
            }
        } catch {
        }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) {
        throw "Blockbench CDP port $Port did not become ready."
    }
    Start-Sleep -Seconds 7
    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File "D:\codex\mod\rebuild\tools\cdp.ps1" --port $Port --file $jobPath | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to load item model into Blockbench."
    }
    $raw = & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File "D:\codex\mod\rebuild\tools\cdp.ps1" --port $Port `
        --file "D:\codex\mod\rebuild\tools\bb_export_java_item.js"
    if ($LASTEXITCODE -ne 0) {
        throw "Java item model export failed."
    }
    $result = ($raw | Out-String) | ConvertFrom-Json
    $content = $null
    foreach ($entry in $result.exports) {
        if ($entry.content) {
            $content = [string]$entry.content
            break
        }
    }
    if (-not $content) {
        throw "Item exporter returned no model content."
    }
    [IO.File]::WriteAllText($outputPath, $content, (New-Object Text.UTF8Encoding($false)))
    Write-Output "Item model: $outputPath ($($content.Length) chars)"
} finally {
    taskkill /PID $process.Id /T /F 2>&1 | Out-Null
}
