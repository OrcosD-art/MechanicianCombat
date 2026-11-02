param(
    [Parameter(Mandatory = $true)]
    [string]$SkillModel,
    [Parameter(Mandatory = $true)]
    [string]$Geometry,
    [switch]$ReplaceExisting
)

$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$invariant = [Globalization.CultureInfo]::InvariantCulture

function Number([object]$value) {
    return [double]::Parse([string]$value, $invariant)
}

function ReadBbmodel([string]$path) {
    $raw = [IO.File]::ReadAllText($path, [Text.Encoding]::UTF8)
    $valid = [regex]::Replace($raw, '\\(?![\\"/bfnrtu])', '/')
    return $valid | ConvertFrom-Json
}

function FindGroups($nodes, [string]$parent, $map, $found) {
    foreach ($node in @($nodes)) {
        if ($node -is [string]) {
            continue
        }
        if ($node.name -in @('skill_1', 'fire_skill',
                'step_1', 'step_2', 'step_3', 'step_4')) {
            $found[$node.name] = [pscustomobject]@{
                Node = $node
                Parent = $parent
            }
        }
        FindGroups $node.children $node.name $map $found
    }
}

function ConvertFace($face) {
    $uv = @($face.uv)
    $x1 = Number ([string]$uv[0])
    $y1 = Number ([string]$uv[1])
    $x2 = Number ([string]$uv[2])
    $y2 = Number ([string]$uv[3])
    $entry = [ordered]@{
        uv = @($x1, $y1)
        uv_size = @(($x2 - $x1), ($y2 - $y1))
    }
    if ($null -ne $face.rotation) {
        $entry.uv_rotation = [int]$face.rotation
    }
    return [pscustomobject]$entry
}

$model = ReadBbmodel $SkillModel
$geometryDoc = [IO.File]::ReadAllText($Geometry, [Text.Encoding]::UTF8) |
        ConvertFrom-Json
$geo = $geometryDoc.'minecraft:geometry'[0]
$elements = @{}
foreach ($element in @($model.elements)) {
    $elements[$element.uuid] = $element
}
$groups = @{}
FindGroups $model.outliner $null $groups $groups

$staleSkillBones = @($geo.bones | Where-Object name -eq 'skill_2')
if ($staleSkillBones.Count -gt 0) {
    $geo.bones = @($geo.bones | Where-Object name -ne 'skill_2')
    Write-Output "removed stale skill_2 arm child"
}

$targetGroups = @('skill_1', 'fire_skill',
        'step_1', 'step_2', 'step_3', 'step_4')
if ($ReplaceExisting) {
    $geo.bones = @($geo.bones | Where-Object { $_.name -notin $targetGroups })
}

foreach ($groupName in @('skill_1', 'fire_skill',
        'step_1', 'step_2', 'step_3', 'step_4')) {
    if (-not $groups.ContainsKey($groupName)) {
        throw "Missing source group: $groupName"
    }
    if (@($geo.bones | Where-Object name -eq $groupName).Count -eq 0) {
        $group = $groups[$groupName]
        $node = $group.Node
        $cubes = New-Object System.Collections.ArrayList
        foreach ($child in @($node.children)) {
            if ($child -isnot [string] -or -not $elements.ContainsKey($child)) {
                continue
            }
            $cube = $elements[$child]
            $from = @($cube.from | ForEach-Object { Number ([string]$_) })
            $to = @($cube.to | ForEach-Object { Number ([string]$_) })
            $uv = [ordered]@{}
            foreach ($faceName in @('north', 'east', 'south', 'west', 'up', 'down')) {
                $face = $cube.faces.$faceName
                if ($null -eq $face -or $null -eq $face.texture) {
                    continue
                }
                $uv[$faceName] = ConvertFace $face
            }
            [void]$cubes.Add([ordered]@{
                origin = $from
                size = @(($to[0] - $from[0]), ($to[1] - $from[1]),
                        ($to[2] - $from[2]))
                uv = [pscustomobject]$uv
            })
        }
        $bone = [ordered]@{
            name = $groupName
            parent = $group.Parent
            pivot = @($node.origin | ForEach-Object { Number ([string]$_) })
            cubes = @($cubes)
        }
        $geo.bones += [pscustomobject]$bone
        Write-Output "added $groupName -> parent=$($group.Parent), cubes=$($cubes.Count)"
    }
}

[IO.File]::WriteAllText($Geometry,
        ($geometryDoc | ConvertTo-Json -Depth 100 -Compress), $utf8)
