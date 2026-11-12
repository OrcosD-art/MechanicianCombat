param(
    [Parameter(Mandatory = $true)]
    [string]$SourceModel,
    [string]$OutputDirectory = "D:\codex\mod\rebuild\work\veritas_close_combat_current",
    [int]$Port = 9360
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$root = "D:\codex\mod\rebuild"
$raw = [IO.File]::ReadAllText($SourceModel, [Text.Encoding]::UTF8)
$source = ([regex]::Replace($raw, '\\(?![\\"/bfnrtu])', '/') |
    ConvertFrom-Json)
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$jobPath = Join-Path $OutputDirectory "raw_export_job.js"
$jobDocument = [ordered]@{
    name = "close_combat"
    modelJson = $source
    skinUv = [pscustomobject]@{}
}
$jobJson = $jobDocument | ConvertTo-Json -Depth 100 -Compress
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText(
    $jobPath,
    "globalThis.__BB_JOB = $jobJson;`nreturn 1;",
    $utf8NoBom)

$jobArg = "$jobPath,$root\tools\bb_export_builtin.js"
$output = & powershell -NoProfile -ExecutionPolicy Bypass `
    -File (Join-Path $root "tools\run_js.ps1") `
    -Exe (Join-Path $root "tools\Blockbench5-portable\Blockbench.exe") `
    -Profile (Join-Path $root "work\bbprofile_war_banner_current") `
    -Port $Port -Js $jobArg 2>&1

$result = $null
for ($index = $output.Count - 1; $index -ge 0; $index--) {
    $line = ([string]$output[$index]).Trim()
    $jsonStart = $line.IndexOf("{")
    if ($jsonStart -lt 0) {
        continue
    }
    $jsonText = $line.Substring($jsonStart)
    $exitIndex = $jsonText.IndexOf("--- exit=")
    if ($exitIndex -ge 0) {
        $jsonText = $jsonText.Substring(0, $exitIndex).Trim()
    }
    try {
        $candidate = $jsonText | ConvertFrom-Json
    } catch {
        continue
    }
    if ($null -ne $candidate.exports) {
        $result = $candidate
        break
    }
}
if ($null -eq $result) {
    throw "Blockbench export result was not found:`n$($output -join "`n")"
}

$geometry = $result.exports |
    Where-Object {
        $null -ne $_.content -and
        $null -ne $_.content.bones
    } |
    Select-Object -First 1
if ($null -eq $geometry) {
    throw "Raw GeckoLib geometry export was not found"
}
$geoPath = Join-Path $OutputDirectory "close_combat.geo.raw.json"
[IO.File]::WriteAllText(
    $geoPath,
    ($geometry.content | ConvertTo-Json -Depth 100 -Compress),
    $utf8NoBom)
$animationPath = Join-Path $OutputDirectory "close_combat.animation.raw.json"
[IO.File]::WriteAllText(
    $animationPath,
    [string]$result.animation_document,
    $utf8NoBom)
Write-Output $geoPath
Write-Output $animationPath
