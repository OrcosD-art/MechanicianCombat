param(
    [Parameter(Mandatory = $true)][string]$Visual,
    [string]$Out = "D:\codex\mod\rebuild\mod\src\main\resources\assets\tkgun\textures\gui"
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force -Path $Out | Out-Null

function Save-TransparentFrames([string]$src, [string]$dest) {
    $img = [System.Drawing.Image]::FromFile($src)
    try {
        $dim = [System.Drawing.Imaging.FrameDimension]::new($img.FrameDimensionsList[0])
        $count = $img.GetFrameCount($dim)
        $sheet = [System.Drawing.Bitmap]::new(
                80 * $count, 80, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            for ($i = 0; $i -lt $count; $i++) {
                $img.SelectActiveFrame($dim, $i)
                $frame = [System.Drawing.Bitmap]::new($img)
                try {
                    for ($y = 0; $y -lt 80; $y++) {
                        for ($x = 0; $x -lt 80; $x++) {
                            $c = $frame.GetPixel($x, $y)
                            if ($c.A -gt 0 -and $c.R -lt 42 -and $c.G -lt 42 -and $c.B -lt 42) {
                                $c = [System.Drawing.Color]::FromArgb(0, 0, 0, 0)
                            }
                            $sheet.SetPixel($i * 80 + $x, $y, $c)
                        }
                    }
                } finally {
                    $frame.Dispose()
                }
            }
            $sheet.Save($dest, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $sheet.Dispose()
        }
    } finally {
        $img.Dispose()
    }
}

$clockwise = -join ([char[]](0x987A, 0x65F6, 0x9488))
$counterClockwise = -join ([char[]](0x9006, 0x65F6, 0x9488))
$switchText = -join ([char[]](0x5207, 0x6362))
$chamberText = -join ([char[]](0x5F39, 0x69FD))
$clockwiseSwitch = Join-Path $Visual ($clockwise + $switchText + ".gif")
$counterSwitch = Join-Path $Visual ($counterClockwise + $switchText + ".gif")
$clockwiseChamber = Join-Path $Visual ($clockwise + $chamberText + ".gif")
$counterChamber = Join-Path $Visual ($counterClockwise + $chamberText + ".gif")

Save-TransparentFrames $clockwiseSwitch "$Out\ammo_switch_cw.png"
Save-TransparentFrames $counterSwitch "$Out\ammo_switch_ccw.png"

$cell = 12
$half = 6
$paletteCount = 7
$rowCount = 2 * 4 * 6
$atlas = [System.Drawing.Bitmap]::new(
        $paletteCount * $cell, $rowCount * $cell,
        [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

# Pixel-cluster anchors, clockwise from the upper-left cluster.
$anchors = @(
    @(37, 34),
    @(43, 34),
    @(47, 38),
    @(47, 43),
    @(33, 43),
    @(33, 38)
)

# Two bullet-head colors per material, sampled from 子弹贴图.png row 1.
$palettes = @(
    @([System.Drawing.Color]::FromArgb(255, 0xFF, 0xEB, 0xD1),
      [System.Drawing.Color]::FromArgb(255, 0xC8, 0x32, 0x64)), # rose quartz
    @([System.Drawing.Color]::FromArgb(255, 0x93, 0x31, 0x1D),
      [System.Drawing.Color]::FromArgb(255, 0x81, 0x21, 0x18)), # explosive
    @([System.Drawing.Color]::FromArgb(255, 0x29, 0x88, 0x60),
      [System.Drawing.Color]::FromArgb(255, 0x1D, 0x5D, 0x3C)), # armor piercing
    @([System.Drawing.Color]::FromArgb(255, 0xE7, 0xFF, 0xFF),
      [System.Drawing.Color]::FromArgb(255, 0x5E, 0x94, 0xAA)), # cutting
    @([System.Drawing.Color]::FromArgb(255, 0xBB, 0xC3, 0x9B),
      [System.Drawing.Color]::FromArgb(255, 0x40, 0x57, 0x6C)), # sculk
    @([System.Drawing.Color]::FromArgb(255, 0x45, 0x45, 0x4C),
      [System.Drawing.Color]::FromArgb(255, 0x3A, 0x3B, 0x43)), # empty/default
    @([System.Drawing.Color]::FromArgb(255, 0xCC, 0xD3, 0xDA),
      [System.Drawing.Color]::FromArgb(255, 0x6C, 0x6D, 0x6F))  # molten trident
)

$chamberFiles = @($clockwiseChamber, $counterChamber)
try {
    for ($direction = 0; $direction -lt 2; $direction++) {
        $img = [System.Drawing.Image]::FromFile($chamberFiles[$direction])
        try {
            $dim = [System.Drawing.Imaging.FrameDimension]::new($img.FrameDimensionsList[0])
            for ($frame = 0; $frame -lt 4; $frame++) {
                $img.SelectActiveFrame($dim, $frame)
                $bitmap = [System.Drawing.Bitmap]::new($img)
                try {
                    for ($y = 0; $y -lt 80; $y++) {
                        for ($x = 0; $x -lt 80; $x++) {
                            $source = $bitmap.GetPixel($x, $y)
                            if ($source.A -eq 0) {
                                continue
                            }
                            $slot = 0
                            $best = [int]::MaxValue
                            for ($i = 0; $i -lt $anchors.Count; $i++) {
                                $dx = $x - $anchors[$i][0]
                                $dy = $y - $anchors[$i][1]
                                $distance = $dx * $dx + $dy * $dy
                                if ($distance -lt $best) {
                                    $best = $distance
                                    $slot = $i
                                }
                            }
                            # The chamber GIF has two shades: lighter #45454C and darker #3A3B43.
                            $shade = if ($source.R -gt 63) { 0 } else { 1 }
                            $localX = $half + ($x - $anchors[$slot][0])
                            $localY = $half + ($y - $anchors[$slot][1])
                            $outside = $localX -lt 0 -or $localX -ge $cell `
                                    -or $localY -lt 0 -or $localY -ge $cell
                            if ($outside) {
                                throw "Chamber pixel outside cell: frame=$frame slot=$slot x=$x y=$y"
                            }
                            $row = ($direction * 4 + $frame) * 6 + $slot
                            for ($palette = 0; $palette -lt $paletteCount; $palette++) {
                                $atlas.SetPixel(
                                        $palette * $cell + $localX,
                                        $row * $cell + $localY,
                                        $palettes[$palette][$shade])
                            }
                        }
                    }
                } finally {
                    $bitmap.Dispose()
                }
            }
        } finally {
            $img.Dispose()
        }
    }
    $atlas.Save("$Out\ammo_chamber_colors.png", [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    $atlas.Dispose()
}
