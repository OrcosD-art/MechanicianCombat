param(
    [string]$Source = "D:\codex\mod\rebuild\build\auto_aim.raw.geo.json",
    [string]$Wearable = "D:\codex\mod\rebuild\mod\src\main\resources\assets\tkgun\geo\entity\combat_backpack.geo.json",
    [string]$Rig = "D:\codex\mod\rebuild\mod\src\main\resources\assets\tkgun\geo\entity\combat_backpack_rig.geo.json"
)

$ErrorActionPreference = "Stop"

function Clone-JsonValue($value) {
    return $value | ConvertTo-Json -Depth 100 | ConvertFrom-Json
}

function Move-Point($point, [double]$dx, [double]$dy, [double]$dz) {
    if ($null -eq $point) {
        return $null
    }
    $px = [double]$point.GetValue(0)
    $py = [double]$point.GetValue(1)
    $pz = [double]$point.GetValue(2)
    $rx = $px + $dx
    $ry = $py + $dy
    $rz = $pz + $dz
    return @($rx, $ry, $rz)
}

function Convert-AutoAimCube($sourceCube, [double]$dx, [double]$dy, [double]$dz) {
    $cube = Clone-JsonValue $sourceCube
    $cube.origin = Move-Point $cube.origin $dx $dy $dz
    if ($null -ne $cube.pivot) {
        $cube.pivot = Move-Point $cube.pivot $dx $dy $dz
    }
    return $cube
}

function Merge-AutoAim($path, [double]$dx, [double]$dy, [double]$dz) {
    $sourceDocument = Get-Content -Raw -Encoding UTF8 $Source | ConvertFrom-Json
    $sourceC3 = $sourceDocument."minecraft:geometry"[0].bones |
        Where-Object { $_.name -eq "C3" } |
        Select-Object -First 1
    if ($null -eq $sourceC3) {
        throw "C3 not found in $Source"
    }

    $document = Get-Content -Raw -Encoding UTF8 $path | ConvertFrom-Json
    $target = $document."minecraft:geometry"[0].bones |
        Where-Object { $_.name -eq "auto_C3" } |
        Select-Object -First 1
    if ($null -eq $target) {
        throw "auto_C3 not found in $path"
    }

    $convertedCubes = @(
        foreach ($cube in @($sourceC3.cubes)) {
            Convert-AutoAimCube $cube $dx $dy $dz
        }
    )
    $target | Add-Member -NotePropertyName cubes -NotePropertyValue $convertedCubes -Force
    if ($null -ne $sourceC3.locators) {
        $locators = [ordered]@{}
        foreach ($property in $sourceC3.locators.PSObject.Properties) {
            $locators[$property.Name] = Move-Point $property.Value $dx $dy $dz
        }
        $target | Add-Member -NotePropertyName locators -NotePropertyValue $locators -Force
    }

    [IO.File]::WriteAllText(
        $path,
        ($document | ConvertTo-Json -Depth 100 -Compress),
        [Text.UTF8Encoding]::new($false))
}

# Wearable coordinates are shifted from the author's 48 px model.
Merge-AutoAim $Wearable -4.0 -24.0 -2.0
# The custom player rig uses the author's x/z coordinates with y shifted by 12.
Merge-AutoAim $Rig 0.0 -12.0 0.0

Write-Output "Merged auto_aim C3 cubes into auto_C3 for both backpack models."
