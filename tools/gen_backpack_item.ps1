param(
    [Parameter(Mandatory = $true)][string]$SourceDir,
    [int]$Port = 9362,
    [string]$Exe = "D:\codex\mod\rebuild\bb516\Blockbench.exe",
    [string]$Profile = "D:\codex\mod\rebuild\bbprofile5"
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8

$Root = "D:\codex\mod\rebuild"
$ItemPath = (Get-ChildItem -LiteralPath $SourceDir -File -Filter "combat_backpack*.bbmodel" |
    Where-Object { $_.Name -ne "combat_backpack.bbmodel" } |
    Select-Object -First 1).FullName
if (-not $ItemPath) { throw "item bbmodel missing" }
$OutItem = Join-Path $Root "mod\src\main\resources\assets\tkgun\models\item\combat_backpack.json"
$Job = Join-Path $Root "build\combat_backpack_item_job.js"
$Exporter = Join-Path $Root "tools\bb_export_java_item.js"
$Cdp = Join-Path $Root "tools\cdp.ps1"

$item = Get-Content -LiteralPath $ItemPath -Raw -Encoding utf8 | ConvertFrom-Json
$item.java_block_version = "1.21.1"
$jobObject = [ordered]@{
    name = "combat_backpack_item"
    modelJson = $item
}
[IO.File]::WriteAllText($Job,
    "globalThis.__BB_JOB = " + ($jobObject | ConvertTo-Json -Depth 100 -Compress) + ";`nreturn 1;",
    [Text.UTF8Encoding]::new($false))

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
    Start-Sleep -Seconds 7

    & powershell -NoProfile -ExecutionPolicy Bypass -File $Cdp --port $Port --file $Job | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "load item project failed" }
    $raw = & powershell -NoProfile -ExecutionPolicy Bypass -File $Cdp --port $Port --file $Exporter
    if ($LASTEXITCODE -ne 0) { throw "export item model failed" }
    $info = ($raw -join "`n") | ConvertFrom-Json
    Write-Output ("format=" + $info.format)
    Write-Output ("exports=" + (($info.exports | ForEach-Object { $_.name }) -join ", "))
    $entry = $info.exports | Where-Object { $_.content -and ([string]$_.content).TrimStart().StartsWith("{") } | Select-Object -First 1
    if (-not $entry) { throw "no JSON item model export" }
    $doc = [string]$entry.content | ConvertFrom-Json
    if ($doc.textures.PSObject.Properties.Name -contains "0") {
        $doc.textures."0" = "tkgun:item/combat_backpack"
    }
    if ($doc.textures.PSObject.Properties.Name -contains "particle") {
        $doc.textures.particle = "tkgun:item/combat_backpack"
    } else {
        $doc.textures | Add-Member -NotePropertyName particle -NotePropertyValue "tkgun:item/combat_backpack"
    }
    foreach ($element in $doc.elements) {
        foreach ($face in @($element.faces.PSObject.Properties)) {
            if ([string]$face.Value.texture -eq "#missing") {
                $element.faces.PSObject.Properties.Remove($face.Name)
            }
        }
    }
    $json = $doc | ConvertTo-Json -Depth 100 -Compress
    [IO.File]::WriteAllText($OutItem, $json, [Text.UTF8Encoding]::new($false))
    Write-Output "wrote $OutItem"
} finally {
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
    }
}
