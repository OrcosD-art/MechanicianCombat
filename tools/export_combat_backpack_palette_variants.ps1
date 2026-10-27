param(
    [Parameter(Mandatory = $true)][string]$TemplateFolder,
    [Parameter(Mandatory = $true)][string]$PaletteReport,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [Parameter(Mandatory = $true)][string]$FinalTextureDirectory
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$templateFolder = [System.IO.Path]::GetFullPath($TemplateFolder)
$output = [System.IO.Path]::GetFullPath($OutputDirectory)
$finalTextures = [System.IO.Path]::GetFullPath($FinalTextureDirectory)
[System.IO.Directory]::CreateDirectory($output) | Out-Null

$grayPath = Join-Path $templateFolder 'gray.png'
$modelPath = Join-Path $templateFolder 'combat_backpack_gray.bbmodel'
$atlasPath = Join-Path $templateFolder 'block_palette_atlas.png'
foreach ($path in @($grayPath, $modelPath, $atlasPath, $PaletteReport)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Required file not found: $path"
    }
}

$reports = [System.Collections.Generic.List[object]]::new()
$reportLines = @(Get-Content -LiteralPath $PaletteReport -Encoding utf8)
for ($i = 1; $i -lt $reportLines.Count; $i++) {
    $line = $reportLines[$i].Trim()
    if ($line.Length -eq 0) {
        continue
    }
    if ($line -notmatch '^([^,]+),[^,]+,(.+)$') {
        throw "Unexpected palette report line: $line"
    }
    $fileName = $Matches[1]
    $steps = @($Matches[2].Trim() -split '\s+')
    if ($steps.Count -ne 6) {
        throw "Expected six colors for $fileName, got $($steps.Count)"
    }
    $suffix = [System.IO.Path]::GetFileNameWithoutExtension($fileName)
    if ($suffix.EndsWith('_shulker_box')) {
        $suffix = $suffix.Substring(0, $suffix.Length - '_shulker_box'.Length)
    }
    $reports.Add([pscustomobject]@{
        FileName = $fileName
        Suffix = $suffix
        Steps = $steps
        Key = $steps -join ' '
    })
}

$byKey = [System.Collections.Generic.Dictionary[string, object]]::new()
foreach ($item in $reports) {
    if ($byKey.ContainsKey($item.Key)) {
        throw "Duplicate palette row: $($item.Key)"
    }
    $byKey[$item.Key] = $item
}

$atlas = [System.Drawing.Bitmap]::new($atlasPath)
$atlasRows = [System.Collections.Generic.List[object]]::new()
try {
    for ($y = 31; $y -le 47; $y++) {
        $steps = [System.Collections.Generic.List[string]]::new()
        for ($x = 10; $x -le 15; $x++) {
            $color = $atlas.GetPixel($x, $y)
            $steps.Add(('#{0:X2}{1:X2}{2:X2}' -f $color.R, $color.G, $color.B))
        }
        $key = $steps -join ' '
        if (-not $byKey.ContainsKey($key)) {
            throw "Vertical palette row $($y - 31) does not match the extracted palette report: $key"
        }
        $item = $byKey[$key]
        $atlasRows.Add([pscustomobject]@{
            Suffix = $item.Suffix
            Steps = @($item.Steps)
        })
    }
} finally {
    $atlas.Dispose()
}
if ($atlasRows.Count -ne 17) {
    throw "Expected 17 vertical palette rows, got $($atlasRows.Count)"
}

$lightGray = $atlasRows | Where-Object { $_.Suffix -eq 'light_gray' } | Select-Object -First 1
if ($null -eq $lightGray) {
    throw 'The vertical palette does not contain the light_gray row.'
}

$grayBitmap = [System.Drawing.Bitmap]::new($grayPath)
$modelRaw = Get-Content -Raw -Encoding utf8 -LiteralPath $modelPath
$manifest = [System.Text.StringBuilder]::new()
[void]$manifest.AppendLine('suffix,texture,model,replaced_gray_scale_1_to_6')

try {
    for ($rowIndex = 0; $rowIndex -lt $atlasRows.Count; $rowIndex++) {
        $row = $atlasRows[$rowIndex]
        $mapping = [System.Collections.Generic.Dictionary[int, System.Drawing.Color]]::new()
        for ($step = 0; $step -lt 6; $step++) {
            $sourceArgb = [System.Drawing.ColorTranslator]::FromHtml($lightGray.Steps[$step]).ToArgb()
            $targetColor = [System.Drawing.ColorTranslator]::FromHtml($row.Steps[$step])
            $targetColor = [System.Drawing.Color]::FromArgb(255, $targetColor.R, $targetColor.G, $targetColor.B)
            $mapping[$sourceArgb] = $targetColor
        }

        $variant = [System.Drawing.Bitmap]::new(
            $grayBitmap.Width,
            $grayBitmap.Height,
            [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            for ($y = 0; $y -lt $grayBitmap.Height; $y++) {
                for ($x = 0; $x -lt $grayBitmap.Width; $x++) {
                    $sourceColor = $grayBitmap.GetPixel($x, $y)
                    if ($mapping.ContainsKey($sourceColor.ToArgb())) {
                        $variant.SetPixel($x, $y, $mapping[$sourceColor.ToArgb()])
                    } else {
                        $variant.SetPixel($x, $y, $sourceColor)
                    }
                }
            }

            $textureName = 'gray_' + $row.Suffix + '.png'
            $modelName = 'combat_backpack_gray_' + $row.Suffix + '.bbmodel'
            $textureOutput = Join-Path $output $textureName
            $modelOutput = Join-Path $output $modelName
            $variant.Save($textureOutput, [System.Drawing.Imaging.ImageFormat]::Png)

            $bytes = [System.IO.File]::ReadAllBytes($textureOutput)
            $dataUri = 'data:image/png;base64,' + [Convert]::ToBase64String($bytes)
            $finalTexturePath = Join-Path $finalTextures $textureName
            $jsonPath = $finalTexturePath.Replace('\', '\\').Replace('"', '\"')

            $newModel = [regex]::Replace(
                $modelRaw,
                '"name":"combat_backpack"',
                '"name":"combat_backpack_gray_' + $row.Suffix + '"',
                1)
            $newModel = [regex]::Replace(
                $newModel,
                '"name":"gray\.png"',
                '"name":"' + $textureName + '"',
                1)
            $newModel = [regex]::Replace(
                $newModel,
                '"path":"[^"]*gray\.png"',
                '"path":"' + $jsonPath + '"',
                1)
            $newModel = [regex]::Replace(
                $newModel,
                '"source":"data:image/png;base64,[^"]*"',
                '"source":"' + $dataUri + '"',
                1)
            [System.IO.File]::WriteAllText(
                $modelOutput,
                $newModel,
                [System.Text.UTF8Encoding]::new($false))

            [void]$manifest.AppendLine(
                ('{0},{1},{2},{3}' -f
                    $row.Suffix,
                    $textureName,
                    $modelName,
                    ($lightGray.Steps -join ' ') + ' -> ' + ($row.Steps -join ' ')))
        } finally {
            $variant.Dispose()
        }
    }
} finally {
    $grayBitmap.Dispose()
}

[System.IO.File]::WriteAllText(
    (Join-Path $output 'combat_backpack_palette_manifest.txt'),
    $manifest.ToString(),
    [System.Text.UTF8Encoding]::new($false))
