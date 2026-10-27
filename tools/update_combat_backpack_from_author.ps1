param(
    [string]$SourceDir = "",
    [string]$Root = "D:\codex\mod\rebuild",
    [string]$OldTextureDir = "",
    [switch]$TexturesOnly
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$resourceRoot = Join-Path $Root "mod\src\main\resources\assets\tkgun"
$entityTextureDir = Join-Path $resourceRoot "textures\entity"
$itemTextureDir = Join-Path $resourceRoot "textures\item"
$itemModelDir = Join-Path $resourceRoot "models\item"
$wearablePath = Join-Path $SourceDir "combat_backpack.bbmodel"
$launcherPath = Get-ChildItem -LiteralPath $SourceDir -File -Filter "*.bbmodel" |
    Where-Object {
        $_.Name -ne "combat_backpack.bbmodel" -and
        $_.Length -lt 10000 -and
        ((Get-Content -Raw -Encoding UTF8 $_.FullName | ConvertFrom-Json).outliner[0].name -eq "C1")
    } |
    Select-Object -First 1 -ExpandProperty FullName
$baseTexturePath = Join-Path $SourceDir "mechanician_combat_backpack.png"
$paletteManifest = Join-Path $Root "dist\combat_backpack_palette_variants\combat_backpack_palette_manifest.txt"
if (-not $OldTextureDir) { throw "OldTextureDir is required" }
$oldBaseTexturePath = Join-Path $OldTextureDir "combat_backpack.png"

function Find-Group($nodes, [string]$name) {
    foreach ($node in @($nodes)) {
        if ($node -is [string]) { continue }
        if ($node.name -eq $name) { return $node }
        $found = Find-Group $node.children $name
        if ($null -ne $found) { return $found }
    }
    return $null
}

function Number-Value($value) {
    $number = [double]$value
    if ($number -eq [Math]::Truncate($number)) { return [int]$number }
    return $number
}

function Converted-Point($values, [bool]$wearable) {
    $x = [double]$values[0]
    $y = [double]$values[1]
    $z = [double]$values[2]
    if ($wearable) {
        return @((Number-Value (-$x - 4.0)), (Number-Value ($y - 24.0)), (Number-Value ($z - 2.0)))
    }
    return @((Number-Value (-$x)), (Number-Value ($y - 12.0)), (Number-Value $z))
}

function Converted-Cube-Origin($element, [bool]$wearable) {
    $from = @($element.from | ForEach-Object { [double]$_ })
    $to = @($element.to | ForEach-Object { [double]$_ })
    if ($wearable) {
        return @((Number-Value (-$to[0] - 4.0)), (Number-Value ($from[1] - 24.0)), (Number-Value ($from[2] - 2.0)))
    }
    return @((Number-Value (-$to[0])), (Number-Value ($from[1] - 12.0)), (Number-Value $from[2]))
}

function Converted-Rotation($values) {
    if ($null -eq $values) { return $null }
    return @((Number-Value (-[double]$values[0])), (Number-Value ([double]$values[1])), (Number-Value ([double]$values[2])))
}

function Convert-Cube($element, [bool]$wearable) {
    $from = @($element.from | ForEach-Object { [double]$_ })
    $to = @($element.to | ForEach-Object { [double]$_ })
    $uv = [ordered]@{}
    foreach ($faceName in @("north", "east", "south", "west", "up", "down")) {
        $sourceFace = $element.faces.$faceName
        if ($null -eq $sourceFace -or $null -eq $sourceFace.uv -or $null -eq $sourceFace.texture) { continue }
        $x1 = [double]$sourceFace.uv[0]
        $y1 = [double]$sourceFace.uv[1]
        $x2 = [double]$sourceFace.uv[2]
        $y2 = [double]$sourceFace.uv[3]
        $face = [ordered]@{
            uv = @((Number-Value $x1), (Number-Value $y1))
            uv_size = @((Number-Value ($x2 - $x1)), (Number-Value ($y2 - $y1)))
        }
        if ($sourceFace.rotation) { $face.uv_rotation = [int]$sourceFace.rotation }
        $uv[$faceName] = $face
    }
    $cube = [ordered]@{
        origin = Converted-Cube-Origin $element $wearable
        size = @(
            Number-Value ([Math]::Abs($to[0] - $from[0]))
            Number-Value ([Math]::Abs($to[1] - $from[1]))
            Number-Value ([Math]::Abs($to[2] - $from[2]))
        )
    }
    if ($uv.Count -gt 0) { $cube.uv = $uv }
    $rotation = Converted-Rotation $element.rotation
    if ($null -ne $rotation -and @($rotation | Where-Object { $_ -ne 0 }).Count -gt 0) {
        $cube.rotation = $rotation
    }
    return $cube
}

function Convert-Geometry($source, [bool]$wearable) {
    $elements = @{}
    foreach ($element in $source.elements) { $elements[[string]$element.uuid] = $element }
    $backpack = Find-Group $source.outliner "combat_backpack"
    if ($null -eq $backpack) { throw "combat_backpack group missing" }
    $bones = [System.Collections.Generic.List[object]]::new()

    function Add-Group($group, $parent) {
        $name = [string]$group.name
        if ($name -eq "sound") { return }
        $bone = [ordered]@{}
        $bone.name = $name
        if ($null -ne $parent) { $bone.parent = $parent }
        if ($name -eq "combat_backpack") {
            $bone.pivot = $(if ($wearable) { @(0, 0, 0) } else { @(0, 0, 4) })
        } else {
            $bone.pivot = Converted-Point $group.origin $wearable
        }
        $rotation = Converted-Rotation $group.rotation
        if ($null -ne $rotation -and @($rotation | Where-Object { $_ -ne 0 }).Count -gt 0) {
            $bone.rotation = $rotation
        }
        $cubes = [System.Collections.Generic.List[object]]::new()
        foreach ($child in @($group.children)) {
            if ($child -is [string]) {
                $element = $elements[$child]
                if ($null -ne $element -and $element.type -eq "cube") {
                    $cubes.Add((Convert-Cube $element $wearable))
                } elseif ($null -ne $element -and $element.type -eq "locator" -and $element.name -in @("front", "rear")) {
                    $locator = [ordered]@{
                        name = $element.name
                        parent = $name
                        pivot = Converted-Point $element.position $wearable
                    }
                    $locatorRotation = Converted-Rotation $element.rotation
                    if ($null -ne $locatorRotation -and @($locatorRotation | Where-Object { $_ -ne 0 }).Count -gt 0) {
                        $locator.rotation = $locatorRotation
                    }
                    $bones.Add($locator)
                }
                continue
            }
            if ($child.type -eq "locator") {
                if ($child.name -in @("front", "rear")) {
                    $locator = [ordered]@{
                        name = $child.name
                        parent = $name
                        pivot = Converted-Point $child.position $wearable
                    }
                    $locatorRotation = Converted-Rotation $child.rotation
                    if ($null -ne $locatorRotation -and @($locatorRotation | Where-Object { $_ -ne 0 }).Count -gt 0) {
                        $locator.rotation = $locatorRotation
                    }
                    $bones.Add($locator)
                }
                continue
            }
            Add-Group $child $name
        }
        if ($cubes.Count -gt 0) { $bone.cubes = @($cubes) }
        $bones.Insert(0, $bone)
    }

    Add-Group $backpack $null
    return [ordered]@{
        format_version = "1.12.0"
        "minecraft:geometry" = @([ordered]@{
            description = [ordered]@{
                identifier = "geometry.unknown"
                texture_width = 48
                texture_height = 48
                visible_bounds_width = 3
                visible_bounds_height = 3.5
                visible_bounds_offset = @(0, 1.25, 0)
            }
            bones = @($bones)
        })
    }
}

function Animation-Time($value) {
    return [string](Number-Value ([Math]::Round([double]$value, 4)))
}

function Convert-Animations($source, $boneNames) {
    $animations = [ordered]@{}
    foreach ($animation in $source.animations) {
        $bones = [ordered]@{}
        $soundEffects = [ordered]@{}
        foreach ($animator in $animation.animators.PSObject.Properties.Value) {
            if ($animator.type -eq "bone" -and $boneNames.Contains([string]$animator.name)) {
                $channels = [ordered]@{}
                foreach ($keyframe in $animator.keyframes) {
                    if ($keyframe.channel -notin @("rotation", "position", "scale")) { continue }
                    if (@($keyframe.data_points).Count -eq 0) { continue }
                    $first = $keyframe.data_points[0]
                    $x = $(if ($null -eq $first.x) { 0 } else { $first.x })
                    $y = $(if ($null -eq $first.y) { 0 } else { $first.y })
                    $z = $(if ($null -eq $first.z) { 0 } else { $first.z })
                    $value = @(
                        Number-Value $x
                        Number-Value $y
                        Number-Value $z
                    )
                    if (-not $channels.Contains($keyframe.channel)) {
                        $channels[$keyframe.channel] = [ordered]@{}
                    }
                    $channels[$keyframe.channel][(Animation-Time $keyframe.time)] = [ordered]@{
                        vector = $value
                    }
                }
                if ($channels.Count -gt 0) { $bones[$animator.name] = $channels }
            } elseif ($animator.type -eq "effect") {
                foreach ($keyframe in $animator.keyframes) {
                    if ($keyframe.channel -ne "sound") { continue }
                    foreach ($data in $keyframe.data_points) {
                        if ($data.effect) {
                            $soundEffects[(Animation-Time $keyframe.time)] = [ordered]@{
                                effect = $data.effect
                            }
                        }
                    }
                }
            }
        }
        $converted = [ordered]@{
            loop = $(if ($animation.loop -eq "hold") { "hold_on_last_frame" } else { "loop" })
            animation_length = Number-Value $animation.length
        }
        if ($bones.Count -gt 0) { $converted.bones = $bones }
        # Backpack open/close audio is driven by CombatBackpackClientState.
        # Keeping the old keyframes would require a sound handler and duplicate it.
        $animations[[string]$animation.name] = $converted
    }
    if ($animations.Contains("Maintenance")) {
        $animations.Maintenance.animation_length = 1
    }
    return [ordered]@{
        format_version = "1.8.0"
        animations = $animations
    }
}

function Write-JsonFile([string]$path, $value) {
    New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
    [IO.File]::WriteAllText(
        $path,
        ($value | ConvertTo-Json -Depth 100 -Compress),
        [Text.UTF8Encoding]::new($false))
}

function Export-PartModel([string]$sourcePath, [string]$outputName) {
    $source = Get-Content -Raw -Encoding UTF8 $sourcePath | ConvertFrom-Json
    $elements = [System.Collections.Generic.List[object]]::new()
    foreach ($src in $source.elements) {
        $faces = [ordered]@{}
        foreach ($faceName in @("north", "east", "south", "west", "up", "down")) {
            $sourceFace = $src.faces.$faceName
            if ($null -eq $sourceFace -or $null -eq $sourceFace.uv -or $null -eq $sourceFace.texture) { continue }
            $uv = @(
                [Math]::Round([double]$sourceFace.uv[0] * 16.0 / 48.0, 6)
                [Math]::Round([double]$sourceFace.uv[1] * 16.0 / 48.0, 6)
                [Math]::Round([double]$sourceFace.uv[2] * 16.0 / 48.0, 6)
                [Math]::Round([double]$sourceFace.uv[3] * 16.0 / 48.0, 6)
            )
            $face = [ordered]@{ uv = $uv; texture = "#0" }
            if ($sourceFace.rotation) { $face.rotation = [int]$sourceFace.rotation }
            $faces[$faceName] = $face
        }
        $element = [ordered]@{
            from = @([double]$src.from[0], [double]$src.from[1], [double]$src.from[2])
            to = @([double]$src.to[0], [double]$src.to[1], [double]$src.to[2])
            faces = $faces
        }
        $axes = @("x", "y", "z")
        if ($src.rotation) {
            for ($index = 0; $index -lt 3; $index++) {
                $angle = [double]$src.rotation[$index]
                if ([Math]::Abs($angle) -gt 0.00001) {
                    $element.rotation = [ordered]@{
                        origin = @([double]$src.origin[0], [double]$src.origin[1], [double]$src.origin[2])
                        axis = $axes[$index]
                        angle = $angle
                        rescale = $false
                    }
                    break
                }
            }
        }
        $elements.Add($element)
    }
    $display = [ordered]@{}
    foreach ($key in @("thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand",
            "firstperson_lefthand", "ground", "gui", "fixed")) {
        if ($null -ne $source.display.$key) {
            $entry = [ordered]@{}
            if ($source.display.$key.rotation) { $entry.rotation = @($source.display.$key.rotation) }
            if ($source.display.$key.translation) { $entry.translation = @($source.display.$key.translation) }
            if ($source.display.$key.scale) { $entry.scale = @($source.display.$key.scale) }
            $display[$key] = $entry
        }
    }
    $model = [ordered]@{
        credit = "Converted from author-provided " + [IO.Path]::GetFileName($sourcePath)
        texture_size = @(48, 48)
        textures = [ordered]@{
            "0" = "tkgun:item/combat_backpack"
            particle = "tkgun:item/combat_backpack"
        }
        elements = @($elements)
        display = $display
    }
    Write-JsonFile (Join-Path $itemModelDir ($outputName + ".json")) $model
}

if (-not $TexturesOnly) {
    $source = Get-Content -Raw -Encoding UTF8 $wearablePath | ConvertFrom-Json
    $wearable = Convert-Geometry $source $true
    $rig = Convert-Geometry $source $false
    $boneNames = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($bone in $wearable."minecraft:geometry"[0].bones) {
        [void]$boneNames.Add([string]$bone.name)
    }
    $animation = Convert-Animations $source $boneNames
    Write-JsonFile (Join-Path $resourceRoot "geo\entity\combat_backpack.geo.json") $wearable
    Write-JsonFile (Join-Path $resourceRoot "geo\entity\combat_backpack_rig.geo.json") $rig
    Write-JsonFile (Join-Path $resourceRoot "animations\entity\combat_backpack.animation.json") $animation
    Export-PartModel $launcherPath "portable_launcher"
}

New-Item -ItemType Directory -Force -Path $entityTextureDir, $itemTextureDir | Out-Null
if (-not $TexturesOnly) {
    Copy-Item -LiteralPath $baseTexturePath -Destination (Join-Path $entityTextureDir "combat_backpack.png") -Force
    Copy-Item -LiteralPath $baseTexturePath -Destination (Join-Path $itemTextureDir "combat_backpack.png") -Force
} else {
    Copy-Item -LiteralPath (Join-Path $entityTextureDir "combat_backpack.png") `
        -Destination (Join-Path $itemTextureDir "combat_backpack.png") -Force
}

$targetPalettes = @{}
foreach ($line in @(Get-Content -LiteralPath $paletteManifest -Encoding UTF8 | Select-Object -Skip 1)) {
    if (-not $line.Trim()) { continue }
    $columns = $line.Split(",", 4)
    $paletteParts = $columns[3].Split([string[]]@("->"), [StringSplitOptions]::None)
    $targetPalettes[$columns[0]] = @($paletteParts[1].Trim().Split(" "))
}

$paletteSteps = @{
    "#3A3B43" = 0
    "#45454C" = 1
    "#4B4C51" = 1
    "#606164" = 2
    "#626368" = 2
    "#6C6D6F" = 4
    "#75757A" = 3
    "#8B8B8E" = 5
}

function Color-Key([Drawing.Color]$color) {
    return "#{0:X2}{1:X2}{2:X2}" -f $color.R, $color.G, $color.B
}

$oldBase = [Drawing.Bitmap]::new($oldBaseTexturePath)
$newBasePath = $(if ($TexturesOnly) {
    Join-Path $entityTextureDir "combat_backpack.png"
} else {
    $baseTexturePath
})
$newBase = [Drawing.Bitmap]::new($newBasePath)
try {
    foreach ($name in $targetPalettes.Keys) {
        $currentVariantPath = Join-Path $entityTextureDir ("combat_backpack_" + $name + ".png")
        $oldVariantPath = Join-Path $OldTextureDir ("combat_backpack_" + $name + ".png")
        $temporaryVariantPath = $currentVariantPath + ".tmp.png"
        $currentVariant = [Drawing.Bitmap]::new($oldVariantPath)
        $updated = [Drawing.Bitmap]::new(48, 48, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            for ($y = 0; $y -lt 48; $y++) {
                for ($x = 0; $x -lt 48; $x++) {
                    $oldColor = $oldBase.GetPixel($x, $y)
                    $newColor = $newBase.GetPixel($x, $y)
                    if ($newColor.A -eq 0) {
                        $updated.SetPixel($x, $y, [Drawing.Color]::Transparent)
                        continue
                    }
                    if ($oldColor.A -gt 0 -and $oldColor.ToArgb() -eq $newColor.ToArgb()) {
                        $preserved = $currentVariant.GetPixel($x, $y)
                        $updated.SetPixel($x, $y, [Drawing.Color]::FromArgb(255, $preserved.R, $preserved.G, $preserved.B))
                        continue
                    }
                    $key = Color-Key $newColor
                    if ($paletteSteps.ContainsKey($key)) {
                        $target = [Drawing.ColorTranslator]::FromHtml($targetPalettes[$name][$paletteSteps[$key]])
                        $updated.SetPixel($x, $y, [Drawing.Color]::FromArgb(255, $target.R, $target.G, $target.B))
                    } else {
                        $updated.SetPixel($x, $y, [Drawing.Color]::FromArgb(255, $newColor.R, $newColor.G, $newColor.B))
                    }
                }
            }
            $updated.Save($temporaryVariantPath, [Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $updated.Dispose()
            $currentVariant.Dispose()
        }
        Move-Item -LiteralPath $temporaryVariantPath -Destination $currentVariantPath -Force
        Copy-Item -LiteralPath $currentVariantPath `
            -Destination (Join-Path $itemTextureDir ("combat_backpack_" + $name + ".png")) -Force
    }
} finally {
    $oldBase.Dispose()
    $newBase.Dispose()
}

Write-Output "Updated combat backpack geometry, animations, portable launcher model, and all palette textures."
