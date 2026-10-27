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

function Convert-ToHsv([System.Drawing.Color]$color) {
    $r = $color.R / 255.0
    $g = $color.G / 255.0
    $b = $color.B / 255.0
    $max = [Math]::Max($r, [Math]::Max($g, $b))
    $min = [Math]::Min($r, [Math]::Min($g, $b))
    $delta = $max - $min
    $h = 0.0
    if ($delta -ne 0.0) {
        if ($max -eq $r) {
            $h = 60.0 * ((($g - $b) / $delta) % 6.0)
        } elseif ($max -eq $g) {
            $h = 60.0 * ((($b - $r) / $delta) + 2.0)
        } else {
            $h = 60.0 * ((($r - $g) / $delta) + 4.0)
        }
    }
    if ($h -lt 0.0) {
        $h += 360.0
    }
    $s = if ($max -eq 0.0) { 0.0 } else { $delta / $max }
    [pscustomobject]@{
        H = $h
        S = $s
        V = $max
    }
}

$files = @(Get-ChildItem -LiteralPath $source -File -Filter '*.png' | Sort-Object Name)
if ($files.Count -eq 0) {
    throw "No PNG textures found in: $source"
}

$report = [System.Text.StringBuilder]::new()
[void]$report.AppendLine('file,hue,steps_dark_to_light')

