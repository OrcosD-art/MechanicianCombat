param(
    [Parameter(Mandatory = $true)][string]$SourceDirectory,
    [switch]$Apply
)

$ErrorActionPreference = 'Stop'

$source = [System.IO.Path]::GetFullPath($SourceDirectory)
$initialPath = Join-Path $source 'combat_backpack.bbmodel'
if (-not (Test-Path -LiteralPath $initialPath -PathType Leaf)) {
    throw "Initial backpack model not found: $initialPath"
}

$initial = Get-Content -LiteralPath $initialPath -Raw -Encoding utf8 | ConvertFrom-Json
$models = Get-ChildItem -LiteralPath $source -File -Filter 'combat_backpack_gray*.bbmodel' |
    Sort-Object Name
if ($models.Count -ne 18) {
    throw "Expected 18 palette backpack models, found $($models.Count)"
}

$manifest = [System.Text.StringBuilder]::new()
[void]$manifest.AppendLine('model,texture,status')

foreach ($modelFile in $models) {
    $modelName = [System.IO.Path]::GetFileNameWithoutExtension($modelFile.Name)
    $textureName = if ($modelName -eq 'combat_backpack_gray') {
        'gray.png'
    } else {
        $modelName.Substring('combat_backpack_'.Length) + '.png'
    }
    $texturePath = Join-Path $source $textureName
    if (-not (Test-Path -LiteralPath $texturePath -PathType Leaf)) {
        throw "Texture for $modelName not found: $texturePath"
    }

    $clone = $initial | ConvertTo-Json -Depth 100 -Compress | ConvertFrom-Json
    $clone.name = $modelName
    $texture = $clone.textures[0]
    $texture.name = $textureName
    $texture | Add-Member -NotePropertyName path -NotePropertyValue $texturePath -Force
    $texture.relative_path = $textureName
    $texture.source = 'data:image/png;base64,' +
        [Convert]::ToBase64String([IO.File]::ReadAllBytes($texturePath))
    $clone.textures = @($texture)

    $json = $clone | ConvertTo-Json -Depth 100 -Compress
    if ($Apply) {
        [IO.File]::WriteAllText(
            $modelFile.FullName,
            $json,
            [System.Text.UTF8Encoding]::new($false))
    }
    [void]$manifest.AppendLine("$modelName,$textureName,synced")
}

if ($Apply) {
    [IO.File]::WriteAllText(
        (Join-Path $source 'combat_backpack_model_sync_manifest.txt'),
        $manifest.ToString(),
        [System.Text.UTF8Encoding]::new($false))
}

Write-Output "Synced model count: $($models.Count)"
Write-Output "Initial model: $initialPath"
