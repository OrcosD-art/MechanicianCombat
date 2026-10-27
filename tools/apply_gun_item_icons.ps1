param(
    [Parameter(Mandatory = $true)][string]$VisualDirectory,
    [Parameter(Mandatory = $true)][string]$ResourceRoot,
    [Parameter(Mandatory = $true)][string]$BowgunIcon,
    [Parameter(Mandatory = $true)][string]$WardenIcon,
    [string]$BackupDirectory = "D:\codex\mod\rebuild\build\gun-icon-model-backup"
)
$ErrorActionPreference = "Stop"

$icons = @(
    @{
        Source = $BowgunIcon
        Texture = Join-Path $ResourceRoot "textures\item\bowgun_icon.png"
        Model = Join-Path $ResourceRoot "models\item\bowgun.json"
        TextureId = "tkgun:item/bowgun_icon"
    },
    @{
        Source = $WardenIcon
        Texture = Join-Path $ResourceRoot "textures\item\abyssal_ancient_cannon_icon.png"
        Model = Join-Path $ResourceRoot "models\item\abyssal_ancient_cannon.json"
        TextureId = "tkgun:item/abyssal_ancient_cannon_icon"
    }
)

Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force -Path $BackupDirectory | Out-Null
foreach ($icon in $icons) {
    if (-not (Test-Path -LiteralPath $icon.Source)) {
        throw "Icon not found: $($icon.Source)"
    }
    $image = [Drawing.Image]::FromFile($icon.Source)
    try {
        if ($image.Width -ne 16 -or $image.Height -ne 16) {
            throw "Icon must be 16x16: $($icon.Source)"
        }
    } finally {
        $image.Dispose()
    }

    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $icon.Texture) | Out-Null
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $icon.Model) | Out-Null
    if (Test-Path -LiteralPath $icon.Model) {
        Copy-Item -LiteralPath $icon.Model `
            -Destination (Join-Path $BackupDirectory (Split-Path -Leaf $icon.Model)) -Force
    }
    Copy-Item -LiteralPath $icon.Source -Destination $icon.Texture -Force

    $model = [ordered]@{
        parent = "item/generated"
        textures = [ordered]@{
            layer0 = $icon.TextureId
        }
    }
    [IO.File]::WriteAllText($icon.Model,
        ($model | ConvertTo-Json -Depth 5 -Compress),
        (New-Object Text.UTF8Encoding($false)))
    Write-Output "Applied $($icon.Source) -> $($icon.Texture)"
}
