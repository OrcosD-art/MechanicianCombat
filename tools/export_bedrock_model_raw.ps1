param(
    [Parameter(Mandatory = $true)][string]$Model,
    [Parameter(Mandatory = $true)][string]$Output,
    [string]$AnimationOutput,
    [string]$Name = "entity_model",
    [int]$Port = 9366,
    [string]$Exe = "C:\Users\lmc\AppData\Local\Programs\Blockbench\Blockbench.exe",
    [string]$Profile = "D:\codex\mod\rebuild\bbprofile_bb4126"
)
$ErrorActionPreference = "Stop"
$outputPath = [IO.Path]::GetFullPath($Output)
$workDir = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Force -Path $workDir | Out-Null
$modelText = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $Model).Path,
    [Text.Encoding]::UTF8)
$jobPath = Join-Path $workDir "bedrock_export_job.js"
$job = "globalThis.__BB_JOB = {name:" + ($Name | ConvertTo-Json -Compress) +
    ",modelJson:" + $modelText + ",skinUv:{}};`nreturn 1;"
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
            if ($pages | Where-Object {
                    $_.type -eq "page" -and $_.webSocketDebuggerUrl
                }) {
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
        -File "D:\codex\mod\rebuild\tools\cdp.ps1" --port $Port --file $jobPath |
        Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to load model into Blockbench."
    }
    $raw = & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File "D:\codex\mod\rebuild\tools\cdp.ps1" --port $Port `
        --file "D:\codex\mod\rebuild\tools\bb_export_builtin.js"
    if ($LASTEXITCODE -ne 0) {
        throw "Bedrock model export failed."
    }
    $result = ($raw | Out-String) | ConvertFrom-Json
    $content = $null
    foreach ($entry in $result.exports) {
        if ($entry.content) {
            $content = if ($entry.content -is [string]) {
                [string]$entry.content
            } else {
                $entry.content | ConvertTo-Json -Depth 100 -Compress
            }
            break
        }
    }
    if (-not $content) {
        throw "Bedrock exporter returned no model content."
    }
    [IO.File]::WriteAllText($outputPath, $content,
        (New-Object Text.UTF8Encoding($false)))
    if ($AnimationOutput -and $result.animation_document) {
        $animationPath = [IO.Path]::GetFullPath($AnimationOutput)
        $animationContent = if ($result.animation_document -is [string]) {
            [string]$result.animation_document
        } else {
            $result.animation_document | ConvertTo-Json -Depth 100 -Compress
        }
        [IO.File]::WriteAllText($animationPath, $animationContent,
            (New-Object Text.UTF8Encoding($false)))
    }
    Write-Output "Bedrock model: $outputPath ($($content.Length) chars)"
} finally {
    taskkill /PID $process.Id /T /F 2>&1 | Out-Null
}
