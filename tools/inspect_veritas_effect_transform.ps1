param(
    [string]$SourceModel = "D:\Download\USER\work\AModel\机械师装备\炮\枢德之裁\close_combat.bbmodel",
    [string]$RawGeo = "D:\codex\mod\rebuild\work\veritas_update_20261103\melee\close_combat.geo.raw.json",
    [string]$TargetGeo = "D:\codex\mod\rebuild\mod\src\main\resources\assets\mechaniciancombat\geo\entity\veritas_et_justitia_rig.geo.json"
)

$ErrorActionPreference = "Stop"
$rawText = [IO.File]::ReadAllText($SourceModel, [Text.Encoding]::UTF8)
$source = ([regex]::Replace($rawText, '\\(?![\\"/bfnrtu])', '/') |
    ConvertFrom-Json)
$raw = [IO.File]::ReadAllText($RawGeo, [Text.Encoding]::UTF8) |
    ConvertFrom-Json
$target = [IO.File]::ReadAllText($TargetGeo, [Text.Encoding]::UTF8) |
    ConvertFrom-Json
$elements = @{}
foreach ($element in $source.elements) {
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

function Bone($document, [string]$name) {
    $bones = if ($null -ne $document.bones) {
        $document.bones
    } else {
        $document.'minecraft:geometry'[0].bones
    }
    return $bones |
        Where-Object { $_.name -eq $name } |
        Select-Object -First 1
}

foreach ($name in @("blade_2", "change2_left", "effect_1", "1_1",
                    "effect_2", "1_5", "effect_3")) {
    Write-Output "BONE $name"
    $group = Find-Group $source.outliner $name
    if ($null -ne $group) {
        Write-Output ("  source group pivot: {0}" -f ($group.origin -join ","))
        foreach ($child in @($group.children)) {
            if ($child -isnot [string]) {
                continue
            }
            $element = $elements[[string]$child]
            if ($null -eq $element -or $element.type -ne "cube") {
                continue
            }
            Write-Output ("  source cube: from={0} to={1} origin={2} upUV={3}" -f
                ($element.from -join ","), ($element.to -join ","),
                ($element.origin -join ","), ($element.faces.up.uv -join ","))
        }
    }
    $rawBone = Bone $raw $name
    if ($null -ne $rawBone) {
        Write-Output ("  raw pivot: {0}" -f ($rawBone.pivot -join ","))
        foreach ($cube in @($rawBone.cubes)) {
            if ($null -eq $cube) {
                continue
            }
            Write-Output ("  raw cube: origin={0} size={1} rotation={2} pivot={3}" -f
                ($cube.origin -join ","), ($cube.size -join ","),
                ($cube.rotation -join ","), ($cube.pivot -join ","))
            $uvFaces = foreach ($face in $cube.uv.PSObject.Properties) {
                "$($face.Name)=$($face.Value.uv -join ',')/$($face.Value.uv_size -join ',')"
            }
            if ($cube.uv -is [array]) {
                Write-Output ("  raw uv raw: {0}" -f
                    ($cube.uv | ConvertTo-Json -Compress -Depth 10))
            } elseif ($uvFaces) {
                Write-Output ("  raw uv: {0}" -f ($uvFaces -join " "))
            } else {
                Write-Output ("  raw uv raw: {0}" -f
                    ($cube.uv | ConvertTo-Json -Compress -Depth 10))
            }
        }
    }
    $targetBone = Bone $target $name
    if ($null -ne $targetBone) {
        Write-Output ("  target parent={0} pivot={1}" -f
            $targetBone.parent, ($targetBone.pivot -join ","))
        foreach ($cube in @($targetBone.cubes)) {
            if ($null -eq $cube) {
                continue
            }
            Write-Output ("  target cube: origin={0} size={1} rotation={2} pivot={3}" -f
                ($cube.origin -join ","), ($cube.size -join ","),
                ($cube.rotation -join ","), ($cube.pivot -join ","))
            $uvFaces = foreach ($face in $cube.uv.PSObject.Properties) {
                "$($face.Name)=$($face.Value.uv -join ',')/$($face.Value.uv_size -join ',')"
            }
            Write-Output ("  target uv: {0}" -f ($uvFaces -join " "))
        }
    }
}
