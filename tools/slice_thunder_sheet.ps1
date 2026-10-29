param(
    [Parameter(Mandatory = $true)]
    [string]$SheetPath,
    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$frameWidth = 96
$frameHeight = 96
$frameCount = 7
$sheet = [Drawing.Bitmap]::new($SheetPath)
try {
    if ($sheet.Width -ne $frameWidth -or
            $sheet.Height -ne $frameHeight * $frameCount) {
        throw "Expected a 96x672 thunder sheet, got $($sheet.Width)x$($sheet.Height)"
    }
    [IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
    for ($frame = 0; $frame -lt $frameCount; $frame++) {
        $rect = [Drawing.Rectangle]::new(
            0, $frame * $frameHeight, $frameWidth, $frameHeight)
        $bitmap = $sheet.Clone($rect, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $path = Join-Path $OutputDirectory "thunder_$frame.png"
            $bitmap.Save($path, [Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $bitmap.Dispose()
        }
    }
} finally {
    $sheet.Dispose()
}

Write-Output "Sliced $frameCount thunder frames into $OutputDirectory"
