param(
    [string]$ParticleRoot =
        "D:\Download\USER\work\AModel\机械师装备\炮\枢德之裁\粒子",
    [string]$TextureRoot =
        "D:\codex\mod\rebuild\mod\src\main\resources\assets\mechaniciancombat\textures\particle",
    [string]$FlashFrames =
        "D:\codex\mod\rebuild\work\flash_current"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

for ($index = 0; $index -lt 5; $index++) {
    Copy-Item -LiteralPath (Join-Path $FlashFrames "flash_$index.png") `
        -Destination (Join-Path $TextureRoot "flash_$index.png") -Force
}
$staleFlash = Join-Path $TextureRoot "flash_5.png"
if (Test-Path -LiteralPath $staleFlash) {
    Remove-Item -LiteralPath $staleFlash -Force
}

$sheetPath = Join-Path $ParticleRoot "skill_spark.particle.png"
$sheet = [System.Drawing.Bitmap]::FromFile($sheetPath)
try {
    if ($sheet.Width -ne 48 -or $sheet.Height -ne 240) {
        throw "Unexpected skill spark sheet size: $($sheet.Width)x$($sheet.Height)"
    }
    for ($index = 0; $index -lt 5; $index++) {
        $frame = [System.Drawing.Bitmap]::new(
            48, 48, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics = [System.Drawing.Graphics]::FromImage($frame)
        try {
            $graphics.DrawImage(
                $sheet,
                [System.Drawing.Rectangle]::new(0, 0, 48, 48),
                [System.Drawing.Rectangle]::new(0, $index * 48, 48, 48),
                [System.Drawing.GraphicsUnit]::Pixel)
        } finally {
            $graphics.Dispose()
        }
        try {
            $frame.Save(
                (Join-Path $TextureRoot "skill_spark_$index.png"),
                [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $frame.Dispose()
        }
    }
} finally {
    $sheet.Dispose()
}

Get-ChildItem -LiteralPath $TextureRoot -Filter "flash_*.png" |
    Sort-Object Name |
    Select-Object Name, Length
Get-ChildItem -LiteralPath $TextureRoot -Filter "skill_spark_*.png" |
    Sort-Object Name |
    Select-Object Name, Length
