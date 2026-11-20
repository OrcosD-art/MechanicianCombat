param(
    [string]$SourceRoot,
    [string]$WorkDir = "D:\codex\mod\rebuild\build-soul-anchor-fpa",
    [string]$TargetRoot =
        "D:\codex\mod\rebuild\mod\src\main\resources\assets\mechaniciancombat",
    [int]$Port = 9370
)

$ErrorActionPreference = "Stop"
$invariant = [Globalization.CultureInfo]::InvariantCulture
$utf8NoBom = New-Object Text.UTF8Encoding($false)
$anchorName = [string][char]0x951A
$textureName = ([string][char]0x7075) + ([char]0x9B42) +
    ([char]0x4E4B) + ([char]0x951A) + ".png"
$sourceModel = Join-Path $SourceRoot "anchor_fist_person_animation.bbmodel"
$sourceTexture = Join-Path $SourceRoot $textureName
$rawGeo = Join-Path $WorkDir "soul_anchor_fpa.raw.geo.json"
$rawAnimation = Join-Path $WorkDir "soul_anchor_fpa.raw.animation.json"
$geoOut = Join-Path $TargetRoot "geo\entity\soul_anchor_fpa.geo.json"
$animationOut =
    Join-Path $TargetRoot "animations\entity\soul_anchor_fpa.animation.json"
$textureOut =
    Join-Path $TargetRoot "textures\entity\soul_anchor_fpa.png"

function Number([object]$value) {
    return [double]::Parse([string]$value, $invariant)
}

function Find-Group($nodes, [string]$name) {
    foreach ($node in @($nodes)) {
        if ($node -is [string]) {
            continue
        }
        if ([string]$node.name -eq $name) {
            return $node
        }
        $found = Find-Group $node.children $name
        if ($null -ne $found) {
            return $found
        }
    }
    return $null
}

function Direct-Source-Cubes($group, $elements) {
    foreach ($child in @($group.children)) {
        if ($child -is [string]) {
            $element = $elements[[string]$child]
            if ($null -ne $element -and
                    ([string]$element.type -eq "cube" -or
                    $null -eq $element.type)) {
                Write-Output $element
            }
        } elseif ($null -ne $child -and
                [string]$child.type -eq "cube") {
            Write-Output $child
        }
    }
}

