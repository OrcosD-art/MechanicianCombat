param(
    [string]$SourceModel = "",
    [string]$AnimationJson = "D:\codex\mod\rebuild\mod\src\main\resources\assets\tkgun\animations\entity\bowgun_rig.animation.json"
)

$ErrorActionPreference = "Stop"
if ([string]::IsNullOrWhiteSpace($SourceModel)) {
    $SourceModel = Get-ChildItem -LiteralPath "D:\Download\USER\work\AModel" `
        -Filter "BG261015.bbmodel" -File -Recurse |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if ([string]::IsNullOrWhiteSpace($SourceModel)) {
    throw "BG261015.bbmodel was not found under D:\Download\USER\work\AModel"
}

function Read-BlockbenchJson([string]$path) {
    $raw = [IO.File]::ReadAllText($path)
    $evaluator = [Text.RegularExpressions.MatchEvaluator] {
        param($match)
        $slashes = $match.Value
        $nextIndex = $match.Index + $match.Length
        $next = if ($nextIndex -lt $raw.Length) { $raw[$nextIndex] } else { [char]0 }
        if (($slashes.Length % 2) -eq 1 -and '"\/bfnrtu'.Contains([string]$next)) {
            return $slashes
        }
        if (($slashes.Length % 2) -eq 1) {
            return $slashes + '\'
        }
        return $slashes
    }
    return [regex]::Replace($raw, '\\+', $evaluator) | ConvertFrom-Json
}

function Vector-Of($point) {
    return @([double]$point.x, [double]$point.y, [double]$point.z)
}

$source = Read-BlockbenchJson $SourceModel
$animation = [IO.File]::ReadAllText($AnimationJson) | ConvertFrom-Json
$repaired = 0

foreach ($sourceAnimation in $source.animations) {
    $targetAnimation = $animation.animations.($sourceAnimation.name)
    if ($null -eq $targetAnimation) {
        continue
    }
    foreach ($animatorProperty in $sourceAnimation.animators.PSObject.Properties) {
        $animator = $animatorProperty.Value
        if ($animator.type -ne "bone") {
            continue
        }
        $targetChannels = $targetAnimation.bones.($animator.name)
        if ($null -eq $targetChannels) {
            continue
        }
        foreach ($channelProperty in $targetChannels.PSObject.Properties) {
            $channelName = $channelProperty.Name
            $targetKeys = $channelProperty.Value
            if ($null -eq $targetKeys -or $targetKeys -is [array]) {
                continue
            }
            $stepKeys = @($animator.keyframes |
                Where-Object {
                    $_.channel -eq $channelName -and
                    ([string]$_.interpolation).ToLowerInvariant() -eq "step"
                } |
                Sort-Object { [double]$_.time })
            for ($index = 0; $index -lt $stepKeys.Count - 1; $index++) {
                $holdTime = [Math]::Round([double]$stepKeys[$index + 1].time - 0.0001, 4)
                $targetProperty = $targetKeys.PSObject.Properties |
                    Where-Object { [Math]::Abs([double]$_.Name - $holdTime) -lt 0.000011 } |
                    Select-Object -First 1
                if ($null -eq $targetProperty) {
                    continue
                }
                $value = $targetProperty.Value
                if ($null -eq $value -or $value -is [array]) {
                    continue
                }
                $held = Vector-Of $stepKeys[$index].data_points[0]
                $value.pre = $held
                $value.post = $held
                $repaired++
            }
        }
    }
}

$json = $animation | ConvertTo-Json -Depth 100 -Compress
[IO.File]::WriteAllText($AnimationJson, $json, [Text.UTF8Encoding]::new($false))
Write-Output "Repaired $repaired step hold frames in $AnimationJson"
