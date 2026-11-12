param(
    [string]$JobPath = "D:\codex\mod\rebuild\work\veritas_close_combat_current\raw_export_job.js",
    [string]$OutputDirectory = "D:\codex\mod\rebuild\work\veritas_close_combat_current",
    [int]$Port = 9361
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$root = "D:\codex\mod\rebuild"
$arguments = @(
    (Join-Path $root "tools\load_geckolib_bb5.js"),
    $JobPath,
    (Join-Path $root "tools\bb_export_geckolib.js")
) -join ","
$output = & powershell -NoProfile -ExecutionPolicy Bypass `
    -File (Join-Path $root "tools\run_js.ps1") `
    -Exe (Join-Path $root "tools\Blockbench5-portable\Blockbench.exe") `
    -Profile (Join-Path $root "work\bbprofile_war_banner_gecko") `
    -Port $Port -Js $arguments 2>&1

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
    throw "GeckoLib export result was not found:`n$($output -join "`n")"
}

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$index = 0
foreach ($entry in @($result.exports)) {
    if ([string]::IsNullOrWhiteSpace([string]$entry.content)) {
        continue
    }
    $content = [string]$entry.content
    if ($content.TrimStart().StartsWith("{")) {
        try {
            $content = $content | ConvertFrom-Json |
                ConvertTo-Json -Depth 100 -Compress
        } catch {
            # Keep non-JSON exports verbatim.
        }
    }
    $name = [string]$entry.name
    if ([string]::IsNullOrWhiteSpace($name)) {
        $name = "gecko_export_$index"
    }
    $name = ($name -replace '[^A-Za-z0-9._-]', '_')
    $path = Join-Path $OutputDirectory $name
    [IO.File]::WriteAllText($path, $content, $utf8NoBom)
    Write-Output $path
    $index++
}
