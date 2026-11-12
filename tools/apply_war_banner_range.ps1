$ErrorActionPreference = "Stop"

$root = "D:\codex\mod\rebuild"
$source = [Environment]::GetEnvironmentVariable(
    "WAR_BANNER_SOURCE", "Process")
if ([string]::IsNullOrWhiteSpace($source)) {
    throw "WAR_BANNER_SOURCE is required"
}
$effectSource = Join-Path $source "effect_circle.png"
$effectTarget = Join-Path $root "mod\src\main\resources\assets\mechaniciancombat\textures\entity\war_banner_effect.png"
$modelSource = Get-ChildItem -LiteralPath $source -Filter "*.bbmodel" |
    Where-Object {
        (Get-Content -Raw -Encoding UTF8 $_.FullName).Contains(
            "5a6a9aa0-d52a-794b-d365-bc6a15615115")
    } |
    Select-Object -First 1 -ExpandProperty FullName
if ([string]::IsNullOrWhiteSpace($modelSource)) {
    throw "Updated banner bbmodel was not found"
}

Add-Type -AssemblyName System.Drawing
$effect = [System.Drawing.Bitmap]::FromFile($effectSource)
try {
    if ($effect.Width -ne 256 -or $effect.Height -ne 2048) {
        throw "effect_circle.png must be 256x2048, got $($effect.Width)x$($effect.Height)"
    }
} finally {
    $effect.Dispose()
}

$model = Get-Content -Raw -Encoding UTF8 $modelSource | ConvertFrom-Json
$rangeCube = $model.elements | Where-Object {
    $_.uuid -eq "5a6a9aa0-d52a-794b-d365-bc6a15615115"
}
if ($null -eq $rangeCube -or
    ($rangeCube.from -join ",") -ne "-128,0.25,-128" -or
    ($rangeCube.to -join ",") -ne "128,0.25,128") {
    throw "Updated 16-block range plane was not found"
}
Copy-Item -LiteralPath $effectSource -Destination $effectTarget -Force

$sourceHash = (Get-FileHash -Algorithm SHA256 $effectSource).Hash
$targetHash = (Get-FileHash -Algorithm SHA256 $effectTarget).Hash
if ($sourceHash -ne $targetHash) {
    throw "Range texture copy hash mismatch"
}

[pscustomobject]@{
    RangePlane = "16 x 16 blocks at y=0.015625"
    EffectTexture = $targetHash
}