New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
& powershell -NoProfile -ExecutionPolicy Bypass `
    -File "D:\codex\mod\rebuild\tools\export_bedrock_model_raw.ps1" `
    -Model $sourceModel -Output $rawGeo -AnimationOutput $rawAnimation `
    -Name "soul_anchor_fpa" -Port $Port `
    -Exe "D:\codex\mod\rebuild\tools\Blockbench5-portable\Blockbench.exe" `
    -Profile "D:\codex\mod\rebuild\work\bbprofile_soul_anchor_fpa"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $rawGeo) -or
        -not (Test-Path -LiteralPath $rawAnimation)) {
    throw "Blockbench first-person export failed"
}

$source = [IO.File]::ReadAllText(
    $sourceModel, [Text.Encoding]::UTF8) | ConvertFrom-Json
$geometry = [IO.File]::ReadAllText(
    $rawGeo, [Text.Encoding]::UTF8) | ConvertFrom-Json
$elements = @{}
foreach ($element in @($source.elements)) {
    $elements[[string]$element.uuid] = $element
}

$rotatedCubes = 0
$restoredPivots = 0
foreach ($bone in @($geometry.bones)) {
    $group = Find-Group $source.outliner ([string]$bone.name)
    if ($null -eq $group) {
        continue
    }
    $sourcePivot = @($group.origin)
    if ($sourcePivot.Count -ge 3) {
        $bonePivotX = -1.0 * (Number $sourcePivot[0])
        $bone.pivot = @(
            $bonePivotX,
            (Number $sourcePivot[1]),
            (Number $sourcePivot[2]))
        $restoredPivots++
    }

    $sourceCubes = @(Direct-Source-Cubes $group $elements)
    $exportedCubes = @()
    if ($null -ne $bone.cubes) {
        $exportedCubes = @($bone.cubes)
    }
    if ($sourceCubes.Count -ne $exportedCubes.Count) {
        if ($exportedCubes.Count -eq 0) {
            continue
        }
        throw ("Cube count mismatch for {0}: {1} != {2}" -f
            $bone.name, $sourceCubes.Count, $exportedCubes.Count)
    }
    for ($cubeIndex = 0;
            $cubeIndex -lt $sourceCubes.Count; $cubeIndex++) {
        $sourceCube = $sourceCubes[$cubeIndex]
        $exportedCube = $exportedCubes[$cubeIndex]
        $uv = [ordered]@{}
        foreach ($faceProperty in
                $sourceCube.faces.PSObject.Properties) {
            $face = $faceProperty.Value
            if ($null -eq $face.uv -or $null -eq $face.texture) {
                continue
            }
            $values = @($face.uv | ForEach-Object { Number $_ })
            $entry = [ordered]@{
                uv = @(
                    (Number $values[0]),
                    (Number $values[1]))
                uv_size = @(
                    ((Number $values[2]) - (Number $values[0])),
                    ((Number $values[3]) - (Number $values[1])))
            }
            if ($null -ne $face.rotation) {
                $entry.uv_rotation = Number $face.rotation
            }
            $uv[$faceProperty.Name] = [pscustomobject]$entry
        }
        if ($uv.Count -gt 0) {
            $exportedCube |
                Add-Member -NotePropertyName uv `
                    -NotePropertyValue ([pscustomobject]$uv) -Force
        }

        if ($null -ne $sourceCube.rotation -and
                $null -ne $sourceCube.origin) {
            $rotation = @($sourceCube.rotation)
            $pivot = @($sourceCube.origin)
            $cubeRotationX = -1.0 * (Number $rotation[0])
            $cubeRotationY = -1.0 * (Number $rotation[1])
            $cubePivotX = -1.0 * (Number $pivot[0])
            $exportedCube |
                Add-Member -NotePropertyName rotation `
                    -NotePropertyValue @(
                        $cubeRotationX,
                        $cubeRotationY,
                        (Number $rotation[2])) -Force
            $exportedCube |
                Add-Member -NotePropertyName pivot `
                    -NotePropertyValue @(
                        $cubePivotX,
                        (Number $pivot[1]),
                        (Number $pivot[2])) -Force
            $rotatedCubes++
        }
    }
}

foreach ($bone in @($geometry.bones)) {
    if ([string]$bone.name -eq $anchorName) {
        $bone.name = "anchor"
    }
}

$document = [ordered]@{
    format_version = "1.12.0"
    "minecraft:geometry" = @(
        [ordered]@{
            description = [ordered]@{
                identifier =
                    "geometry.mechaniciancombat.soul_anchor_fpa"
                texture_width = 32
                texture_height = 32
                visible_bounds_width = 2.0
                visible_bounds_height = 3.0
                visible_bounds_offset = @(0, 1.0, 0)
            }
            bones = @($geometry.bones)
        })
}

$animation = [IO.File]::ReadAllText(
    $rawAnimation, [Text.Encoding]::UTF8) | ConvertFrom-Json
foreach ($clip in $animation.animations.PSObject.Properties.Value) {
    $anchorBone = $clip.bones.PSObject.Properties[$anchorName]
    if ($null -eq $anchorBone) {
        continue
    }
    $clip.bones |
        Add-Member -NotePropertyName "anchor" `
            -NotePropertyValue $anchorBone.Value -Force
    $clip.bones.PSObject.Properties.Remove($anchorName)
}

New-Item -ItemType Directory -Force -Path (
    Split-Path -Parent $geoOut) | Out-Null
New-Item -ItemType Directory -Force -Path (
    Split-Path -Parent $animationOut) | Out-Null
New-Item -ItemType Directory -Force -Path (
    Split-Path -Parent $textureOut) | Out-Null
[IO.File]::WriteAllText(
    $geoOut, ($document | ConvertTo-Json -Depth 100 -Compress), $utf8NoBom)
[IO.File]::WriteAllText(
    $animationOut,
    ($animation | ConvertTo-Json -Depth 100 -Compress), $utf8NoBom)
Copy-Item -LiteralPath $sourceTexture -Destination $textureOut -Force

Write-Output "Geo: $geoOut"
Write-Output "Animation: $animationOut"
Write-Output "Texture: $textureOut"
Write-Output "Restored rotated cubes: $rotatedCubes"
Write-Output "Restored bone pivots: $restoredPivots"
