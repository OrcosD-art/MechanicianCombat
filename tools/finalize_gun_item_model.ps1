param(
    [Parameter(Mandatory = $true)][string]$SourceModel,
    [Parameter(Mandatory = $true)][string]$DisplayTemplate,
    [Parameter(Mandatory = $true)][string]$DestinationModel,
    [Parameter(Mandatory = $true)][string]$SourceTexture,
    [Parameter(Mandatory = $true)][string]$DestinationTexture,
    [Parameter(Mandatory = $true)][string]$TextureId
)
$ErrorActionPreference = "Stop"

$model = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $SourceModel).Path,
    [Text.Encoding]::UTF8) | ConvertFrom-Json
$template = [IO.File]::ReadAllText((Resolve-Path -LiteralPath $DisplayTemplate).Path,
    [Text.Encoding]::UTF8) | ConvertFrom-Json

if ($model.PSObject.Properties.Name -contains "format_version") {
    $model.PSObject.Properties.Remove("format_version")
}
$removeFire = {
    param($group)
    $indices = New-Object 'System.Collections.Generic.HashSet[int]'
    $walk = {
        param($node)
        foreach ($child in @($node.children)) {
            if ($child -is [int] -or $child -is [long]) {
                [void]$indices.Add([int]$child)
            } elseif ($child -isnot [string]) {
                & $walk $child
            }
        }
    }
    & $walk $group
    return ,$indices
}

$fireGroup = $null
foreach ($child in @($model.groups[0].children)) {
    if ($child -isnot [string] -and $child.name -eq "fire") {
        $fireGroup = $child
        break
    }
}
if ($fireGroup) {
    $removed = & $removeFire $fireGroup
    $newElements = New-Object System.Collections.ArrayList
    $remap = @{}
    for ($i = 0; $i -lt @($model.elements).Count; $i++) {
        if ($removed.Contains($i)) {
            continue
        }
        $remap[$i] = $newElements.Count
        [void]$newElements.Add($model.elements[$i])
    }
    $model.elements = @($newElements)
    $filterGroups = $null
    $filterGroups = {
        param($groups)
        $result = New-Object System.Collections.ArrayList
        foreach ($group in @($groups)) {
            if ($group -is [string]) {
                continue
            }
            if ($group.name -eq "fire") {
                continue
            }
            $children = New-Object System.Collections.ArrayList
            foreach ($child in @($group.children)) {
                if ($child -is [int] -or $child -is [long]) {
                    if ($remap.ContainsKey([int]$child)) {
                        [void]$children.Add($remap[[int]$child])
                    }
                } else {
                    [void]$children.Add((& $filterGroups @($child))[0])
                }
            }
            $group.children = @($children)
            [void]$result.Add($group)
        }
        return ,$result
    }
    $model.groups[0].children = @(& $filterGroups @($model.groups[0].children))
}

foreach ($element in @($model.elements)) {
    foreach ($face in $element.faces.PSObject.Properties) {
        if ($face.Value.texture -eq "#1") {
            $face.Value.texture = "#0"
        }
    }
    if (-not $element.rotation) {
        continue
    }
    $keys = @($element.rotation.PSObject.Properties.Name)
    if ($keys -contains "x") {
        $x = [double]$element.rotation.x
        $y = [double]$element.rotation.y
        $z = [double]$element.rotation.z
        $axis = if ([Math]::Abs($x) -gt 1.0e-9) {
            "x"
        } elseif ([Math]::Abs($y) -gt 1.0e-9) {
            "y"
        } else {
            "z"
        }
        $angle = if ($axis -eq "x") {
            $x
        } elseif ($axis -eq "y") {
            $y
        } else {
            $z
        }
        $element.rotation = [pscustomobject]@{
            origin = $element.rotation.origin
            axis = $axis
            angle = $angle
        }
    }
    if ($element.rotation.PSObject.Properties.Name -contains "angle") {
        $angle = [double]$element.rotation.angle
        $angle = [Math]::Round($angle / 22.5) * 22.5
        $angle = [Math]::Max(-45.0, [Math]::Min(45.0, $angle))
        $element.rotation.angle = $angle
    }
    # Minecraft item elements must stay inside [-16, 32] on each axis. Match
    # the Fortress Destroyer icon pipeline by fitting the long cannon into the
    # same visual envelope as the existing bowgun model.
    $scaleXZ = 0.8
    $translateX = 7.5
    $translateZ = 10.5
    $element.from[0] = [double]$element.from[0] * $scaleXZ + $translateX
    $element.to[0] = [double]$element.to[0] * $scaleXZ + $translateX
    $element.from[2] = [double]$element.from[2] * $scaleXZ + $translateZ
    $element.to[2] = [double]$element.to[2] * $scaleXZ + $translateZ
    if ($element.rotation) {
        $element.rotation.origin[0] =
            [double]$element.rotation.origin[0] * $scaleXZ + $translateX
        $element.rotation.origin[2] =
            [double]$element.rotation.origin[2] * $scaleXZ + $translateZ
    }
}

if ($model.PSObject.Properties.Name -contains "groups") {
    $model.PSObject.Properties.Remove("groups")
}

$model.textures = [pscustomobject]@{
    "0" = $TextureId
    particle = $TextureId
}
if ($model.PSObject.Properties.Name -contains "display") {
    $model.display = $template.display
} else {
    $model | Add-Member -NotePropertyName display -NotePropertyValue $template.display
}

$destinationPath = [IO.Path]::GetFullPath($DestinationModel)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $destinationPath) | Out-Null
[IO.File]::WriteAllText($destinationPath,
    ($model | ConvertTo-Json -Depth 100 -Compress),
    (New-Object Text.UTF8Encoding($false)))

$textureDestination = [IO.Path]::GetFullPath($DestinationTexture)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $textureDestination) | Out-Null
Copy-Item -LiteralPath $SourceTexture -Destination $textureDestination -Force
Write-Output "Item model: $destinationPath"
Write-Output "Item texture: $textureDestination"
