param(
    [string]$Root = "D:\codex\mod\rebuild",
    [string]$SourceDir = "D:\Download\USER\work\AModel\机械师装备\背部装备\combat_backpack\compat_bb4126"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$resources = Join-Path $Root "mod\src\main\resources"
$sourceAssetDir = Join-Path $Root "assets_src\背包改装台"
$modelDir = Join-Path $resources "assets\tkgun\models\item"
$blockTextureDir = Join-Path $resources "assets\tkgun\textures\gui\container"
New-Item -ItemType Directory -Force -Path $sourceAssetDir, $modelDir, $blockTextureDir | Out-Null

$textureWidth = 48.0D
$uvScale = 16.0D / $textureWidth

function Convert-PartModel([string]$sourceName, [string]$outputName) {
    $sourcePath = Join-Path $SourceDir ($sourceName + ".bbmodel")
    $raw = Get-Content -Raw -LiteralPath $sourcePath
    $match = [regex]::Match(
            $raw,
            '"elements":(\[.*?\]),"outliner":',
            [Text.RegularExpressions.RegexOptions]::Singleline)
    if (-not $match.Success) {
        throw "Cannot find elements in $sourcePath"
    }
    $sourceElements = $match.Groups[1].Value | ConvertFrom-Json
    $elements = New-Object System.Collections.Generic.List[object]
    foreach ($src in $sourceElements) {
        $faces = [ordered]@{}
        foreach ($faceName in @('north', 'east', 'south', 'west', 'up', 'down')) {
            $face = $src.faces.$faceName
            if ($null -eq $face -or $null -eq $face.uv -or $null -eq $face.texture) {
                continue
            }
            $uv = @(
                [Math]::Round([double]$face.uv[0] * $uvScale, 6),
                [Math]::Round([double]$face.uv[1] * $uvScale, 6),
                [Math]::Round([double]$face.uv[2] * $uvScale, 6),
                [Math]::Round([double]$face.uv[3] * $uvScale, 6))
            $convertedFace = [ordered]@{ uv = $uv; texture = "#0" }
            if ($face.rotation) {
                $convertedFace.rotation = [int]$face.rotation
            }
            $faces[$faceName] = $convertedFace
        }
        $element = [ordered]@{
            from = @([double]$src.from[0], [double]$src.from[1], [double]$src.from[2])
            to = @([double]$src.to[0], [double]$src.to[1], [double]$src.to[2])
            faces = $faces
        }
        if ($src.rotation) {
            $axes = @('x', 'y', 'z')
            for ($i = 0; $i -lt 3; $i++) {
                $angle = [double]$src.rotation[$i]
                if ([Math]::Abs($angle) -gt 0.00001D) {
                    $element.rotation = [ordered]@{
                        origin = @(
                            [double]$src.origin[0],
                            [double]$src.origin[1],
                            [double]$src.origin[2])
                        axis = $axes[$i]
                        angle = $angle
                        rescale = $false
                    }
                    break
                }
            }
        }
        $elements.Add($element)
    }

    $model = [ordered]@{
        credit = "Converted from author-provided $sourceName.bbmodel"
        texture_size = @(48, 48)
        textures = [ordered]@{
            "0" = "tkgun:item/combat_backpack"
            particle = "tkgun:item/combat_backpack"
        }
        elements = $elements
        display = [ordered]@{
            gui = [ordered]@{
                rotation = @(22.5, -45, 0)
                translation = @(3.75, 4.5, 0)
                scale = @(1.25, 1.25, 1.25)
            }
            fixed = [ordered]@{
                translation = @(0, 3.5, -3.25)
            }
        }
    }
    if ($outputName -eq "auto_portable_launcher") {
        $model.display.gui = [ordered]@{
            rotation = @(-158, 45.75, -180)
            translation = @(0.75, 3.5, 0)
        }
    } elseif ($outputName -eq "portable_launcher") {
        $model.display.gui = [ordered]@{
            rotation = @(-158, 45.75, -180)
            translation = @(0, 6, 0)
            scale = @(1.5, 1.5, 1.5)
        }
    } elseif ($outputName -eq "backpack_core") {
        $model.display.gui = [ordered]@{
            rotation = @(-157.5, 45, -180)
            translation = @(0, 10.5, 0)
            scale = @(2, 2, 2)
        }
    }
    [IO.File]::WriteAllText(
            (Join-Path $modelDir ($outputName + ".json")),
            ($model | ConvertTo-Json -Depth 20),
            [Text.UTF8Encoding]::new($false))
}

Convert-PartModel "背包核心" "backpack_core"
Convert-PartModel "便携发射器物品" "portable_launcher"
Convert-PartModel "自动便携发射器物品" "auto_portable_launcher"

# Duplicate the existing backpack slot frame twice above it. This preserves the
# authored icon style exactly and leaves the preview frame untouched.
$template = Join-Path $sourceAssetDir "backpack_modification_table_ui_template.png"
$bitmap = [Drawing.Bitmap]::new($template)
try {
    $sourceRect = [Drawing.Rectangle]::new(151, 65, 18, 18)
    foreach ($targetY in @(47, 29)) {
        $pixels = $bitmap.Clone($sourceRect, $bitmap.PixelFormat)
        try {
            $graphics = [Drawing.Graphics]::FromImage($bitmap)
            try {
                $graphics.DrawImageUnscaled($pixels, 151, $targetY)
            } finally {
                $graphics.Dispose()
            }
        } finally {
            $pixels.Dispose()
        }
    }
    $temporary = Join-Path $sourceAssetDir "backpack_modification_table_ui_template.tmp.png"
    $bitmap.Save($temporary, [Drawing.Imaging.ImageFormat]::Png)
} finally {
    $bitmap.Dispose()
}
Move-Item -LiteralPath $temporary -Destination $template -Force
Copy-Item -LiteralPath $template `
        -Destination (Join-Path $blockTextureDir "backpack_modification_table.png") -Force

Write-Output "Exported backpack_core and portable_launcher item models; added two right-side UI slots."