foreach ($file in $files) {
    $sourceBitmap = [System.Drawing.Bitmap]::new($file.FullName)
    try {
        $counts = [System.Collections.Generic.Dictionary[int, int]]::new()
        for ($y = 0; $y -lt $sourceBitmap.Height; $y++) {
            for ($x = 0; $x -lt $sourceBitmap.Width; $x++) {
                $argb = $sourceBitmap.GetPixel($x, $y).ToArgb()
                if ([System.Drawing.Color]::FromArgb($argb).A -eq 0) {
                    continue
                }
                if ($counts.ContainsKey($argb)) {
                    $counts[$argb]++
                } else {
                    $counts[$argb] = 1
                }
            }
        }

        $samples = [System.Collections.Generic.List[object]]::new()
        $sinSum = 0.0
        $cosSum = 0.0
        $hueWeight = 0.0
        foreach ($entry in $counts.GetEnumerator()) {
            $color = [System.Drawing.Color]::FromArgb([int]$entry.Key)
            $hsv = Convert-ToHsv $color
            $luma = 0.2126 * $color.R + 0.7152 * $color.G + 0.0722 * $color.B
            $samples.Add([pscustomobject]@{
                Color = $color
                Count = [int]$entry.Value
                H = [double]$hsv.H
                S = [double]$hsv.S
                V = [double]$hsv.V
                Luma = [double]$luma
            })
            $weight = [int]$entry.Value * [double]$hsv.S * [double]$hsv.S
            if ($weight -gt 0.0) {
                $radians = [double]$hsv.H * [Math]::PI / 180.0
                $sinSum += [Math]::Sin($radians) * $weight
                $cosSum += [Math]::Cos($radians) * $weight
                $hueWeight += $weight
            }
        }

        if ($samples.Count -eq 0) {
            throw "Texture has no opaque pixels: $($file.FullName)"
        }

        $meanHue = if ($hueWeight -gt 0.0) {
            ([Math]::Atan2($sinSum, $cosSum) * 180.0 / [Math]::PI + 360.0) % 360.0
        } else {
            0.0
        }
        $maxHueDelta = 0.0
        foreach ($sample in $samples) {
            $delta = (($sample.H - $meanHue + 540.0) % 360.0) - 180.0
            $sample | Add-Member -NotePropertyName HueDelta -NotePropertyValue $delta
            if ([Math]::Abs($delta) -gt $maxHueDelta) {
                $maxHueDelta = [Math]::Abs($delta)
            }
        }

        # A coherent hue family is a brightness ramp. Textures spanning several
        # hue families are grouped by hue first, then ordered by brightness.
        if ($maxHueDelta -gt 20.0) {
            $sorted = @($samples | Sort-Object HueDelta, Luma, S)
        } else {
            $sorted = @($samples | Sort-Object Luma, S, HueDelta)
        }

        $totalPixels = 0
        foreach ($sample in $sorted) {
            $totalPixels += $sample.Count
        }

        $groups = @(
            [System.Collections.Generic.List[object]]::new(),
            [System.Collections.Generic.List[object]]::new(),
            [System.Collections.Generic.List[object]]::new(),
            [System.Collections.Generic.List[object]]::new(),
            [System.Collections.Generic.List[object]]::new(),
            [System.Collections.Generic.List[object]]::new()
        )
        $cursor = 0.0
        foreach ($sample in $sorted) {
            $midpoint = $cursor + $sample.Count / 2.0
            $groupIndex = [Math]::Min(
                5,
                [Math]::Floor($midpoint * 6.0 / $totalPixels))
            $groups[$groupIndex].Add($sample)
            $cursor += $sample.Count
        }

        # Pick one real source color nearest to each group's weighted mean.
        # Each unique source color belongs to one group, so all six steps differ.
        $steps = [System.Collections.Generic.List[System.Drawing.Color]]::new()
        foreach ($group in $groups) {
            if ($group.Count -eq 0) {
                continue
            }
            $sumWeight = 0.0
            $sumLuma = 0.0
            $sumHue = 0.0
            foreach ($sample in $group) {
                $sumWeight += $sample.Count
                $sumLuma += $sample.Luma * $sample.Count
                $sumHue += $sample.HueDelta * $sample.Count
            }
            $meanLuma = $sumLuma / $sumWeight
            $groupMeanHue = $sumHue / $sumWeight
            $best = $null
            $bestDistance = [double]::MaxValue
            foreach ($sample in $group) {
                $distance =
                    [Math]::Pow(($sample.Luma - $meanLuma) / 255.0, 2.0) +
                    [Math]::Pow(($sample.HueDelta - $groupMeanHue) / 360.0, 2.0)
                if ($distance -lt $bestDistance) {
                    $bestDistance = $distance
                    $best = $sample
                }
            }
            $steps.Add($best.Color)
        }

        # Defensive fallback for extremely small source textures.
        foreach ($sample in $sorted) {
            if ($steps.Count -ge 6) {
                break
            }
            if (-not $steps.Contains($sample.Color)) {
                $steps.Add($sample.Color)
            }
        }
        $orderedSteps = @($steps | Sort-Object {
            0.2126 * $_.R + 0.7152 * $_.G + 0.0722 * $_.B
        })
        $steps = [System.Collections.Generic.List[System.Drawing.Color]]::new()
        foreach ($color in $orderedSteps) {
            $steps.Add($color)
        }
        while ($steps.Count -lt 6) {
            $steps.Add($sorted[$sorted.Count - 1].Color)
        }

        $palette = [System.Drawing.Bitmap]::new(
            16,
            16,
            [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            for ($y = 0; $y -lt 16; $y++) {
                for ($x = 0; $x -lt 16; $x++) {
                    $palette.SetPixel($x, $y, [System.Drawing.Color]::Transparent)
                }
            }
            # Reference layout from mechanician_combat_backpack.png:
            # six one-pixel columns in the bottom-right, each two pixels tall.
            for ($i = 0; $i -lt 6; $i++) {
                $x = 10 + $i
                $palette.SetPixel($x, 14, $steps[$i])
                $palette.SetPixel($x, 15, $steps[$i])
            }
            $destination = Join-Path $output ($file.BaseName + '_palette.png')
            $palette.Save($destination, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $palette.Dispose()
        }

        $hexSteps = for ($i = 0; $i -lt 6; $i++) {
            '#{0:X2}{1:X2}{2:X2}' -f $steps[$i].R, $steps[$i].G, $steps[$i].B
        }
        [void]$report.AppendLine(('{0},{1:N1},{2}' -f $file.Name, $meanHue, ($hexSteps -join ' ')))
    } finally {
        $sourceBitmap.Dispose()
    }
}

[System.IO.File]::WriteAllText(
    (Join-Path $output 'palette_16x16_colors.txt'),
    $report.ToString(),
    [System.Text.UTF8Encoding]::new($false))
