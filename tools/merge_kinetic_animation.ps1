param(
    [Parameter(Mandatory = $true)]
    [string]$Source,
    [Parameter(Mandatory = $true)]
    [string]$Target,
    [Parameter(Mandatory = $true)]
    [string[]]$Animations
)

$ErrorActionPreference = "Stop"
$invariant = [Globalization.CultureInfo]::InvariantCulture
$sourceModel = Get-Content -Raw -Encoding UTF8 -LiteralPath $Source |
        ConvertFrom-Json
$targetModel = Get-Content -Raw -Encoding UTF8 -LiteralPath $Target |
        ConvertFrom-Json

function Convert-Vector($point) {
    return @(
        [double]::Parse([string]$point.x, $invariant),
        [double]::Parse([string]$point.y, $invariant),
        [double]::Parse([string]$point.z, $invariant)
    )
}

function Convert-Time([double]$time) {
    return $time.ToString("0.####", $invariant)
}

foreach ($animationName in $Animations) {
    $animation = $sourceModel.animations |
            Where-Object { $_.name -eq $animationName } |
            Select-Object -First 1
    if ($null -eq $animation) {
        throw "Animation not found in source: $animationName"
    }

    $bones = [ordered]@{}
    $soundEffects = [ordered]@{}
    $timeline = [ordered]@{}
    foreach ($animatorProperty in $animation.animators.PSObject.Properties) {
        $animator = $animatorProperty.Value
        if ($animator.type -eq "effect") {
            foreach ($keyframe in $animator.keyframes) {
                if ($null -eq $keyframe.data_points -or
                        $keyframe.data_points.Count -eq 0) {
                    continue
                }
                $time = [double]::Parse([string]$keyframe.time, $invariant)
                $timeKey = Convert-Time $time
                $point = $keyframe.data_points[0]
                if ($keyframe.channel -eq "sound" -and
                        -not [string]::IsNullOrWhiteSpace(
                                [string]$point.effect)) {
                    $soundEffects[$timeKey] = [ordered]@{
                        effect = [string]$point.effect
                    }
                } elseif ($keyframe.channel -eq "timeline" -and
                        -not [string]::IsNullOrWhiteSpace(
                                [string]$point.script)) {
                    $timeline[$timeKey] = [string]$point.script
                }
            }
            continue
        }
        if ($animator.type -ne "bone") {
            continue
        }
        $channels = [ordered]@{}
        foreach ($channel in @("rotation", "position", "scale")) {
            $frames = [ordered]@{}
            $sourceFrames = $animator.keyframes |
                    Where-Object { $_.channel -eq $channel } |
                    Sort-Object { [double]$_.time }
            foreach ($keyframe in $sourceFrames) {
                if ($null -eq $keyframe.data_points -or
                        $keyframe.data_points.Count -eq 0) {
                    continue
                }
                $time = [double]::Parse([string]$keyframe.time, $invariant)
                $frames[(Convert-Time $time)] =
                        Convert-Vector $keyframe.data_points[0]
            }
            if ($frames.Count -gt 0) {
                $channels[$channel] = $frames
            }
        }
        if ($channels.Count -gt 0) {
            $bones[$animator.name] = $channels
        }
    }

    $loop = if ($animation.loop -eq $true) {
        $true
    } elseif ($animation.loop -eq "hold") {
        "hold_on_last_frame"
    } else {
        $animation.loop
    }
    $clip = [ordered]@{
        loop = $loop
        animation_length = [double]::Parse(
                [string]$animation.length, $invariant)
        bones = $bones
    }
    if ($soundEffects.Count -gt 0) {
        $clip.sound_effects = $soundEffects
    }
    if ($timeline.Count -gt 0) {
        $clip.timeline = $timeline
    }

    if ($targetModel.animations.PSObject.Properties.Name -contains
            $animationName) {
        $targetModel.animations.$animationName = $clip
    } else {
        $targetModel.animations | Add-Member -NotePropertyName $animationName `
                -NotePropertyValue $clip
    }
    Write-Output ("Merged {0}: length={1}, bones={2}" -f `
            $animationName, $clip.animation_length, $bones.Count)
}

$json = $targetModel | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText($Target, $json,
        (New-Object Text.UTF8Encoding($false)))
Write-Output "Wrote $Target"
