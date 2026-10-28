# Launch the isolated Blockbench, evaluate one or more JS files over CDP, then close it.
#   powershell -NoProfile -ExecutionPolicy Bypass -File run_js.ps1 -Exe <bb.exe> -Profile <dir> -Js a.js,b.js
param(
    [Parameter(Mandatory = $true)][string[]]$Js,
    [int]$Port = 9333,
    [string]$Exe = "D:\codex\mod\rebuild\tools\Blockbench5-portable\Blockbench.exe",
    [string]$Profile = "D:\codex\mod\rebuild\bbprofile_bb522"
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
# a single "-Js a.js,b.js" argument may arrive as one string - split it up again
$Js = $Js | ForEach-Object { $_ -split ',' } | Where-Object { $_ }

New-Item -ItemType Directory -Force -Path $Profile | Out-Null
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
    Start-Sleep -Seconds 8
    foreach ($f in $Js) {
        Write-Output "=== $f ==="
        & powershell -NoProfile -ExecutionPolicy Bypass -File "D:\codex\mod\rebuild\tools\cdp.ps1" --port $Port --file $f
        Write-Output "--- exit=$LASTEXITCODE ---"
    }
}
finally {
    taskkill /PID $p.Id /T /F 2>&1 | Out-Null
}
