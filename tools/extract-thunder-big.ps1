$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$source = Get-ChildItem -LiteralPath "D:\Download\USER\work\AModel" `
    -Recurse -File -Filter "thunder_big.png" |
    Select-Object -First 1
if (-not $source) {
    throw "thunder_big.png not found"
}
$image = [System.Drawing.Image]::FromFile($source.FullName)
try {
    if ($image.Width -ne 160 -or $image.Height -ne 1440) {
        throw "Unexpected thunder_big.png dimensions"
    }
    $target = "D:\codex\mod\rebuild\mod\src\main\resources\assets\mechaniciancombat\textures\particle"
    for ($frame = 0; $frame -lt 9; $frame++) {
        $rect = [System.Drawing.Rectangle]::new(0, $frame * 160, 160, 160)
        $crop = $image.Clone(
            $rect, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $crop.Save(
                (Join-Path $target "thunder_big_$frame.png"),
                [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $crop.Dispose()
        }
    }
} finally {
    $image.Dispose()
}
Get-ChildItem -LiteralPath $target -Filter "thunder_big_*.png" |
    Select-Object Name, Length
