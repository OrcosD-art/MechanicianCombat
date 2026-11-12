param(
    [Parameter(Mandatory = $true)]
    [string]$SourceRoot,
    [string]$TargetRoot = "D:\codex\mod\rebuild\mod\src\main\resources\assets\mechaniciancombat"
)

$ErrorActionPreference = "Stop"
$invariant = [Globalization.CultureInfo]::InvariantCulture
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$modelPath = Join-Path $SourceRoot "close_combat.bbmodel"
$textureSource = Join-Path $SourceRoot "close_combat_effect"
$effectSheetSource = Join-Path $textureSource "close_combat_effect-sheet.png"
$geoPath = Join-Path $TargetRoot "geo\entity\veritas_et_justitia_rig.geo.json"
$animationPath = Join-Path $TargetRoot "animations\entity\veritas_et_justitia_rig.animation.json"
$textureTarget = Join-Path $TargetRoot "textures\entity"

function Number([object]$value) {
    return [double]::Parse([string]$value, $invariant)
}

function TimeKey([object]$value) {
    $number = [Math]::Round((Number $value), 5)
    if ($number -eq [Math]::Floor($number)) {
        return [string][long]$number
    }
    return $number.ToString("0.#####", $invariant)
}

function VectorValue([object]$point) {
    return [pscustomobject][ordered]@{
        vector = @((Number $point.x), (Number $point.y), (Number $point.z))
    }
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

function Direct-CubeElements($group, $elements) {
    foreach ($child in @($group.children)) {
        if ($child -isnot [string]) {
            continue
        }
        $element = $elements[[string]$child]
        if ($null -ne $element -and $element.type -eq "cube") {
            Write-Output $element
        }
    }
}

function Effect-TextureIndex($element) {
    foreach ($face in $element.faces.PSObject.Properties) {
        if ($null -ne $face.Value.texture) {
            return [int]$face.Value.texture
        }
    }
    return -1
}

function Convert-EffectCube($element, [double]$uvScaleX,
        [double]$uvScaleY) {
    $textureIndex = Effect-TextureIndex $element
    if ($textureIndex -lt 0) {
        throw "Effect cube $($element.uuid) has no texture"
    }
    $uv = [ordered]@{}
    foreach ($face in $element.faces.PSObject.Properties) {
        if ($null -eq $face.Value.texture -or
                [int]$face.Value.texture -ne $textureIndex) {
            continue
        }
        $values = @($face.Value.uv | ForEach-Object { Number $_ })
        $uv[$face.Name] = [pscustomobject][ordered]@{
            # Top faces are emitted from the mirrored lower-right origin.
            uv = @(($values[2] * $uvScaleX),
                   ($values[3] * $uvScaleY))
            uv_size = @((($values[0] - $values[2]) * $uvScaleX),
                        (($values[1] - $values[3]) * $uvScaleY))
        }
    }
    if ($uv.Count -eq 0) {
        throw "Effect cube $($element.uuid) has no texture $textureIndex faces"
    }
    $from = @($element.from | ForEach-Object { Number $_ })
    $to = @($element.to | ForEach-Object { Number $_ })
    # GeckoLib's free-model export mirrors the X axis. Mirror the interval
    # rather than negating size, so the plane keeps positive dimensions.
    return [pscustomobject][ordered]@{
        origin = @((-$to[0]), $from[1], $from[2])
        size = @(($to[0] - $from[0]),
                 ($to[1] - $from[1]),
                 ($to[2] - $from[2]))
        uv = [pscustomobject]$uv
    }
}

$rawModel = [IO.File]::ReadAllText($modelPath, [Text.Encoding]::UTF8)
$modelJson = [regex]::Replace($rawModel, '\\(?![\\"/bfnrtu])', '/')
$model = $modelJson | ConvertFrom-Json
$elements = @{}
foreach ($element in $model.elements) {
    $elements[[string]$element.uuid] = $element
}

$groupSpecs = New-Object System.Collections.ArrayList
function Collect-EffectGroups($nodes, [string]$parent, [bool]$inside) {
    foreach ($node in @($nodes)) {
        if ($node -is [string]) {
            continue
        }
        $name = [string]$node.name
        $isEffect = $inside -or $name.StartsWith("effect_")
        if ($isEffect) {
            [void]$groupSpecs.Add([pscustomobject]@{
                Name = $name
                Parent = if ($parent) { $parent } else { $null }
            })
        }
        if ($node.children) {
            Collect-EffectGroups $node.children `
                $(if ($isEffect) { $name } else { $parent }) $isEffect
        }
    }
}
Collect-EffectGroups $model.outliner "" $false
if ($groupSpecs.Count -eq 0) {
    throw "No close-combat effect groups were found"
}

$textureSizes = @{}
foreach ($texture in $model.textures) {
    $textureSizes[[string]$texture.id] =
        @([double]$texture.width, [double]$texture.height)
}

$effectBones = [ordered]@{}
foreach ($spec in $groupSpecs) {
    $group = Find-Group $model.outliner $spec.Name
    if ($null -eq $group) {
        throw "Missing close-combat effect group: $($spec.Name)"
    }
    $sourcePivot = @($group.origin | ForEach-Object { Number $_ })
    $pivot = @((-$sourcePivot[0]), $sourcePivot[1], $sourcePivot[2])
    $bone = [ordered]@{
        name = $spec.Name
        pivot = $pivot
    }
    if ($null -ne $spec.Parent) {
        $bone.parent = $spec.Parent
    }
    if ($null -ne $group.rotation) {
        $rotation = @($group.rotation | ForEach-Object { Number $_ })
        $bone.rotation = @(
            (-$rotation[0]), (-$rotation[1]), $rotation[2])
    }
    $cubes = @(Direct-CubeElements $group $elements)
    if ($cubes.Count -gt 0) {
        $bone.cubes = @($cubes | ForEach-Object {
            $textureIndex = Effect-TextureIndex $_
            $size = $textureSizes[[string]$textureIndex]
            if ($null -eq $size) {
                throw "Unknown effect texture index $textureIndex"
            }
            Convert-EffectCube $_ (64.0D / $size[0]) (64.0D / $size[1])
        })
    } elseif ($spec.Name -match '^1_') {
        $childSummary = @($group.children | ForEach-Object {
            $id = [string]$_
            $entry = $elements[$id]
            "{0}:{1}:found={2}:type={3}" -f $_.GetType().Name, $id,
                ($null -ne $entry), $(if ($null -eq $entry) { "" } else {
                    [string]$entry.type
                })
        }) -join "; "
        throw "Effect leaf $($spec.Name) has no cube children ($childSummary)"
    }
    $effectBones[$spec.Name] = [pscustomobject]$bone
}

$geo = [IO.File]::ReadAllText($geoPath, [Text.Encoding]::UTF8) |
    ConvertFrom-Json
$geometry = $geo.'minecraft:geometry'[0]
$keptBones = @($geometry.bones | Where-Object {
    [string]$_.name -notmatch '^(effect_|1_)'
})
$geometry.bones = @($keptBones + @($effectBones.Values))
$geoJson = $geo | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText(
    $geoPath, $geoJson, $utf8NoBom)

$animation = [IO.File]::ReadAllText(
    $animationPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
$importNames = @(
    "ATK_run", "ATK_1", "ATK_1_to_idle",
    "ATK_2", "ATK_2_to_idle",
    "ATK_3", "ATK_3_to_idle", "ATK_3_hold",
    "ATK_4", "ATK_4_to_idle", "ATK_hold_over", "run"
)
foreach ($name in $importNames) {
    $source = $model.animations |
        Where-Object { $_.name -eq $name } |
        Select-Object -First 1
    if ($null -eq $source) {
        throw "Missing close-combat animation: $name"
    }
    $converted = [ordered]@{
        loop = if ($source.loop -eq "loop" -or $source.loop -eq $true) {
            $true
        } else {
            "hold_on_last_frame"
        }
        animation_length = Number $source.length
        bones = [ordered]@{}
    }
    $sounds = [ordered]@{}
    $particles = [ordered]@{}
    $timeline = [ordered]@{}

    foreach ($animator in $source.animators.PSObject.Properties.Value) {
        if ($animator.type -eq "bone") {
            $bone = [ordered]@{}
            $grouped = [ordered]@{}
            foreach ($keyframe in $animator.keyframes) {
                if (-not $grouped.Contains($keyframe.channel)) {
                    $grouped[$keyframe.channel] =
                        New-Object System.Collections.ArrayList
                }
                [void]$grouped[$keyframe.channel].Add($keyframe)
            }
            foreach ($channel in $grouped.Keys) {
                $keys = @($grouped[$channel] |
                        Sort-Object { Number $_.time })
                $values = [ordered]@{}
                foreach ($keyframe in $keys) {
                    $values[(TimeKey $keyframe.time)] =
                        VectorValue $keyframe.data_points[0]
                }
                for ($index = 0; $index -lt $keys.Count - 1; $index++) {
                    if ([string]$keys[$index].interpolation -ne "step") {
                        continue
                    }
                    $next = $keys[$index + 1]
                    $values[(TimeKey $next.time)] =
                        [pscustomobject][ordered]@{
                            pre = (VectorValue $keys[$index].data_points[0]).vector
                            post = (VectorValue $next.data_points[0]).vector
                        }
                }
                $bone[$channel] = [pscustomobject]$values
            }
            if ($bone.Count -gt 0) {
                $converted.bones[$animator.name] =
                    [pscustomobject]$bone
            }
            continue
        }

        foreach ($keyframe in $animator.keyframes) {
            $key = TimeKey $keyframe.time
            $point = $keyframe.data_points[0]
            switch ($keyframe.channel) {
                "sound" {
                    $sounds[$key] = [pscustomobject][ordered]@{
                        effect = [string]$point.effect
                        locator = [string]$point.locator
                    }
                }
                "particle" {
                    $particles[$key] = [pscustomobject][ordered]@{
                        effect = [string]$point.effect
                        locator = [string]$point.locator
                    }
                }
                "timeline" {
                    if (-not [string]::IsNullOrWhiteSpace(
                            [string]$point.script)) {
                        $timeline[$key] = [string]$point.script
                    }
                }
            }
        }
    }
    if ($sounds.Count -gt 0) {
        $converted.sound_effects = [pscustomobject]$sounds
    }
    if ($particles.Count -gt 0) {
        $converted.particle_effects = [pscustomobject]$particles
    }
    if ($timeline.Count -gt 0) {
        $converted.timeline = [pscustomobject]$timeline
    }
    $animation.animations |
        Add-Member -NotePropertyName $name `
            -NotePropertyValue ([pscustomobject]$converted) -Force
}

foreach ($sourceName in @("ATK_1", "ATK_1_to_idle")) {
    $clone = $animation.animations.$sourceName |
        ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $keptChannels = [ordered]@{}
    foreach ($property in $clone.bones.PSObject.Properties) {
        if ($property.Name -notin @("leg_left", "leg_right")) {
            $keptChannels[$property.Name] = $property.Value
        }
    }
    $clone.bones = [pscustomobject]$keptChannels
    $animation.animations |
        Add-Member -NotePropertyName "${sourceName}_upper" `
            -NotePropertyValue $clone -Force
}

$effectBoneNames = @($effectBones.Keys)
foreach ($sourceName in @("ATK_1", "ATK_2", "ATK_4", "ATK_hold_over")) {
    $effectClip = $animation.animations.$sourceName |
        ConvertTo-Json -Depth 100 | ConvertFrom-Json
    $keptEffects = [ordered]@{}
    foreach ($property in $effectClip.bones.PSObject.Properties) {
        if ($property.Name -in $effectBoneNames) {
            $keptEffects[$property.Name] = $property.Value
        }
    }
    $effectClip.bones = [pscustomobject]$keptEffects
    $effectClip.loop = "hold_on_last_frame"
    foreach ($cueProperty in @(
            "sound_effects", "particle_effects", "timeline")) {
        $effectClip.PSObject.Properties.Remove($cueProperty)
    }
    $animation.animations |
        Add-Member -NotePropertyName "close_effect_$sourceName" `
            -NotePropertyValue $effectClip -Force
}

$animationJson = $animation | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText(
    $animationPath, $animationJson, $utf8NoBom)

New-Item -ItemType Directory -Force -Path $textureTarget | Out-Null
Copy-Item -LiteralPath $effectSheetSource `
    -Destination (Join-Path $textureTarget "close_combat_effect-sheet.png") -Force
Copy-Item -LiteralPath (Join-Path $textureSource "close_combat_effect2.png") `
    -Destination (Join-Path $textureTarget "close_combat_effect2.png") -Force

$soundTarget = Join-Path $TargetRoot "sounds"
$soundSource = Join-Path (Split-Path $SourceRoot -Parent) "sound"
if (Test-Path -LiteralPath $soundSource) {
    $soundMap = @{
        "anchor_slam-01.ogg" = "anchor_slam1.ogg"
        "double_axe_swing-01.ogg" = "double_axe_swing1.ogg"
        "glaive_slash-02.ogg" = "glaive_slash2.ogg"
        "slash.ogg" = "slash.ogg"
        "skill_spark.ogg" = "skill_spark.ogg"
        "smash_ground2.ogg" = "smash_ground2.ogg"
    }
    foreach ($entry in $soundMap.GetEnumerator()) {
        $sourceFile = Get-ChildItem -LiteralPath $soundSource -Recurse `
            -File -Filter $entry.Key | Select-Object -First 1
        if ($null -ne $sourceFile) {
            Copy-Item -LiteralPath $sourceFile.FullName `
                -Destination (Join-Path $soundTarget $entry.Value) -Force
        }
    }
}

$reloaded = [IO.File]::ReadAllText(
    $animationPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
$stepCount = 0
foreach ($name in $importNames) {
    foreach ($bone in $reloaded.animations.$name.bones.PSObject.Properties) {
        foreach ($channel in $bone.Value.PSObject.Properties) {
            foreach ($key in $channel.Value.PSObject.Properties) {
                if ($null -ne $key.Value.pre) {
                    $stepCount++
                }
            }
        }
    }
}

[pscustomobject]@{
    EffectGroups = (@($effectBones.Keys) -join ", ")
    ImportedAnimations = ($importNames -join ", ")
    PreservedStepKeys = $stepCount
    Geometry = $geoPath
    Animations = $animationPath
}
