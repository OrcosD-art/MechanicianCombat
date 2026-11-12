$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$output = "D:\codex\mod\rebuild\mod\src\main\resources\assets\mechaniciancombat\textures\particle"
New-Item -ItemType Directory -Force -Path $output | Out-Null

$cream = [System.Drawing.Color]::FromArgb(255, 246, 242, 200)
$light = [System.Drawing.Color]::FromArgb(235, 248, 220, 163)
$gold = [System.Drawing.Color]::FromArgb(205, 232, 193, 112)
$amber = [System.Drawing.Color]::FromArgb(165, 222, 158, 65)
$brown = [System.Drawing.Color]::FromArgb(110, 190, 119, 43)
$dark = [System.Drawing.Color]::FromArgb(70, 136, 75, 43)

function New-Frame {
    $bitmap = [System.Drawing.Bitmap]::new(
        16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([System.Drawing.Color]::Transparent)
    return @($bitmap, $graphics)
}

function Put($graphics, [int]$x, [int]$y, $color) {
    $brush = [System.Drawing.SolidBrush]::new($color)
    try {
        $graphics.FillRectangle($brush, $x, $y, 1, 1)
    } finally {
        $brush.Dispose()
    }
}

function VLine($graphics, [int]$x, [int]$from, [int]$to, $color) {
    for ($y = $from; $y -le $to; $y++) {
        Put $graphics $x $y $color
    }
}

function HLine($graphics, [int]$y, [int]$from, [int]$to, $color) {
    for ($x = $from; $x -le $to; $x++) {
        Put $graphics $x $y $color
    }
}

function Save-Frame([int]$index, $graphics, $bitmap) {
    $path = Join-Path $output ("war_banner_spark_{0}.png" -f $index)
    $graphics.Dispose()
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bitmap.Dispose()
}

$frame = New-Frame
$g = $frame[1]
Put $g 7 7 $gold
Put $g 8 7 $cream
Put $g 7 8 $cream
Put $g 8 8 $light
Put $g 7 6 $light
Put $g 8 6 $light
Put $g 7 9 $light
Put $g 8 9 $light
Save-Frame 0 $g $frame[0]

$frame = New-Frame
$g = $frame[1]
VLine $g 7 5 10 $light
VLine $g 8 5 10 $gold
VLine $g 7 6 9 $cream
VLine $g 8 6 9 $cream
Put $g 6 7 $gold
Put $g 9 7 $gold
Save-Frame 1 $g $frame[0]

$frame = New-Frame
$g = $frame[1]
VLine $g 7 3 12 $gold
VLine $g 8 3 12 $dark
HLine $g 7 4 11 $light
HLine $g 8 4 11 $gold
Put $g 7 5 $cream
Put $g 8 5 $cream
Put $g 6 7 $cream
Put $g 7 7 $cream
Put $g 8 7 $cream
Put $g 9 7 $cream
Put $g 7 9 $cream
Put $g 8 9 $cream
Save-Frame 2 $g $frame[0]

$frame = New-Frame
$g = $frame[1]
VLine $g 7 2 13 $light
VLine $g 8 3 12 $gold
HLine $g 7 2 13 $light
HLine $g 8 3 12 $gold
Put $g 5 5 $gold
Put $g 4 4 $amber
Put $g 10 5 $gold
Put $g 11 4 $amber
Put $g 5 10 $gold
Put $g 4 11 $amber
Put $g 10 10 $gold
Put $g 11 11 $amber
Put $g 7 7 $cream
Put $g 8 7 $cream
Put $g 7 8 $cream
Put $g 8 8 $cream
Save-Frame 3 $g $frame[0]

$frame = New-Frame
$g = $frame[1]
VLine $g 7 3 12 $gold
VLine $g 8 4 11 $amber
Put $g 6 5 $light
Put $g 7 5 $cream
Put $g 8 5 $cream
Put $g 9 5 $light
Put $g 7 6 $cream
Put $g 8 6 $cream
Put $g 7 7 $light
Put $g 8 7 $light
Put $g 5 8 $amber
Put $g 10 8 $amber
Put $g 4 10 $brown
Put $g 11 10 $brown
Save-Frame 4 $g $frame[0]

$frame = New-Frame
$g = $frame[1]
Put $g 5 4 $light
Put $g 6 5 $cream
Put $g 7 6 $cream
Put $g 8 7 $cream
Put $g 9 8 $gold
Put $g 10 9 $amber
Put $g 9 5 $gold
Put $g 10 4 $amber
Put $g 6 9 $gold
Put $g 5 10 $amber
Put $g 7 11 $brown
Put $g 8 12 $dark
Save-Frame 5 $g $frame[0]

$frame = New-Frame
$g = $frame[1]
Put $g 4 5 $amber
Put $g 5 4 $gold
Put $g 6 6 $light
Put $g 10 4 $amber
Put $g 11 6 $gold
Put $g 9 8 $brown
Put $g 6 10 $brown
Put $g 8 11 $amber
Put $g 10 10 $dark
Save-Frame 6 $g $frame[0]

$frame = New-Frame
$g = $frame[1]
Put $g 5 5 $brown
Put $g 10 7 $dark
Put $g 7 10 $brown
Save-Frame 7 $g $frame[0]

Get-ChildItem -LiteralPath $output -Filter "war_banner_spark_*.png" |
    Sort-Object Name |
    Select-Object Name, Length
