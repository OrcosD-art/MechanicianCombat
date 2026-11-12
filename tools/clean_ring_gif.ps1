param(
    [Parameter(Mandatory = $true)][string]$InputGif,
    [Parameter(Mandatory = $true)][string]$MaskSource,
    [Parameter(Mandatory = $true)][string]$WorkDir,
    [Parameter(Mandatory = $true)][string]$OutputGif,
    [Parameter(Mandatory = $true)][string]$Ffmpeg,
    [int]$SmallComponentLimit = 3,
    [int]$IsolationPasses = 4,
    [switch]$BridgeGaps
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

function Get-Rgb([System.Drawing.Color]$color) {
    return ($color.ToArgb() -band 0x00FFFFFF)
}

function Get-NeighborKeys(
        [int[]]$pixels, [bool[]]$opaque, [int]$width, [int]$height,
        [int]$x, [int]$y) {
    $keys = New-Object System.Collections.Generic.List[int]
    for ($oy = -1; $oy -le 1; $oy++) {
        for ($ox = -1; $ox -le 1; $ox++) {
            if ($ox -eq 0 -and $oy -eq 0) { continue }
            $nx = $x + $ox
            $ny = $y + $oy
            if ($nx -lt 0 -or $nx -ge $width -or $ny -lt 0 -or $ny -ge $height) {
                continue
            }
            $index = $ny * $width + $nx
            if ($opaque[$index]) {
                $keys.Add($pixels[$index])
            }
        }
    }
    return $keys
}

function Get-ModeKey($keys) {
    $keyArray = @($keys)
    if ($keyArray.Count -eq 0) { return -1 }
    $counts = @{}
    foreach ($key in $keyArray) {
        $counts[$key] = 1 + ($counts[$key] -as [int])
    }
    return ($counts.GetEnumerator() |
            Sort-Object @{Expression = "Value"; Descending = $true},
                        @{Expression = "Key"; Descending = $false} |
            Select-Object -First 1).Key
}

function Remove-IsolatedPixels(
        [int[]]$pixels, [bool[]]$opaque, [int]$width, [int]$height) {
    for ($pass = 0; $pass -lt $IsolationPasses; $pass++) {
        $changes = @{}
        for ($y = 1; $y -lt $height - 1; $y++) {
            for ($x = 1; $x -lt $width - 1; $x++) {
                $index = $y * $width + $x
                if (-not $opaque[$index]) { continue }
                $key = $pixels[$index]
                $same = 0
                for ($oy = -1; $oy -le 1; $oy++) {
                    for ($ox = -1; $ox -le 1; $ox++) {
                        if ($ox -eq 0 -and $oy -eq 0) { continue }
                        $ni = ($y + $oy) * $width + $x + $ox
                        if ($opaque[$ni] -and $pixels[$ni] -eq $key) {
                            $same++
                        }
                    }
                }
                if ($same -ne 0) { continue }
                $replacement = Get-ModeKey (Get-NeighborKeys `
                        $pixels $opaque $width $height $x $y)
                if ($replacement -ge 0) {
                    $changes[$index] = $replacement
                }
            }
        }
        if ($changes.Count -eq 0) { break }
        foreach ($entry in $changes.GetEnumerator()) {
            $pixels[$entry.Key] = $entry.Value
        }
    }
}

function Bridge-OnePixelGaps(
        [int[]]$pixels, [bool[]]$opaque, [int]$width, [int]$height) {
    $changes = @{}
    $directions = @(
            @(1, 0), @(0, 1), @(1, 1), @(1, -1)
    )
    for ($y = 1; $y -lt $height - 1; $y++) {
        for ($x = 1; $x -lt $width - 1; $x++) {
            $index = $y * $width + $x
            if (-not $opaque[$index]) { continue }
            $current = $pixels[$index]
            $currentCount = 0
            for ($oy = -1; $oy -le 1; $oy++) {
                for ($ox = -1; $ox -le 1; $ox++) {
                    $ni = ($y + $oy) * $width + $x + $ox
                    if ($opaque[$ni] -and $pixels[$ni] -eq $current) {
                        $currentCount++
                    }
                }
            }
            if ($currentCount -gt 2) { continue }
            $pairs = @{}
            foreach ($direction in $directions) {
                $ax = $x - $direction[0]
                $ay = $y - $direction[1]
                $bx = $x + $direction[0]
                $by = $y + $direction[1]
                $ai = $ay * $width + $ax
                $bi = $by * $width + $bx
                if ($opaque[$ai] -and $opaque[$bi] -and
                        $pixels[$ai] -eq $pixels[$bi] -and
                        $pixels[$ai] -ne $current) {
                    $pairs[$pixels[$ai]] =
                            1 + ($pairs[$pixels[$ai]] -as [int])
                }
            }
            $replacement = Get-ModeKey $pairs.Keys
            if ($replacement -ge 0) {
                $changes[$index] = $replacement
            }
        }
    }
    if ($changes.Count -eq 0) { return }
    foreach ($entry in $changes.GetEnumerator()) {
        $pixels[$entry.Key] = $entry.Value
    }
}

function Merge-SmallComponents(
        [int[]]$pixels, [bool[]]$opaque, [int]$width, [int]$height) {
    $visited = New-Object "bool[]" ($width * $height)
    $changes = @{}
    for ($start = 0; $start -lt $pixels.Length; $start++) {
        if (-not $opaque[$start] -or $visited[$start]) { continue }
        $key = $pixels[$start]
        $queue = New-Object System.Collections.Generic.Queue[int]
        $component = New-Object System.Collections.Generic.List[int]
        $queue.Enqueue($start)
        $visited[$start] = $true
        while ($queue.Count -gt 0) {
            $current = $queue.Dequeue()
            $component.Add($current)
            $x = $current % $width
            $y = [Math]::Floor($current / $width)
            for ($oy = -1; $oy -le 1; $oy++) {
                for ($ox = -1; $ox -le 1; $ox++) {
                    if ($ox -eq 0 -and $oy -eq 0) { continue }
                    $nx = $x + $ox
                    $ny = $y + $oy
                    if ($nx -lt 0 -or $nx -ge $width -or
                            $ny -lt 0 -or $ny -ge $height) {
                        continue
                    }
                    $ni = $ny * $width + $nx
                    if ($opaque[$ni] -and -not $visited[$ni] -and
                            $pixels[$ni] -eq $key) {
                        $visited[$ni] = $true
                        $queue.Enqueue($ni)
                    }
                }
            }
        }
        if ($component.Count -gt $SmallComponentLimit) { continue }
        $outside = New-Object System.Collections.Generic.List[int]
        foreach ($index in $component) {
            $x = $index % $width
            $y = [Math]::Floor($index / $width)
            foreach ($neighbor in Get-NeighborKeys `
                    $pixels $opaque $width $height $x $y) {
                if ($neighbor -ne $key) {
                    $outside.Add($neighbor)
                }
            }
        }
        $replacement = Get-ModeKey $outside
        if ($replacement -lt 0) { continue }
        foreach ($index in $component) {
            $changes[$index] = $replacement
        }
    }
    if ($changes.Count -eq 0) { return $false }
    foreach ($entry in $changes.GetEnumerator()) {
        $pixels[$entry.Key] = $entry.Value
    }
    return $true
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
    for ($frameIndex = 0; $frameIndex -lt $gif.GetFrameCount($frameDimension);
            $frameIndex++) {
        $gif.SelectActiveFrame($frameDimension, $frameIndex) | Out-Null
        $pixels = New-Object "int[]" ($width * $height)
        for ($y = 0; $y -lt $height; $y++) {
            for ($x = 0; $x -lt $width; $x++) {
                $index = $y * $width + $x
                if ($mask[$index]) {
                    $pixels[$index] = Get-Rgb ($gif.GetPixel($x, $y))
                }
            }
        }
        if ($BridgeGaps) {
            Bridge-OnePixelGaps $pixels $mask $width $height
        }
        Remove-IsolatedPixels $pixels $mask $width $height
        $thisPass = 0
        while ($thisPass -lt 4 -and
                (Merge-SmallComponents $pixels $mask $width $height)) {
            $thisPass++
            Remove-IsolatedPixels $pixels $mask $width $height
        }
        $output = New-Object System.Drawing.Bitmap(
                $width, $height,
                [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        for ($y = 0; $y -lt $height; $y++) {
            for ($x = 0; $x -lt $width; $x++) {
                $index = $y * $width + $x
                if ($mask[$index]) {
                    $rgb = $pixels[$index]
                    $output.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(
                            255,
                            ($rgb -shr 16) -band 0xFF,
                            ($rgb -shr 8) -band 0xFF,
                            $rgb -band 0xFF))
                }
            }
        }
        $output.Save((Join-Path $WorkDir ("frame_{0:D2}.png" -f $frameIndex)),
                [System.Drawing.Imaging.ImageFormat]::Png)
        $output.Dispose()
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
