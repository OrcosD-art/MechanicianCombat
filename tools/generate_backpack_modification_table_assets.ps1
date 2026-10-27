param(
    [string]$Root = "D:\codex\mod\rebuild",
    [string]$SourceModel = "D:\Download\USER\work\AModel\机械师装备\背包改装台\model.bbmodel",
    [string]$SourceTexture = "D:\Download\USER\work\AModel\机械师装备\背包改装台\displayer.png",
    [string]$SourceSmithingUi = "D:\Game\mc\测试端\.minecraft\versions\1.21.1-Fabric 0.18.2\1.21.1-Fabric 0.18.2.jar"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
Add-Type -AssemblyName System.IO.Compression.FileSystem
Add-Type -AssemblyName System.Drawing

$resources = Join-Path $Root "mod\src\main\resources"
$assetSource = Join-Path $Root "assets_src\背包改装台"
$modelDir = Join-Path $resources "assets\tkgun\models\block"
$itemModelDir = Join-Path $resources "assets\tkgun\models\item"
$blockstateDir = Join-Path $resources "assets\tkgun\blockstates"
$blockTextureDir = Join-Path $resources "assets\tkgun\textures\block"
$guiTextureDir = Join-Path $resources "assets\tkgun\textures\gui\container"
foreach ($dir in @($assetSource, $modelDir, $itemModelDir, $blockstateDir,
        $blockTextureDir, $guiTextureDir)) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}

# Blockbench 4.10 java_block source. The stored project has unescaped Windows paths
# in its texture metadata, so only the otherwise-valid element array is parsed.
$raw = Get-Content -Raw -LiteralPath $SourceModel
$elementsMatch = [regex]::Match(
        $raw,
        '"elements":(\[.*?\]),"outliner":',
        [Text.RegularExpressions.RegexOptions]::Singleline)
if (-not $elementsMatch.Success) {
    throw "Cannot locate the Blockbench elements array in $SourceModel"
}
$sourceElements = $elementsMatch.Groups[1].Value | ConvertFrom-Json

$resolutionMatch = [regex]::Match($raw, '"resolution":\{"width":(\d+),"height":(\d+)\}')
if (-not $resolutionMatch.Success) {
    throw "Cannot locate the Blockbench texture resolution"
}
$sourceTextureWidth = [double]$resolutionMatch.Groups[1].Value
$sourceTextureHeight = [double]$resolutionMatch.Groups[2].Value
$uvScale = 16.0D / $sourceTextureWidth
$displayMatch = [regex]::Match(
        $raw,
        '"display":(\{.*\})\s*\}\s*$',
        [Text.RegularExpressions.RegexOptions]::Singleline)
$display = if ($displayMatch.Success) {
    $displayMatch.Groups[1].Value | ConvertFrom-Json
} else {
    $null
}

$converted = New-Object System.Collections.Generic.List[object]
foreach ($element in $sourceElements) {
    if ($element.PSObject.Properties.Name -contains "export" -and -not $element.export) {
        continue
    }
    $out = [ordered]@{
        from = @([double]$element.from[0], [double]$element.from[1], [double]$element.from[2])
        to = @([double]$element.to[0], [double]$element.to[1], [double]$element.to[2])
        faces = [ordered]@{}
    }
    if ($element.rotation) {
        $axes = @('x', 'y', 'z')
        for ($i = 0; $i -lt 3; $i++) {
            $angle = [double]$element.rotation[$i]
            if ([Math]::Abs($angle) -gt 0.00001D) {
                $out.rotation = [ordered]@{
                    origin = @(
                        [double]$element.origin[0],
                        [double]$element.origin[1],
                        [double]$element.origin[2])
                    axis = $axes[$i]
                    angle = $angle
                    rescale = $false
                }
                break
            }
        }
    }
    foreach ($faceName in @('north', 'east', 'south', 'west', 'up', 'down')) {
        $face = $element.faces.$faceName
        if ($null -eq $face -or $null -eq $face.uv) {
            continue
        }
        $mcFace = [ordered]@{
            uv = @(
                [Math]::Round([double]$face.uv[0] * $uvScale, 6),
                [Math]::Round([double]$face.uv[1] * $uvScale, 6),
                [Math]::Round([double]$face.uv[2] * $uvScale, 6),
                [Math]::Round([double]$face.uv[3] * $uvScale, 6))
            texture = "#0"
        }
        if ($null -ne $face.rotation -and [double]$face.rotation -ne 0.0D) {
            $mcFace.rotation = [int]$face.rotation
        }
        $out.faces[$faceName] = $mcFace
    }
    $converted.Add($out)
}

