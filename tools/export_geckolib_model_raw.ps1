param(
    [Parameter(Mandatory = $true)][string]$Model,
    [Parameter(Mandatory = $true)][string]$Output,
    [string]$Name = "entity_model",
    [int]$Port = 9362,
    [string]$Exe = "C:\Users\lmc\AppData\Local\Programs\Blockbench\Blockbench.exe",
    [string]$Profile = "D:\codex\mod\rebuild\bbprofile_bb4126",
    [string]$Plugin = "D:\codex\mod\rebuild\tools\geckolib_legacy_412.js"
)
$ErrorActionPreference = "Stop"
$outputPath = [IO.Path]::GetFullPath($Output)
$workDir = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Force -Path $workDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $Profile "plugins") | Out-Null
$pluginPath = (Resolve-Path -LiteralPath $Plugin).Path.Replace("\", "/")
Copy-Item -LiteralPath $Plugin -Destination (Join-Path $Profile "plugins\geckolib.js") -Force

$modelPath = (Resolve-Path -LiteralPath $Model).Path
$modelDoc = [IO.File]::ReadAllText($modelPath, [Text.Encoding]::UTF8) |
    ConvertFrom-Json
if (-not $modelDoc.model_identifier) {
    $modelDoc | Add-Member -NotePropertyName model_identifier `
        -NotePropertyValue ("geometry." + $Name) -Force
}
$modelText = $modelDoc | ConvertTo-Json -Depth 100 -Compress
$jobPath = Join-Path $workDir "gecko_export_job.js"
$job = "globalThis.__BB_JOB = {name:" + ($Name | ConvertTo-Json -Compress) +
    ",modelJson:" + $modelText + ",skinUv:{},allowNoSkinUv:true};`nreturn 1;"
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
    $probePath = Join-Path $workDir "gecko_probe.js"
    $probe = @"
const info = {actions: Object.keys(BarItems || {}).filter(k => /^export_geckolib/.test(k)), error: null};
if (!info.actions.length) {
    try {
        if (Plugins.loading_promise) {
            try { await Plugins.loading_promise; } catch (e) { }
        }
        let p = (Plugins.all || []).filter(p => p && (p.id === 'animation_utils' || p.id === 'geckolib'))[0];
        if (p) {
            await p.load($($pluginPath | ConvertTo-Json -Compress));
            for (let i = 0; i < 40; i++) {
                if (Object.keys(BarItems || {}).some(k => /^export_geckolib/.test(k))) break;
                await new Promise(r => setTimeout(r, 500));
            }
        }
    } catch (e) { info.error = String(e && (e.stack || e)); }
}
info.actions = Object.keys(BarItems || {}).filter(k => /^export_geckolib/.test(k));
return JSON.stringify(info);
"@
    [IO.File]::WriteAllText($probePath, $probe,
        (New-Object Text.UTF8Encoding($false)))
    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File "D:\codex\mod\rebuild\tools\cdp.ps1" --port $Port --file $probePath |
        Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to ensure GeckoLib exporter."
    }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File "D:\codex\mod\rebuild\tools\cdp.ps1" --port $Port --file $jobPath |
        Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to load model into Blockbench."
    }
    $raw = & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File "D:\codex\mod\rebuild\tools\cdp.ps1" --port $Port `
        --file "D:\codex\mod\rebuild\tools\bb_export_geckolib.js"
    if ($LASTEXITCODE -ne 0) {
        throw "GeckoLib model export failed."
    }
    $result = ($raw | Out-String) | ConvertFrom-Json
    $resultPath = [IO.Path]::ChangeExtension($outputPath, ".result.json")
    [IO.File]::WriteAllText($resultPath,
        (($raw | Out-String).Trim()),
        (New-Object Text.UTF8Encoding($false)))
    $content = $null
    foreach ($entry in $result.exports) {
        if ($entry.content -and
                ([string]$entry.content).Contains('"minecraft:geometry"')) {
            $content = [string]$entry.content
            break
        }
    }
    if (-not $content) {
        $names = ($result.exports | ForEach-Object {
                "$($_.name) [$($_.type)]"
            }) -join ", "
        throw "GeckoLib exporter returned no geo content. Exports: $names"
    }
    [IO.File]::WriteAllText($outputPath, $content,
        (New-Object Text.UTF8Encoding($false)))
    Write-Output "GeckoLib model: $outputPath ($($content.Length) chars)"
} finally {
    taskkill /PID $process.Id /T /F 2>&1 | Out-Null
}
