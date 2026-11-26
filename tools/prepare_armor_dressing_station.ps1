param(
    [Parameter(Mandatory = $true)][string]$SourceModel,
    [switch]$CenterJavaBlock,
    [string]$OutputDirectory =
        "D:\codex\mod\rebuild\work\armor_dressing_station_prepared"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null

$model = Get-Content -LiteralPath $SourceModel -Raw -Encoding UTF8 |
    ConvertFrom-Json
$model | Add-Member -NotePropertyName model_identifier `
    -NotePropertyValue "geometry.armor_dressing_station" -Force

# The authored player seat is a visible part of the workstation model. Keep it
# in runtime geometry, while ungrouped collision and direction reference cubes
# remain editor-only helpers.
$rootCubeIds = [Collections.Generic.HashSet[string]]::new()
foreach ($rootNode in @($model.outliner)) {
    if ($rootNode -is [string]) {
        [void]$rootCubeIds.Add([string]$rootNode)
    } elseif ([string]$rootNode.name -eq "group") {
        foreach ($child in @($rootNode.children)) {
            if ($child -is [string]) {
                [void]$rootCubeIds.Add([string]$child)
            }
        }
    }
}
foreach ($element in @($model.elements)) {
    if ([string]$element.name -eq "player") {
        $element | Add-Member -NotePropertyName export `
            -NotePropertyValue $true -Force
    } elseif ($element.visibility -eq $false) {
        $element | Add-Member -NotePropertyName export `
            -NotePropertyValue $false -Force
    } elseif ($rootCubeIds.Contains([string]$element.uuid)) {
        $element | Add-Member -NotePropertyName export `
            -NotePropertyValue $false -Force
    }
}

if ($CenterJavaBlock) {
    function Center-Origin($origin) {
        if ($null -eq $origin) {
            return
        }
        $origin[0] = [double]$origin[0] - 8.0
        $origin[2] = [double]$origin[2] - 8.0
    }
    function Center-Groups($nodes) {
        foreach ($node in @($nodes)) {
            if ($null -eq $node -or $node -is [string]) {
                continue
            }
            Center-Origin $node.origin
            Center-Groups $node.children
        }
    }
    Center-Groups $model.outliner
    foreach ($element in @($model.elements)) {
        $element.from[0] = [double]$element.from[0] - 8.0
        $element.from[2] = [double]$element.from[2] - 8.0
        $element.to[0] = [double]$element.to[0] - 8.0
        $element.to[2] = [double]$element.to[2] - 8.0
        Center-Origin $element.origin
    }
}

$textures = @{}
$orderedTextures = @($model.textures)
$cursor = 0
for ($index = 0; $index -lt $orderedTextures.Count; $index++) {
    $texture = $orderedTextures[$index]
    $textures[[string]$index] = @{
        X = $cursor
        Width = [int]$texture.uv_width
        Height = [int]$texture.uv_height
        Bitmap = [Drawing.Bitmap]::FromStream(
            [IO.MemoryStream]::new([Convert]::FromBase64String(
                (([string]$texture.source).Split(",", 2))[1])))
    }
    $cursor += [int]$texture.uv_width
}

