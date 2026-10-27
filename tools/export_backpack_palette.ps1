param(
    [Parameter(Mandatory = $true)][string]$SourceFolder,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$source = [System.IO.Path]::GetFullPath($SourceFolder)
$output = [System.IO.Path]::GetFullPath($OutputDirectory)
if (-not (Test-Path -LiteralPath $source -PathType Container)) {
    throw "Source folder not found: $source"
}
[System.IO.Directory]::CreateDirectory($output) | Out-Null

$files = @(Get-ChildItem -LiteralPath $source -File | Where-Object {
    $_.Extension -match '^\.(png|jpg|jpeg|bmp)$'
})
if ($files.Count -eq 0) {
    throw "No supported textures found in: $source"
}

$baseName = 'shulker_box.png'
$ordered = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
$base = $files | Where-Object { $_.Name -eq $baseName } | Select-Object -First 1
if ($null -ne $base) {
    $ordered.Add($base)
}
$files | Where-Object { $_.Name -ne $baseName } | Sort-Object Name | ForEach-Object {
    $ordered.Add($_)
}

$rows = [System.Collections.Generic.List[object]]::new()
$globalColors = [System.Collections.Generic.HashSet[int]]::new()
$maxColors = 0
foreach ($file in $ordered) {
    $bitmap = [System.Drawing.Bitmap]::new($file.FullName)
    $counts = [System.Collections.Generic.Dictionary[int, int]]::new()
    for ($y = 0; $y -lt $bitmap.Height; $y++) {
        for ($x = 0; $x -lt $bitmap.Width; $x++) {
            $argb = $bitmap.GetPixel($x, $y).ToArgb()
            $alpha = [System.Drawing.Color]::FromArgb($argb).A
            if ($alpha -eq 0) {
                continue
            }
            [void]$globalColors.Add($argb)
            if ($counts.ContainsKey($argb)) {
                $counts[$argb]++
            } else {
                $counts[$argb] = 1
            }
        }
    }
    $sorted = @($counts.GetEnumerator() | Sort-Object Value -Descending)
    if ($sorted.Count -gt $maxColors) {
        $maxColors = $sorted.Count
    }
    $rows.Add([pscustomobject]@{
        File = $file
        Bitmap = $bitmap
        Colors = $sorted
    })
}

$margin = 20
$headerHeight = 58
$rowHeight = 32
$labelWidth = 190
$previewSize = 26
$previewGap = 8
$swatchStep = 30
$swatchSize = 26
$width = $margin * 2 + $labelWidth + $previewSize + $previewGap + $maxColors * $swatchStep
$height = $headerHeight + $rows.Count * $rowHeight + $margin

$image = [System.Drawing.Bitmap]::new(
    $width,
    $height,
    [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($image)
try {
    $graphics.Clear([System.Drawing.Color]::FromArgb(255, 23, 24, 28))
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $graphics.TextRenderingHint =
        [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit

    $titleFont = [System.Drawing.Font]::new(
        'Microsoft YaHei UI', 16, [System.Drawing.FontStyle]::Bold,
        [System.Drawing.GraphicsUnit]::Pixel)
    $bodyFont = [System.Drawing.Font]::new(
        'Microsoft YaHei UI', 11, [System.Drawing.FontStyle]::Regular,
        [System.Drawing.GraphicsUnit]::Pixel)
    $smallFont = [System.Drawing.Font]::new(
        'Microsoft YaHei UI', 9, [System.Drawing.FontStyle]::Regular,
        [System.Drawing.GraphicsUnit]::Pixel)
    $titleBrush = [System.Drawing.SolidBrush]::new(
        [System.Drawing.Color]::FromArgb(255, 242, 243, 245))
    $bodyBrush = [System.Drawing.SolidBrush]::new(
        [System.Drawing.Color]::FromArgb(255, 214, 217, 222))
    $mutedBrush = [System.Drawing.SolidBrush]::new(
        [System.Drawing.Color]::FromArgb(255, 146, 151, 160))
    $gridPen = [System.Drawing.Pen]::new(
        [System.Drawing.Color]::FromArgb(255, 47, 49, 56), 1)
    $borderPen = [System.Drawing.Pen]::new(
        [System.Drawing.Color]::FromArgb(255, 88, 92, 102), 1)

    $graphics.DrawString('BACKPACK EQUIPMENT / BLOCK TEXTURE PALETTE', $titleFont, $titleBrush, 20, 10)
    $graphics.DrawString(
        "Source preview + unique colors by pixel frequency  |  textures $($rows.Count)  |  global unique colors $($globalColors.Count)",
        $smallFont, $mutedBrush, 21, 36)

    $rowIndex = 0
    foreach ($row in $rows) {
        $rowY = $headerHeight + $rowIndex * $rowHeight
        if (($rowIndex % 2) -eq 1) {
            $bandBrush = [System.Drawing.SolidBrush]::new(
                [System.Drawing.Color]::FromArgb(255, 29, 30, 35))
            $graphics.FillRectangle($bandBrush, 0, $rowY, $width, $rowHeight)
            $bandBrush.Dispose()
        }
        $graphics.DrawLine($gridPen, $margin, $rowY, $width - $margin, $rowY)

        $label = '{0}  [{1}]' -f $row.File.Name, $row.Colors.Count
        $graphics.DrawString($label, $bodyFont, $bodyBrush, $margin, $rowY + 9)

        $previewX = $margin + $labelWidth
        $imageY = $rowY + 3
        $previewRect = [System.Drawing.Rectangle]::new(
            $previewX, $imageY, $previewSize, $previewSize)
        $graphics.DrawImage(
            $row.Bitmap,
            $previewRect,
            0,
            0,
            $row.Bitmap.Width,
            $row.Bitmap.Height,
            [System.Drawing.GraphicsUnit]::Pixel)
        $graphics.DrawRectangle($borderPen, $previewRect)

        $swatchX = $previewX + $previewSize + $previewGap
        foreach ($entry in $row.Colors) {
            $color = [System.Drawing.Color]::FromArgb([int]$entry.Key)
            if ($color.A -lt 255) {
                $checkerLight = [System.Drawing.SolidBrush]::new(
                    [System.Drawing.Color]::FromArgb(255, 190, 190, 190))
                $checkerDark = [System.Drawing.SolidBrush]::new(
                    [System.Drawing.Color]::FromArgb(255, 120, 120, 120))
                $graphics.FillRectangle($checkerLight, $swatchX + 1, $imageY + 1, 24, 24)
                $graphics.FillRectangle($checkerDark, $swatchX + 1, $imageY + 1, 6, 6)
                $graphics.FillRectangle($checkerDark, $swatchX + 13, $imageY + 1, 6, 6)
                $graphics.FillRectangle($checkerDark, $swatchX + 7, $imageY + 7, 6, 6)
                $graphics.FillRectangle($checkerDark, $swatchX + 19, $imageY + 7, 6, 6)
                $graphics.FillRectangle($checkerDark, $swatchX + 1, $imageY + 13, 6, 6)
                $graphics.FillRectangle($checkerDark, $swatchX + 13, $imageY + 13, 6, 6)
                $graphics.FillRectangle($checkerDark, $swatchX + 7, $imageY + 19, 6, 6)
                $graphics.FillRectangle($checkerDark, $swatchX + 19, $imageY + 19, 6, 6)
                $checkerLight.Dispose()
                $checkerDark.Dispose()
            }
            $swatchBrush = [System.Drawing.SolidBrush]::new($color)
            $graphics.FillRectangle($swatchBrush, $swatchX + 1, $imageY + 1, 24, 24)
            $swatchBrush.Dispose()
            $graphics.DrawRectangle(
                $borderPen, $swatchX, $imageY, $swatchSize, $swatchSize)
            $swatchX += $swatchStep
        }
        $rowIndex++
    }

    $imagePath = Join-Path $output 'backpack_block_palette.png'
    $textPath = Join-Path $output 'backpack_block_palette_hex.txt'
    $image.Save($imagePath, [System.Drawing.Imaging.ImageFormat]::Png)

    $builder = [System.Text.StringBuilder]::new()
    [void]$builder.AppendLine('BACKPACK EQUIPMENT - BLOCK TEXTURE PALETTE')
    [void]$builder.AppendLine("Textures: $($rows.Count)")
    [void]$builder.AppendLine("Global unique colors: $($globalColors.Count)")
    [void]$builder.AppendLine('Format: #RRGGBB xPIXEL_COUNT; alpha is appended as /AA when not opaque')
    [void]$builder.AppendLine()
    foreach ($row in $rows) {
        [void]$builder.AppendLine("$($row.File.Name) [$($row.Colors.Count)]")
        foreach ($entry in $row.Colors) {
            $color = [System.Drawing.Color]::FromArgb([int]$entry.Key)
            $hex = '#{0:X2}{1:X2}{2:X2}' -f $color.R, $color.G, $color.B
            if ($color.A -ne 255) {
                $hex += '/{0:X2}' -f $color.A
            }
            [void]$builder.AppendLine(('{0} x{1}' -f $hex, $entry.Value))
        }
        [void]$builder.AppendLine()
    }
    [System.IO.File]::WriteAllText(
        $textPath,
        $builder.ToString(),
        [System.Text.UTF8Encoding]::new($false))

    Write-Output $imagePath
    Write-Output $textPath
} finally {
    $graphics.Dispose()
    $image.Dispose()
    foreach ($row in $rows) {
        $row.Bitmap.Dispose()
    }
}
