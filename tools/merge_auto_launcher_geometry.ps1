param(
    [string]$Root = "D:\codex\mod\rebuild"
)

$ErrorActionPreference = "Stop"
$resourceRoot = Join-Path $Root "mod\src\main\resources\assets\tkgun"
$rawPath = Join-Path $Root "build\auto_launcher.raw.geo.json"
$rawAnimationPath = Join-Path $Root "build\auto_launcher.raw.animation.json"
$wearablePath = Join-Path $resourceRoot "geo\entity\combat_backpack.geo.json"
$rigPath = Join-Path $resourceRoot "geo\entity\combat_backpack_rig.geo.json"
$animationPath = Join-Path $resourceRoot "animations\entity\combat_backpack.animation.json"

function Clone-Json($value) {
    return ($value | ConvertTo-Json -Depth 100 | ConvertFrom-Json)
}

function AutoName([string]$name) {
    if ($name.StartsWith("auto_")) { return $name }
    return "auto_" + $name
}

function Convert-AutoBones($rawBones, [bool]$wearable) {
    $result = [System.Collections.Generic.List[object]]::new()
    foreach ($raw in $rawBones) {
        if ([string]$raw.name -in @("combat_backpack", "A", "B", "core")) {
            continue
        }
        $bone = Clone-Json $raw
        $sourceName = [string]$bone.name
        $bone.name = AutoName $sourceName
        if ($sourceName -eq "C1") {
            $bone | Add-Member -NotePropertyName parent -NotePropertyValue "combat_backpack" -Force
        } else {
            $parent = [string]$bone.parent
            $bone | Add-Member -NotePropertyName parent -NotePropertyValue (AutoName $parent) -Force
        }
        if ($null -ne $bone.pivot) {
            if ($wearable) {
                $bone.pivot[0] = [double]$bone.pivot[0] - 4.0
                $bone.pivot[1] = [double]$bone.pivot[1] - 24.0
                $bone.pivot[2] = [double]$bone.pivot[2] - 2.0
            } else {
                $bone.pivot[1] = [double]$bone.pivot[1] - 12.0
            }
        }
        foreach ($cube in $bone.cubes) {
            if ($wearable) {
                $cube.origin[0] = [double]$cube.origin[0] - 4.0
                $cube.origin[1] = [double]$cube.origin[1] - 24.0
                $cube.origin[2] = [double]$cube.origin[2] - 2.0
                if ($null -ne $cube.pivot) {
                    $cube.pivot[0] = [double]$cube.pivot[0] - 4.0
                    $cube.pivot[1] = [double]$cube.pivot[1] - 24.0
                    $cube.pivot[2] = [double]$cube.pivot[2] - 2.0
                }
            } else {
                $cube.origin[1] = [double]$cube.origin[1] - 12.0
                if ($null -ne $cube.pivot) {
                    $cube.pivot[1] = [double]$cube.pivot[1] - 12.0
                }
            }
        }
        $result.Add($bone)
    }
    $front = $(if ($wearable) {
        @(-13.24264, 3.2426400000000015, -8)
    } else {
        @(-9.24264, 15.242640000000002, -6)
    })
    $rear = $(if ($wearable) {
        @(-13.24264, 3.2426400000000015, 1)
    } else {
        @(-9.24264, 15.242640000000002, 3)
    })
    $result.Add([pscustomobject]@{name="auto_front";parent="auto_C3";pivot=$front})
    $result.Add([pscustomobject]@{name="auto_rear";parent="auto_C3";pivot=$rear})

    return $result
}

function Merge-Geometry([string]$path, [bool]$wearable) {
    $document = Get-Content -Raw -Encoding UTF8 $path | ConvertFrom-Json
    $geometry = $document."minecraft:geometry"[0]
    $existing = @($geometry.bones | Where-Object {
        -not ([string]$_.name).StartsWith("auto_")
    })
    $raw = Get-Content -Raw -Encoding UTF8 $rawPath | ConvertFrom-Json
    $automatic = Convert-AutoBones $raw."minecraft:geometry"[0].bones $wearable
    $geometry.bones = @($existing) + @($automatic)
    [IO.File]::WriteAllText(
        $path,
        ($document | ConvertTo-Json -Depth 100 -Compress),
        [Text.UTF8Encoding]::new($false))
}

Merge-Geometry $wearablePath $true
Merge-Geometry $rigPath $false

$animationDocument = Get-Content -Raw -Encoding UTF8 $animationPath | ConvertFrom-Json
$rawAnimationDocument = Get-Content -Raw -Encoding UTF8 $rawAnimationPath | ConvertFrom-Json
foreach ($animationProperty in $animationDocument.animations.PSObject.Properties) {
    $bones = $animationProperty.Value.bones
    if ($null -eq $bones) {
        continue
    }
    foreach ($name in @("auto_C1", "auto_C2", "auto_C3")) {
        if ($bones.PSObject.Properties.Name -contains $name) {
            $bones.PSObject.Properties.Remove($name)
        }
    }
    $autoNames = @($bones.PSObject.Properties |
        ForEach-Object { $_.Name } |
        Where-Object { $_.StartsWith("auto_") })
    foreach ($name in $autoNames) {
        if ($name.StartsWith("auto_")) {
            $bones.PSObject.Properties.Remove($name)
        }
    }
    $rawAnimation = $rawAnimationDocument.animations.($animationProperty.Name)
    if ($null -ne $rawAnimation -and $null -ne $rawAnimation.bones) {
        foreach ($rawBone in $rawAnimation.bones.PSObject.Properties) {
            if ($rawBone.Name -in @("A", "B")) {
                continue
            }
            $targetName = AutoName $rawBone.Name
            $copy = Clone-Json $rawBone.Value
            $bones | Add-Member -NotePropertyName $targetName -NotePropertyValue $copy -Force
        }
    }
}
[IO.File]::WriteAllText(
    $animationPath,
    ($animationDocument | ConvertTo-Json -Depth 100 -Compress),
    [Text.UTF8Encoding]::new($false))

Write-Output "Merged automatic launcher geometry and animation channels."
