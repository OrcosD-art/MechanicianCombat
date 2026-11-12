param(
    [Parameter(Mandatory = $true)]
    [string]$SourceRoot,
    [string]$GeoPath = "D:\codex\mod\rebuild\mod\src\main\resources\assets\mechaniciancombat\geo\entity\veritas_et_justitia_rig.geo.json"
)

$ErrorActionPreference = "Stop"
$raw = [IO.File]::ReadAllText(
    (Join-Path $SourceRoot "close_combat.bbmodel"),
    [Text.Encoding]::UTF8)
$model = ([regex]::Replace($raw, '\\(?![\\"/bfnrtu])', '/') |
    ConvertFrom-Json)
$geo = [IO.File]::ReadAllText($geoPath, [Text.Encoding]::UTF8) |
    ConvertFrom-Json
$elements = @{}
foreach ($element in $model.elements) {
    $elements[[string]$element.uuid] = $element
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

function Direct-Cubes($group) {
    $result = New-Object System.Collections.ArrayList
    foreach ($child in @($group.children)) {
        if ($child -isnot [string]) {
            continue
        }
        $element = $elements[[string]$child]
        if ($null -ne $element -and $element.type -eq "cube") {
            [void]$result.Add($element)
        }
    }
    return $result.ToArray()
}

function Same-Vector($left, $right) {
    if ($left.Count -ne 3 -or $right.Count -ne 3) {
        return $false
    }
    for ($index = 0; $index -lt 3; $index++) {
        if ([Math]::Abs([double]$left[$index] -
                [double]$right[$index]) -gt 0.01) {
            return $false
        }
    }
    return $true
}

$issues = New-Object System.Collections.ArrayList
foreach ($bone in $geo.'minecraft:geometry'[0].bones) {
    if ([string]$bone.name -match '^(effect_|1_)') {
        continue
    }
    $group = Find-Group $model.outliner ([string]$bone.name)
    if ($null -eq $group) {
        continue
    }
    $sourceCubes = @(Direct-Cubes $group)
    $targetCubes = @($bone.cubes)
    if ($sourceCubes.Count -ne $targetCubes.Count) {
        [void]$issues.Add("$($bone.name): cubes $($sourceCubes.Count) -> $($targetCubes.Count)")
        continue
    }
    for ($index = 0; $index -lt $sourceCubes.Count; $index++) {
        $sourceCube = $sourceCubes[$index]
        $targetCube = $targetCubes[$index]
        $sourceFrom = @($sourceCube.from)
        $sourceTo = @($sourceCube.to)
        $sourceSize = @(
            ([double]$sourceTo[0] - [double]$sourceFrom[0]),
            ([double]$sourceTo[1] - [double]$sourceFrom[1]),
            ([double]$sourceTo[2] - [double]$sourceFrom[2])
        )
        if (-not (Same-Vector $sourceFrom $targetCube.origin) -or
                -not (Same-Vector $sourceSize $targetCube.size)) {
            [void]$issues.Add("$($bone.name)[$index]: geometry differs")
        }
    }
}

if ($issues.Count -eq 0) {
    Write-Output "Existing close-combat bone geometry matches the updated source."
} else {
    $issues
}
