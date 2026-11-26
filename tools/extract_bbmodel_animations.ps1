param(
    [Parameter(Mandatory = $true)][string]$Model,
    [Parameter(Mandatory = $true)][string]$Output,
    [switch]$NoAxisConversion
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$script:skipAxisConversion = [bool]$NoAxisConversion

function Convert-Loop([object]$value) {
    if ($null -eq $value) {
        return "once"
    }
    if ($value -is [bool]) {
        return $(if ($value) { "loop" } else { "hold_on_last_frame" })
    }
    $text = [string]$value
    if ($text -eq "hold" -or $text -eq "hold_on_last_frame") {
        return "hold_on_last_frame"
    }
    if ($text -eq "loop" -or $text -eq "true") {
        return "loop"
    }
    return "once"
}

function Convert-Vector([object]$point) {
    return @([double]$point.x, [double]$point.y, [double]$point.z)
}

function Convert-Keyframe([object]$keyframe, [string]$channel,
        [object]$previousKeyframe) {
    $points = @($keyframe.data_points)
    if ($points.Count -le 1) {
        if ($null -ne $previousKeyframe -and
                [string]$previousKeyframe.interpolation -eq "step") {
            $previousPoints = @($previousKeyframe.data_points)
            return [ordered]@{
                pre = Convert-Point $previousPoints[
                        $previousPoints.Count - 1] $channel
                post = Convert-Point $points[0] $channel
            }
        }
        return Convert-Point $points[0] $channel
    }
    $result = [ordered]@{}
    if ($points.Count -ge 1) {
        $result.pre = Convert-Point $points[0] $channel
    }
    if ($points.Count -ge 2) {
        $result.post = Convert-Point $points[1] $channel
    }
    return $result
}

function Convert-Point([object]$point, [string]$channel) {
    $value = Convert-Vector $point
    if ($script:skipAxisConversion) {
        return $value
    }
    if ($channel -eq "position") {
        $value[0] = -$value[0]
    } elseif ($channel -eq "rotation") {
        $value[0] = -$value[0]
        $value[1] = -$value[1]
    }
    return $value
}

$document = Get-Content -Raw -Encoding UTF8 $Model | ConvertFrom-Json
$animations = [ordered]@{}

foreach ($animation in $document.animations) {
    $bones = [ordered]@{}
    foreach ($animatorProperty in $animation.animators.PSObject.Properties) {
        $animator = $animatorProperty.Value
        if ([string]$animator.type -ne "bone" -or -not $animator.name) {
            continue
        }
        $boneName = [string]$animator.name
        $bone = if ($bones.Contains($boneName)) {
            $bones[$boneName]
        } else {
            $value = [ordered]@{}
            $bones[$boneName] = $value
            $value
        }

        foreach ($channelName in "rotation", "position", "scale") {
            $keyframes = @($animator.keyframes |
                    Where-Object { $_.channel -eq $channelName } |
                    Sort-Object { [double]$_.time })
            if ($keyframes.Count -eq 0) {
                continue
            }
            if ($keyframes.Count -eq 1) {
                $bone[$channelName] = Convert-Keyframe $keyframes[0] `
                    $channelName $null
                continue
            }

            $channel = [ordered]@{}
            for ($kfIndex = 0; $kfIndex -lt $keyframes.Count; $kfIndex++) {
                $keyframe = $keyframes[$kfIndex]
                $previousKeyframe = if ($kfIndex -gt 0) {
                    $keyframes[$kfIndex - 1]
                } else {
                    $null
                }
                $time = [double]$keyframe.time
                $key = ("{0:0.0000}" -f $time).TrimEnd("0").TrimEnd(".")
                if (-not $key) {
                    $key = "0"
                }
                $channel[$key] = Convert-Keyframe $keyframe `
                    $channelName $previousKeyframe
            }
            $bone[$channelName] = $channel
        }
    }

    $animations[[string]$animation.name] = [ordered]@{
        loop = Convert-Loop $animation.loop
        animation_length = [double]$animation.length
        bones = $bones
    }
}

$result = [ordered]@{
    format_version = "1.8.0"
    animations = $animations
}
New-Item -ItemType Directory -Force -Path (Split-Path $Output -Parent) |
    Out-Null
[IO.File]::WriteAllText(
    $Output,
    ($result | ConvertTo-Json -Depth 100 -Compress),
    (New-Object Text.UTF8Encoding($false)))
Write-Output "Wrote $Output"
