param(
    [Parameter(Mandatory = $true)][string]$SourceDirectory,
    [Parameter(Mandatory = $true)][string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
$source = [System.IO.Path]::GetFullPath($SourceDirectory)
$output = [System.IO.Path]::GetFullPath($OutputDirectory)
[System.IO.Directory]::CreateDirectory($output) | Out-Null

function Clone-Json($value) {
    return $value | ConvertTo-Json -Depth 100 -Compress | ConvertFrom-Json
}

function Convert-OutlinerNode($node, $groupMap, [string]$modelDirectory) {
    if ($node -is [string]) {
        return $node
    }
    $group = $groupMap[[string]$node.uuid]
    if ($null -eq $group) {
        throw "Group data missing for outliner UUID $($node.uuid)"
    }
    $converted = Clone-Json $group
    $children = @()
    foreach ($child in $node.children) {
        $children += Convert-OutlinerNode $child $groupMap $modelDirectory
    }
    $converted.children = @($children)
    return $converted
}

$models = Get-ChildItem -LiteralPath $source -File -Filter '*.bbmodel'
foreach ($modelFile in $models) {
    $model = Get-Content -LiteralPath $modelFile.FullName -Raw -Encoding utf8 |
        ConvertFrom-Json
    if ($model.meta.format_version -ne '5.0' -or $null -eq $model.groups) {
        Copy-Item -LiteralPath $modelFile.FullName -Destination (
            Join-Path $output $modelFile.Name) -Force
        continue
    }

    $groupMap = @{}
    foreach ($group in $model.groups) {
        $groupMap[[string]$group.uuid] = $group
    }
    $convertedOutliner = @()
    foreach ($node in $model.outliner) {
        $convertedOutliner += Convert-OutlinerNode $node $groupMap $source
    }
    $model.outliner = @($convertedOutliner)
    $model.PSObject.Properties.Remove('groups')
    $model.meta.format_version = '4.10'

    foreach ($texture in $model.textures) {
        if ($texture.PSObject.Properties.Name -notcontains 'path') {
            $relative = [string]$texture.relative_path
            if ($relative) {
                $texturePath = [System.IO.Path]::GetFullPath(
                    (Join-Path $source $relative))
                $texture | Add-Member -NotePropertyName path `
                    -NotePropertyValue $texturePath -Force
            }
        }
    }

    $json = $model | ConvertTo-Json -Depth 100 -Compress
    [IO.File]::WriteAllText(
        (Join-Path $output $modelFile.Name),
        $json,
        [System.Text.UTF8Encoding]::new($false))
}

Write-Output "Created $($models.Count) Blockbench 4.12.6-compatible files in $output"
