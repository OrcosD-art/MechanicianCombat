# Minimal Chrome DevTools Protocol driver for Blockbench.
# Same CLI/exit codes as bb_cdp.mjs so the python pipeline can call it unchanged:
#   powershell -NoProfile -ExecutionPolicy Bypass -File cdp.ps1 --port 9333 --file expr.js
param(
    [string]$Port = "9222",
    [string]$File = "",
    [string]$Expr = ""
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8

if ($File) { $Expr = [IO.File]::ReadAllText($File, [Text.Encoding]::UTF8) }
if (-not $Expr) { [Console]::Error.WriteLine("need --file or --expr"); exit 2 }

$list = (Invoke-WebRequest -Uri "http://127.0.0.1:$Port/json/list" -UseBasicParsing -TimeoutSec 20).Content | ConvertFrom-Json
$page = $list | Where-Object { $_.type -eq "page" -and $_.webSocketDebuggerUrl } | Select-Object -First 1
if (-not $page) { [Console]::Error.WriteLine("no debuggable page found on port $Port"); exit 3 }

$wrapped = "(async () => { try { const v = await (async()=>{ " + $Expr + " })(); const s = (typeof v === 'undefined' || v === null) ? '' : (typeof v === 'string' ? v : JSON.stringify(v)); return 'OK:' + btoa(unescape(encodeURIComponent(s))); } catch (e) { return 'ERR:' + btoa(unescape(encodeURIComponent(String(e && e.stack || e)))); } })()"
$request = @{ id = 1; method = "Runtime.evaluate"; params = @{ expression = $wrapped; awaitPromise = $true; returnByValue = $true; userGesture = $true } } | ConvertTo-Json -Compress -Depth 6

$ws = [System.Net.WebSockets.ClientWebSocket]::new()
$ct = [System.Threading.CancellationToken]::None
$ws.ConnectAsync([Uri]$page.webSocketDebuggerUrl, $ct).GetAwaiter().GetResult() | Out-Null
$bytes = [Text.Encoding]::UTF8.GetBytes($request)
$ws.SendAsync([ArraySegment[byte]]::new($bytes), [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $ct).GetAwaiter().GetResult() | Out-Null

$buf = New-Object byte[] 65536
$ms = New-Object System.IO.MemoryStream
do {
    $r = $ws.ReceiveAsync([ArraySegment[byte]]::new($buf), $ct).GetAwaiter().GetResult()
    $ms.Write($buf, 0, $r.Count)
} while (-not $r.EndOfMessage)
$ws.Dispose()
$envelope = [Text.Encoding]::UTF8.GetString($ms.ToArray())

$flat = $envelope -replace '\s+', ''
$mark = '"type":"string","value":"'
$at = $flat.IndexOf($mark)
if ($at -lt 0) {
    if ($flat.Contains('exceptionDetails')) { [Console]::Error.WriteLine("EVAL EXCEPTION: " + $flat.Substring(0, [Math]::Min(2000, $flat.Length))) }
    exit 4
}
$start = $at + $mark.Length
$end = $flat.IndexOf('"', $start)
$payload = $flat.Substring($start, $end - $start)

if ($payload.StartsWith("ERR:")) {
    $msg = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload.Substring(4)))
    [Console]::Error.WriteLine("JS ERROR:`n" + $msg)
    exit 5
}
$out = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload.Substring(3)))
[Console]::Out.Write($out)