$blockModel = [ordered]@{
    credit = "Converted from the author-provided Blockbench java_block model"
    parent = "minecraft:block/block"
    ambientocclusion = $true
    texture_size = @($sourceTextureWidth, $sourceTextureHeight)
    textures = [ordered]@{
        "0" = "tkgun:block/backpack_modification_table"
        particle = "#0"
    }
    elements = $converted
}
if ($null -ne $display) {
    $blockModel.display = $display
}
[IO.File]::WriteAllText(
        (Join-Path $modelDir "backpack_modification_table.json"),
        ($blockModel | ConvertTo-Json -Depth 20),
        [Text.UTF8Encoding]::new($false))

[IO.File]::WriteAllText(
        (Join-Path $blockstateDir "backpack_modification_table.json"),
@'
{
  "variants": {
    "": {
      "model": "tkgun:block/backpack_modification_table"
    }
  }
}
'@,
        [Text.UTF8Encoding]::new($false))

[IO.File]::WriteAllText(
        (Join-Path $itemModelDir "backpack_modification_table.json"),
@'
{
  "parent": "tkgun:block/backpack_modification_table"
}
'@,
        [Text.UTF8Encoding]::new($false))

Copy-Item -LiteralPath $SourceTexture `
        -Destination (Join-Path $blockTextureDir "backpack_modification_table.png") -Force
Copy-Item -LiteralPath $SourceTexture `
        -Destination (Join-Path $assetSource "backpack_modification_table.png") -Force

# The current block intentionally uses vanilla's smithing-table screen. This exact
# 176x166 copy is provided as the editable art template for a future custom screen.
$zip = [IO.Compression.ZipFile]::OpenRead($SourceSmithingUi)
try {
    $entry = $zip.GetEntry("assets/minecraft/textures/gui/container/smithing.png")
    if ($null -eq $entry) {
        throw "smithing.png is missing from $SourceSmithingUi"
    }
    foreach ($destination in @(
            (Join-Path $guiTextureDir "backpack_modification_table.png"),
            (Join-Path $assetSource "backpack_modification_table_ui_template_clean.png"),
            (Join-Path $assetSource "backpack_modification_table_ui_template.png"))) {
        $input = $entry.Open()
        try {
            $output = [IO.File]::Create($destination)
            try {
                $input.CopyTo($output)
            } finally {
                $output.Dispose()
            }
        } finally {
            $input.Dispose()
        }
    }
} finally {
    $zip.Dispose()
}

# The armor stand is rendered at GUI-local (141, 75) with scale 25. This green
# guide box marks a safe 56x73 preview region around that render anchor.
$markedTemplate = Join-Path $assetSource "backpack_modification_table_ui_template.png"
$temporaryTemplate = Join-Path $assetSource "backpack_modification_table_ui_template.tmp.png"
$bitmap = [Drawing.Bitmap]::new($markedTemplate)
try {
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::None
        $fill = [Drawing.SolidBrush]::new([Drawing.Color]::FromArgb(46, 0, 255, 80))
        $border = [Drawing.Pen]::new([Drawing.Color]::FromArgb(255, 0, 220, 72), 1.0)
        try {
            $graphics.FillRectangle($fill, 113, 8, 56, 73)
            $graphics.DrawRectangle($border, 113, 8, 55, 72)
            # Fixed smithing inputs: template, equipment and material. The output
            # slot is intentionally unmarked because its future position may move.
            foreach ($slot in @(@(7, 47), @(25, 47), @(43, 47))) {
                $graphics.FillRectangle($fill, $slot[0], $slot[1], 18, 18)
                $graphics.DrawRectangle($border, $slot[0], $slot[1], 17, 17)
            }
            # Empty positions verified against the clean vanilla texture. Each
            # cell is 18x18, matching the fixed smithing input cells.
            foreach ($slot in @(
                    @(43, 25), @(61, 25), @(79, 25),
                    @(7, 65), @(25, 65), @(43, 65), @(61, 65), @(79, 65))) {
                $graphics.FillRectangle($fill, $slot[0], $slot[1], 18, 18)
                $graphics.DrawRectangle($border, $slot[0], $slot[1], 17, 17)
            }
        } finally {
            $fill.Dispose()
            $border.Dispose()
        }
    } finally {
        $graphics.Dispose()
    }
    $bitmap.Save($temporaryTemplate, [Drawing.Imaging.ImageFormat]::Png)
} finally {
    $bitmap.Dispose()
}
Move-Item -LiteralPath $temporaryTemplate -Destination $markedTemplate -Force

Write-Output ("Converted {0} elements, UV scale {1}, texture {2}x{2}" -f
        $converted.Count, $uvScale, $sourceTextureWidth)
