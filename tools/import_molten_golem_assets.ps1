param(
    [string]$Root = "D:\codex\mod\rebuild",
    [Parameter(Mandatory = $true)]
    [string]$Source
)

$ErrorActionPreference = "Stop"
$resources = Join-Path $Root "mod\src\main\resources\assets\tkgun"
$geoDir = Join-Path $resources "geo\entity"
$animationDir = Join-Path $resources "animations\entity"
$textureDir = Join-Path $resources "textures\entity"
$particleTextureDir = Join-Path $resources "textures\particle"
$soundDir = Join-Path $resources "sounds"

New-Item -ItemType Directory -Force -Path $geoDir, $animationDir, $textureDir,
    $particleTextureDir, $soundDir | Out-Null

$sourceModel = Join-Path $Source "molten_golem_combat.bbmodel"
$exportInput = Join-Path $Root "build\molten_golem_export_input.bbmodel"
$modelDocument = Get-Content -Raw -Encoding UTF8 $sourceModel | ConvertFrom-Json
foreach ($element in $modelDocument.elements) {
    $element | Add-Member -NotePropertyName export -NotePropertyValue $true -Force
}
function Enable-ExportNodes($nodes) {
    foreach ($node in @($nodes)) {
        if ($node -is [string]) {
            continue
        }
        $node | Add-Member -NotePropertyName export -NotePropertyValue $true -Force
        if ($node.children) {
            Enable-ExportNodes $node.children
        }
    }
}
Enable-ExportNodes $modelDocument.outliner
[IO.File]::WriteAllText(
    $exportInput,
    ($modelDocument | ConvertTo-Json -Depth 100 -Compress),
    [Text.UTF8Encoding]::new($false))

$rawGeoPath = Join-Path $Root "build\molten_golem.raw.geo.json"
$rawAnimationPath = Join-Path $Root "build\molten_golem.raw.animation.json"
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
    (Join-Path $Root "tools\export_auto_launcher_model.ps1") `
    -SourceModel $exportInput `
    -Out $rawGeoPath `
    -AnimationOut $rawAnimationPath `
    -Port 9355
if ($LASTEXITCODE -ne 0) {
    throw "Molten golem Blockbench export failed with exit code $LASTEXITCODE"
}

Copy-Item -LiteralPath $rawGeoPath `
    -Destination (Join-Path $geoDir "molten_golem.geo.json") -Force
Copy-Item -LiteralPath (Join-Path $Source "iron_golem.png") `
    -Destination (Join-Path $textureDir "molten_golem.png") -Force
Copy-Item -LiteralPath (Join-Path $Source "effect.png") `
    -Destination (Join-Path $textureDir "molten_golem_effect.png") -Force

$soundCopies = @{
    "8191" = "golem_swing.ogg"
    "9343" = "golem_hammer.ogg"
    "20647" = "golem_explosion.ogg"
    "28705" = "golem_electric.ogg"
}
foreach ($file in Get-ChildItem -LiteralPath (Join-Path $Source "sound") -File -Filter "*.ogg") {
    $destination = $soundCopies[[string]$file.Length]
    if ($null -eq $destination) {
        throw "Unexpected golem sound file: $($file.FullName)"
    }
    Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $soundDir $destination) -Force
}

Add-Type -AssemblyName System.Drawing
$explosionSheetPath = Get-ChildItem -LiteralPath $Source -Recurse -File `
    -Filter "xplo.png" | Select-Object -First 1 -ExpandProperty FullName
