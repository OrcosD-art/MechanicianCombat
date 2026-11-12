param(
    [Parameter(Mandatory = $true)][string]$InputGif,
    [Parameter(Mandatory = $true)][string]$MaskSource,
    [Parameter(Mandatory = $true)][string]$WorkDir,
    [Parameter(Mandatory = $true)][string]$OutputGif,
    [Parameter(Mandatory = $true)][string]$Ffmpeg
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$palette = @(
        0x884B2B, 0xBE772B, 0xDE9E41,
        0xE8C170, 0xF8DCA3, 0xF6F2C8
)

function Get-Rgb([System.Drawing.Color]$color) {
    return ($color.ToArgb() -band 0x00FFFFFF)
}

function Get-Rank([int]$rgb) {
    for ($rank = 0; $rank -lt $palette.Count; $rank++) {
        if ($palette[$rank] -eq $rgb) { return $rank }
    }
    throw ("Unknown source color: {0:X6}" -f $rgb)
}

function Get-Neighbors([int]$index, [int]$width, [int]$height) {
    $x = $index % $width
    $y = [Math]::Floor($index / $width)
    $neighbors = New-Object System.Collections.Generic.List[int]
    for ($oy = -1; $oy -le 1; $oy++) {
        for ($ox = -1; $ox -le 1; $ox++) {
            if ($ox -eq 0 -and $oy -eq 0) { continue }
            $nx = $x + $ox
            $ny = $y + $oy
            if ($nx -lt 0 -or $nx -ge $width -or
                    $ny -lt 0 -or $ny -ge $height) {
                continue
            }
            $neighbors.Add($ny * $width + $nx)
        }
    }
    return $neighbors
}

function Get-Distance([bool[]]$seeds, [bool[]]$opaque,
        [int]$width, [int]$height) {
    $distance = New-Object "int[]" ($width * $height)
    for ($i = 0; $i -lt $distance.Length; $i++) {
        $distance[$i] = -2
    }
    $queue = New-Object System.Collections.Generic.Queue[int]
    for ($i = 0; $i -lt $seeds.Length; $i++) {
        if ($opaque[$i] -and $seeds[$i]) {
            $distance[$i] = 0
            $queue.Enqueue($i)
        }
    }
    while ($queue.Count -gt 0) {
        $current = $queue.Dequeue()
        foreach ($neighbor in Get-Neighbors $current $width $height) {
            if (-not $opaque[$neighbor] -or $distance[$neighbor] -ne -2) {
                continue
            }
            $distance[$neighbor] = $distance[$current] + 1
            $queue.Enqueue($neighbor)
        }
    }
    return $distance
}

function Get-NearestBaseRanks([int[]]$sourceRanks, [bool[]]$opaque,
        [int]$width, [int]$height) {
    $result = New-Object "int[]" ($width * $height)
    $seen = New-Object "bool[]" ($width * $height)
    $queue = New-Object System.Collections.Generic.Queue[int]
    for ($i = 0; $i -lt $sourceRanks.Length; $i++) {
        if ($opaque[$i] -and $sourceRanks[$i] -le 1) {
            $result[$i] = $sourceRanks[$i]
            $seen[$i] = $true
            $queue.Enqueue($i)
        }
    }
    while ($queue.Count -gt 0) {
        $current = $queue.Dequeue()
        foreach ($neighbor in Get-Neighbors $current $width $height) {
            if (-not $opaque[$neighbor] -or $seen[$neighbor]) { continue }
            $seen[$neighbor] = $true
            $result[$neighbor] = $result[$current]
            $queue.Enqueue($neighbor)
        }
    }
    return $result
}

function Save-Frame([int[]]$ranks, [bool[]]$opaque,
        [int]$width, [int]$height, [string]$path) {
    $bitmap = New-Object System.Drawing.Bitmap(
            $width, $height,
            [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $height; $y++) {
        for ($x = 0; $x -lt $width; $x++) {
            $index = $y * $width + $x
            if (-not $opaque[$index]) { continue }
            $rgb = $palette[$ranks[$index]]
            $bitmap.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(
                    255,
                    ($rgb -shr 16) -band 0xFF,
                    ($rgb -shr 8) -band 0xFF,
                    $rgb -band 0xFF))
        }
    }
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

if (Test-Path -LiteralPath $WorkDir) {
    $resolved = (Resolve-Path -LiteralPath $WorkDir).Path
    if (-not $resolved.Equals($WorkDir, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Unsafe work directory"
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
New-Item -ItemType Directory -Path $WorkDir -Force | Out-Null

$maskBitmap = [System.Drawing.Bitmap]::FromFile($MaskSource)
$gif = [System.Drawing.Bitmap]::FromFile($InputGif)
try {
    $width = $gif.Width
    $height = $gif.Height
    $mask = New-Object "bool[]" ($width * $height)
    for ($y = 0; $y -lt $height; $y++) {
        for ($x = 0; $x -lt $width; $x++) {
            $mask[$y * $width + $x] = $maskBitmap.GetPixel($x, $y).A -gt 0
        }
    }
    $frameDimension = [System.Drawing.Imaging.FrameDimension]::Time
    $gif.SelectActiveFrame($frameDimension, 0) | Out-Null
    $sourceRanks = New-Object "int[]" ($width * $height)
    $highlightSeeds = New-Object "bool[]" ($width * $height)
    for ($y = 0; $y -lt $height; $y++) {
        for ($x = 0; $x -lt $width; $x++) {
            $index = $y * $width + $x
            if (-not $mask[$index]) { continue }
            $rank = Get-Rank (Get-Rgb ($gif.GetPixel($x, $y)))
            $sourceRanks[$index] = $rank
            if ($rank -ge 2) {
                $highlightSeeds[$index] = $true
            }
        }
    }
    $highlightDistance = Get-Distance `
            $highlightSeeds $mask $width $height
    $baseRanks = Get-NearestBaseRanks `
            $sourceRanks $mask $width $height
    $maxDistance = 1
    foreach ($value in $highlightDistance) {
        if ($value -gt $maxDistance) { $maxDistance = $value }
    }

    for ($frame = 0; $frame -lt 8; $frame++) {
        $ranks = New-Object "int[]" ($width * $height)
        for ($index = 0; $index -lt $ranks.Length; $index++) {
            if (-not $mask[$index]) { continue }
            $source = $sourceRanks[$index]
            $full = [Math]::Min(5, $source + 2)
            $normalized = $highlightDistance[$index] / [double]$maxDistance
            $rank = $source
            if ($frame -eq 1 -and $normalized -le 0.35) {
                $rank = $full
            } elseif ($frame -eq 2) {
                $rank = if ($normalized -le 0.82) {
                    $full
                } else {
                    [Math]::Min(5, $source + 1)
                }
            } elseif ($frame -ge 3) {
                $rank = $full
            }
            if ($frame -ge 4) {
                $base = $baseRanks[$index]
                $factor = @(0.70, 0.40, 0.15, 0.0)[$frame - 4]
                $rank = [int][Math]::Round(
                        $base + ($full - $base) * $factor)
            }
            $ranks[$index] = [Math]::Max(0, [Math]::Min(5, $rank))
        }
        Save-Frame $ranks $mask $width $height `
                (Join-Path $WorkDir ("frame_{0:D2}.png" -f $frame))
    }
}
finally {
    $gif.Dispose()
    $maskBitmap.Dispose()
}

& $Ffmpeg -hide_banner -loglevel error -y -framerate 10 `
        -i (Join-Path $WorkDir "frame_%02d.png") `
        -filter_complex "[0:v]split[a][b];[a]palettegen=reserve_transparent=1[p];[b][p]paletteuse=alpha_threshold=128" `
        -loop 0 $OutputGif
if ($LASTEXITCODE -ne 0) {
    throw "ffmpeg GIF generation failed"
}
