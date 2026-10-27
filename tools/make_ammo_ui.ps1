param(
    [string]$SlotPng = "D:\codex\mod\rebuild\build\ui_tex\assets\minecraft\textures\gui\sprites\container\slot.png",
    [string]$Out = "D:\codex\mod\rebuild\mod\src\main\resources\assets\tkgun\textures\gui\ammo_storage.png"
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

New-Item -ItemType Directory -Force -Path (Split-Path $Out) | Out-Null
$bmp = New-Object System.Drawing.Bitmap(176, 212)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
$g.Clear([System.Drawing.Color]::FromArgb(255, 198, 198, 198))

$border = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 55, 55, 55), 1)
$g.DrawRectangle($border, 0, 0, 175, 211)
$sep = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 90, 90, 90), 1)
$g.DrawLine($sep, 8, 112, 168, 112)

$slot = [System.Drawing.Bitmap]::FromFile($SlotPng)
$centers = @()
for ($i = 0; $i -lt 8; $i++) {
    $a = -[Math]::PI / 2 + $i * 2 * [Math]::PI / 8
    $cx = [int][Math]::Round(88 + [Math]::Cos($a) * 37)
    $cy = [int][Math]::Round(56 + [Math]::Sin($a) * 37)
    $g.DrawImageUnscaled($slot, $cx - 9, $cy - 9)
    $centers += ,@($cx, $cy)
}

$line = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(150, 255, 210, 74), 1)
for ($i = 0; $i -lt 8; $i++) {
    $a = $centers[$i]
    $b = $centers[($i + 1) % 8]
    $g.DrawLine($line, $a[0], $a[1], $b[0], $b[1])
}
$line.Dispose()

for ($i = 0; $i -lt 36; $i++) {
    if ($i -lt 9) {
        $x = 8 + $i * 18
        $y = 184
    } else {
        $x = 8 + (($i - 9) % 9) * 18
        $y = 122 + [int][Math]::Floor(($i - 9) / 9) * 18
    }
    $g.DrawImageUnscaled($slot, $x, $y)
}

$gold = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(220, 255, 210, 74), 1)
foreach ($c in $centers) {
    $g.DrawRectangle($gold, $c[0] - 10, $c[1] - 10, 20, 20)
}

$bmp.Save($Out, [System.Drawing.Imaging.ImageFormat]::Png)
$gold.Dispose()
$sep.Dispose()
$border.Dispose()
$slot.Dispose()
$g.Dispose()
$bmp.Dispose()
Write-Output $Out
