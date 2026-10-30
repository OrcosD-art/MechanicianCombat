param(
    [Parameter(Mandatory = $true)]
    [string]$ModelPath,
    [Parameter(Mandatory = $true)]
    [string]$AnimationPath,
    [Parameter(Mandatory = $true)]
    [string]$AnimationName,
    [switch]$IncludeLegs
)

$ErrorActionPreference = "Stop"
$invariant = [Globalization.CultureInfo]::InvariantCulture

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

$rawModel = [IO.File]::ReadAllText($ModelPath, [Text.Encoding]::UTF8)
$validatedJson = [regex]::Replace(
    $rawModel, '\\(?![\\"/bfnrtu])', '/')
$model = $validatedJson | ConvertFrom-Json
$source = $model.animations |
    Where-Object { $_.name -eq $AnimationName } |
    Select-Object -First 1
if ($null -eq $source) {
    throw "Missing source animation: $AnimationName"
}

$converted = [ordered]@{
    loop = if ($source.loop -eq "hold" -or $source.loop -eq $false) {
        "hold_on_last_frame"
    } else {
        $source.loop
    }
    animation_length = Number $source.length
    bones = [ordered]@{}
}
$sounds = [ordered]@{}
$particles = [ordered]@{}
$timeline = [ordered]@{}

foreach ($animator in $source.animators.PSObject.Properties.Value) {
    if ($animator.type -eq "bone") {
        if ((-not $IncludeLegs) -and ($animator.name -in @("leg_left", "leg_right"))) {
            continue
        }
        $bone = [ordered]@{}
        $grouped = [ordered]@{}
        foreach ($keyframe in $animator.keyframes) {
            if (-not $grouped.Contains($keyframe.channel)) {
                $grouped[$keyframe.channel] = New-Object System.Collections.ArrayList
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
                $values[(TimeKey $next.time)] = [pscustomobject][ordered]@{
                    pre = (VectorValue $keys[$index].data_points[0]).vector
                    post = (VectorValue $next.data_points[0]).vector
                }
            }
            $bone[$channel] = [pscustomobject]$values
        }
        if ($bone.Count -gt 0) {
            $converted.bones[$animator.name] = [pscustomobject]$bone
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
                if (-not [string]::IsNullOrWhiteSpace([string]$point.script)) {
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

$target = [IO.File]::ReadAllText(
    $AnimationPath, [Text.Encoding]::UTF8) | ConvertFrom-Json
$orderedAnimations = [ordered]@{}
$replaced = $false
foreach ($property in $target.animations.PSObject.Properties) {
    if ($property.Name -eq $AnimationName) {
        $orderedAnimations[$AnimationName] = [pscustomobject]$converted
        $replaced = $true
    } else {
        $orderedAnimations[$property.Name] = $property.Value
    }
}
if (-not $replaced) {
    $orderedAnimations[$AnimationName] = [pscustomobject]$converted
}
$target.animations = [pscustomobject]$orderedAnimations

$encoding = New-Object System.Text.UTF8Encoding($false)
$json = $target | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText($AnimationPath, $json, $encoding)
Write-Output ("Imported {0}: length={1}, bones={2}, sounds={3}" -f
        $AnimationName, $converted.animation_length,
        $converted.bones.Count, $sounds.Count)
