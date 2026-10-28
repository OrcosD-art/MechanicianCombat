param(
    [string]$SourceModel = "",
    [string]$Out = "D:\codex\mod\rebuild\build\auto_launcher.raw.geo.json",
    [string]$AnimationOut = "D:\codex\mod\rebuild\build\auto_launcher.raw.animation.json",
    [int]$Port = 9352,
    [string]$Exe = "D:\codex\mod\rebuild\tools\Blockbench5-portable\Blockbench.exe",
    [string]$Profile = "D:\codex\mod\rebuild\bbprofile_bb522",
    [string]$Plugin = "D:\codex\mod\rebuild\tools\geckolib.js"
)

$ErrorActionPreference = "Stop"
if (-not $SourceModel) { throw "SourceModel is required" }
$root = "D:\codex\mod\rebuild"
$jobFile = Join-Path $root "build\auto_launcher_job.js"
$exportFile = Join-Path $root "build\auto_launcher_export.js"
$cdp = Join-Path $root "tools\cdp.ps1"
New-Item -ItemType Directory -Force -Path (Join-Path $Profile "plugins") | Out-Null
Copy-Item -LiteralPath $Plugin -Destination (Join-Path $Profile "plugins\geckolib.js") -Force

$model = Get-Content -Raw -Encoding UTF8 $SourceModel
$job = "globalThis.__AUTO_JOB = {name:'auto_portable_launcher',modelJson:$model,skinUv:{}}; return 1;"
[IO.File]::WriteAllText($jobFile, $job, [Text.UTF8Encoding]::new($false))
$js = @'
const job = globalThis.__AUTO_JOB;
if (!job) throw new Error("auto job missing");
if (!BarItems["export_geckolib_model"]) {
    if (Plugins.loading_promise) {
        try { await Plugins.loading_promise; } catch (e) { }
    }
    let plugin = null;
    for (let i = 0; i < 40 && !plugin; i++) {
        const all = Plugins.all || [];
        plugin = all.filter(p => p && p.id === "geckolib")[0] || all["geckolib"] || null;
        if (!plugin) await new Promise(r => setTimeout(r, 250));
    }
    if (plugin) await plugin.load("__PLUGIN_PATH__");
}
if (!Formats.geckolib_model) throw new Error("GeckoLib plugin unavailable");
if (Project && Project.close) {
    try { Project.close(true); } catch (e) { }
}
setupProject("free");
Codecs.project.parse(job.modelJson, "auto_portable_launcher.bbmodel");
Formats.geckolib_model.select();
Project.format = Formats.geckolib_model;
Canvas.updateAll();
globalThis.__AUTO_EXPORTS = [];
Blockbench.export = async function (opts) {
    globalThis.__AUTO_EXPORTS.push({name: opts && opts.name, content: opts && opts.content});
    return opts && opts.name;
};
const action = BarItems["export_geckolib_model"];
if (!action) throw new Error("GeckoLib model export action missing");
action.click();
await new Promise(r => setTimeout(r, 1500));
for (let i = 0; i < 2 && Dialog.open; i++) {
    const buttons = [...document.querySelectorAll("button, .btn")].filter(b => b.offsetParent !== null);
    const pick = buttons.find(b => /^confirm$/i.test((b.textContent || "").trim()))
        || buttons.find(b => /^(export|save)$/i.test((b.textContent || "").trim()))
        || buttons.find(b => /^ok$/i.test((b.textContent || "").trim()));
    if (pick) pick.click();
    else Dialog.open.confirm();
    await new Promise(r => setTimeout(r, 1000));
}
const animations = BarItems["export_geckolib_animations"];
if (animations) {
    animations.click();
    await new Promise(r => setTimeout(r, 1500));
    for (let i = 0; i < 2 && Dialog.open; i++) {
        const buttons = [...document.querySelectorAll("button, .btn")].filter(b => b.offsetParent !== null);
        const pick = buttons.find(b => /^confirm$/i.test((b.textContent || "").trim()))
            || buttons.find(b => /^(export|save)$/i.test((b.textContent || "").trim()))
            || buttons.find(b => /^ok$/i.test((b.textContent || "").trim()));
        if (pick) pick.click();
        else Dialog.open.confirm();
        await new Promise(r => setTimeout(r, 1000));
    }
}
return JSON.stringify(globalThis.__AUTO_EXPORTS);
'@.Replace("__PLUGIN_PATH__", $Plugin.Replace("\", "/"))
[IO.File]::WriteAllText($exportFile, $js, [Text.UTF8Encoding]::new($false))

$process = Start-Process -FilePath $Exe -ArgumentList @(
    "--remote-debugging-port=$Port", "--no-sandbox", "--user-data-dir=$Profile"
) -WindowStyle Hidden -PassThru
try {
    $ready = $false
    for ($i = 0; $i -lt 45; $i++) {
        try {
            $content = (Invoke-WebRequest "http://127.0.0.1:$Port/json/list" -UseBasicParsing -TimeoutSec 3).Content
            if ($content -match "webSocketDebuggerUrl") { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw "Blockbench debug port did not start" }
    Start-Sleep -Seconds 8
    & powershell -NoProfile -ExecutionPolicy Bypass -File $cdp --port $Port --file $jobFile | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Blockbench model load failed" }
    $raw = & powershell -NoProfile -ExecutionPolicy Bypass -File $cdp --port $Port --file $exportFile
    if ($LASTEXITCODE -ne 0) { throw "Blockbench model export failed" }
    $exports = ($raw -join "`n") | ConvertFrom-Json
    $modelExport = $exports |
        Where-Object { [string]$_.content -match '"minecraft:geometry"' } |
        Select-Object -First 1
    if ($null -eq $modelExport) { throw "No GeckoLib geometry export returned" }
    [IO.File]::WriteAllText($Out, [string]$modelExport.content, [Text.UTF8Encoding]::new($false))
    $animationExport = $exports |
        Where-Object { [string]$_.content -match '"animations"' } |
        Select-Object -First 1
    if ($null -eq $animationExport) { throw "No GeckoLib animation export returned" }
    [IO.File]::WriteAllText(
        $AnimationOut,
        [string]$animationExport.content,
        [Text.UTF8Encoding]::new($false))
    Write-Output "Wrote $Out"
    Write-Output "Wrote $AnimationOut"
} finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
}
