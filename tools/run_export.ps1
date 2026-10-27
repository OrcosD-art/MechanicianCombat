# Launch the isolated headless Blockbench, run the GeckoLib export pipeline for one bbmodel,
# then close Blockbench again. Kept in one process tree on purpose: the harness reaps
# children when a bare command finishes.
#   powershell -NoProfile -ExecutionPolicy Bypass -File run_export.ps1 -Model <bbmodel> [-Port 9333]
param(
    [Parameter(Mandatory = $true)][string]$Model,
    [int]$Port = 9333,
    [string]$Exe = "C:\Users\lmc\AppData\Local\Programs\Blockbench\Blockbench.exe",
    [string]$Profile = "D:\codex\mod\rebuild\bbprofile",
    [string]$Plugin = "D:\codex\mod\rebuild\tools\geckolib.js",
    [string]$Python = "D:\Blender43\python\bin\python.exe",
    [string]$Exporter = "D:\codex\mod\rebuild\tools\gen_geckolib_mech.py",
    [string]$Log = "D:\codex\mod\rebuild\build\export.log",
    # fresh throwaway profile per run: a profile that has "geckolib installed" makes Blockbench
    # re-download the plugin from the CDN into a 0-byte file, which breaks the load
    [switch]$Fresh
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
if ($Fresh) { $Profile = Join-Path $Profile ("run_" + (Get-Date -Format "yyyyMMdd_HHmmss")) }

New-Item -ItemType Directory -Force -Path "$Profile\plugins" | Out-Null
# the exporter hands this exact file path to Blockbench's Plugin#load()
Copy-Item $Plugin "$Profile\plugins\geckolib.js" -Force

$p = Start-Process -FilePath $Exe -ArgumentList "--remote-debugging-port=$Port", "--no-sandbox", "--user-data-dir=$Profile" -WindowStyle Hidden -PassThru
try {
    $ready = $false
    for ($i = 0; $i -lt 45; $i++) {
        try {
            $c = (Invoke-WebRequest "http://127.0.0.1:$Port/json/list" -UseBasicParsing -TimeoutSec 3).Content
            if ($c -match "webSocketDebuggerUrl") { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw "Blockbench debug port $Port never came up" }
    Start-Sleep -Seconds 6          # let the renderer finish booting / loading plugins

    $env:BB_PORT = "$Port"
    Write-Output "exporting: $Model"
    # keep stdout and stderr apart: python buffers stdout, so a traceback would otherwise
    # end up before the prints in a merged log
    $errLog = "$Log.err"
    cmd /c "`"$Python`" -u `"$Exporter`" `"$Model`" > `"$Log`" 2> `"$errLog`""
    $code = $LASTEXITCODE
    Write-Output "exit=$code"
    Get-Content -Encoding UTF8 $Log | ForEach-Object { Write-Output $_ }
    if (Test-Path $errLog) {
        $e = Get-Content -Encoding UTF8 $errLog
        if ($e) { Write-Output "--- stderr ---"; $e | ForEach-Object { Write-Output $_ } }
    }
}
finally {
    taskkill /PID $p.Id /T /F 2>&1 | Out-Null
}
