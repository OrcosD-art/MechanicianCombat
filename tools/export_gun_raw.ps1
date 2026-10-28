param(
    [Parameter(Mandatory = $true)][string]$Model,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$Prefix = "gun",
    [int]$Port = 9341,
    [string]$Exe = "D:\codex\mod\rebuild\tools\Blockbench5-portable\Blockbench.exe",
    [string]$Profile = "D:\codex\mod\rebuild\bbprofile_bb522"
)
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Model)) {
    throw "Model not found: $Model"
}
if (-not (Test-Path -LiteralPath $Exe)) {
    throw "Blockbench not found: $Exe"
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$modelPath = (Resolve-Path -LiteralPath $Model).Path
$modelText = [IO.File]::ReadAllText($modelPath, [Text.Encoding]::UTF8)
$jobPath = Join-Path $OutputDirectory "raw_export_job.js"
$pluginLoaderPath = Join-Path $OutputDirectory "load_geckolib.js"
$pluginPath = "D:\codex\mod\rebuild\tools\geckolib.js"
$job = "globalThis.__BB_JOB = {name:" + ($Prefix | ConvertTo-Json -Compress) +
    ",modelJson:" + $modelText + ",skinUv:{}};`nreturn 1;"
[IO.File]::WriteAllText($jobPath, $job, (New-Object Text.UTF8Encoding($false)))
$pluginLoader = @"
const info = { catalog:false, loaded:false, actions:[], err:null };
function findGecko() {
    const arr = Plugins.all || [];
    return arr.filter(p => p && p.id === 'geckolib')[0] || arr['geckolib'] || null;
}
try {
    if (Plugins.loading_promise) { try { await Plugins.loading_promise; } catch (e) {} }
    for (let i=0; i<60; i++) {
        info.catalog = !!findGecko();
        if (info.catalog) break;
        await new Promise(r => setTimeout(r, 500));
    }
    const p = findGecko();
    if (!p) throw new Error('geckolib missing from plugin catalog');
    const alreadyLoaded = Object.keys(BarItems).some(k => /^export_geckolib/.test(k));
    if (!alreadyLoaded) {
        await p.load($($pluginPath | ConvertTo-Json -Compress));
    }
    info.loaded = alreadyLoaded || !!p.loaded;
    for (let i=0; i<40; i++) {
        info.actions = Object.keys(BarItems).filter(k => /^export_geckolib/.test(k));
        if (info.actions.length) break;
        await new Promise(r => setTimeout(r, 500));
    }
} catch (e) { info.err = String(e && (e.stack || e.message || e)); }
return JSON.stringify(info);
"@
[IO.File]::WriteAllText($pluginLoaderPath, $pluginLoader,
    (New-Object Text.UTF8Encoding($false)))

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

    $pluginResult = & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File "D:\codex\mod\rebuild\tools\cdp.ps1" --port $Port --file $pluginLoaderPath
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to load GeckoLib plugin."
    }
    Write-Output "Plugin: $pluginResult"
    $useBuiltin = $true
    try {
        $pluginInfo = ($pluginResult | Out-String) | ConvertFrom-Json
        $useBuiltin = @($pluginInfo.actions).Count -eq 0
    } catch {
        $useBuiltin = $true
    }

    & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File "D:\codex\mod\rebuild\tools\cdp.ps1" --port $Port --file $jobPath | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to load model into Blockbench."
    }

    $exporter = if ($useBuiltin) {
        "D:\codex\mod\rebuild\tools\bb_export_builtin.js"
    } else {
        "D:\codex\mod\rebuild\tools\bb_export_geckolib.js"
    }
    Write-Output "Exporter: $exporter"
    $raw = & powershell.exe -NoProfile -ExecutionPolicy Bypass `
        -File "D:\codex\mod\rebuild\tools\cdp.ps1" --port $Port --file $exporter
    if ($LASTEXITCODE -ne 0) {
        throw "GeckoLib export failed."
    }
    $result = ($raw | Out-String) | ConvertFrom-Json
    Write-Output "Export log: $($result.log -join '; ')"
    Write-Output "Export items: $((@($result.exports | ForEach-Object { [string]$_.name })) -join ', ')"
    $exportedGeo = $null
    $exportedAnim = if ($result.animation_document) {
        [string]$result.animation_document
    } else {
        $null
    }
    foreach ($entry in $result.exports) {
        $name = ([string]$entry.name).ToLowerInvariant()
        if (-not $entry.content) {
            continue
        }
        if ($name.Contains("geo")) {
            $exportedGeo = [string]$entry.content
        } elseif ($name.Contains("animation")) {
            $exportedAnim = [string]$entry.content
        }
    }
    if (-not $exportedGeo -or -not $exportedAnim) {
        throw "Exporter did not return both geo and animation content."
    }

    $geoPath = Join-Path $OutputDirectory "$Prefix.geo.raw.json"
    $animPath = Join-Path $OutputDirectory "$Prefix.animation.raw.json"
    [IO.File]::WriteAllText($geoPath, $exportedGeo, (New-Object Text.UTF8Encoding($false)))
    [IO.File]::WriteAllText($animPath, $exportedAnim, (New-Object Text.UTF8Encoding($false)))

    Write-Output "Geo: $geoPath ($($exportedGeo.Length) chars)"
    Write-Output "Animation: $animPath ($($exportedAnim.Length) chars)"
    Write-Output "Animations: $($result.animations.names -join ', ')"
} finally {
    taskkill /PID $process.Id /T /F 2>&1 | Out-Null
}