if (-not $explosionSheetPath) {
    throw "xplo.png not found below $Source"
}
$explosionSheet = [System.Drawing.Image]::FromFile($explosionSheetPath)
try {
    if ($explosionSheet.Width -ne 48 -or $explosionSheet.Height -ne 288) {
        throw "Unexpected xplo.png size: $($explosionSheet.Width)x$($explosionSheet.Height)"
    }
    for ($frame = 0; $frame -lt 6; $frame++) {
        $bitmap = New-Object System.Drawing.Bitmap 48, 48
        try {
            $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.DrawImage(
                    $explosionSheet,
                    [System.Drawing.Rectangle]::new(0, 0, 48, 48),
                    [System.Drawing.Rectangle]::new(0, $frame * 48, 48, 48),
                    [System.Drawing.GraphicsUnit]::Pixel)
            } finally {
                $graphics.Dispose()
            }
            $bitmap.Save(
                (Join-Path $particleTextureDir "energy_xplo_$frame.png"),
                [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $bitmap.Dispose()
        }
    }
} finally {
    $explosionSheet.Dispose()
}

$rawGeo = Get-Content -Raw -Encoding UTF8 $rawGeoPath |
    ConvertFrom-Json
$animation = Get-Content -Raw -Encoding UTF8 $rawAnimationPath |
    ConvertFrom-Json

foreach ($property in @($animation.animations.PSObject.Properties)) {
    $property.Value.PSObject.Properties.Remove("sound_effects")
}

function Get-LandingFinalVector($boneName, $channel, $fallback) {
    $landingBone = $animation.animations.landing.bones.PSObject.Properties[$boneName]
    if ($null -eq $landingBone -or $null -eq $landingBone.Value.$channel) {
        return @($fallback)
    }
    $channelValue = $landingBone.Value.$channel
    if ($channelValue.PSObject.Properties.Name -contains "vector") {
        return @($channelValue.vector)
    }
    $last = $channelValue.PSObject.Properties |
        Sort-Object { [double]$_.Name } |
        Select-Object -Last 1
    if ($null -eq $last) {
        return @($fallback)
    }
    return @($last.Value.vector)
}

$idleBones = [ordered]@{}
foreach ($bone in $rawGeo."minecraft:geometry"[0].bones) {
    $defaultRotation = if ($null -ne $bone.rotation) {
        @($bone.rotation)
    } else {
        @(0, 0, 0)
    }
    $idleBones[$bone.name] = [ordered]@{
        rotation = [ordered]@{
            vector = Get-LandingFinalVector $bone.name "rotation" $defaultRotation
        }
        position = [ordered]@{
            vector = Get-LandingFinalVector $bone.name "position" @(0, 0, 0)
        }
    }
}
$animation.animations | Add-Member -NotePropertyName idle -NotePropertyValue (
    [ordered]@{
        loop = $true
        animation_length = 1
        bones = $idleBones
    }) -Force

[IO.File]::WriteAllText(
    (Join-Path $animationDir "molten_golem.animation.json"),
    ($animation | ConvertTo-Json -Depth 100 -Compress),
    [Text.UTF8Encoding]::new($false))

$soundsPath = Join-Path $resources "sounds.json"
$sounds = Get-Content -Raw -Encoding UTF8 $soundsPath | ConvertFrom-Json
foreach ($name in @("golem_swing", "golem_hammer", "golem_explosion", "golem_electric")) {
    $sounds | Add-Member -NotePropertyName $name -NotePropertyValue (
        [ordered]@{sounds = @([ordered]@{name = "tkgun:$name"; stream = $false})}
    ) -Force
}
[IO.File]::WriteAllText(
    $soundsPath,
    ($sounds | ConvertTo-Json -Depth 20),
    [Text.UTF8Encoding]::new($false))

foreach ($langName in @("zh_cn.json", "en_us.json")) {
    $langPath = Join-Path $resources "lang\$langName"
    $lang = Get-Content -Raw -Encoding UTF8 $langPath | ConvertFrom-Json
    if ($langName -eq "zh_cn.json") {
        $entityName = -join ([char[]]@(0x94F8, 0x9020, 0x5080, 0x5121))
        $spawnEggName = -join (
            [char[]]@(0x94F8, 0x9020, 0x5080, 0x5121, 0x53EC, 0x5524, 0x7269))
        $removalToolName = -join (
            [char[]]@(0x5B9E, 0x4F53, 0x5220, 0x9664, 0x5DE5, 0x5177))
    } else {
        $entityName = "Molten Golem"
        $spawnEggName = "Molten Golem Summoning Ingot"
        $removalToolName = "Entity Removal Tool"
    }
    $lang | Add-Member -NotePropertyName "entity.tkgun.molten_golem" `
        -NotePropertyValue $entityName -Force
    $lang | Add-Member -NotePropertyName "item.tkgun.molten_golem_spawn_egg" `
        -NotePropertyValue $spawnEggName -Force
    $lang | Add-Member -NotePropertyName "item.tkgun.entity_removal_tool" `
        -NotePropertyValue $removalToolName -Force
    [IO.File]::WriteAllText(
        $langPath,
        ($lang | ConvertTo-Json -Depth 20),
        [Text.UTF8Encoding]::new($false))
}

Write-Output "Imported molten golem geometry, idle animation, texture, sounds, and language entries."
