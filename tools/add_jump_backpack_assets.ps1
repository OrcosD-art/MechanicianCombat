param(
    [string]$SourceDir = "",
    [string]$Resources = (Join-Path $PSScriptRoot "..\mod\src\main\resources")
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing

$entityDir = Join-Path $Resources "assets\tkgun\geo\entity"
$animationPath = Join-Path $Resources "assets\tkgun\animations\entity\combat_backpack.animation.json"
$itemModelPath = Join-Path $Resources "assets\tkgun\models\item\jump_backpack_thruster.json"
$itemTexturePath = Join-Path $Resources "assets\tkgun\textures\item\jump_backpack_thruster.png"
$missileItemModelPath = Join-Path $Resources "assets\tkgun\models\item\missile_box.json"
$missileItemTexturePath = Join-Path $Resources "assets\tkgun\textures\item\missile_box.png"
$textureDir = Join-Path $Resources "assets\tkgun\textures\entity"

function Read-Json([string]$path) {
    return Get-Content -Raw -Encoding UTF8 -LiteralPath $path | ConvertFrom-Json
}

function Write-Json([string]$path, $value) {
    New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
    $temporaryPath = $path + ".generated"
    [IO.File]::WriteAllText(
        $temporaryPath,
        ($value | ConvertTo-Json -Depth 100 -Compress),
        [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporaryPath -Destination $path -Force
}

function Number-Value($value) {
    $number = [double]$value
    if ($number -eq [Math]::Truncate($number)) {
        return [int]$number
    }
    return $number
}

function Find-Group($nodes, [string]$name) {
    foreach ($node in @($nodes)) {
        if ($node -is [string] -or $null -eq $node) { continue }
        if ($node.name -eq $name) { return $node }
        $found = Find-Group $node.children $name
        if ($null -ne $found) { return $found }
    }
    return $null
}

function Converted-Point($values, [bool]$wearable) {
    $x = [double]$values[0]
    $y = [double]$values[1]
    $z = [double]$values[2]
    if ($wearable) {
        return @(
            Number-Value (-$x - 4.0)
            Number-Value ($y - 24.0)
            Number-Value ($z - 2.0)
        )
    }
    return @(
        Number-Value (-$x)
        Number-Value ($y - 12.0)
        Number-Value $z
    )
}

function Converted-Rotation($rotation) {
    if ($null -eq $rotation) { return @() }
    if ($null -ne $rotation.angle) {
        $axis = [string]$rotation.axis
        $angle = [double]$rotation.angle
        if ([Math]::Abs($angle) -lt 0.00001) { return @() }
        $values = @(0.0, 0.0, 0.0)
        if ($axis -eq "x") { $values[0] = -$angle }
        elseif ($axis -eq "y") { $values[1] = -$angle }
        elseif ($axis -eq "z") { $values[2] = $angle }
        return @($values | ForEach-Object { Number-Value $_ })
    }
    return @(
        Number-Value (-[double]$rotation[0])
        Number-Value (-[double]$rotation[1])
        Number-Value ([double]$rotation[2])
    )
}

function Convert-Cube($element, [bool]$wearable) {
    $from = @($element.from | ForEach-Object { [double]$_ })
    $to = @($element.to | ForEach-Object { [double]$_ })
    $origin = $(if ($wearable) {
        @(
            Number-Value (-$to[0] - 4.0)
            Number-Value ($from[1] - 24.0)
            Number-Value ($from[2] - 2.0)
        )
    } else {
        @(
            Number-Value (-$to[0])
            Number-Value ($from[1] - 12.0)
            Number-Value ($from[2])
        )
    })
    $uv = [ordered]@{}
    foreach ($faceName in @("north", "east", "south", "west", "up", "down")) {
        $face = $element.faces.$faceName
        if ($null -eq $face -or $null -eq $face.uv -or $null -eq $face.texture) {
            continue
        }
        $x1 = [double]$face.uv[0]
        $y1 = [double]$face.uv[1]
        $x2 = [double]$face.uv[2]
        $y2 = [double]$face.uv[3]
        $entry = [ordered]@{
            uv = @((Number-Value $x1), (Number-Value $y1))
            uv_size = @((Number-Value ($x2 - $x1)), (Number-Value ($y2 - $y1)))
        }
        if ($face.rotation) { $entry.uv_rotation = [int]$face.rotation }
        $uv[$faceName] = $entry
    }
    $cube = [ordered]@{
        origin = $origin
        size = @(
            Number-Value ([Math]::Abs($to[0] - $from[0]))
            Number-Value ([Math]::Abs($to[1] - $from[1]))
            Number-Value ([Math]::Abs($to[2] - $from[2]))
        )
    }
    if ($uv.Count -gt 0) { $cube.uv = $uv }
    $rotation = Converted-Rotation $element.rotation
    if ($rotation.Count -eq 3 -and @($rotation | Where-Object { $_ -ne 0 }).Count -gt 0) {
        $cube.rotation = $rotation
        if ($null -ne $element.origin) {
            $cube.pivot = Converted-Point $element.origin $wearable
        }
    }
    return $cube
}

function Build-JetBones($source, [bool]$wearable) {
    $elements = @{}
    foreach ($element in $source.elements) {
        $elements[[string]$element.uuid] = $element
    }
    $result = [System.Collections.Generic.List[object]]::new()

    function Add-Group($group, [string]$parent) {
        $bone = [ordered]@{
            name = [string]$group.name
            parent = $parent
            pivot = Converted-Point $group.origin $wearable
        }
        $rotation = Converted-Rotation $group.rotation
        if ($rotation.Count -eq 3 -and @($rotation | Where-Object { $_ -ne 0 }).Count -gt 0) {
            $bone.rotation = $rotation
        }
        $cubes = [System.Collections.Generic.List[object]]::new()
        $flameCubes = [System.Collections.Generic.List[object]]::new()
        foreach ($child in @($group.children)) {
            if ($child -is [string]) {
                $element = $elements[$child]
                if ($null -ne $element -and $element.type -eq "locator") {
                    $result.Add([ordered]@{
                        name = [string]$element.name
                        parent = [string]$group.name
                        pivot = Converted-Point $element.position $wearable
                    })
                } elseif ($null -ne $element -and $element.type -eq "cube") {
                    $textureIds = @($element.faces.PSObject.Properties.Value.texture |
                            ForEach-Object { [string]$_ } | Select-Object -Unique)
                    if ($textureIds -contains "1" -or $textureIds -contains "#1") {
                        $flameCubes.Add((Convert-Cube $element $wearable))
                    } else {
                        $cubes.Add((Convert-Cube $element $wearable))
                    }
                }
            } elseif ($null -ne $child -and $child.name) {
                Add-Group $child $group.name
            }
        }
        if ($cubes.Count -gt 0) { $bone.cubes = @($cubes) }
        $result.Add($bone)
        if ($flameCubes.Count -gt 0) {
            $result.Add([ordered]@{
                name = ([string]$group.name + "_flame")
                parent = [string]$group.name
                pivot = Converted-Point $group.origin $wearable
                cubes = @($flameCubes)
            })
        }
    }

    foreach ($name in @("jet_F", "jet_left", "jet_right", "missile_box")) {
        Add-Group (Find-Group $source.outliner $name) "combat_backpack"
    }
    return @($result)
}

function Animation-Time($value) {
    return [string](Number-Value ([Math]::Round([double]$value, 4)))
}

function Build-JumpAnimations($source, [string]$existingPath) {
    $existing = Read-Json $existingPath
    $names = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($animationProperty in $existing.animations.PSObject.Properties) {
        $bonesProperty = $animationProperty.Value.PSObject.Properties["bones"]
        if ($null -eq $bonesProperty) { continue }
        foreach ($boneProperty in $bonesProperty.Value.PSObject.Properties) {
            [void]$names.Add($boneProperty.Name)
        }
    }
    foreach ($name in @("jet_F", "jet_F2", "jet_left", "jet_right",
            "missile_box", "missile_left", "missile_right",
            "port1_left", "port2_left", "port3_left",
            "port1_right", "port2_right", "port3_right")) {
        [void]$names.Add($name)
    }

    foreach ($animation in $source.animations) {
        if ($animation.name -notin @("ready_to_fly", "flying", "close",
                "misslie_aim", "misslie_CD")) { continue }
        $timeScale = $(if ($animation.name -eq "ready_to_fly") { 0.25 } else { 1.0 })
        $bones = [ordered]@{}
        foreach ($animator in $animation.animators.PSObject.Properties.Value) {
            if ($animator.type -ne "bone" -or -not $names.Contains([string]$animator.name)) {
                continue
            }
            $channelFrames = [ordered]@{}
            foreach ($keyframe in $animator.keyframes) {
                if ($keyframe.channel -notin @("rotation", "position", "scale")) { continue }
                $point = @($keyframe.data_points)[0]
                if ($null -eq $point) { continue }
                $channelName = [string]$keyframe.channel
                if (-not $channelFrames.Contains($channelName)) {
                    $channelFrames[$channelName] =
                        [System.Collections.Generic.List[object]]::new()
                }
                $vector = @(
                    Number-Value $(if ($null -eq $point.x) { 0 } else { $point.x })
                    Number-Value $(if ($null -eq $point.y) { 0 } else { $point.y })
                    Number-Value $(if ($null -eq $point.z) { 0 } else { $point.z })
                )
                if ($channelName -eq "rotation") {
                    $vector = @(
                        Number-Value (-[double]$vector[0])
                        Number-Value (-[double]$vector[1])
                        Number-Value ([double]$vector[2])
                    )
                } elseif ($channelName -eq "position") {
                    $vector = @(
                        Number-Value (-[double]$vector[0])
                        Number-Value ([double]$vector[1])
                        Number-Value ([double]$vector[2])
                    )
                }
                $channelFrames[$channelName].Add([pscustomobject]@{
                    Time = [double]$keyframe.time
                    Data = [ordered]@{
                        vector = $vector
                    }
                })
            }
            $channels = [ordered]@{}
            foreach ($channelName in $channelFrames.Keys) {
                $values = [ordered]@{}
                foreach ($frame in ($channelFrames[$channelName] |
                        Sort-Object Time)) {
                    $values[(Animation-Time ($frame.Time * $timeScale))] = $frame.Data
                }
                $channels[$channelName] = $values
            }
            if ($channels.Count -gt 0) { $bones[[string]$animator.name] = $channels }
        }
        $length = [double]$animation.length
        if ($length -le 0.0) {
            foreach ($animator in $animation.animators.PSObject.Properties.Value) {
                foreach ($keyframe in $animator.keyframes) {
                    $length = [Math]::Max($length, [double]$keyframe.time)
                }
            }
        }
        $converted = [ordered]@{
            loop = "hold_on_last_frame"
            animation_length = Number-Value ($length * $timeScale)
        }
        if ($bones.Count -gt 0) { $converted.bones = $bones }
        $existing.animations | Add-Member -NotePropertyName $animation.name `
            -NotePropertyValue ([pscustomobject]$converted) -Force
    }
    return $existing
}

function Export-JavaItemModel($source, [string]$texturePath) {
    $elements = [System.Collections.Generic.List[object]]::new()
    foreach ($src in $source.elements) {
        $faces = [ordered]@{}
        foreach ($faceName in @("north", "east", "south", "west", "up", "down")) {
            $sourceFace = $src.faces.$faceName
            if ($null -eq $sourceFace -or $null -eq $sourceFace.uv `
                    -or $null -eq $sourceFace.texture) {
                continue
            }
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
            from = @($src.from | ForEach-Object { Number-Value $_ })
            to = @($src.to | ForEach-Object { Number-Value $_ })
            faces = $faces
        }
        if ($null -ne $src.rotation -and $null -ne $src.rotation.angle `
                -and [Math]::Abs([double]$src.rotation.angle) -gt 0.00001) {
            $element.rotation = [ordered]@{
                origin = @($src.rotation.origin | ForEach-Object { Number-Value $_ })
                axis = [string]$src.rotation.axis
                angle = Number-Value $src.rotation.angle
                rescale = $false
            }
        }
        $elements.Add($element)
    }
    $display = [ordered]@{}
    foreach ($key in @("thirdperson_righthand", "thirdperson_lefthand",
            "firstperson_righthand", "firstperson_lefthand", "ground", "gui", "fixed")) {
        if ($null -eq $source.display.$key) { continue }
        $entry = [ordered]@{}
        foreach ($field in @("rotation", "translation", "scale")) {
            if ($null -ne $source.display.$key.$field) {
                $entry[$field] = @($source.display.$key.$field)
            }
        }
        $display[$key] = $entry
    }
    return [ordered]@{
        credit = "Converted from author-provided jump thruster model"
        texture_size = @(48, 48)
        textures = [ordered]@{
            "0" = $texturePath
            particle = $texturePath
        }
        gui_light = $(if ($source.front_gui_light) { "front" } else { "side" })
        elements = @($elements)
        display = $display
    }
}

if (-not $SourceDir) {
    $models = Get-ChildItem -LiteralPath "D:\Download\USER\work\AModel" `
        -Filter "*.bbmodel" -File -Recurse
    foreach ($file in $models) {
        $probe = Read-Json $file.FullName
        if (@($probe.animations | Where-Object { $_.name -eq "ready_to_fly" }).Count -gt 0) {
            foreach ($sibling in Get-ChildItem -LiteralPath $file.DirectoryName `
                    -Filter "*.bbmodel" -File) {
                $candidate = Read-Json $sibling.FullName
                if ((Find-Group $candidate.outliner "jet_F") `
                        -and -not (Find-Group $candidate.outliner "core")) {
                    $SourceDir = $file.DirectoryName
                    break
                }
            }
        }
        if ($SourceDir) { break }
    }
    if (-not $SourceDir) {
        throw "Jump backpack flight and thruster models were not found."
    }
}

$flight = $null
$thruster = $null
$missileBox = $null
foreach ($modelFile in Get-ChildItem -LiteralPath $SourceDir -Filter "*.bbmodel" -File) {
    $candidate = Read-Json $modelFile.FullName
    if (@($candidate.animations |
            Where-Object { $_.name -eq "ready_to_fly" }).Count -gt 0) {
        $flight = $candidate
        continue
    }
    $jet = Find-Group $candidate.outliner "jet_F"
    $core = Find-Group $candidate.outliner "core"
    $elementCount = @($candidate.elements).Count
    if ($candidate.meta.model_format -eq "java_block" -and $elementCount -eq 2) {
        $missileBox = $candidate
    } elseif ($null -ne $jet -and $null -eq $core) {
        $thruster = $candidate
    }
}
if ($null -eq $flight -or $null -eq $thruster -or $null -eq $missileBox) {
    throw "Could not identify the jump backpack flight and thruster models."
}

foreach ($target in @(
        @{ Path = Join-Path $entityDir "combat_backpack.geo.json"; Wearable = $true },
        @{ Path = Join-Path $entityDir "combat_backpack_rig.geo.json"; Wearable = $false })) {
    $geometry = Read-Json $target.Path
    $bones = @($geometry."minecraft:geometry"[0].bones | Where-Object {
        $_.name -notlike "jet*" -and
        $_.name -notlike "missile_*" -and
        $_.name -notlike "port*"
    })
    $geometry."minecraft:geometry"[0].bones = $bones + @(Build-JetBones $flight $target.Wearable)
    $geometry."minecraft:geometry"[0].description.texture_width = 48
    $geometry."minecraft:geometry"[0].description.texture_height = 48
    Write-Json $target.Path $geometry
}

Write-Json $animationPath (Build-JumpAnimations $flight $animationPath)
$thrusterTexture = $thruster.textures | Select-Object -First 1
[IO.File]::WriteAllBytes($itemTexturePath,
    [Convert]::FromBase64String(($thrusterTexture.source -split ",", 2)[1]))
Write-Json $itemModelPath (Export-JavaItemModel $thruster "tkgun:item/jump_backpack_thruster")
$missileTexture = $missileBox.textures | Select-Object -First 1
[IO.File]::WriteAllBytes($missileItemTexturePath,
    [Convert]::FromBase64String(($missileTexture.source -split ",", 2)[1]))
Write-Json $missileItemModelPath (Export-JavaItemModel $missileBox "tkgun:item/missile_box")

$blaze = $flight.textures | Where-Object { $_.id -eq "1" } | Select-Object -First 1
$blazeBytes = [Convert]::FromBase64String(($blaze.source -split ",", 2)[1])
$baseTexture = $flight.textures | Where-Object { $_.id -eq "0" } | Select-Object -First 1
$baseBytes = [Convert]::FromBase64String(($baseTexture.source -split ",", 2)[1])

foreach ($file in Get-ChildItem -LiteralPath $textureDir -Filter "combat_backpack*.png" -File |
        Where-Object { $_.Name -ne "combat_backpack_flame.png" }) {
    if ($file.Name -eq "combat_backpack.png") {
        [IO.File]::WriteAllBytes($file.FullName, $baseBytes)
        continue
    }
    $templatePath = Join-Path (Join-Path $Resources "assets\tkgun\textures\item") $file.Name
    if (-not (Test-Path -LiteralPath $templatePath)) {
        throw "Missing palette template: $templatePath"
    }
    $baseStream = [IO.MemoryStream]::new($baseBytes)
    $baseSource = [Drawing.Bitmap]::FromStream($baseStream)
    $template = [Drawing.Bitmap]::new($templatePath)
    $output = [Drawing.Bitmap]::new(48, 48,
        [Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $colourMap = @{}
        for ($y = 0; $y -lt 16; $y++) {
            for ($x = 0; $x -lt 48; $x++) {
                $sourcePixel = $baseSource.GetPixel($x, $y)
                $targetPixel = $template.GetPixel($x, $y)
                if ($sourcePixel.A -gt 0 -and $targetPixel.A -gt 0) {
                    $colourMap[$sourcePixel.ToArgb()] = $targetPixel
                }
            }
        }
        $palette = @($colourMap.GetEnumerator() | ForEach-Object {
            $colour = [Drawing.Color]::FromArgb([int]$_.Key)
            [pscustomobject]@{
                R = [int]$colour.R
                G = [int]$colour.G
                B = [int]$colour.B
                Target = $_.Value
            }
        })
        for ($y = 0; $y -lt 48; $y++) {
            for ($x = 0; $x -lt 48; $x++) {
                $sourcePixel = $baseSource.GetPixel($x, $y)
                if ($sourcePixel.A -eq 0) {
                    $output.SetPixel($x, $y, [Drawing.Color]::Transparent)
                    continue
                }
                $mapped = $colourMap[$sourcePixel.ToArgb()]
                if ($null -eq $mapped) {
                    $bestDistance = [int]::MaxValue
                    foreach ($entry in $palette) {
                        $dr = $sourcePixel.R - $entry.R
                        $dg = $sourcePixel.G - $entry.G
                        $db = $sourcePixel.B - $entry.B
                        $distance = $dr * $dr + $dg * $dg + $db * $db
                        if ($distance -lt $bestDistance) {
                            $bestDistance = $distance
                            $mapped = $entry.Target
                        }
                    }
                }
                if ($null -eq $mapped) {
                    $mapped = $sourcePixel
                }
                $output.SetPixel($x, $y,
                    [Drawing.Color]::FromArgb($sourcePixel.A,
                        $mapped.R, $mapped.G, $mapped.B))
            }
        }
        $temporaryPath = $file.FullName + ".generated.png"
        $output.Save($temporaryPath, [Drawing.Imaging.ImageFormat]::Png)
        Move-Item -LiteralPath $temporaryPath -Destination $file.FullName -Force
    } finally {
        $output.Dispose()
        $template.Dispose()
        $baseSource.Dispose()
        $baseStream.Dispose()
    }
}

$flamePath = Join-Path $textureDir "combat_backpack_flame.png"
[IO.File]::WriteAllBytes($flamePath, $blazeBytes)
Write-Json ($flamePath + ".mcmeta") ([ordered]@{
    animation = [ordered]@{
        frametime = [int]$blaze.frame_time
        frames = @(0, 1, 2)
    }
})

Get-ChildItem -LiteralPath $textureDir -Filter "combat_backpack*.png.mcmeta" -File |
    Where-Object { $_.Name -ne "combat_backpack_flame.png.mcmeta" } |
    Remove-Item -Force

Write-Output "Jump backpack geometry, animations, item model, and flame texture generated."
