param(
    [Parameter(Mandatory = $true)][string]$MainRaw,
    [Parameter(Mandatory = $true)][string]$ActiveRaw,
    [Parameter(Mandatory = $true)][string]$OutputRoot
)

$ErrorActionPreference = "Stop"
$encoding = New-Object Text.UTF8Encoding($false)

function Read-Geometry([string]$path) {
    return Get-Content -Raw -Encoding UTF8 $path | ConvertFrom-Json
}

function Copy-Document([object]$document) {
    return $document | ConvertTo-Json -Depth 100 -Compress |
        ConvertFrom-Json
}

function Set-AtlasSpace([object]$document) {
    $description = $document.'minecraft:geometry'[0].description
    $description.texture_width = 256
    $description.texture_height = 64
}

function Find-Bone([object[]]$bones, [string]$name) {
    return $bones | Where-Object { [string]$_.name -eq $name } |
        Select-Object -First 1
}

function Get-PlayerCube([object]$document) {
    foreach ($bone in @($document.'minecraft:geometry'[0].bones)) {
        foreach ($cube in @($bone.cubes)) {
            if ($null -eq $cube) {
                continue
            }
            if (@($cube.origin | ForEach-Object { [double]$_ }) -join "," -eq
                    "-8,-1,-8" -and
                    @($cube.size | ForEach-Object { [double]$_ }) -join "," -eq
                    "16,3,16") {
                return $cube | ConvertTo-Json -Depth 100 -Compress |
                    ConvertFrom-Json
            }
        }
    }
    throw "The visible player cube was not present in the active export."
}

function Remove-WithDescendants([object]$document,
        [string]$rootBoneName) {
    $geometry = $document.'minecraft:geometry'[0]
    $removed = [Collections.Generic.HashSet[string]]::new()
    [void]$removed.Add($rootBoneName)
    do {
        $changed = $false
        foreach ($bone in @($geometry.bones)) {
            $parent = [string]$bone.parent
            if ($parent -and $removed.Contains($parent) -and
                    $removed.Add([string]$bone.name)) {
                $changed = $true
            }
        }
    } while ($changed)
    $geometry.bones = @($geometry.bones | Where-Object {
        -not $removed.Contains([string]$_.name)
    })
}

function Append-PlayerBody([object]$document, [object]$mainDocument) {
    $mainBones = @($mainDocument.'minecraft:geometry'[0].bones)
    $selected = [Collections.Generic.HashSet[string]]::new()
    [void]$selected.Add("body")
    do {
        $changed = $false
        foreach ($bone in $mainBones) {
            if ($selected.Contains([string]$bone.parent) -and
                    $selected.Add([string]$bone.name)) {
                $changed = $true
            }
        }
    } while ($changed)
    $bodyBones = @($mainBones | Where-Object {
        $selected.Contains([string]$_.name)
    })
    $geometry = $document.'minecraft:geometry'[0]
    $existing = [Collections.Generic.HashSet[string]]::new()
    foreach ($bone in @($geometry.bones)) {
        [void]$existing.Add([string]$bone.name)
    }
    $geometry.bones = @($geometry.bones) + @($bodyBones |
        Where-Object { $existing.Add([string]$_.name) })
}

function Align-RootOrientation([object]$document, [object]$mainDocument) {
    $mainBones = @($mainDocument.'minecraft:geometry'[0].bones)
    $mainRootName = [string][char]0x6574 + [char]0x4F53
    $mainRoot = Find-Bone $mainBones $mainRootName
    $stateRoot = Find-Bone @($document.'minecraft:geometry'[0].bones) "group"
    if ($null -eq $mainRoot -or $null -eq $stateRoot -or
            $null -eq $mainRoot.rotation) {
        return
    }
    $stateRoot | Add-Member -NotePropertyName rotation `
        -NotePropertyValue @($mainRoot.rotation) -Force
}

function Write-Geometry([object]$document, [string]$path) {
    New-Item -ItemType Directory -Force -Path (
        Split-Path $path -Parent) | Out-Null
    [IO.File]::WriteAllText($path,
        ($document | ConvertTo-Json -Depth 100 -Compress), $encoding)
}

$main = Read-Geometry $MainRaw
$activeSource = Read-Geometry $ActiveRaw
$playerCube = Get-PlayerCube $activeSource

Set-AtlasSpace $main
$mainGeometry = $main.'minecraft:geometry'[0]
$mainGeometry.description.identifier = "geometry.armor_dressing_station"
$mainHasPlayer = $false
try {
    [void](Get-PlayerCube $main)
    $mainHasPlayer = $true
} catch {
    $mainHasPlayer = $false
}
if (-not $mainHasPlayer) {
    $mainRoot = Find-Bone @($mainGeometry.bones) "bb_main"
    if ($null -eq $mainRoot) {
        $mainRoot = [pscustomobject][ordered]@{
            name = "bb_main"
            pivot = @(0, 0, 0)
        }
        $mainGeometry.bones = @($mainRoot) + @($mainGeometry.bones)
    }
    $mainRoot | Add-Member -NotePropertyName cubes `
        -NotePropertyValue @($playerCube) -Force
}
Write-Geometry $main (Join-Path $OutputRoot "main.geo.json")

$active = Copy-Document $activeSource
Set-AtlasSpace $active
$activeName = [string][char]0x6FC0 + [char]0x6D3B
$inactiveName = [string][char]0x672A + [char]0x542F + [char]0x52A8
Remove-WithDescendants $active $inactiveName
Align-RootOrientation $active $main
$activeRoot = Find-Bone @($active.'minecraft:geometry'[0].bones) "group"
if ($null -ne $activeRoot) {
    # Apply the latest half-turn relative to the previous active orientation.
    $activeRoot | Add-Member -NotePropertyName rotation `
        -NotePropertyValue @(0, 0, 0) -Force
}
Append-PlayerBody $active $main
$active.'minecraft:geometry'[0].description.identifier =
    "geometry.armor_dressing_station_active"
Write-Geometry $active (Join-Path $OutputRoot "active.geo.json")

$inactive = Copy-Document $activeSource
Set-AtlasSpace $inactive
Remove-WithDescendants $inactive $activeName
Align-RootOrientation $inactive $main
Append-PlayerBody $inactive $main
$inactive.'minecraft:geometry'[0].description.identifier =
    "geometry.armor_dressing_station_inactive"
Write-Geometry $inactive (Join-Path $OutputRoot "inactive.geo.json")

Write-Output $OutputRoot