$atlasWidth = 256
$atlasHeight = 64
$model.resolution.width = $atlasWidth
$model.resolution.height = $atlasHeight
$atlas = [Drawing.Bitmap]::new(
    $atlasWidth, $atlasHeight, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [Drawing.Graphics]::FromImage($atlas)
$graphics.Clear([Drawing.Color]::Transparent)
$graphics.InterpolationMode =
    [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
foreach ($layout in $textures.Values) {
    $graphics.DrawImageUnscaled($layout.Bitmap, $layout.X, 0)
}
$graphics.Dispose()
foreach ($layout in $textures.Values) {
    $layout.Bitmap.Dispose()
}

$atlasPath = Join-Path $OutputDirectory (
    "armor_dressing_station_atlas_" +
    [Guid]::NewGuid().ToString("N") + ".png")
$atlasStream = [IO.MemoryStream]::new()
$atlas.Save($atlasStream, [Drawing.Imaging.ImageFormat]::Png)
$atlasBytes = $atlasStream.ToArray()
$atlasStream.Dispose()
$atlas.Dispose()
[IO.File]::WriteAllBytes($atlasPath, $atlasBytes)
$atlasSource = "data:image/png;base64," +
    [Convert]::ToBase64String($atlasBytes)

foreach ($element in @($model.elements)) {
    foreach ($face in $element.faces.PSObject.Properties) {
        if ($null -eq $face.Value.texture) {
            continue
        }
        $textureIndex = [string]$face.Value.texture
        if (-not $textures.ContainsKey($textureIndex)) {
            continue
        }
        $offsetX = $textures[$textureIndex].X
        $uv = @($face.Value.uv)
        $newUv = [double[]]::new(4)
        $newUv[0] = [double]$uv[0] + [double]$offsetX
        $newUv[1] = [double]$uv[1]
        $newUv[2] = [double]$uv[2] + [double]$offsetX
        $newUv[3] = [double]$uv[3]
        $face.Value.uv = $newUv
        $face.Value | Add-Member -NotePropertyName texture `
            -NotePropertyValue "0" -Force
    }
}

$model.textures = @([pscustomobject][ordered]@{
    path = $atlasPath
    name = "armor_dressing_station_atlas.png"
    folder = ""
    namespace = ""
    id = "0"
    group = ""
    width = $atlasWidth
    height = $atlasHeight
    uv_width = $atlasWidth
    uv_height = $atlasHeight
    particle = $false
    render_mode = "default"
    render_sides = "auto"
    visible = $true
    internal = $true
    saved = $true
    source = $atlasSource
})

$preparedPath = Join-Path $OutputDirectory "armor_dressing_station_prepared.bbmodel"
$json = $model | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText(
    $preparedPath, $json, (New-Object Text.UTF8Encoding($false)))

$mapping = [ordered]@{
    body = "body"
    chest = "torso"
    head = "head"
    arm_left = "leftArm"
    arm_right = "rightArm"
    leg_left = "leftLeg"
    leg_right = "rightLeg"
}
$dressing = $model.animations |
    Where-Object { [double]$_.length -eq 2.5 } |
    Select-Object -First 1
if ($null -ne $dressing) {
    $playerBones = [ordered]@{}
    foreach ($pair in $mapping.GetEnumerator()) {
        $animator = $dressing.animators.PSObject.Properties |
            Where-Object { $_.Value.name -eq $pair.Key } |
            Select-Object -First 1
        if ($null -eq $animator) {
            continue
        }
        $channels = [ordered]@{}
        foreach ($keyframe in $animator.Value.keyframes) {
            if ($keyframe.channel -notin @("rotation", "position", "scale")) {
                continue
            }
            $point = @($keyframe.data_points)[0]
            $time = [double]$keyframe.time
            if (-not $channels.Contains($keyframe.channel)) {
                $channels[$keyframe.channel] = [ordered]@{}
            }
            $channels[$keyframe.channel][
                ("{0:0.00000}" -f $time).TrimEnd("0")] =
                @([double]$point.x, [double]$point.y, [double]$point.z)
        }
        $playerBones[$pair.Value] = $channels
    }
    $playerAnimation = [ordered]@{
        format_version = "1.8.0"
        animations = [ordered]@{
            armor_dressing_station = [ordered]@{
                loop = "hold_on_last_frame"
                animation_length = 2.5
                bones = $playerBones
            }
        }
    }
    $playerAnimationPath = Join-Path $OutputDirectory `
        "armor_dressing_station_player.json"
    [IO.File]::WriteAllText(
        $playerAnimationPath,
        ($playerAnimation | ConvertTo-Json -Depth 30 -Compress),
        (New-Object Text.UTF8Encoding($false)))
    Write-Output $playerAnimationPath
}

Write-Output $preparedPath
Write-Output $atlasPath
